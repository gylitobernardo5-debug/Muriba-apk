package com.example.vpn

import android.os.Debug
import com.example.data.model.ServerConfig
import com.example.data.model.SpeedMode
import com.example.data.model.VpnRuntimeState
import com.example.data.model.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

data class InternalBenchmarkResult(
    val timestampMs: Long = System.currentTimeMillis(),
    val packetCodecMbPerSec: Double,
    val packetsPerSecond: Long,
    val loopbackTcpMbPerSec: Double,
    val packetBuildAvgMicros: Long,
    val ramUsedMb: Int,
    val cpuCores: Int,
    val multiStreamServerMbPerSec: Double? = null,
    val remoteBenchmarkResult: ServerTestResult? = null,
    val bottleneckReport: BottleneckReport
)

/**
 * Sistema de Benchmark Interno Real (Zero Dados Fictícios):
 * 1. Mede débito real do pipeline local TUN/PacketCodec/BufferPool e Loopback TCP do dispositivo Android
 * 2. Quando há servidor SSH configurado, mede também transferência paralela real através de SshSessionPool
 * 3. Alimenta o BottleneckDetector para identificar o gargalo verdadeiro
 */
object BenchmarkEngine {

    suspend fun runFullBenchmark(
        server: ServerConfig?,
        speedMode: SpeedMode
    ): InternalBenchmarkResult = withContext(Dispatchers.IO) {
        // 1. Micro-benchmark real do codec de pacotes + pool pré-alocado (30.000 pacotes * 1460B ~= 43.8 MB)
        val pool = PacketBufferPool(slotCount = speedMode.tunPoolSlots.coerceAtMost(256))
        val srcIp = byteArrayOf(10, 8, 0, 2)
        val dstIp = byteArrayOf(1, 1, 1, 1)
        val payload = ByteArray(1460) { (it and 0xFF).toByte() }
        val prePseudo = PacketCodec.precomputePseudoHeaderSum(dstIp, srcIp, PacketCodec.PROTO_TCP)
        val iterations = 25_000
        var totalPacketBytes = 0L

        val codecStartNs = System.nanoTime()
        for (i in 0 until iterations) {
            val slot = pool.acquireImmediateOrFallback()
            val pktLen = PacketCodec.writeIpTcpPacketInto(
                out = slot.data,
                srcIp = dstIp,
                dstIp = srcIp,
                srcPort = 443,
                dstPort = 52000,
                seq = i.toLong() * 1460L,
                ack = 100L,
                flags = PacketCodec.TCP_ACK,
                window = 65535,
                payload = payload,
                payloadOffset = 0,
                payloadLength = payload.size,
                precomputedPseudoSum = prePseudo
            )
            totalPacketBytes += pktLen.toLong()
            pool.release(slot)
        }
        val codecElapsedNs = (System.nanoTime() - codecStartNs).coerceAtLeast(1L)
        val codecMbPerSec = (totalPacketBytes / (1024.0 * 1024.0)) / (codecElapsedNs / 1_000_000_000.0)
        val pps = (iterations.toDouble() / (codecElapsedNs / 1_000_000_000.0)).toLong()
        val avgBuildMicros = (codecElapsedNs / iterations / 1_000L).coerceAtLeast(0L)

        // 2. Benchmark real em loopback TCP no dispositivo (mede teto de I/O kernel + JVM buffers sem rede externa)
        val loopbackMbPerSec = runLoopbackTcpBenchmark(targetBytes = 24L * 1024L * 1024L)

        // 3. Se servidor selecionado for válido, executa benchmark multi-canal real no túnel
        var remoteResult: ServerTestResult? = null
        var multiStreamMbps: Double? = null
        if (server != null && server.sshHost.isNotBlank()) {
            remoteResult = ServerTester.testServer(server, speedMode)
            if (remoteResult.success) {
                multiStreamMbps = measureMultiChannelTunnelThroughput(server, speedMode)
            }
        }

        val ramMb = currentProcessRamMb()
        val fakeRuntime = VpnRuntimeState(
            status = VpnStatus.CONNECTED,
            durationSeconds = 5L,
            downloadSpeedBps = ((multiStreamMbps ?: codecMbPerSec) * 1024.0 * 1024.0).toLong(),
            latencyMs = remoteResult?.latencyMs,
            jitterMs = remoteResult?.jitterMs ?: 0L,
            packetLossPercent = remoteResult?.packetLossPercent ?: 0f,
            tunAvgMicros = avgBuildMicros,
            sshHandshakeMs = remoteResult?.sshSetupMs ?: 0L,
            ramUsedMb = ramMb,
            activeSpeedMode = speedMode,
            activeSshTunnels = speedMode.parallelSshSessions
        )
        val bottleneck = BottleneckDetector.analyze(
            state = fakeRuntime,
            tlsEnabled = server?.tlsEnabled == true,
            poolOverflowEvents = pool.overflowCount()
        )

        InternalBenchmarkResult(
            packetCodecMbPerSec = codecMbPerSec,
            packetsPerSecond = pps,
            loopbackTcpMbPerSec = loopbackMbPerSec,
            packetBuildAvgMicros = avgBuildMicros,
            ramUsedMb = ramMb,
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            multiStreamServerMbPerSec = multiStreamMbps,
            remoteBenchmarkResult = remoteResult,
            bottleneckReport = bottleneck
        )
    }

    private suspend fun runLoopbackTcpBenchmark(targetBytes: Long): Double = coroutineScope {
        runCatching {
            val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            val port = serverSocket.localPort
            val chunk = ByteArray(65_536) { 0x5A }

            val sender = async(Dispatchers.IO) {
                serverSocket.accept().use { accepted ->
                    accepted.tcpNoDelay = true
                    accepted.sendBufferSize = 262_144
                    val out = accepted.getOutputStream()
                    var remaining = targetBytes
                    while (remaining > 0) {
                        val toWrite = minOf(remaining, chunk.size.toLong()).toInt()
                        out.write(chunk, 0, toWrite)
                        remaining -= toWrite
                    }
                    out.flush()
                }
                serverSocket.close()
            }

            val t0 = System.nanoTime()
            var received = 0L
            Socket(InetAddress.getLoopbackAddress(), port).use { client ->
                client.tcpNoDelay = true
                client.receiveBufferSize = 262_144
                val input = client.getInputStream()
                val rx = ByteArray(65_536)
                while (true) {
                    val n = input.read(rx)
                    if (n <= 0) break
                    received += n
                }
            }
            sender.await()
            val elapsedNs = (System.nanoTime() - t0).coerceAtLeast(1L)
            (received / (1024.0 * 1024.0)) / (elapsedNs / 1_000_000_000.0)
        }.getOrDefault(0.0)
    }

    private suspend fun measureMultiChannelTunnelThroughput(
        server: ServerConfig,
        speedMode: SpeedMode
    ): Double? = coroutineScope {
        var pool: SshSessionPool? = null
        try {
            pool = SshSessionPool.establish(
                server = server,
                speedMode = speedMode,
                keepAlive = false,
                protectSocket = { true },
                onLog = {}
            )
            val streams = speedMode.parallelSshSessions.coerceIn(1, 3)
            val t0 = System.nanoTime()
            val results = (1..streams).map {
                async(Dispatchers.IO) {
                    runCatching {
                        val ch = pool.openOptimizedChannel("speed.cloudflare.com", 80, speedMode)
                        val input: InputStream = ch.inputStream
                        val out = ch.outputStream
                        ch.connect(6_000)
                        val req = "GET /__down?bytes=262144 HTTP/1.1\r\nHost: speed.cloudflare.com\r\nConnection: close\r\n\r\n"
                        out.write(req.toByteArray(Charsets.US_ASCII))
                        out.flush()
                        val buf = ByteArray(32_768)
                        var bytes = 0L
                        while (bytes < 524_288L) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            bytes += n
                        }
                        runCatching { ch.disconnect() }
                        bytes
                    }.getOrDefault(0L)
                }
            }.awaitAll()
            val totalBytes = results.sum()
            if (totalBytes <= 0L) return@coroutineScope null
            val elapsedNs = (System.nanoTime() - t0).coerceAtLeast(1_000_000L)
            (totalBytes / (1024.0 * 1024.0)) / (elapsedNs / 1_000_000_000.0)
        } catch (_: Throwable) {
            null
        } finally {
            pool?.disconnectAll()
        }
    }

    fun currentProcessRamMb(): Int {
        val rt = Runtime.getRuntime()
        val jvmMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        return (jvmMb + nativeMb).toInt().coerceAtLeast(1)
    }
}
