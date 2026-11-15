package com.example.data.model

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class VpnRuntimeState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val durationSeconds: Long = 0L,
    val downloadSpeedBps: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val avgDownloadBps: Long = 0L,
    val avgUploadBps: Long = 0L,
    val peakDownloadBps: Long = 0L,
    val peakUploadBps: Long = 0L,
    val totalDownloadBytes: Long = 0L,
    val totalUploadBytes: Long = 0L,
    val latencyMs: Long? = null,
    val jitterMs: Long = 0L,
    val packetLossPercent: Float = 0f,
    val retransmissions: Long = 0L,
    val packetsPerSecond: Long = 0L,
    val tunAvgMicros: Long = 0L,
    val sshHandshakeMs: Long = 0L,
    val tlsHandshakeMs: Long = 0L,
    val tlsProtocol: String = "",
    val sshCipher: String = "",
    val activeSshTunnels: Int = 1,
    val cpuPercent: Float = 0f,
    val ramUsedMb: Int = 0,
    val bottleneckSummary: String = "",
    val bottleneckRecommendation: String = "",
    val connectedIp: String = "",
    val serverName: String = "",
    val serverHost: String = "",
    val networkLabel: String = "",
    val activeSpeedMode: SpeedMode = SpeedMode.ULTRA_TURBO,
    val effectiveMtu: Int = 1500,
    val blockedAdsCount: Long = 0L,
    val activeConnections: Int = 0,
    val lastError: String? = null,
    val muribaScreenEventId: Long = 0L
)

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}
