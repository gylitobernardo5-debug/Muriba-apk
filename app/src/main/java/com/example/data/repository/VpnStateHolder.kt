package com.example.data.repository

import com.example.data.model.SpeedMode
import com.example.data.model.VpnRuntimeState
import com.example.data.model.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

class VpnStateHolder {
    private val _state = MutableStateFlow(VpnRuntimeState())
    val state: StateFlow<VpnRuntimeState> = _state.asStateFlow()

    private val splashSeq = AtomicLong(0L)
    private var prevLatencyMs: Long? = null

    fun setConnecting(
        serverName: String,
        serverHost: String,
        speedMode: SpeedMode,
        effectiveMtu: Int,
        networkLabel: String
    ) {
        prevLatencyMs = null
        _state.update {
            it.copy(
                status = VpnStatus.CONNECTING,
                serverName = serverName,
                serverHost = serverHost,
                activeSpeedMode = speedMode,
                effectiveMtu = effectiveMtu,
                networkLabel = networkLabel,
                connectedIp = "",
                downloadSpeedBps = 0L,
                uploadSpeedBps = 0L,
                avgDownloadBps = 0L,
                avgUploadBps = 0L,
                peakDownloadBps = 0L,
                peakUploadBps = 0L,
                latencyMs = null,
                jitterMs = 0L,
                packetLossPercent = 0f,
                retransmissions = 0L,
                packetsPerSecond = 0L,
                tunAvgMicros = 0L,
                sshHandshakeMs = 0L,
                tlsHandshakeMs = 0L,
                tlsProtocol = "",
                sshCipher = "",
                activeSshTunnels = 1,
                cpuPercent = 0f,
                ramUsedMb = 0,
                bottleneckSummary = "",
                bottleneckRecommendation = "",
                lastError = null
            )
        }
    }

    fun setReconnecting(reason: String, networkLabel: String = "") {
        _state.update {
            it.copy(
                status = VpnStatus.RECONNECTING,
                downloadSpeedBps = 0L,
                uploadSpeedBps = 0L,
                networkLabel = networkLabel.ifBlank { it.networkLabel },
                lastError = reason
            )
        }
    }

    fun setConnected(
        serverName: String,
        serverHost: String,
        exitIp: String,
        speedMode: SpeedMode,
        effectiveMtu: Int,
        networkLabel: String,
        initialLatencyMs: Long?,
        sshHandshakeMs: Long = 0L,
        tlsHandshakeMs: Long = 0L,
        tlsProtocol: String = "",
        sshCipher: String = "",
        activeSshTunnels: Int = 1
    ) {
        val nextSplashId = splashSeq.incrementAndGet()
        prevLatencyMs = initialLatencyMs
        _state.update {
            it.copy(
                status = VpnStatus.CONNECTED,
                serverName = serverName,
                serverHost = serverHost,
                connectedIp = exitIp,
                activeSpeedMode = speedMode,
                effectiveMtu = effectiveMtu,
                networkLabel = networkLabel,
                latencyMs = initialLatencyMs,
                jitterMs = 0L,
                sshHandshakeMs = sshHandshakeMs,
                tlsHandshakeMs = tlsHandshakeMs,
                tlsProtocol = tlsProtocol,
                sshCipher = sshCipher,
                activeSshTunnels = activeSshTunnels,
                lastError = null,
                muribaScreenEventId = nextSplashId
            )
        }
    }

    fun updateExitIp(exitIp: String) {
        _state.update {
            if (it.status == VpnStatus.CONNECTED) it.copy(connectedIp = exitIp) else it
        }
    }

    fun updateNetworkLabel(label: String) {
        _state.update { it.copy(networkLabel = label) }
    }

    fun updateLatency(latencyMs: Long?) {
        if (latencyMs == null) return
        val jitter = prevLatencyMs?.let { prev -> abs(latencyMs - prev) } ?: 0L
        prevLatencyMs = latencyMs
        _state.update {
            if (it.status == VpnStatus.CONNECTED) {
                val smoothedJitter = if (it.jitterMs == 0L) jitter else ((it.jitterMs * 3L + jitter) / 4L)
                it.copy(latencyMs = latencyMs, jitterMs = smoothedJitter)
            } else {
                it
            }
        }
    }

    fun updateMetrics(
        durationSeconds: Long,
        downloadSpeedBps: Long,
        uploadSpeedBps: Long,
        totalDownloadBytes: Long,
        totalUploadBytes: Long,
        packetLossPercent: Float,
        retransmissions: Long,
        packetsPerSecond: Long,
        tunAvgMicros: Long,
        activeSshTunnels: Int,
        cpuPercent: Float,
        ramUsedMb: Int,
        blockedAdsCount: Long,
        activeConnections: Int,
        bottleneckSummary: String,
        bottleneckRecommendation: String
    ) {
        val safeSec = durationSeconds.coerceAtLeast(1L)
        _state.update { current ->
            current.copy(
                durationSeconds = durationSeconds,
                downloadSpeedBps = downloadSpeedBps,
                uploadSpeedBps = uploadSpeedBps,
                avgDownloadBps = totalDownloadBytes / safeSec,
                avgUploadBps = totalUploadBytes / safeSec,
                peakDownloadBps = maxOf(current.peakDownloadBps, downloadSpeedBps),
                peakUploadBps = maxOf(current.peakUploadBps, uploadSpeedBps),
                totalDownloadBytes = totalDownloadBytes,
                totalUploadBytes = totalUploadBytes,
                packetLossPercent = packetLossPercent,
                retransmissions = retransmissions,
                packetsPerSecond = packetsPerSecond,
                tunAvgMicros = tunAvgMicros,
                activeSshTunnels = activeSshTunnels,
                cpuPercent = cpuPercent,
                ramUsedMb = ramUsedMb,
                blockedAdsCount = blockedAdsCount,
                activeConnections = activeConnections,
                bottleneckSummary = bottleneckSummary,
                bottleneckRecommendation = bottleneckRecommendation
            )
        }
    }

    fun setDisconnected(error: String? = null) {
        prevLatencyMs = null
        _state.update {
            VpnRuntimeState(
                status = VpnStatus.DISCONNECTED,
                activeSpeedMode = it.activeSpeedMode,
                lastError = error
            )
        }
    }
}
