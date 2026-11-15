package com.example

import com.example.data.model.ServerConfig
import com.example.data.model.SpeedMode
import com.example.data.model.VpnRuntimeState
import com.example.data.model.VpnStatus
import com.example.vpn.AdBlockEngine
import com.example.vpn.AdBlockListSync
import com.example.vpn.AdBlockMatchType
import com.example.vpn.BottleneckCategory
import com.example.vpn.BottleneckDetector
import com.example.vpn.DnsCache
import com.example.vpn.PacketBufferPool
import com.example.vpn.PacketCodec
import com.example.vpn.PayloadProcessor
import com.example.vpn.SshChannelOptimizer
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelDirectTCPIP
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class VpnCoreUnitTest {

    @Test
    fun packetCodec_buildsAndParsesIpv4TcpWithWindowScaleAndZeroAllocBuffer() {
        val srcIp = byteArrayOf(10, 8, 0, 2)
        val dstIp = byteArrayOf(1, 1, 1, 1)
        val payload = "EXTREME_SPEED".toByteArray(StandardCharsets.US_ASCII)
        val buffer = ByteArray(1560)

        val totalLen = PacketCodec.writeIpTcpPacketInto(
            out = buffer,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = 54321,
            dstPort = 443,
            seq = 123456L,
            ack = 654321L,
            flags = PacketCodec.TCP_SYN or PacketCodec.TCP_ACK,
            window = 65535,
            payload = payload,
            payloadOffset = 0,
            payloadLength = payload.size,
            mssOption = 1460,
            windowScaleOption = 7
        )

        val parsedIp = PacketCodec.parseIpv4(buffer, totalLen)
        assertNotNull(parsedIp)
        assertEquals(PacketCodec.PROTO_TCP, parsedIp!!.protocol)
        assertArrayEquals(srcIp, parsedIp.srcIp)
        assertArrayEquals(dstIp, parsedIp.dstIp)

        val parsedTcp = PacketCodec.parseTcp(buffer, parsedIp)
        assertNotNull(parsedTcp)
        assertEquals(54321, parsedTcp!!.srcPort)
        assertEquals(443, parsedTcp.dstPort)
        assertEquals(1460, parsedTcp.mss)
        assertEquals(7, parsedTcp.windowScale)
        assertEquals(payload.size, parsedIp.totalLength - parsedIp.ihl - parsedTcp.headerLen)
    }

    @Test
    fun packetCodec_buildsIpv6TcpRstForSynFallback() {
        val pkt = ByteArray(60)
        pkt[0] = 0x60
        PacketCodec.writeU16(pkt, 4, 20)
        pkt[6] = PacketCodec.PROTO_TCP.toByte()
        pkt[7] = 64
        pkt[23] = 2
        pkt[39] = 1
        PacketCodec.writeU16(pkt, 40, 50000)
        PacketCodec.writeU16(pkt, 42, 443)
        PacketCodec.writeU32(pkt, 44, 1000L)
        pkt[52] = (5 shl 4).toByte()
        pkt[53] = PacketCodec.TCP_SYN.toByte()

        val rst = PacketCodec.buildIpv6TcpRstIfSyn(pkt, pkt.size)
        assertNotNull(rst)
        assertEquals(60, rst!!.size)
        assertEquals(443, PacketCodec.u16(rst, 40))
        assertEquals(50000, PacketCodec.u16(rst, 42))
        assertEquals(1001L, PacketCodec.u32(rst, 48))
        assertEquals(PacketCodec.TCP_RST or PacketCodec.TCP_ACK, rst[53].toInt() and 0x3F)
    }

    @Test
    fun dnsCache_rewritesTransactionIdAndRespectsRcode() {
        val cache = DnsCache(16)
        val okResp = ByteArray(24).apply {
            this[0] = 0x11
            this[1] = 0x22
            this[2] = 0x81.toByte()
            this[3] = 0x80.toByte()
        }
        cache.put("example.com:1", okResp, ttlSeconds = 60L)

        val hit = cache.get("example.com:1", 0xAA.toByte(), 0xBB.toByte())
        assertNotNull(hit)
        assertEquals(0xAA.toByte(), hit!![0])
        assertEquals(0xBB.toByte(), hit[1])

        val errResp = ByteArray(24).apply {
            this[3] = 0x83.toByte()
        }
        cache.put("bad.example:1", errResp)
        assertNull(cache.get("bad.example:1", 0x01, 0x02))
    }

    @Test
    fun adBlockEngine_blocksSubdomainsButAllowsNormalDomains() {
        assertTrue(AdBlockEngine.isBlocked("pagead2.googlesyndication.com"))
        assertTrue(AdBlockEngine.isBlocked("stats.g.doubleclick.net"))
        assertTrue(AdBlockEngine.isBlocked("net.rayjump.com"))
        assertTrue(AdBlockEngine.isBlocked("api.pangle.io"))
        assertTrue(AdBlockEngine.isBlocked("tracking.intl.miui.com"))
        assertFalse(AdBlockEngine.isBlocked("google.com"))
        assertFalse(AdBlockEngine.isBlocked("github.com"))
    }

    @Test
    fun adBlockEngine_supportsHeuristicsDohWhitelistBlacklistAndListParsing() {
        assertEquals(
            AdBlockMatchType.HEURISTIC,
            AdBlockEngine.evaluateBlock("adserver.novojogoapk.com", aggressive = true, blockDoh = true)
        )
        assertEquals(
            AdBlockMatchType.DOH_BYPASS,
            AdBlockEngine.evaluateBlock("use-application-dns.net", aggressive = true, blockDoh = true)
        )
        assertEquals(
            AdBlockMatchType.DOH_BYPASS,
            AdBlockEngine.evaluateBlock("dns.google", aggressive = true, blockDoh = true)
        )

        assertTrue(AdBlockEngine.addWhitelist("stats.g.doubleclick.net"))
        assertNull(AdBlockEngine.evaluateBlock("stats.g.doubleclick.net", aggressive = true, blockDoh = true))
        AdBlockEngine.removeWhitelist("stats.g.doubleclick.net")
        assertNotNull(AdBlockEngine.evaluateBlock("stats.g.doubleclick.net", aggressive = true, blockDoh = true))

        assertTrue(AdBlockEngine.addCustomBlacklist("anuncio-custom.test"))
        assertEquals(
            AdBlockMatchType.CUSTOM_BLACKLIST,
            AdBlockEngine.evaluateBlock("cdn.anuncio-custom.test", aggressive = false, blockDoh = false)
        )
        AdBlockEngine.removeCustomBlacklist("anuncio-custom.test")

        assertEquals("ads.site.com", AdBlockListSync.parseFilterLine("0.0.0.0 ads.site.com # tracker"))
        assertEquals("sdk.ads.io", AdBlockListSync.parseFilterLine("||sdk.ads.io^" + '$' + "third-party"))
        assertEquals("wild.tracker.net", AdBlockListSync.parseFilterLine("*.wild.tracker.net"))
        assertNull(AdBlockListSync.parseFilterLine("@@||whitelist.example.com^"))
        assertNull(AdBlockListSync.parseFilterLine("! comment line"))
    }

    @Test
    fun adBlockEngine_extractsTlsSniAndHttpHostForCachedApkAds() {
        val clientHello = buildSyntheticTlsClientHello("ads.applovin.com")
        assertEquals("ads.applovin.com", AdBlockEngine.extractTlsSni(clientHello))

        val httpReq = "GET /banner.js HTTP/1.1\r\nHost: pagead2.googlesyndication.com:80\r\nConnection: close\r\n\r\n"
            .toByteArray(StandardCharsets.US_ASCII)
        assertEquals("pagead2.googlesyndication.com", AdBlockEngine.extractHttpHost(httpReq))
    }

    @Test
    fun payloadProcessor_formatsPlaceholdersAndParsesHttp101Response() {
        val raw = "GET / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: websocket[crlf][crlf]"
        val chunks = PayloadProcessor.buildPayloadChunks(raw, "cdn.exemplo.com", 443)
        assertEquals(1, chunks.size)
        val formatted = String(chunks[0], StandardCharsets.ISO_8859_1)
        assertTrue(formatted.contains("Host: cdn.exemplo.com\r\n"))

        val serverReply = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n\r\nSSH-2.0-OpenSSH_9.6\r\n"
        val stream = PayloadProcessor.consumeHttpResponseIfPresent(
            ByteArrayInputStream(serverReply.toByteArray(StandardCharsets.ISO_8859_1))
        ) {}
        val remaining = stream.bufferedReader().readLine()
        assertEquals("SSH-2.0-OpenSSH_9.6", remaining)
    }

    @Test
    fun sshChannelOptimizer_unlocks4MbWindowAnd64KbPacketSize() {
        val ctor = ChannelDirectTCPIP::class.java.getDeclaredConstructor().apply { isAccessible = true }
        val channel = ctor.newInstance()

        SshChannelOptimizer.applyHighSpeedWindow(channel, SpeedMode.ULTRA_TURBO)

        val lwMaxField = Channel::class.java.getDeclaredField("lwsize_max").apply { isAccessible = true }
        val lmpField = Channel::class.java.getDeclaredField("lmpsize").apply { isAccessible = true }
        assertEquals(4 * 1024 * 1024, lwMaxField.getInt(channel))
        assertEquals(65_536, lmpField.getInt(channel))
    }

    @Test
    fun packetBufferPool_andPacketCodec_achieveHighLocalThroughputWithoutOverflow() {
        val pool = PacketBufferPool(slotCount = 128)
        val srcIp = byteArrayOf(10, 8, 0, 2)
        val dstIp = byteArrayOf(1, 1, 1, 1)
        val payload = ByteArray(1460) { 0x42 }
        val pseudo = PacketCodec.precomputePseudoHeaderSum(dstIp, srcIp, PacketCodec.PROTO_TCP)

        val count = 15_000
        var totalBytes = 0L
        val t0 = System.nanoTime()
        for (i in 0 until count) {
            val slot = pool.acquireImmediateOrFallback()
            val len = PacketCodec.writeIpTcpPacketInto(
                out = slot.data,
                srcIp = dstIp,
                dstIp = srcIp,
                srcPort = 443,
                dstPort = 51000,
                seq = i * 1460L,
                ack = 1L,
                flags = PacketCodec.TCP_ACK,
                window = 65535,
                payload = payload,
                payloadOffset = 0,
                payloadLength = payload.size,
                precomputedPseudoSum = pseudo
            )
            totalBytes += len
            pool.release(slot)
        }
        val elapsedNs = (System.nanoTime() - t0).coerceAtLeast(1L)
        val mbPerSec = (totalBytes / (1024.0 * 1024.0)) / (elapsedNs / 1_000_000_000.0)
        assertEquals(0, pool.overflowCount())
        assertTrue("Expected > 50 MB/s local codec rate, got $mbPerSec", mbPerSec > 50.0)
    }

    @Test
    fun speedModes_preserveIdsAndEffectivePortRule() {
        assertEquals(4, SpeedMode.entries.size)
        assertEquals(SpeedMode.ECONOMIA, SpeedMode.fromId("ECONOMIA"))
        assertEquals(SpeedMode.ULTRA_TURBO, SpeedMode.fromId("ULTRA_TURBO"))

        val cfg = ServerConfig(sshHost = "vps.test", sshPort = 443, tlsPort = 8443, tlsEnabled = true, sniHost = "cdn.test")
        assertEquals(443, cfg.effectivePort)
    }

    @Test
    fun bottleneckDetector_identifiesRealLossAndCpuConditions() {
        val lossReport = BottleneckDetector.analyze(
            state = VpnRuntimeState(status = VpnStatus.CONNECTED, packetLossPercent = 4.2f, retransmissions = 12L),
            tlsEnabled = true
        )
        assertEquals(BottleneckCategory.PACKET_LOSS, lossReport.category)

        val healthy = BottleneckDetector.analyze(
            state = VpnRuntimeState(
                status = VpnStatus.CONNECTED,
                durationSeconds = 10,
                downloadSpeedBps = 250L * 1024 * 1024,
                cpuPercent = 35f,
                tunAvgMicros = 80L,
                activeSshTunnels = 4
            ),
            tlsEnabled = true
        )
        assertEquals(BottleneckCategory.NONE, healthy.category)
    }

    private fun buildSyntheticTlsClientHello(serverName: String): ByteArray {
        val nameBytes = serverName.toByteArray(StandardCharsets.US_ASCII)
        val sniEntryLen = 1 + 2 + nameBytes.size
        val sniListLen = sniEntryLen
        val sniExtDataLen = 2 + sniListLen
        val totalExtLen = 4 + sniExtDataLen

        val body = ByteArrayOutputStream().apply {
            // Client Version TLS 1.2 (0x03 0x03)
            write(0x03); write(0x03)
            // Random (32 bytes)
            repeat(32) { write(0x11) }
            // Session ID len = 0
            write(0x00)
            // Cipher Suites len = 2 (TLS_AES_128_GCM_SHA256 = 0x13 0x01)
            write(0x00); write(0x02); write(0x13); write(0x01)
            // Compression Methods len = 1 (null)
            write(0x01); write(0x00)
            // Extensions Total Len
            write((totalExtLen ushr 8) and 0xFF); write(totalExtLen and 0xFF)
            // SNI Extension Type = 0x0000
            write(0x00); write(0x00)
            write((sniExtDataLen ushr 8) and 0xFF); write(sniExtDataLen and 0xFF)
            write((sniListLen ushr 8) and 0xFF); write(sniListLen and 0xFF)
            write(0x00) // host_name
            write((nameBytes.size ushr 8) and 0xFF); write(nameBytes.size and 0xFF)
            write(nameBytes)
        }.toByteArray()

        val handshakeLen = body.size
        val recordLen = 4 + handshakeLen
        return ByteArrayOutputStream().apply {
            // TLS Record Header
            write(0x16); write(0x03); write(0x01)
            write((recordLen ushr 8) and 0xFF); write(recordLen and 0xFF)
            // Handshake Header (ClientHello = 0x01)
            write(0x01)
            write((handshakeLen ushr 16) and 0xFF)
            write((handshakeLen ushr 8) and 0xFF)
            write(handshakeLen and 0xFF)
            write(body)
        }.toByteArray()
    }
}
