package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServerConfig
import com.example.data.model.SpeedMode
import com.example.data.model.VpnRuntimeState
import com.example.ui.components.formatSpeed
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenContainer
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.vpn.InternalBenchmarkResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    vpnState: VpnRuntimeState,
    selectedServer: ServerConfig?,
    activeMode: SpeedMode,
    benchmarkResult: InternalBenchmarkResult?,
    isRunning: Boolean,
    onBack: () -> Unit,
    onRunBenchmark: () -> Unit
) {
    var sniTunnelReferenceMbPerSec by remember { mutableStateOf("") }

    Scaffold(
        containerColor = PitchBlack,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Benchmark & Gargalos V4", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("benchmark_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianSurface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Speed, null, tint = NeonGreen)
                            Text("Benchmark Real do Motor V4", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        Text(
                            "Mede débito real do pipeline PacketCodec + Pool TUN + TCP Loopback + Túnel SSH/TLS ativo sem simular números.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Modo: ${activeMode.label} (${activeMode.parallelSshSessions} túneis • Janela ${activeMode.sshLocalWindowBytes / 1024}KB)" +
                                if (selectedServer != null) " • ${selectedServer.displayTitle}" else "",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = ElectricBlue
                        )
                        Button(
                            onClick = onRunBenchmark,
                            enabled = !isRunning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("run_internal_benchmark_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = PitchBlack)
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PitchBlack, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("A executar benchmark real...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Filled.PlayArrow, null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Executar Benchmark Completo V4", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (benchmarkResult != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = NeonGreenContainer,
                        border = BorderStroke(1.dp, NeonGreen)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "RESULTADOS REAIS MEDIDOS",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen
                            )
                            BenchmarkRow(
                                "Motor TUN / PacketCodec V4",
                                String.format("%.1f MB/s (%d pps)", benchmarkResult.packetCodecMbPerSec, benchmarkResult.packetsPerSecond),
                                NeonGreen
                            )
                            BenchmarkRow(
                                "Loopback TCP Local Kernel",
                                String.format("%.1f MB/s", benchmarkResult.loopbackTcpMbPerSec),
                                ElectricBlue
                            )
                            BenchmarkRow(
                                "Latência Build Pacote",
                                "${benchmarkResult.packetBuildAvgMicros} µs • ${benchmarkResult.cpuCores} núcleos • ${benchmarkResult.ramUsedMb} MB RAM",
                                TextPrimary
                            )
                            benchmarkResult.multiStreamServerMbPerSec?.let { mbps ->
                                BenchmarkRow(
                                    "Transferência Multi-Canal SSH",
                                    String.format("%.2f MB/s (%.0f Mbps)", mbps, mbps * 8.0),
                                    NeonGreen
                                )
                            }
                            benchmarkResult.remoteBenchmarkResult?.let { rem ->
                                BenchmarkRow(
                                    "Ping / SSH / TLS Setup",
                                    "${rem.latencyMs}ms • SSH ${rem.sshSetupMs}ms • TLS ${rem.tlsSetupMs}ms • Score ${rem.compositeScore}/100",
                                    ElectricBlue
                                )
                            }
                        }
                    }

                    // Detector Automático de Gargalos
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = CyberCardElevated,
                        border = BorderStroke(1.dp, AmberPulse.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Analytics, null, tint = AmberPulse)
                                Text("Detector Automático de Gargalos", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            }
                            Text(
                                benchmarkResult.bottleneckReport.category.label,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberPulse
                            )
                            Text(benchmarkResult.bottleneckReport.headline, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text(benchmarkResult.bottleneckReport.recommendation, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                // Telemetria Instantânea do Túnel Ativo
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Memory, null, tint = ElectricBlue)
                            Text("Diagnóstico do Túnel Ativo", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        BenchmarkRow("Download / Médio / Máx", "${formatSpeed(vpnState.downloadSpeedBps)} / ${formatSpeed(vpnState.avgDownloadBps)} / ${formatSpeed(vpnState.peakDownloadBps)}", NeonGreen)
                        BenchmarkRow("Túneis SSH • TLS", "${vpnState.activeSshTunnels} • ${vpnState.tlsProtocol.ifBlank { "SSH Direto" }}", ElectricBlue)
                        BenchmarkRow("CPU App / RAM / PPS", "${vpnState.cpuPercent.toInt()}% • ${vpnState.ramUsedMb} MB • ${vpnState.packetsPerSecond} pps", TextPrimary)
                        BenchmarkRow("Escrita TUN / Retransm.", "${vpnState.tunAvgMicros} µs • ${vpnState.retransmissions}", AmberPulse)
                        if (vpnState.bottleneckSummary.isNotBlank()) {
                            HorizontalDivider(color = CyberBorder)
                            Text(vpnState.bottleneckSummary, color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(vpnState.bottleneckRecommendation, color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                // Comparação Transparente com SNI Tunnel (entrada real medida pelo utilizador)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.CompareArrows, null, tint = ElectricBlue)
                            Text("Comparação Real vs SNI Tunnel", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        Text(
                            "Introduza a velocidade real medida por si no SNI Tunnel com a mesma conta SSH e rede para calcular o rácio real sem dados inventados:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            value = sniTunnelReferenceMbPerSec,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() || it == '.' } && input.length <= 6) {
                                    sniTunnelReferenceMbPerSec = input
                                }
                            },
                            label = { Text("Velocidade medida no SNI Tunnel (MB/s)") },
                            placeholder = { Text("Ex: 50") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = CyberBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        val refVal = sniTunnelReferenceMbPerSec.toDoubleOrNull()
                        val muribaMb = benchmarkResult?.multiStreamServerMbPerSec
                            ?: (vpnState.peakDownloadBps / (1024.0 * 1024.0))
                        if (refVal != null && refVal > 0.0 && muribaMb > 0.0) {
                            val ratio = (muribaMb / refVal) * 100.0
                            Text(
                                text = String.format(
                                    "ADM MURIBA V4: %.2f MB/s vs SNI Tunnel: %.2f MB/s (%.0f%%)",
                                    muribaMb,
                                    refVal,
                                    ratio
                                ),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (ratio >= 95.0) NeonGreen else AmberPulse
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, color = TextSecondary)
        Text(
            value,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = valueColor
        )
    }
}
