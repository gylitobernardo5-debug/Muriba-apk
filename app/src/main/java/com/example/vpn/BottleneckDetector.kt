package com.example.vpn

import com.example.data.model.SpeedMode
import com.example.data.model.VpnRuntimeState

enum class BottleneckCategory(val label: String) {
    NONE("Sem gargalo crítico detetado"),
    PACKET_LOSS("Perda de pacotes / Rede móvel"),
    CPU_CRYPTO("Uso elevado de CPU / Criptografia"),
    TLS_OVERHEAD("Sobrecarga no encapsulamento TLS"),
    TUN_BACKPRESSURE("Fila / Contenção no descritor TUN"),
    SSH_WINDOW("Janela SSH / Bloqueio HoL de sessão única"),
    SERVER_OR_ISP("Limite de largura de banda do Servidor ou Operadora"),
    RAM_PRESSURE("Pressão de memória RAM")
}

data class BottleneckReport(
    val category: BottleneckCategory,
    val headline: String,
    val recommendation: String
)

/**
 * Detector Automático de Gargalos (baseado estritamente nas métricas reais medidas pelo motor V4).
 */
object BottleneckDetector {

    fun analyze(
        state: VpnRuntimeState,
        tlsEnabled: Boolean,
        poolOverflowEvents: Int = 0
    ): BottleneckReport {
        val throughputMbps = (state.downloadSpeedBps * 8.0) / 1_000_000.0
        val latency = state.latencyMs ?: 0L

        return when {
            state.ramUsedMb > 380 -> BottleneckReport(
                category = BottleneckCategory.RAM_PRESSURE,
                headline = "RAM elevada (${state.ramUsedMb} MB)",
                recommendation = "Use o modo EQUILIBRADO ou ECONOMIA para reduzir o tamanho do pool de buffers."
            )

            state.packetLossPercent >= 2.5f || state.retransmissions >= 30L -> BottleneckReport(
                category = BottleneckCategory.PACKET_LOSS,
                headline = "Perda ${String.format("%.1f%%", state.packetLossPercent)} / ${state.retransmissions} retransmissões",
                recommendation = "Experimente ajustar MTU para 1360–1420 ou testar outro servidor com menor jitter (${state.jitterMs}ms)."
            )

            state.cpuPercent >= 82f -> BottleneckReport(
                category = BottleneckCategory.CPU_CRYPTO,
                headline = "CPU alta (${state.cpuPercent.toInt()}%) em cifra SSH/TLS",
                recommendation = "Verifique suporte AES-GCM ou reduza para modo EXTREME/EQUILIBRADO se houver estrangulamento térmico."
            )

            poolOverflowEvents > 15 || state.tunAvgMicros > 1_200L -> BottleneckReport(
                category = BottleneckCategory.TUN_BACKPRESSURE,
                headline = "Latência elevada na escrita TUN (${state.tunAvgMicros}µs)",
                recommendation = "Selecione o modo ULTRA TURBO para expandir o pool TUN para 1024 slots."
            )

            tlsEnabled && state.tlsHandshakeMs > 900L && state.durationSeconds > 3L && throughputMbps < 40.0 -> BottleneckReport(
                category = BottleneckCategory.TLS_OVERHEAD,
                headline = "Handshake TLS lento (${state.tlsHandshakeMs}ms) ou throttled pela SNI",
                recommendation = "Verifique a rota do SNI ou compare transferência com SSH direto para isolar limitação TLS."
            )

            state.activeSshTunnels == 1 &&
                state.activeSpeedMode.parallelSshSessions > 1 &&
                latency > 80L &&
                throughputMbps in 5.0..180.0 -> BottleneckReport(
                category = BottleneckCategory.SSH_WINDOW,
                headline = "Sessão SSH única em rede de ${latency}ms (servidor limitou pool)",
                recommendation = "O servidor aceitou apenas 1 sessão SSH; janela 4MB está ativa para mitigar BDP."
            )

            state.durationSeconds >= 4L &&
                state.downloadSpeedBps > 512 * 1024 &&
                throughputMbps < 120.0 &&
                state.cpuPercent < 55f &&
                state.packetLossPercent < 1f -> BottleneckReport(
                category = BottleneckCategory.SERVER_OR_ISP,
                headline = "Motor V4 livre (CPU ${state.cpuPercent.toInt()}%, TUN ${state.tunAvgMicros}µs) • Limite no servidor/operadora",
                recommendation = "Dispositivo com folga de hardware; gargalo atual é largura de banda do VPS SSH ou rede local."
            )

            else -> BottleneckReport(
                category = BottleneckCategory.NONE,
                headline = "Fluxo V4 saudável (${state.activeSshTunnels} túneis • TUN ${state.tunAvgMicros}µs)",
                recommendation = if (state.activeSpeedMode == SpeedMode.ULTRA_TURBO) {
                    "Modo ULTRA TURBO ativo com ajuste adaptativo em tempo real."
                } else {
                    "Pode ativar ULTRA TURBO para máximo paralelismo adaptativo."
                }
            )
        }
    }
}
