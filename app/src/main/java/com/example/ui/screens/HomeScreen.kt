package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.model.ServerConfig
import com.example.data.model.SpeedMode
import com.example.data.model.VpnRuntimeState
import com.example.data.model.VpnSettings
import com.example.data.model.VpnStatus
import com.example.ui.components.AboutDialog
import com.example.ui.components.DnsConfigDialog
import com.example.ui.components.PowerHeroButton
import com.example.ui.components.QuickToggleChip
import com.example.ui.components.SpeedMetricTile
import com.example.ui.components.StatusPill
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import com.example.ui.components.formatSpeed
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    vpnState: VpnRuntimeState,
    selectedServer: ServerConfig?,
    totalServersCount: Int,
    settings: VpnSettings,
    onPowerClick: () -> Unit,
    onOpenServerEditor: (Long?) -> Unit,
    onOpenSavedServers: () -> Unit,
    onOpenCloudServers: () -> Unit,
    onOpenBenchmark: () -> Unit,
    onOpenAdBlock: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenServerTest: () -> Unit,
    onOpenMuribaMcScreen: () -> Unit,
    onSelectSpeedMode: (SpeedMode) -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onToggleKillSwitch: (Boolean) -> Unit,
    onToggleAdBlock: (Boolean) -> Unit,
    onSaveCustomDns: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val isDisconnected = vpnState.status == VpnStatus.DISCONNECTED
    val isConnected = vpnState.status == VpnStatus.CONNECTED

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF060B12), PitchBlack, Color(0xFF030508))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cabeçalho com Logo + Motor V4 + Menu dos Três Pontinhos
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.muriba_vpn_icon_1794728569771),
                            contentDescription = "Ícone ADM MURIBA VPN",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column {
                            Text(
                                text = "ADM MURIBA VPN",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "BEYOND SNI TUNNEL • v${BuildConfig.VERSION_NAME}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = NeonGreen
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("three_dots_menu_button")
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu de opções", tint = TextPrimary)
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier
                                .background(ObsidianSurface)
                                .widthIn(min = 250.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("1. Configurações", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Filled.Settings, null, tint = ElectricBlue) },
                                onClick = { menuExpanded = false; onOpenSettings() }
                            )
                            DropdownMenuItem(
                                text = { Text("2. Servidores guardados", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Filled.Storage, null, tint = NeonGreen) },
                                onClick = { menuExpanded = false; onOpenSavedServers() }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("3. AdBlock Ultra", color = TextPrimary)
                                        MenuStatusTag(settings.adBlockEnabled, NeonGreen)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Shield, null, tint = NeonGreen) },
                                onClick = { menuExpanded = false; onOpenAdBlock() }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("4. DNS", color = TextPrimary)
                                        Text(settings.customDns, fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = ElectricBlue)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Dns, null, tint = ElectricBlue) },
                                onClick = { menuExpanded = false; showDnsDialog = true }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("5. Auto Reconectar", color = TextPrimary)
                                        MenuStatusTag(settings.autoReconnect, ElectricBlue)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Autorenew, null, tint = ElectricBlue) },
                                onClick = { onToggleAutoReconnect(!settings.autoReconnect); menuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("6. Manter Conectado", color = TextPrimary)
                                        MenuStatusTag(settings.keepAlive, NeonGreen)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Bolt, null, tint = NeonGreen) },
                                onClick = { onToggleKeepAlive(!settings.keepAlive); menuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("7. Kill Switch", color = TextPrimary)
                                        MenuStatusTag(settings.killSwitch, AmberPulse)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Lock, null, tint = AmberPulse) },
                                onClick = { onToggleKillSwitch(!settings.killSwitch); menuExpanded = false }
                            )
                            HorizontalDivider(color = CyberBorder)
                            DropdownMenuItem(
                                text = { Text("8. Registos de conexão", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Filled.ReceiptLong, null, tint = TextSecondary) },
                                onClick = { menuExpanded = false; onOpenLogs() }
                            )
                            DropdownMenuItem(
                                text = { Text("9. Testar servidor", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Filled.NetworkCheck, null, tint = NeonGreen) },
                                onClick = { menuExpanded = false; onOpenServerTest() }
                            )
                            DropdownMenuItem(
                                text = { Text("10. Sobre ADM MURIBA VPN", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Filled.Info, null, tint = ElectricBlue) },
                                onClick = { menuExpanded = false; showAboutDialog = true }
                            )
                            HorizontalDivider(color = CyberBorder)
                            DropdownMenuItem(
                                text = { Text("Cloud Server Infinity ($totalServersCount)", color = ElectricBlue) },
                                leadingIcon = { Icon(Icons.Filled.Cloud, null, tint = ElectricBlue) },
                                onClick = { menuExpanded = false; onOpenCloudServers() }
                            )
                            DropdownMenuItem(
                                text = { Text("Benchmark & Gargalos V4", color = NeonGreen) },
                                leadingIcon = { Icon(Icons.Filled.Analytics, null, tint = NeonGreen) },
                                onClick = { menuExpanded = false; onOpenBenchmark() }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 4 Modos do Sistema Inteligente de Velocidade
                SpeedModeSelectorBar(
                    selectedMode = settings.speedMode,
                    enabled = isDisconnected,
                    onSelect = onSelectSpeedMode
                )

                Spacer(modifier = Modifier.height(8.dp))

                StatusPill(
                    status = vpnState.status,
                    onClick = if (isConnected) onOpenMuribaMcScreen else null
                )

                if (isConnected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Toque em CONECTADO para abrir ecrã MURIBA MC",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 11.sp,
                        color = NeonGreen.copy(alpha = 0.85f),
                        modifier = Modifier.clickable(onClick = onOpenMuribaMcScreen)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                PowerHeroButton(status = vpnState.status, onClick = onPowerClick)
                Spacer(modifier = Modifier.height(8.dp))

                // Velocidade Real Instantânea + Média + Máxima
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val downMbps = (vpnState.downloadSpeedBps * 8.0) / 1_000_000.0
                    val upMbps = (vpnState.uploadSpeedBps * 8.0) / 1_000_000.0
                    SpeedMetricTile(
                        label = "Download (${String.format("%.1f", downMbps)} Mbps)",
                        speedText = formatSpeed(vpnState.downloadSpeedBps),
                        totalText = "${formatBytes(vpnState.totalDownloadBytes)} • Méd ${formatSpeed(vpnState.avgDownloadBps)} • Máx ${formatSpeed(vpnState.peakDownloadBps)}",
                        accent = NeonGreen,
                        icon = Icons.Filled.ArrowDownward,
                        modifier = Modifier.weight(1f)
                    )
                    SpeedMetricTile(
                        label = "Upload (${String.format("%.1f", upMbps)} Mbps)",
                        speedText = formatSpeed(vpnState.uploadSpeedBps),
                        totalText = "${formatBytes(vpnState.totalUploadBytes)} • Méd ${formatSpeed(vpnState.avgUploadBps)} • Máx ${formatSpeed(vpnState.peakUploadBps)}",
                        accent = ElectricBlue,
                        icon = Icons.Filled.ArrowUpward,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Painel Real de Telemetria V4 (Ping, Jitter, Perda, MTU, Túneis, CPU%, RAM)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Timer, null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                                Text(
                                    text = formatDuration(vpnState.durationSeconds),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Public,
                                    null,
                                    tint = if (isConnected) NeonGreen else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isConnected && vpnState.connectedIp.isNotBlank()) vpnState.connectedIp else "—",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isConnected) NeonGreen else TextMuted
                                )
                            }
                        }

                        HorizontalDivider(color = CyberBorder.copy(alpha = 0.6f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MiniTelemetryCell(
                                label = "Ping / Jitter",
                                value = vpnState.latencyMs?.let { "${it}ms / ${vpnState.jitterMs}ms" } ?: "—",
                                valueColor = if ((vpnState.latencyMs ?: 999L) < 120L) NeonGreen else AmberPulse
                            )
                            MiniTelemetryCell(
                                label = "Perda / Retrans.",
                                value = if (isConnected) {
                                    "${String.format("%.1f%%", vpnState.packetLossPercent)} / ${vpnState.retransmissions}"
                                } else "—",
                                valueColor = if (vpnState.packetLossPercent < 1f) NeonGreen else AmberPulse
                            )
                            MiniTelemetryCell(
                                label = "CPU / RAM",
                                value = if (isConnected) "${vpnState.cpuPercent.toInt()}% • ${vpnState.ramUsedMb}MB" else "—",
                                valueColor = ElectricBlue
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MiniTelemetryCell(
                                label = "MTU / Rede",
                                value = "${settings.effectiveMtu} • ${vpnState.networkLabel.ifBlank { "Auto" }}",
                                valueColor = ElectricBlue
                            )
                            MiniTelemetryCell(
                                label = "Túneis / PPS",
                                value = if (isConnected) "${vpnState.activeSshTunnels}x • ${vpnState.packetsPerSecond} pps" else "${settings.speedMode.parallelSshSessions}x disp.",
                                valueColor = NeonGreen
                            )
                            MiniTelemetryCell(
                                label = "TUN Lat.",
                                value = if (isConnected) "${vpnState.tunAvgMicros} µs" else "—",
                                valueColor = TextPrimary
                            )
                        }

                        if (isConnected && vpnState.bottleneckSummary.isNotBlank()) {
                            HorizontalDivider(color = CyberBorder.copy(alpha = 0.5f))
                            Text(
                                text = "Gargalo: ${vpnState.bottleneckSummary}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = AmberPulse,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable(onClick = onOpenBenchmark)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = !vpnState.lastError.isNullOrBlank(), enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = CrimsonContainer,
                        border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.ErrorOutline, null, tint = CrimsonAlert)
                            Text(
                                text = vpnState.lastError.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Cartão Servidor Selecionado + Acesso rápido Cloud & Benchmark V4
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_server_card"),
                    shape = RoundedCornerShape(18.dp),
                    color = CyberCardElevated,
                    border = BorderStroke(1.dp, if (selectedServer != null) ElectricBlue.copy(alpha = 0.4f) else CyberBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SERVIDOR SELECIONADO",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = ElectricBlue,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedServer != null) {
                                Text(
                                    text = selectedServer.summaryProtocol,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = NeonGreen
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (selectedServer == null) {
                            Text("Nenhum servidor configurado", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Adicione o seu servidor SSH ou sincronize um catálogo JSON na nuvem.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onOpenServerEditor(null) },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("add_first_server_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = PitchBlack)
                                ) {
                                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Adicionar SSH", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = onOpenCloudServers,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.7f))
                                ) {
                                    Icon(Icons.Filled.Cloud, null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cloud", color = ElectricBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text(
                                text = selectedServer.displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${selectedServer.sshHost}:${selectedServer.effectivePort}" +
                                    if (selectedServer.tlsEnabled && selectedServer.sniHost.isNotBlank()) " • SNI: ${selectedServer.sniHost}" else "",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onOpenServerEditor(selectedServer.id) },
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("edit_selected_server_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CyberBorder)
                                ) {
                                    Icon(Icons.Filled.Edit, null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Editar", color = TextPrimary, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = onOpenCloudServers,
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("open_cloud_servers_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Filled.Cloud, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cloud", color = TextPrimary, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = onOpenBenchmark,
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("open_benchmark_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Filled.Analytics, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bench", color = TextPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickToggleChip(
                            title = "Auto Reconectar",
                            checked = settings.autoReconnect,
                            accent = ElectricBlue,
                            icon = Icons.Filled.Autorenew,
                            testTag = "quick_auto_reconnect",
                            onToggle = { onToggleAutoReconnect(!settings.autoReconnect) },
                            modifier = Modifier.weight(1f)
                        )
                        QuickToggleChip(
                            title = "Manter Ligado",
                            checked = settings.keepAlive,
                            accent = NeonGreen,
                            icon = Icons.Filled.Bolt,
                            testTag = "quick_keep_alive",
                            onToggle = { onToggleKeepAlive(!settings.keepAlive) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickToggleChip(
                            title = "Kill Switch",
                            checked = settings.killSwitch,
                            accent = AmberPulse,
                            icon = Icons.Filled.Lock,
                            testTag = "quick_kill_switch",
                            onToggle = { onToggleKillSwitch(!settings.killSwitch) },
                            modifier = Modifier.weight(1f)
                        )
                        QuickToggleChip(
                            title = if (settings.adBlockAggressive) "AdBlock Ultra" else "AdBlock DNS",
                            checked = settings.adBlockEnabled,
                            accent = NeonGreen,
                            icon = Icons.Filled.Shield,
                            testTag = "quick_adblock",
                            onToggle = { onToggleAdBlock(!settings.adBlockEnabled) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showDnsDialog) {
        DnsConfigDialog(
            currentDns = settings.customDns,
            onDismiss = { showDnsDialog = false },
            onSave = { dns -> onSaveCustomDns(dns); showDnsDialog = false }
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
private fun SpeedModeSelectorBar(
    selectedMode: SpeedMode,
    enabled: Boolean,
    onSelect: (SpeedMode) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("speed_mode_selector"),
        shape = RoundedCornerShape(14.dp),
        color = CyberCard,
        border = BorderStroke(1.dp, CyberBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SpeedMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                val accent = when (mode) {
                    SpeedMode.ECONOMIA -> TextSecondary
                    SpeedMode.EQUILIBRADO -> ElectricBlue
                    SpeedMode.EXTREME_SPEED -> NeonGreen
                    SpeedMode.ULTRA_TURBO -> AmberPulse
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled) { onSelect(mode) }
                        .testTag("speed_mode_${mode.id.lowercase()}"),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, accent.copy(alpha = 0.75f)) else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (mode == SpeedMode.ULTRA_TURBO || mode == SpeedMode.EXTREME_SPEED) {
                            Icon(
                                imageVector = if (mode == SpeedMode.ULTRA_TURBO) Icons.Filled.Bolt else Icons.Filled.Speed,
                                contentDescription = null,
                                tint = if (isSelected) accent else TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = mode.shortLabel,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) accent else if (enabled) TextSecondary else TextMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTelemetryCell(label: String, value: String, valueColor: Color) {
    Column {
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MenuStatusTag(enabled: Boolean, accent: Color) {
    Surface(
        shape = CircleShape,
        color = if (enabled) accent.copy(alpha = 0.16f) else CyberBorder.copy(alpha = 0.4f)
    ) {
        Text(
            text = if (enabled) "ON" else "OFF",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) accent else TextMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
