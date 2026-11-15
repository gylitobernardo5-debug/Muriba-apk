package com.example.vpn

import android.os.Debug
import android.os.ParcelFileDescriptor
import com.example.data.model.SpeedMode
import com.example.data.repository.LogStore
import com.jcraft.jsch.ChannelDirectTCPIP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.TreeMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * ULTRA TUN ENGINE V4:
 * - Pool pré-alocado PacketBufferPool (128 a 1024 slots) + Thread dedicada TunWriterThread para eliminar contenção em /dev/tun
 * - DirectChannelSink (channel.setOutputStream) para evitar MyPipedInputStream (elimina lock/notifyAll e 1 thread extra por fluxo)
 * - Janela SSH desbloqueada até 4 MB + pacotes SSH de 64 KB via SshChannelOptimizer
 * - Pseudo-cabeçalho TCP pré-computado por fluxo e soma unrolled em PacketCodec
 * - Distribuição multi-núcleo em round-robin pelos túneis paralelos do SshSessionPool
 * - Instrumentação real de CPU%, RAM MB, PPS, retransmissões e latência TUN (µs)
 */
class TunPacketForwarder(
    private val tunFd: ParcelFileDescriptor,
    private val sessionPool: SshSessionPool,
    private val speedMode: SpeedMode,
    private val effectiveMtu: Int,
    private val dnsServerIp: String,
    private val adBlockEnabledProvider: () -> Boolean,
    private val adBlockAggressiveProvider: () -> Boolean = { true },
    private val adBlockBlockDohProvider: () -> Boolean = { true },
    private val adBlockSniHttpProvider: () -> Boolean = { true },
    private val logStore: LogStore,
    private val scope: CoroutineScope
) {
    val bytesDownload = AtomicLong(0L)
    val bytesUpload = AtomicLong(0L)
    val blockedAdsCount = AtomicLong(0L)
    val totalPacketsProcessed = AtomicLong(0L)
    val packetLossEvents = AtomicLong(0L)
    val retransmissionEvents = AtomicLong(0L)

    private val tunWriteNanosTotal = AtomicLong(0L)
    private val tunWriteOpsCount = AtomicLong(0L)

    @Volatile
    private var adaptiveAckThreshold: Int = speedMode.ackCoalesceThreshold

    @Volatile
    private var adaptiveBatchLimit: Int = if (speedMode.isAdaptive) 24 else 16

    private val running = AtomicBoolean(false)
    private val tunOutput = FileOutputStream(tunFd.fileDescriptor)
    private val tunInput = FileInputStream(tunFd.fileDescriptor)
    private val dnsCache = DnsCache(maxEntries = 512)

    private val bufferPool = PacketBufferPool(slotCount = speedMode.tunPoolSlots)
    private val tunWriteQueue = ArrayBlockingQueue<PacketBufferPool.Slot>(speedMode.tunPoolSlots)
    private val tcpSessions = ConcurrentHashMap<TcpKey, TcpSession>()

    private var readerJob: Job? = null
    private var tunWriterThread: Thread? = null

    private val maxTcpPayload = (effectiveMtu - 40).coerceIn(536, 1460)

    // CPU sampling via /proc/self/stat
    private var lastProcCpuJiffies = -1L
    private var lastWallCpuNs = 0L

    val activeSessionCount: Int
        get() = tcpSessions.size

    val poolOverflowCount: Int
        get() = bufferPool.overflowCount()

    fun computePacketLossPercent(): Float {
        val total = totalPacketsProcessed.get()
        if (total < 10L) return 0f
        val lost = packetLossEvents.get()
        return ((lost.toDouble() / total.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
    }

    fun sampleTunAvgMicros(): Long {
        val ops = tunWriteOpsCount.getAndSet(0L)
        val nanos = tunWriteNanosTotal.getAndSet(0L)
        return if (ops > 0L) (nanos / ops / 1_000L).coerceAtLeast(1L) else 0L
    }

    fun tuneAdaptiveParameters(speedDownBps: Long, lossPercent: Float) {
        if (!speedMode.isAdaptive) return
        val mbps = speedDownBps / (1024.0 * 1024.0)
        when {
            lossPercent >= 2.0f -> {
                adaptiveAckThreshold = 3
                adaptiveBatchLimit = 12
            }
            mbps >= 25.0 -> {
                adaptiveAckThreshold = 10
                adaptiveBatchLimit = 32
            }
            else -> {
                adaptiveAckThreshold = 7
                adaptiveBatchLimit = 24
            }
        }
    }

    fun sampleCpuPercent(): Float {
        return runCatching {
            RandomAccessFile("/proc/self/stat", "r").use { raf ->
                val line = raf.readLine() ?: return@use 0f
                val rparen = line.lastIndexOf(')')
                if (rparen <= 0 || rparen + 2 >= line.length) return@use 0f
                val fields = line.substring(rparen + 2).trim().split(' ')
                if (fields.size < 13) return@use 0f
                val utime = fields[11].toLongOrNull() ?: 0L
                val stime = fields[12].toLongOrNull() ?: 0L
                val totalJiffies = utime + stime
                val nowNs = System.nanoTime()
                val prevJiffies = lastProcCpuJiffies
                val prevWallNs = lastWallCpuNs
                lastProcCpuJiffies = totalJiffies
                lastWallCpuNs = nowNs
                if (prevJiffies < 0L || nowNs <= prevWallNs) return@use 0f

                val deltaJiffies = (totalJiffies - prevJiffies).coerceAtLeast(0L)
                val cpuMs = deltaJiffies * 10.0 // Linux USER_HZ = 100 -> 10ms por jiffy
                val wallMs = (nowNs - prevWallNs) / 1_000_000.0
                val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                ((cpuMs / (wallMs * cores)) * 100.0).toFloat().coerceIn(0f, 100f)
            }
        }.getOrDefault(0f)
    }

    fun sampleRamUsedMb(): Int {
        val rt = Runtime.getRuntime()
        val jvmMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val nativeMb = runCatching { Debug.getNativeHeapAllocatedSize() / (1024 * 1024) }.getOrDefault(0L)
        return (jvmMb + nativeMb).toInt().coerceAtLeast(1)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return

        tunWriterThread = Thread({
            while (running.get()) {
                val first = try {
                    tunWriteQueue.poll(100, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                } ?: continue

                writeSlotToTun(first)
                // Drena rajada até 16 pacotes sem reentrar em poll()
                var batch = 0
                while (batch < 16 && running.get()) {
                    val next = tunWriteQueue.poll() ?: break
                    writeSlotToTun(next)
                    batch++
                }
            }
            while (true) {
                val leftover = tunWriteQueue.poll() ?: break
                bufferPool.release(leftover)
            }
        }, "Muriba-TunWriter").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }

        readerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(32768)
            try {
                while (isActive && running.get() && sessionPool.primaryConnected) {
                    val readLen = tunInput.read(buffer)
                    if (readLen <= 0) {
                        if (readLen < 0) break
                        continue
                    }

                    totalPacketsProcessed.incrementAndGet()
                    val version = (buffer[0].toInt() ushr 4) and 0x0F
                    if (version == 6) {
                        PacketCodec.buildIpv6TcpRstIfSyn(buffer, readLen)?.let { rst ->
                            enqueueRawPacket(rst, rst.size)
                        }
                        continue
                    }
                    if (version != 4) continue

                    val ip = PacketCodec.parseIpv4(buffer, readLen) ?: continue
                    when (ip.protocol) {
                        PacketCodec.PROTO_TCP -> handleTcpPacket(buffer, ip)
                        PacketCodec.PROTO_UDP -> handleUdpPacket(buffer, ip)
                        PacketCodec.PROTO_ICMP -> Unit
                    }
                }
            } catch (_: IOException) {
            } catch (t: Throwable) {
                if (running.get()) {
                    logStore.warn("Interrupção no motor TUN V4: ${t.message}")
                }
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        readerJob?.cancel()
        tunWriterThread?.interrupt()
        tcpSessions.values.forEach { it.closeSilently() }
        tcpSessions.clear()
        dnsCache.clear()
        runCatching { tunInput.close() }
        runCatching { tunOutput.close() }
    }

    private fun writeSlotToTun(slot: PacketBufferPool.Slot) {
        try {
            val len = slot.length
            if (len > 0 && running.get()) {
                val t0 = System.nanoTime()
                tunOutput.write(slot.data, 0, len)
                tunWriteNanosTotal.addAndGet(System.nanoTime() - t0)
                tunWriteOpsCount.incrementAndGet()
            }
        } catch (_: IOException) {
        } finally {
            bufferPool.release(slot)
        }
    }

    private fun enqueueSlotToTun(slot: PacketBufferPool.Slot) {
        if (!running.get()) {
            bufferPool.release(slot)
            return
        }
        if (!tunWriteQueue.offer(slot)) {
            val inserted = try {
                tunWriteQueue.offer(slot, 15, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                false
            }
            if (!inserted) {
                bufferPool.release(slot)
                packetLossEvents.incrementAndGet()
            }
        }
    }

    private fun enqueueRawPacket(packet: ByteArray, length: Int) {
        if (!running.get() || length <= 0 || length > bufferPool.slotCapacity) return
        val slot = bufferPool.acquireImmediateOrFallback()
        System.arraycopy(packet, 0, slot.data, 0, length)
        slot.length = length
        enqueueSlotToTun(slot)
    }

    private fun handleUdpPacket(buffer: ByteArray, ip: PacketCodec.ParsedIpv4) {
        val udp = PacketCodec.parseUdp(buffer, ip) ?: return
        if (udp.dstPort != 53) {
            if (udp.dstPort == 443 || udp.dstPort == 80) {
                PacketCodec.buildIcmpPortUnreachable(buffer, ip)?.let { enqueueRawPacket(it, it.size) }
            }
            return
        }

        val dnsPayload = buffer.copyOfRange(udp.payloadOffset, udp.payloadOffset + udp.payloadLength)
        bytesUpload.addAndGet(dnsPayload.size.toLong())

        val queryMeta = PacketCodec.extractDnsDomainAndType(dnsPayload, 0, dnsPayload.size)
        if (queryMeta != null) {
            val (domain, qtype) = queryMeta
            val match = if (adBlockEnabledProvider()) {
                AdBlockEngine.evaluateBlock(
                    domain = domain,
                    aggressive = adBlockAggressiveProvider(),
                    blockDoh = adBlockBlockDohProvider()
                )
            } else {
                null
            }
            if (match != null) {
                val layer = if (match == AdBlockMatchType.DOH_BYPASS) "DoH" else "DNS"
                AdBlockEngine.recordBlockedEvent(domain, layer)
                val blockedReply = PacketCodec.buildDnsNxDomainResponse(dnsPayload, 0, dnsPayload.size)
                if (blockedReply != null) {
                    val replyIp = PacketCodec.buildIpUdpPacket(
                        srcIp = ip.dstIp,
                        dstIp = ip.srcIp,
                        srcPort = udp.dstPort,
                        dstPort = udp.srcPort,
                        payload = blockedReply
                    )
                    enqueueRawPacket(replyIp, replyIp.size)
                    val count = blockedAdsCount.incrementAndGet()
                    if (count <= 20 || count % 30L == 0L) {
                        logStore.info("AdBlock $layer bloqueou: $domain")
                    }
                }
                return
            }

            if (dnsPayload.size >= 2) {
                val cacheKey = "${domain.lowercase()}:$qtype"
                val cached = dnsCache.get(cacheKey, dnsPayload[0], dnsPayload[1])
                if (cached != null) {
                    bytesDownload.addAndGet(cached.size.toLong())
                    val replyIp = PacketCodec.buildIpUdpPacket(
                        srcIp = ip.dstIp,
                        dstIp = ip.srcIp,
                        srcPort = udp.dstPort,
                        dstPort = udp.srcPort,
                        payload = cached
                    )
                    enqueueRawPacket(replyIp, replyIp.size)
                    return
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            forwardDnsOverSshTcp(ip, udp, dnsPayload, queryMeta)
        }
    }

    private fun forwardDnsOverSshTcp(
        ip: PacketCodec.ParsedIpv4,
        udp: PacketCodec.ParsedUdp,
        dnsQuery: ByteArray,
        queryMeta: Pair<String, Int>?
    ) {
        if (!running.get() || !sessionPool.primaryConnected) return
        var channel: ChannelDirectTCPIP? = null
        try {
            channel = sessionPool.openOptimizedChannel(dnsServerIp, 53, speedMode)
            val input = channel.inputStream
            val output = channel.outputStream
            channel.connect(5_000)

            val framed = ByteArray(2 + dnsQuery.size)
            framed[0] = ((dnsQuery.size ushr 8) and 0xFF).toByte()
            framed[1] = (dnsQuery.size and 0xFF).toByte()
            System.arraycopy(dnsQuery, 0, framed, 2, dnsQuery.size)
            output.write(framed)
            output.flush()

            val dataIn = DataInputStream(input)
            val respLen = dataIn.readUnsignedShort()
            if (respLen in 12..4096) {
                val dnsResp = ByteArray(respLen)
                dataIn.readFully(dnsResp)
                bytesDownload.addAndGet(respLen.toLong())

                if (queryMeta != null) {
                    val cacheKey = "${queryMeta.first.lowercase()}:${queryMeta.second}"
                    dnsCache.put(cacheKey, dnsResp)
                }

                val replyIp = PacketCodec.buildIpUdpPacket(
                    srcIp = ip.dstIp,
                    dstIp = ip.srcIp,
                    srcPort = udp.dstPort,
                    dstPort = udp.srcPort,
                    payload = dnsResp
                )
                enqueueRawPacket(replyIp, replyIp.size)
            }
        } catch (_: Throwable) {
            packetLossEvents.incrementAndGet()
        } finally {
            runCatching { channel?.disconnect() }
        }
    }

    private fun handleTcpPacket(buffer: ByteArray, ip: PacketCodec.ParsedIpv4) {
        val tcp = PacketCodec.parseTcp(buffer, ip) ?: return
        val key = TcpKey(ip.srcIpInt, tcp.srcPort, ip.dstIpInt, tcp.dstPort)
        val payloadOffset = ip.ihl + tcp.headerLen
        val payloadLen = (ip.totalLength - payloadOffset).coerceAtLeast(0)

        val isSyn = (tcp.flags and PacketCodec.TCP_SYN) != 0
        val isAck = (tcp.flags and PacketCodec.TCP_ACK) != 0
        val isRst = (tcp.flags and PacketCodec.TCP_RST) != 0
        val isFin = (tcp.flags and PacketCodec.TCP_FIN) != 0
        val isPsh = (tcp.flags and PacketCodec.TCP_PSH) != 0

        if (isRst) {
            tcpSessions.remove(key)?.closeSilently()
            return
        }

        if (isSyn && !isAck) {
            tcpSessions.remove(key)?.closeSilently()

            if (tcp.dstPort == 853 && adBlockEnabledProvider() && adBlockBlockDohProvider()) {
                val rst = PacketCodec.buildIpTcpPacket(
                    srcIp = ip.dstIp,
                    dstIp = ip.srcIp,
                    srcPort = tcp.dstPort,
                    dstPort = tcp.srcPort,
                    seq = 0L,
                    ack = (tcp.seq + 1L) and 0xFFFFFFFFL,
                    flags = PacketCodec.TCP_RST or PacketCodec.TCP_ACK,
                    window = 0
                )
                enqueueRawPacket(rst, rst.size)
                AdBlockEngine.recordBlockedEvent("Porta 853 TCP", "DoT")
                val count = blockedAdsCount.incrementAndGet()
                if (count <= 15 || count % 25L == 0L) {
                    logStore.info("AdBlock DoT bloqueou ligação porta 853 (${PacketCodec.ipBytesToString(ip.dstIp)})")
                }
                return
            }

            if (tcpSessions.size >= speedMode.maxConcurrentSessions) {
                val oldest = tcpSessions.values.minByOrNull { it.lastActivityMs }
                if (oldest != null) {
                    tcpSessions.remove(oldest.key)
                    oldest.closeSilently()
                }
            }

            val negotiatedWScale = if (tcp.windowScale != null) speedMode.windowScaleShift else null
            val session = TcpSession(
                key = key,
                clientIp = ip.srcIp,
                remoteIp = ip.dstIp,
                remoteHost = PacketCodec.ipBytesToString(ip.dstIp),
                clientPort = tcp.srcPort,
                remotePort = tcp.dstPort,
                initialClientSeq = tcp.seq,
                mss = (tcp.mss ?: maxTcpPayload).coerceIn(536, maxTcpPayload),
                initialWindow = tcp.window.toLong().coerceAtLeast(16384L),
                clientWindowScale = tcp.windowScale ?: 0,
                serverWindowScale = negotiatedWScale ?: 0,
                advertiseWindowScale = negotiatedWScale
            )
            tcpSessions[key] = session
            session.start()
            return
        }

        val session = tcpSessions[key]
        if (session == null) {
            if (!isFin) {
                val rst = PacketCodec.buildIpTcpPacket(
                    srcIp = ip.dstIp,
                    dstIp = ip.srcIp,
                    srcPort = tcp.dstPort,
                    dstPort = tcp.srcPort,
                    seq = if (isAck) tcp.ack else 0L,
                    ack = (tcp.seq + payloadLen) and 0xFFFFFFFFL,
                    flags = PacketCodec.TCP_RST or PacketCodec.TCP_ACK,
                    window = 0
                )
                enqueueRawPacket(rst, rst.size)
            }
            return
        }

        val payload = if (payloadLen > 0) {
            buffer.copyOfRange(payloadOffset, payloadOffset + payloadLen)
        } else {
            null
        }
        session.onClientSegment(tcp, payload, isFin, isPsh)
    }

    private data class TcpKey(
        val srcIp: Int,
        val srcPort: Int,
        val dstIp: Int,
        val dstPort: Int
    )

    private inner class TcpSession(
        val key: TcpKey,
        val clientIp: ByteArray,
        val remoteIp: ByteArray,
        val remoteHost: String,
        val clientPort: Int,
        val remotePort: Int,
        initialClientSeq: Long,
        val mss: Int,
        initialWindow: Long,
        val clientWindowScale: Int,
        val serverWindowScale: Int,
        val advertiseWindowScale: Int?
    ) {
        @Volatile
        var lastActivityMs: Long = System.currentTimeMillis()

        private val precomputedPseudoSum = PacketCodec.precomputePseudoHeaderSum(remoteIp, clientIp, PacketCodec.PROTO_TCP)
        private var clientNextSeq: Long = (initialClientSeq + 1L) and 0xFFFFFFFFL
        private var serverSeq: Long = Random.nextLong(100_000L, 0x7FFFFFFFL)
        private val seqLock = Any()

        @Volatile
        private var clientWindowBytes: Long = initialWindow

        @Volatile
        private var closed = false

        @Volatile
        private var finFromClientReceived = false

        @Volatile
        private var firstPayloadInspected = false

        private var unackedCount = 0
        private val outQueue = Channel<ByteArray>(capacity = speedMode.queueCapacity)
        private val outOfOrder = TreeMap<Long, ByteArray>()
        private var sshChannel: ChannelDirectTCPIP? = null
        private var job: Job? = null

        private val advertisedAckWindow: Int = 65535

        fun start() {
            job = scope.launch(Dispatchers.IO) {
                try {
                    if (!sessionPool.primaryConnected || !running.get()) {
                        sendRstToClient()
                        return@launch
                    }

                    val channel = sessionPool.openOptimizedChannel(remoteHost, remotePort, speedMode)

                    // Direct Download Sink elimina MyPipedInputStream e 1 thread extra por fluxo!
                    val directSink = object : OutputStream() {
                        override fun write(b: Int) {
                            write(byteArrayOf(b.toByte()), 0, 1)
                        }

                        override fun write(b: ByteArray, off: Int, len: Int) {
                            if (len <= 0 || closed || !running.get()) return
                            lastActivityMs = System.currentTimeMillis()
                            bytesDownload.addAndGet(len.toLong())

                            var cursor = off
                            val end = off + len
                            while (cursor < end && !closed && running.get()) {
                                var spin = 0
                                while (clientWindowBytes < mss && !closed && running.get() && spin < 10) {
                                    Thread.sleep(1)
                                    spin++
                                }

                                val segLen = minOf(mss, end - cursor)
                                val isLastSeg = (cursor + segLen) >= end
                                val pktFlags = if (isLastSeg) {
                                    PacketCodec.TCP_PSH or PacketCodec.TCP_ACK
                                } else {
                                    PacketCodec.TCP_ACK
                                }

                                val slot = bufferPool.acquireBlocking(10L)
                                val seqNow: Long
                                val ackNow: Long
                                synchronized(seqLock) {
                                    seqNow = serverSeq
                                    ackNow = clientNextSeq
                                    serverSeq = (serverSeq + segLen.toLong()) and 0xFFFFFFFFL
                                }
                                val pktLen = PacketCodec.writeIpTcpPacketInto(
                                    out = slot.data,
                                    srcIp = remoteIp,
                                    dstIp = clientIp,
                                    srcPort = remotePort,
                                    dstPort = clientPort,
                                    seq = seqNow,
                                    ack = ackNow,
                                    flags = pktFlags,
                                    window = advertisedAckWindow,
                                    payload = b,
                                    payloadOffset = cursor,
                                    payloadLength = segLen,
                                    precomputedPseudoSum = precomputedPseudoSum
                                )
                                slot.length = pktLen
                                enqueueSlotToTun(slot)
                                cursor += segLen
                            }
                        }

                        override fun close() {
                            if (!closed && running.get()) {
                                val slot = bufferPool.acquireImmediateOrFallback()
                                val seqNow: Long
                                val ackNow: Long
                                synchronized(seqLock) {
                                    seqNow = serverSeq
                                    ackNow = clientNextSeq
                                    serverSeq = (serverSeq + 1L) and 0xFFFFFFFFL
                                }
                                val finLen = PacketCodec.writeIpTcpPacketInto(
                                    out = slot.data,
                                    srcIp = remoteIp,
                                    dstIp = clientIp,
                                    srcPort = remotePort,
                                    dstPort = clientPort,
                                    seq = seqNow,
                                    ack = ackNow,
                                    flags = PacketCodec.TCP_FIN or PacketCodec.TCP_ACK,
                                    window = advertisedAckWindow,
                                    precomputedPseudoSum = precomputedPseudoSum
                                )
                                slot.length = finLen
                                enqueueSlotToTun(slot)
                                tcpSessions.remove(key, this@TcpSession)
                                closeSilently()
                            }
                        }
                    }

                    channel.setOutputStream(directSink, false)
                    val remoteOut = BufferedOutputStream(
                        channel.outputStream,
                        speedMode.sshLocalPacketBytes.coerceAtMost(65_536)
                    )
                    channel.connect(8_000)
                    sshChannel = channel

                    // Envia SYN+ACK imediato pela fila de escrita TUN
                    val synSlot = bufferPool.acquireImmediateOrFallback()
                    val synAckLen = PacketCodec.writeIpTcpPacketInto(
                        out = synSlot.data,
                        srcIp = remoteIp,
                        dstIp = clientIp,
                        srcPort = remotePort,
                        dstPort = clientPort,
                        seq = serverSeq,
                        ack = clientNextSeq,
                        flags = PacketCodec.TCP_SYN or PacketCodec.TCP_ACK,
                        window = 65535,
                        mssOption = mss,
                        windowScaleOption = advertiseWindowScale,
                        precomputedPseudoSum = precomputedPseudoSum
                    )
                    synchronized(seqLock) {
                        serverSeq = (serverSeq + 1L) and 0xFFFFFFFFL
                    }
                    synSlot.length = synAckLen
                    enqueueSlotToTun(synSlot)

                    // Pipeline de subida em rajadas (Batch drain + flush único)
                    for (first in outQueue) {
                        if (closed) break
                        var sentBytes = first.size.toLong()
                        remoteOut.write(first)

                        var drained = 0
                        val batchCap = adaptiveBatchLimit
                        while (drained < batchCap) {
                            val next = outQueue.tryReceive().getOrNull() ?: break
                            remoteOut.write(next)
                            sentBytes += next.size.toLong()
                            drained++
                        }
                        remoteOut.flush()
                        bytesUpload.addAndGet(sentBytes)
                    }
                    if (finFromClientReceived && !closed) {
                        closeOutputHalf(remoteOut)
                    }
                } catch (_: Throwable) {
                    packetLossEvents.incrementAndGet()
                    sendRstToClient()
                    tcpSessions.remove(key, this@TcpSession)
                    closeSilently()
                }
            }
        }

        fun onClientSegment(
            tcp: PacketCodec.ParsedTcp,
            payload: ByteArray?,
            isFin: Boolean,
            isPsh: Boolean
        ) {
            if (closed) return
            lastActivityMs = System.currentTimeMillis()
            clientWindowBytes = (tcp.window.toLong() shl clientWindowScale).coerceAtLeast(0L)

            if (payload != null && payload.isNotEmpty()) {
                if (!firstPayloadInspected) {
                    firstPayloadInspected = true
                    if (adBlockEnabledProvider() && adBlockSniHttpProvider()) {
                        val sni = AdBlockEngine.extractTlsSni(payload)
                        val httpHost = if (sni == null) AdBlockEngine.extractHttpHost(payload) else null
                        val detectedHost = sni ?: httpHost
                        if (detectedHost != null) {
                            val match = AdBlockEngine.evaluateBlock(
                                domain = detectedHost,
                                aggressive = adBlockAggressiveProvider(),
                                blockDoh = adBlockBlockDohProvider()
                            )
                            if (match != null) {
                                val layer = if (match == AdBlockMatchType.DOH_BYPASS) {
                                    "DoH"
                                } else if (sni != null) {
                                    "SNI"
                                } else {
                                    "HTTP"
                                }
                                AdBlockEngine.recordBlockedEvent(detectedHost, layer)
                                val count = blockedAdsCount.incrementAndGet()
                                if (count <= 20 || count % 25L == 0L) {
                                    logStore.info("AdBlock $layer bloqueou: $detectedHost")
                                }
                                sendRstToClient()
                                tcpSessions.remove(key, this)
                                closeSilently()
                                return
                            }
                        }
                    }
                }

                val diff = seqDiff(tcp.seq, clientNextSeq)
                if (diff == 0L) {
                    acceptInOrder(payload)
                    drainOutOfOrder()
                    unackedCount++
                    if (isPsh || unackedCount >= adaptiveAckThreshold) {
                        sendAck()
                    }
                } else if (diff > 0L && outOfOrder.size < 32) {
                    outOfOrder[tcp.seq] = payload
                    packetLossEvents.incrementAndGet()
                    retransmissionEvents.incrementAndGet()
                    sendAck()
                } else {
                    packetLossEvents.incrementAndGet()
                    retransmissionEvents.incrementAndGet()
                    sendAck()
                }
            }

            if (isFin && !finFromClientReceived) {
                val finSeq = (tcp.seq + (payload?.size ?: 0)) and 0xFFFFFFFFL
                if (seqDiff(finSeq, clientNextSeq) <= 0L) {
                    finFromClientReceived = true
                    synchronized(seqLock) {
                        clientNextSeq = (clientNextSeq + 1L) and 0xFFFFFFFFL
                    }
                    sendAck()
                    outQueue.close()
                }
            }
        }

        private fun acceptInOrder(data: ByteArray) {
            synchronized(seqLock) {
                clientNextSeq = (clientNextSeq + data.size.toLong()) and 0xFFFFFFFFL
            }
            val result = outQueue.trySend(data)
            if (result.isFailure && !closed) {
                scope.launch(Dispatchers.IO) {
                    runCatching { outQueue.send(data) }
                }
            }
        }

        private fun drainOutOfOrder() {
            while (true) {
                val next = outOfOrder.remove(clientNextSeq) ?: break
                acceptInOrder(next)
            }
        }

        private fun sendAck() {
            if (closed) return
            unackedCount = 0
            val slot = bufferPool.acquireImmediateOrFallback()
            val seqNow: Long
            val ackNow: Long
            synchronized(seqLock) {
                seqNow = serverSeq
                ackNow = clientNextSeq
            }
            val ackLen = PacketCodec.writeIpTcpPacketInto(
                out = slot.data,
                srcIp = remoteIp,
                dstIp = clientIp,
                srcPort = remotePort,
                dstPort = clientPort,
                seq = seqNow,
                ack = ackNow,
                flags = PacketCodec.TCP_ACK,
                window = advertisedAckWindow,
                precomputedPseudoSum = precomputedPseudoSum
            )
            slot.length = ackLen
            enqueueSlotToTun(slot)
        }

        private fun sendRstToClient() {
            if (closed) return
            val slot = bufferPool.acquireImmediateOrFallback()
            val seqNow: Long
            val ackNow: Long
            synchronized(seqLock) {
                seqNow = serverSeq
                ackNow = clientNextSeq
            }
            val rstLen = PacketCodec.writeIpTcpPacketInto(
                out = slot.data,
                srcIp = remoteIp,
                dstIp = clientIp,
                srcPort = remotePort,
                dstPort = clientPort,
                seq = seqNow,
                ack = ackNow,
                flags = PacketCodec.TCP_RST or PacketCodec.TCP_ACK,
                window = 0,
                precomputedPseudoSum = precomputedPseudoSum
            )
            slot.length = rstLen
            enqueueSlotToTun(slot)
        }

        private fun closeOutputHalf(out: OutputStream) {
            runCatching {
                out.flush()
                sshChannel?.sendSignal("EOF")
            }
        }

        fun closeSilently() {
            if (closed) return
            closed = true
            outQueue.close()
            runCatching { sshChannel?.disconnect() }
            job?.cancel()
        }

        private fun seqDiff(a: Long, b: Long): Long = ((a - b).toInt()).toLong()
    }
}
