package com.example.vpn

import com.example.data.model.ServerConfig
import com.example.data.model.SpeedMode
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

data class ServerTestResult(
    val serverId: Long = 0L,
    val serverName: String = "",
    val success: Boolean,
    val latencyMs: Long,
    val jitterMs: Long = 0L,
    val tcpStabilityPercent: Int = 0,
    val packetLossPercent: Float = 0f,
    val tlsCompatible: Boolean? = null,
    val sshSetupMs: Long = 0L,
    val tlsSetupMs: Long = 0L,
    val transferSpeedBps: Long = 0L,
    val compositeScore: Int = 0,
    val resolvedIp: String,
    val details: List<String>,
    val errorMessage: String? = null
)

object ServerTester {

    suspend fun testServer(
        config: ServerConfig,
        speedMode: SpeedMode = SpeedMode.ULTRA_TURBO
    ): ServerTestResult = withContext(Dispatchers.IO) {
        val steps = mutableListOf<String>()
        var session: Session? = null

        try {
            if (config.sshHost.isBlank()) {
                return@withContext ServerTestResult(
                    serverId = config.id,
                    serverName = config.displayTitle,
                    success = false,
                    latencyMs = 0L,
                    resolvedIp = "",
                    details = emptyList(),
                    errorMessage = "Indique o Host/IP do servidor antes de testar."
                )
            }

            val host = config.sshHost.trim()
            val address = InetAddress.getByName(host)
            val resolvedIp = address.hostAddress ?: host
            steps.add("DNS resolvido: $host -> $resolvedIp")

            // 1. Sonda de estabilidade TCP & perda de pacotes
            val tcpLatencies = mutableListOf<Long>()
            var failedProbes = 0
            val probeCount = 3
            for (i in 1..probeCount) {
                val rtt = probeTcpOnce(resolvedIp, config.effectivePort)
                if (rtt != null) {
                    tcpLatencies.add(rtt)
                } else {
                    failedProbes++
                }
            }

            val packetLossPct = (failedProbes.toFloat() / probeCount.toFloat()) * 100f
            val avgLatency = if (tcpLatencies.isNotEmpty()) tcpLatencies.average().toLong() else 0L
            val jitter = if (tcpLatencies.size >= 2) {
                tcpLatencies.zipWithNext { a, b -> abs(a - b) }.average().toLong()
            } else {
                0L
            }
            val stabilityPct = (((probeCount - failedProbes).toFloat() / probeCount) * 100f -
                (jitter.coerceAtMost(80L) / 4f)).toInt().coerceIn(0, 100)

            steps.add(
                "Sonda TCP: média ${avgLatency}ms • jitter ${jitter}ms • estabilidade $stabilityPct% • perda ${String.format("%.0f", packetLossPct)}%"
            )

            if (tcpLatencies.isEmpty()) {
                return@withContext ServerTestResult(
                    serverId = config.id,
                    serverName = config.displayTitle,
                    success = false,
                    latencyMs = 0L,
                    jitterMs = jitter,
                    tcpStabilityPercent = 0,
                    packetLossPercent = 100f,
                    resolvedIp = resolvedIp,
                    details = steps,
                    errorMessage = "Porta ${config.effectivePort} inacessível (100% perda TCP)."
                )
            }

            // 2. Estabelecimento real SSH (+ TLS 1.3/1.2 + SNI + Payload)
            val sshStart = System.currentTimeMillis()
            val factory = SshTlsSocketFactory(
                config = config,
                speedMode = speedMode,
                keepAlive = false,
                protectSocket = { true },
                onLog = { msg -> steps.add(msg) }
            )
            val jsch = JSch()
            session = jsch.getSession(config.username.trim(), host, config.effectivePort).apply {
                setPassword(config.password)
                SshTlsSocketFactory.applySpeedOptions(this, speedMode, keepAlive = false)
                setSocketFactory(factory)
            }

            session.connect(12_000)
            val sshSetupMs = (System.currentTimeMillis() - sshStart).coerceAtLeast(1L)
            val tlsSetupMs = factory.lastTlsHandshakeMs.get()
            steps.add("SSH concluído em ${sshSetupMs}ms (${session.serverVersion ?: "SSH-2.0"})")

            // 3. Medição real de transferência com janela SSH 4MB desbloqueada
            val measuredBps = measureRealTunnelTransfer(session, speedMode, steps)

            // 4. Pontuação composta V4
            val score = computeCompositeScore(
                transferBps = measuredBps,
                stabilityPct = stabilityPct,
                packetLossPct = packetLossPct,
                latencyMs = avgLatency,
                sshSetupMs = sshSetupMs
            )
            steps.add("Pontuação inteligente V4: $score/100")

            ServerTestResult(
                serverId = config.id,
                serverName = config.displayTitle,
                success = true,
                latencyMs = avgLatency,
                jitterMs = jitter,
                tcpStabilityPercent = stabilityPct,
                packetLossPercent = packetLossPct,
                tlsCompatible = if (config.tlsEnabled) true else null,
                sshSetupMs = sshSetupMs,
                tlsSetupMs = tlsSetupMs,
                transferSpeedBps = measuredBps,
                compositeScore = score,
                resolvedIp = resolvedIp,
                details = steps
            )
        } catch (e: JSchException) {
            val friendly = when {
                e.message?.contains("Auth fail", ignoreCase = true) == true ||
                    e.message?.contains("Auth cancel", ignoreCase = true) == true ->
                    "Falha de autenticação: utilizador ou palavra-passe SSH incorretos."
                else -> "Erro SSH: ${e.message ?: "ligação recusada"}"
            }
            steps.add(friendly)
            ServerTestResult(
                serverId = config.id,
                serverName = config.displayTitle,
                success = false,
                latencyMs = 0L,
                tlsCompatible = if (config.tlsEnabled) false else null,
                resolvedIp = "",
                details = steps,
                errorMessage = friendly
            )
        } catch (e: Exception) {
            val msg = e.message ?: "Falha de rede ao contactar servidor"
            steps.add("Erro: $msg")
            ServerTestResult(
                serverId = config.id,
                serverName = config.displayTitle,
                success = false,
                latencyMs = 0L,
                tlsCompatible = if (config.tlsEnabled) false else null,
                resolvedIp = "",
                details = steps,
                errorMessage = msg
            )
        } finally {
            runCatching { session?.disconnect() }
        }
    }

    suspend fun benchmarkAllServers(
        servers: List<ServerConfig>,
        speedMode: SpeedMode
    ): List<ServerTestResult> {
        return servers
            .take(50) // Limita benchmark automático em massa aos 50 principais para evitar timeouts longos
            .map { server -> testServer(server, speedMode) }
            .sortedWith(
                compareByDescending<ServerTestResult> { it.success }
                    .thenByDescending { it.compositeScore }
                    .thenByDescending { it.transferSpeedBps }
                    .thenBy { it.latencyMs }
            )
    }

    private fun probeTcpOnce(ip: String, port: Int): Long? {
        val socket = Socket()
        return try {
            val t0 = System.currentTimeMillis()
            socket.connect(InetSocketAddress(ip, port), 4_000)
            (System.currentTimeMillis() - t0).coerceAtLeast(1L)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun measureRealTunnelTransfer(
        session: Session,
        speedMode: SpeedMode,
        steps: MutableList<String>
    ): Long {
        var channel: ChannelDirectTCPIP? = null
        return try {
            channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost("speed.cloudflare.com")
            channel.setPort(80)
            SshChannelOptimizer.applyHighSpeedWindow(channel, speedMode)
            val input = channel.inputStream
            val output = channel.outputStream
            channel.connect(6_000)

            val req = "GET /__down?bytes=262144 HTTP/1.1\r\nHost: speed.cloudflare.com\r\nConnection: close\r\n\r\n"
            val t0 = System.nanoTime()
            output.write(req.toByteArray(Charsets.US_ASCII))
            output.flush()

            val buf = ByteArray(32768)
            var totalBytes = 0L
            while (totalBytes < 524_288L) {
                val n = input.read(buf)
                if (n <= 0) break
                totalBytes += n
            }
            val elapsedNs = (System.nanoTime() - t0).coerceAtLeast(1_000_000L)
            val bps = ((totalBytes * 1_000_000_000L) / elapsedNs).coerceAtLeast(0L)
            steps.add("Transferência real medida: ${totalBytes / 1024} KB (${bps / 1024} KB/s)")
            bps
        } catch (_: Throwable) {
            steps.add("Transferência externa indisponível; pontuação baseada em estabilidade TCP/SSH.")
            0L
        } finally {
            runCatching { channel?.disconnect() }
        }
    }

    private fun computeCompositeScore(
        transferBps: Long,
        stabilityPct: Int,
        packetLossPct: Float,
        latencyMs: Long,
        sshSetupMs: Long
    ): Int {
        // Transferência até 45 pts (5 MB/s+ atinge teto no teste rápido de 256KB)
        val transferPts = ((transferBps.toDouble() / (5.0 * 1024.0 * 1024.0)).coerceIn(0.0, 1.0) * 45.0)
        val stabilityPts = (stabilityPct / 100.0) * 20.0
        val lossPts = ((100f - packetLossPct).coerceIn(0f, 100f) / 100.0) * 20.0
        val combinedDelay = (latencyMs + sshSetupMs / 4.0).coerceAtLeast(10.0)
        val latencyPts = (1.0 - ((combinedDelay - 25.0) / 1000.0).coerceIn(0.0, 1.0)) * 15.0

        return (transferPts + stabilityPts + lossPts + latencyPts).toInt().coerceIn(1, 100)
    }
}
