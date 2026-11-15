package com.example.vpn

import java.util.concurrent.atomic.AtomicInteger

object PacketCodec {

    const val PROTO_ICMP = 1
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    const val TCP_FIN = 0x01
    const val TCP_SYN = 0x02
    const val TCP_RST = 0x04
    const val TCP_PSH = 0x08
    const val TCP_ACK = 0x10

    private val ipIdGenerator = AtomicInteger(1000)
    private val EMPTY_BYTES = ByteArray(0)

    data class ParsedIpv4(
        val ihl: Int,
        val totalLength: Int,
        val protocol: Int,
        val dontFragment: Boolean,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcIpInt: Int,
        val dstIpInt: Int
    )

    data class ParsedTcp(
        val srcPort: Int,
        val dstPort: Int,
        val seq: Long,
        val ack: Long,
        val headerLen: Int,
        val flags: Int,
        val window: Int,
        val mss: Int?,
        val windowScale: Int?
    )

    data class ParsedUdp(
        val srcPort: Int,
        val dstPort: Int,
        val payloadOffset: Int,
        val payloadLength: Int
    )

    fun parseIpv4(packet: ByteArray, length: Int): ParsedIpv4? {
        if (length < 20) return null
        val b0 = packet[0].toInt()
        val version = (b0 ushr 4) and 0x0F
        if (version != 4) return null
        val ihl = (b0 and 0x0F) * 4
        if (ihl < 20 || length < ihl) return null

        val totalLen = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        val actualLen = if (totalLen in ihl..length) totalLen else length
        val flagsFrag = u16(packet, 6)
        val dontFragment = (flagsFrag and 0x4000) != 0
        val protocol = packet[9].toInt() and 0xFF
        val srcIp = byteArrayOf(packet[12], packet[13], packet[14], packet[15])
        val dstIp = byteArrayOf(packet[16], packet[17], packet[18], packet[19])
        val srcInt = ((packet[12].toInt() and 0xFF) shl 24) or
            ((packet[13].toInt() and 0xFF) shl 16) or
            ((packet[14].toInt() and 0xFF) shl 8) or
            (packet[15].toInt() and 0xFF)
        val dstInt = ((packet[16].toInt() and 0xFF) shl 24) or
            ((packet[17].toInt() and 0xFF) shl 16) or
            ((packet[18].toInt() and 0xFF) shl 8) or
            (packet[19].toInt() and 0xFF)

        return ParsedIpv4(
            ihl = ihl,
            totalLength = actualLen,
            protocol = protocol,
            dontFragment = dontFragment,
            srcIp = srcIp,
            dstIp = dstIp,
            srcIpInt = srcInt,
            dstIpInt = dstInt
        )
    }

    fun parseTcp(packet: ByteArray, ip: ParsedIpv4): ParsedTcp? {
        val offset = ip.ihl
        if (ip.totalLength < offset + 20) return null

        val srcPort = u16(packet, offset)
        val dstPort = u16(packet, offset + 2)
        val seq = u32(packet, offset + 4)
        val ack = u32(packet, offset + 8)
        val dataOffset = ((packet[offset + 12].toInt() ushr 4) and 0x0F) * 4
        if (dataOffset < 20 || ip.totalLength < offset + dataOffset) return null
        val flags = packet[offset + 13].toInt() and 0x3F
        val window = u16(packet, offset + 14)

        var mss: Int? = null
        var windowScale: Int? = null
        if (dataOffset > 20) {
            var optPos = offset + 20
            val optEnd = offset + dataOffset
            while (optPos < optEnd) {
                val kind = packet[optPos].toInt() and 0xFF
                if (kind == 0) break
                if (kind == 1) {
                    optPos++
                    continue
                }
                if (optPos + 1 >= optEnd) break
                val optLen = packet[optPos + 1].toInt() and 0xFF
                if (optLen < 2 || optPos + optLen > optEnd) break
                if (kind == 2 && optLen == 4) {
                    mss = u16(packet, optPos + 2)
                } else if (kind == 3 && optLen == 3) {
                    windowScale = (packet[optPos + 2].toInt() and 0xFF).coerceAtMost(14)
                }
                optPos += optLen
            }
        }

        return ParsedTcp(
            srcPort = srcPort,
            dstPort = dstPort,
            seq = seq,
            ack = ack,
            headerLen = dataOffset,
            flags = flags,
            window = window,
            mss = mss,
            windowScale = windowScale
        )
    }

    fun parseUdp(packet: ByteArray, ip: ParsedIpv4): ParsedUdp? {
        val offset = ip.ihl
        if (ip.totalLength < offset + 8) return null
        val srcPort = u16(packet, offset)
        val dstPort = u16(packet, offset + 2)
        val udpLen = u16(packet, offset + 4)
        val payloadOffset = offset + 8
        val payloadLength = minOf(udpLen - 8, ip.totalLength - payloadOffset)
        if (payloadLength < 0) return null
        return ParsedUdp(srcPort, dstPort, payloadOffset, payloadLength)
    }

    fun precomputePseudoHeaderSum(srcIp: ByteArray, dstIp: ByteArray, protocol: Int): Long {
        return u16(srcIp, 0).toLong() +
            u16(srcIp, 2).toLong() +
            u16(dstIp, 0).toLong() +
            u16(dstIp, 2).toLong() +
            protocol.toLong()
    }

    /**
     * Escreve um pacote IPv4+TCP diretamente num buffer pré-alocado (zero alocações no caminho rápido).
     * Permite reutilizar soma do pseudo-cabeçalho por sessão e evita Arrays.fill redundante.
     */
    fun writeIpTcpPacketInto(
        out: ByteArray,
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        window: Int,
        payload: ByteArray = EMPTY_BYTES,
        payloadOffset: Int = 0,
        payloadLength: Int = 0,
        mssOption: Int? = null,
        windowScaleOption: Int? = null,
        precomputedPseudoSum: Long = precomputePseudoHeaderSum(srcIp, dstIp, PROTO_TCP)
    ): Int {
        val optLen = (if (mssOption != null) 4 else 0) + (if (windowScaleOption != null) 4 else 0)
        val tcpHeaderLen = 20 + optLen
        val transportLen = tcpHeaderLen + payloadLength
        val totalLen = 20 + transportLen

        writeIpv4Header(out, totalLen, PROTO_TCP, srcIp, dstIp)

        val t = 20
        writeU16(out, t, srcPort)
        writeU16(out, t + 2, dstPort)
        writeU32(out, t + 4, seq)
        writeU32(out, t + 8, ack)
        out[t + 12] = ((tcpHeaderLen / 4) shl 4).toByte()
        out[t + 13] = (flags and 0x3F).toByte()
        writeU16(out, t + 14, window.coerceIn(0, 65535))
        // Zera checksum + urgent pointer explicitamente (4 bytes)
        out[t + 16] = 0
        out[t + 17] = 0
        out[t + 18] = 0
        out[t + 19] = 0

        if (optLen > 0) {
            var optCursor = t + 20
            if (mssOption != null) {
                out[optCursor] = 2
                out[optCursor + 1] = 4
                writeU16(out, optCursor + 2, mssOption)
                optCursor += 4
            }
            if (windowScaleOption != null) {
                out[optCursor] = 3
                out[optCursor + 1] = 3
                out[optCursor + 2] = windowScaleOption.coerceIn(0, 14).toByte()
                out[optCursor + 3] = 1
            }
        }

        if (payloadLength > 0) {
            System.arraycopy(payload, payloadOffset, out, 20 + tcpHeaderLen, payloadLength)
        }

        val tcpChecksum = computeTransportChecksumFast(
            precomputedPseudoSum = precomputedPseudoSum,
            packet = out,
            transportOffset = 20,
            transportLength = transportLen
        )
        writeU16(out, t + 16, tcpChecksum)
        return totalLen
    }

    fun buildIpTcpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        window: Int,
        payload: ByteArray = EMPTY_BYTES,
        payloadOffset: Int = 0,
        payloadLength: Int = payload.size,
        mssOption: Int? = null,
        windowScaleOption: Int? = null
    ): ByteArray {
        val optLen = (if (mssOption != null) 4 else 0) + (if (windowScaleOption != null) 4 else 0)
        val out = ByteArray(20 + 20 + optLen + payloadLength)
        writeIpTcpPacketInto(
            out = out,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            seq = seq,
            ack = ack,
            flags = flags,
            window = window,
            payload = payload,
            payloadOffset = payloadOffset,
            payloadLength = payloadLength,
            mssOption = mssOption,
            windowScaleOption = windowScaleOption
        )
        return out
    }

    fun buildIpUdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLen = 8 + payload.size
        val totalLen = 20 + udpLen
        val out = ByteArray(totalLen)

        writeIpv4Header(out, totalLen, PROTO_UDP, srcIp, dstIp)

        val u = 20
        writeU16(out, u, srcPort)
        writeU16(out, u + 2, dstPort)
        writeU16(out, u + 4, udpLen)
        out[u + 6] = 0
        out[u + 7] = 0
        System.arraycopy(payload, 0, out, u + 8, payload.size)

        var udpChecksum = computeTransportChecksumFast(
            precomputedPseudoSum = precomputePseudoHeaderSum(srcIp, dstIp, PROTO_UDP),
            packet = out,
            transportOffset = 20,
            transportLength = udpLen
        )
        if (udpChecksum == 0) udpChecksum = 0xFFFF
        writeU16(out, u + 6, udpChecksum)
        return out
    }

    fun buildIcmpPortUnreachable(original: ByteArray, ip: ParsedIpv4): ByteArray? {
        val quotedLen = minOf(ip.ihl + 8, ip.totalLength)
        if (quotedLen < 28) return null
        val icmpLen = 8 + quotedLen
        val totalLen = 20 + icmpLen
        val out = ByteArray(totalLen)

        writeIpv4Header(out, totalLen, PROTO_ICMP, ip.dstIp, ip.srcIp)
        out[20] = 3
        out[21] = 3
        out[22] = 0
        out[23] = 0
        System.arraycopy(original, 0, out, 28, quotedLen)

        val icmpCsum = computeInternetChecksum(out, 20, icmpLen)
        writeU16(out, 22, icmpCsum)
        return out
    }

    /**
     * Se uma app tentar ligar TCP por IPv6, responde imediatamente com TCP RST+ACK em IPv6
     * para forçar fallback instantâneo (< 1ms) para o túnel IPv4 sem bloqueios Happy Eyeballs.
     */
    fun buildIpv6TcpRstIfSyn(packet: ByteArray, length: Int): ByteArray? {
        if (length < 60) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 6) return null
        val nextHeader = packet[6].toInt() and 0xFF
        if (nextHeader != PROTO_TCP) return null

        val tcpOffset = 40
        val flags = packet[tcpOffset + 13].toInt() and 0x3F
        val isSyn = (flags and TCP_SYN) != 0
        val isRst = (flags and TCP_RST) != 0
        if (!isSyn || isRst) return null

        val srcPort = u16(packet, tcpOffset)
        val dstPort = u16(packet, tcpOffset + 2)
        val clientSeq = u32(packet, tcpOffset + 4)

        val out = ByteArray(60)
        out[0] = 0x60
        writeU16(out, 4, 20)
        out[6] = PROTO_TCP.toByte()
        out[7] = 64
        System.arraycopy(packet, 24, out, 8, 16)
        System.arraycopy(packet, 8, out, 24, 16)

        val t = 40
        writeU16(out, t, dstPort)
        writeU16(out, t + 2, srcPort)
        writeU32(out, t + 4, 0L)
        writeU32(out, t + 8, (clientSeq + 1L) and 0xFFFFFFFFL)
        out[t + 12] = (5 shl 4).toByte()
        out[t + 13] = (TCP_RST or TCP_ACK).toByte()

        var sum = 0L
        var i = 8
        while (i < 40) {
            sum += u16(out, i).toLong()
            i += 2
        }
        sum += PROTO_TCP.toLong() + 20L
        i = t
        while (i < 60) {
            sum += u16(out, i).toLong()
            i += 2
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        writeU16(out, t + 16, (sum.inv() and 0xFFFFL).toInt())
        return out
    }

    fun extractDnsDomainAndType(packet: ByteArray, offset: Int, length: Int): Pair<String, Int>? {
        if (length < 14) return null
        var pos = offset + 12
        val end = offset + length
        val sb = StringBuilder()
        while (pos < end) {
            val labelLen = packet[pos].toInt() and 0xFF
            if (labelLen == 0) {
                pos++
                break
            }
            if ((labelLen and 0xC0) != 0) return null
            pos++
            if (pos + labelLen > end) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until labelLen) {
                sb.append((packet[pos + i].toInt() and 0xFF).toChar())
            }
            pos += labelLen
        }
        if (sb.isEmpty()) return null
        val qtype = if (pos + 2 <= end) u16(packet, pos) else 1
        return sb.toString() to qtype
    }

    fun extractDnsDomain(packet: ByteArray, offset: Int, length: Int): String? {
        return extractDnsDomainAndType(packet, offset, length)?.first
    }

    fun buildDnsNxDomainResponse(packet: ByteArray, offset: Int, length: Int): ByteArray? {
        if (length < 12) return null
        var pos = offset + 12
        val end = offset + length
        while (pos < end) {
            val labelLen = packet[pos].toInt() and 0xFF
            pos++
            if (labelLen == 0) break
            if ((labelLen and 0xC0) != 0) {
                pos++
                break
            }
            pos += labelLen
        }
        val questionEnd = minOf(pos + 4, end)
        val respLen = questionEnd - offset
        val out = ByteArray(respLen)
        System.arraycopy(packet, offset, out, 0, respLen)

        val rd = packet[offset + 2].toInt() and 0x01
        out[2] = (0x84 or rd).toByte()
        out[3] = 0x83.toByte()
        out[4] = 0; out[5] = 1
        out[6] = 0; out[7] = 0
        out[8] = 0; out[9] = 0
        out[10] = 0; out[11] = 0
        return out
    }

    fun ipBytesToString(ip: ByteArray): String {
        return "${ip[0].toInt() and 0xFF}.${ip[1].toInt() and 0xFF}.${ip[2].toInt() and 0xFF}.${ip[3].toInt() and 0xFF}"
    }

    private fun writeIpv4Header(
        out: ByteArray,
        totalLen: Int,
        protocol: Int,
        srcIp: ByteArray,
        dstIp: ByteArray
    ) {
        out[0] = 0x45
        out[1] = 0x00
        writeU16(out, 2, totalLen)
        writeU16(out, 4, ipIdGenerator.getAndIncrement() and 0xFFFF)
        writeU16(out, 6, 0x4000)
        out[8] = 64
        out[9] = protocol.toByte()
        out[10] = 0
        out[11] = 0
        out[12] = srcIp[0]; out[13] = srcIp[1]; out[14] = srcIp[2]; out[15] = srcIp[3]
        out[16] = dstIp[0]; out[17] = dstIp[1]; out[18] = dstIp[2]; out[19] = dstIp[3]

        // Inline IPv4 header checksum (exatamente 10 palavras de 16 bits, zero alocações)
        var sum = ((out[0].toInt() and 0xFF) shl 8) or (out[1].toInt() and 0xFF)
        sum += ((out[2].toInt() and 0xFF) shl 8) or (out[3].toInt() and 0xFF)
        sum += ((out[4].toInt() and 0xFF) shl 8) or (out[5].toInt() and 0xFF)
        sum += ((out[6].toInt() and 0xFF) shl 8) or (out[7].toInt() and 0xFF)
        sum += ((out[8].toInt() and 0xFF) shl 8) or (out[9].toInt() and 0xFF)
        sum += ((out[12].toInt() and 0xFF) shl 8) or (out[13].toInt() and 0xFF)
        sum += ((out[14].toInt() and 0xFF) shl 8) or (out[15].toInt() and 0xFF)
        sum += ((out[16].toInt() and 0xFF) shl 8) or (out[17].toInt() and 0xFF)
        sum += ((out[18].toInt() and 0xFF) shl 8) or (out[19].toInt() and 0xFF)
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        writeU16(out, 10, sum.inv() and 0xFFFF)
    }

    /**
     * Loop desenrolado (unrolled 8 bytes por iteração) para cálculo rápido de checksum TCP/UDP.
     */
    private fun computeTransportChecksumFast(
        precomputedPseudoSum: Long,
        packet: ByteArray,
        transportOffset: Int,
        transportLength: Int
    ): Int {
        var sum = precomputedPseudoSum + transportLength.toLong()
        var i = transportOffset
        val end = transportOffset + transportLength
        val fastLimit = end - 7
        while (i < fastLimit) {
            sum += (((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)).toLong()
            sum += (((packet[i + 2].toInt() and 0xFF) shl 8) or (packet[i + 3].toInt() and 0xFF)).toLong()
            sum += (((packet[i + 4].toInt() and 0xFF) shl 8) or (packet[i + 5].toInt() and 0xFF)).toLong()
            sum += (((packet[i + 6].toInt() and 0xFF) shl 8) or (packet[i + 7].toInt() and 0xFF)).toLong()
            i += 8
        }
        while (i + 1 < end) {
            sum += (((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)).toLong()
            i += 2
        }
        if (i < end) {
            sum += ((packet[i].toInt() and 0xFF) shl 8).toLong()
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    private fun computeInternetChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += u16(data, i).toLong()
            i += 2
        }
        if (i < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8).toLong()
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    fun u16(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 8) or (b[offset + 1].toInt() and 0xFF)

    fun u32(b: ByteArray, offset: Int): Long =
        ((b[offset].toLong() and 0xFFL) shl 24) or
            ((b[offset + 1].toLong() and 0xFFL) shl 16) or
            ((b[offset + 2].toLong() and 0xFFL) shl 8) or
            (b[offset + 3].toLong() and 0xFFL)

    fun writeU16(b: ByteArray, offset: Int, value: Int) {
        b[offset] = ((value ushr 8) and 0xFF).toByte()
        b[offset + 1] = (value and 0xFF).toByte()
    }

    fun writeU32(b: ByteArray, offset: Int, value: Long) {
        b[offset] = ((value ushr 24) and 0xFFL).toByte()
        b[offset + 1] = ((value ushr 16) and 0xFFL).toByte()
        b[offset + 2] = ((value ushr 8) and 0xFFL).toByte()
        b[offset + 3] = (value and 0xFFL).toByte()
    }
}
