package com.example.data.model

enum class SpeedMode(
    val id: String,
    val label: String,
    val shortLabel: String,
    val shortDesc: String,
    val defaultMtu: Int,
    val socketBufferBytes: Int,
    val readChunkBytes: Int,
    val queueCapacity: Int,
    val tunPoolSlots: Int,
    val sshLocalWindowBytes: Int,
    val sshLocalPacketBytes: Int,
    val parallelSshSessions: Int,
    val maxConcurrentSessions: Int,
    val tcpNoDelay: Boolean,
    val keepAliveIntervalMs: Int,
    val windowScaleShift: Int,
    val ackCoalesceThreshold: Int,
    val isAdaptive: Boolean
) {
    ECONOMIA(
        id = "ECONOMIA",
        label = "ECONOMIA",
        shortLabel = "ECO",
        shortDesc = "Baixo consumo de bateria e memória RAM",
        defaultMtu = 1360,
        socketBufferBytes = 64 * 1024,
        readChunkBytes = 16 * 1024,
        queueCapacity = 48,
        tunPoolSlots = 128,
        sshLocalWindowBytes = 512 * 1024,
        sshLocalPacketBytes = 32 * 1024,
        parallelSshSessions = 1,
        maxConcurrentSessions = 96,
        tcpNoDelay = false,
        keepAliveIntervalMs = 35_000,
        windowScaleShift = 5,
        ackCoalesceThreshold = 2,
        isAdaptive = false
    ),
    EQUILIBRADO(
        id = "EQUILIBRADO",
        label = "EQUILIBRADO",
        shortLabel = "EQUIL.",
        shortDesc = "Equilíbrio estável com 2 túneis SSH paralelos",
        defaultMtu = 1460,
        socketBufferBytes = 192 * 1024,
        readChunkBytes = 32 * 1024,
        queueCapacity = 160,
        tunPoolSlots = 256,
        sshLocalWindowBytes = 1536 * 1024,
        sshLocalPacketBytes = 32 * 1024,
        parallelSshSessions = 2,
        maxConcurrentSessions = 220,
        tcpNoDelay = true,
        keepAliveIntervalMs = 20_000,
        windowScaleShift = 7,
        ackCoalesceThreshold = 4,
        isAdaptive = false
    ),
    EXTREME_SPEED(
        id = "EXTREME_SPEED",
        label = "EXTREME SPEED",
        shortLabel = "EXTREME",
        shortDesc = "Máxima largura de banda • Janela SSH 4MB + 3 túneis",
        defaultMtu = 1500,
        socketBufferBytes = 384 * 1024,
        readChunkBytes = 64 * 1024,
        queueCapacity = 320,
        tunPoolSlots = 512,
        sshLocalWindowBytes = 4 * 1024 * 1024,
        sshLocalPacketBytes = 65_536,
        parallelSshSessions = 3,
        maxConcurrentSessions = 360,
        tcpNoDelay = true,
        keepAliveIntervalMs = 15_000,
        windowScaleShift = 8,
        ackCoalesceThreshold = 6,
        isAdaptive = false
    ),
    ULTRA_TURBO(
        id = "ULTRA_TURBO",
        label = "ULTRA TURBO",
        shortLabel = "TURBO",
        shortDesc = "Motor adaptativo V4 • 4 túneis + ajuste em tempo real",
        defaultMtu = 1500,
        socketBufferBytes = 512 * 1024,
        readChunkBytes = 64 * 1024,
        queueCapacity = 512,
        tunPoolSlots = 1024,
        sshLocalWindowBytes = 4 * 1024 * 1024,
        sshLocalPacketBytes = 65_536,
        parallelSshSessions = 4,
        maxConcurrentSessions = 480,
        tcpNoDelay = true,
        keepAliveIntervalMs = 12_000,
        windowScaleShift = 9,
        ackCoalesceThreshold = 8,
        isAdaptive = true
    );

    companion object {
        fun fromId(id: String?): SpeedMode {
            return when (id) {
                "ECONOMY" -> ECONOMIA
                "BALANCED" -> EQUILIBRADO
                "EXTREME" -> EXTREME_SPEED
                else -> entries.firstOrNull { it.id == id } ?: ULTRA_TURBO
            }
        }
    }
}
