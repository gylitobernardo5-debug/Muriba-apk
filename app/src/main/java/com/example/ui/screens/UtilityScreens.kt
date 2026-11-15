package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import com.example.data.model.ServerConfig
import com.example.data.model.SpeedMode
import com.example.data.model.VpnSettings
import com.example.ui.components.DnsConfigDialog
import com.example.ui.components.formatSpeed
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenContainer
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.vpn.AdBlockEngine
import com.example.vpn.ServerTestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: VpnSettings,
    blockedAdsCount: Long,
    onBack: () -> Unit,
    onSelectSpeedMode: (SpeedMode) -> Unit,
    onSaveCustomMtu: (Int) -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onToggleKillSwitch: (Boolean) -> Unit,
    onToggleAdBlock: (Boolean) -> Unit,
    onToggleAdBlockAggressive: (Boolean) -> Unit,
    onOpenAdBlock: () -> Unit,
    onToggleReconnectOnNetRestore: (Boolean) -> Unit,
    onSaveCustomDns: (String) -> Unit
) {
    var showDnsDialog by remember { mutableStateOf(false) }
    var showMtuDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PitchBlack,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Configurações", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Perfil de Velocidade
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.45f))
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
                            Text("Modo do Motor de Velocidade", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        SpeedMode.entries.forEach { mode ->
                            val selected = mode == settings.speedMode
                            val accent = when (mode) {
                                SpeedMode.ULTRA_TURBO -> AmberPulse
                                SpeedMode.EXTREME_SPEED -> NeonGreen
                                SpeedMode.EQUILIBRADO -> ElectricBlue
                                SpeedMode.ECONOMIA -> TextSecondary
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSpeedMode(mode) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) accent.copy(alpha = 0.14f) else PitchBlack.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, if (selected) accent else CyberBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.label,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) accent else TextPrimary,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${mode.shortDesc} • ${mode.parallelSshSessions} túneis • Janela ${mode.sshLocalWindowBytes / 1024}KB • MTU ${mode.defaultMtu}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    if (selected) {
                                        Icon(Icons.Filled.CheckCircle, null, tint = accent, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // MTU Configurável
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMtuDialog = true }
                        .testTag("settings_mtu_row"),
                    shape = RoundedCornerShape(16.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Filled.SettingsEthernet, null, tint = ElectricBlue)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("MTU da Interface TUN", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                "Controla fragmentação e tamanho dos segmentos TCP (1200–1500).",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = if (settings.customMtu == 0) "Auto (${settings.effectiveMtu})" else "${settings.customMtu}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue,
                            fontSize = 13.sp
                        )
                    }
                }

                SettingToggleItem(
                    title = "Auto Reconectar",
                    subtitle = "Reinicia automaticamente o túnel SSH se a ligação cair.",
                    icon = Icons.Filled.Autorenew,
                    accent = ElectricBlue,
                    checked = settings.autoReconnect,
                    onCheckedChange = onToggleAutoReconnect
                )

                SettingToggleItem(
                    title = "Reconexão inteligente (Wi-Fi / Dados)",
                    subtitle = "Deteta mudanças entre Wi-Fi e Dados Móveis e restabelece o túnel rapidamente.",
                    icon = Icons.Filled.Wifi,
                    accent = ElectricBlue,
                    checked = settings.reconnectOnNetworkRestore,
                    onCheckedChange = onToggleReconnectOnNetRestore
                )

                SettingToggleItem(
                    title = "Manter Conectado (Keep Alive)",
                    subtitle = "Envia keep-alive SSH/TCP configurável e mantém o serviço ativo em segundo plano.",
                    icon = Icons.Filled.Bolt,
                    accent = NeonGreen,
                    checked = settings.keepAlive,
                    onCheckedChange = onToggleKeepAlive
                )

                SettingToggleItem(
                    title = "Kill Switch (Bloquear sem VPN)",
                    subtitle = "Mantém a interface TUN bloqueando tráfego fora da VPN durante quedas ou reconexões.",
                    icon = Icons.Filled.Lock,
                    accent = AmberPulse,
                    checked = settings.killSwitch,
                    onCheckedChange = onToggleKillSwitch
                )

                SettingToggleItem(
                    title = "AdBlock Ultra (APKs e Sites)",
                    subtitle = "Bloqueia ${AdBlockEngine.ruleCount} domínios + heurística ($blockedAdsCount bloqueados).",
                    icon = Icons.Filled.Shield,
                    accent = NeonGreen,
                    checked = settings.adBlockEnabled,
                    onCheckedChange = onToggleAdBlock
                )

                SettingToggleItem(
                    title = "Modo Ultra (TLS SNI + HTTP + Anti-DoH)",
                    subtitle = "Inspeciona o 1.º pacote TCP para travar anúncios que furam DNS.",
                    icon = Icons.Filled.Bolt,
                    accent = ElectricBlue,
                    checked = settings.adBlockAggressive,
                    onCheckedChange = onToggleAdBlockAggressive
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAdBlock)
                        .testTag("settings_open_adblock_row"),
                    shape = RoundedCornerShape(16.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Filled.Shield, null, tint = NeonGreen)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gerir Listas & Exceções AdBlock", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                "Sincronizar listas fortes (HaGeZi, OISD, AdGuard), Blacklist, Whitelist e bloqueios ao vivo.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "Abrir",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen,
                            fontSize = 13.sp
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDnsDialog = true }
                        .testTag("settings_dns_row"),
                    shape = RoundedCornerShape(16.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Filled.Dns, null, tint = ElectricBlue)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("DNS Personalizado", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                "DNS com cache TTL encaminhado pelo túnel SSH (atual: ${settings.customDns})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = settings.customDns,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    if (showDnsDialog) {
        DnsConfigDialog(
            currentDns = settings.customDns,
            onDismiss = { showDnsDialog = false },
            onSave = { dns ->
                onSaveCustomDns(dns)
                showDnsDialog = false
            }
        )
    }

    if (showMtuDialog) {
        MtuConfigDialog(
            currentMtu = settings.customMtu,
            onDismiss = { showMtuDialog = false },
            onSave = { mtu ->
                onSaveCustomMtu(mtu)
                showMtuDialog = false
            }
        )
    }
}

@Composable
private fun MtuConfigDialog(
    currentMtu: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var mtuText by remember(currentMtu) { mutableStateOf(if (currentMtu == 0) "" else currentMtu.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        title = { Text("MTU Configurável", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Deixe vazio para Automático consoante o modo de velocidade, ou indique um valor entre 1200 e 1500.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = mtuText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 4) mtuText = input
                    },
                    label = { Text("MTU (ex: 1500 ou vazio = Auto)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Auto" to "", "1500" to "1500", "1460" to "1460", "1360" to "1360").forEach { (label, valStr) ->
                        OutlinedButton(
                            onClick = { mtuText = valStr },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CyberBorder)
                        ) {
                            Text(label, fontSize = 11.sp, color = ElectricBlue, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = mtuText.toIntOrNull() ?: 0
                    onSave(if (parsed == 0) 0 else parsed.coerceIn(1200, 1500))
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = PitchBlack)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun SettingToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CyberCard,
        border = BorderStroke(1.dp, if (checked) accent.copy(alpha = 0.45f) else CyberBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, null, tint = if (checked) accent else TextMuted)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, color = TextSecondary)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PitchBlack,
                    checkedTrackColor = accent
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    logs: List<LogEntry>,
    onBack: () -> Unit,
    onClearLogs: () -> Unit
) {
    Scaffold(
        containerColor = PitchBlack,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Registos de Conexão", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("logs_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = onClearLogs, modifier = Modifier.testTag("clear_logs_button")) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Limpar registos", tint = TextSecondary)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentAlignment = Alignment.TopCenter
        ) {
            if (logs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.ReceiptLong, null, tint = TextMuted, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Sem registos ainda", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "Os eventos reais de TCP, TLS/SNI, Payload, SSH, MTU e AdBlock aparecerão aqui.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logs.asReversed(), key = { it.id }) { entry ->
                        val levelColor = when (entry.level) {
                            LogLevel.SUCCESS -> NeonGreen
                            LogLevel.WARN -> AmberPulse
                            LogLevel.ERROR -> CrimsonAlert
                            LogLevel.INFO -> ElectricBlue
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberCard,
                            border = BorderStroke(1.dp, CyberBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = entry.timestamp,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = levelColor
                                )
                                Text(
                                    text = entry.message,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerTestScreen(
    selectedServer: ServerConfig?,
    serversCount: Int,
    isTesting: Boolean,
    isBenchmarkingAll: Boolean,
    result: ServerTestResult?,
    benchmarkResults: List<ServerTestResult>,
    onBack: () -> Unit,
    onRunTest: (ServerConfig?) -> Unit,
    onRunSmartBenchmarkAll: () -> Unit
) {
    Scaffold(
        containerColor = PitchBlack,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Testar & Selecionar Servidor", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("test_back_button")) {
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
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.NetworkCheck, null, tint = ElectricBlue)
                            Text("Benchmark & Seleção Inteligente", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }

                        if (selectedServer == null) {
                            Text(
                                "Nenhum servidor selecionado. Configure e selecione um servidor primeiro.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        } else {
                            Text(
                                text = "${selectedServer.displayTitle} (${selectedServer.sshHost}:${selectedServer.effectivePort})",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 13.sp,
                                color = NeonGreen
                            )
                            Text(
                                text = "Avalia latência, jitter, estabilidade TCP, TLS/SNI, tempo SSH, perda de pacotes e transferência real.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        Button(
                            onClick = { onRunTest(selectedServer) },
                            enabled = !isTesting && !isBenchmarkingAll && selectedServer != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("run_server_test_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = PitchBlack
                            )
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = PitchBlack,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("A testar servidor...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Filled.PlayArrow, null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testar Servidor Selecionado", fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onRunSmartBenchmarkAll,
                            enabled = !isTesting && !isBenchmarkingAll && serversCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("benchmark_all_servers_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.7f))
                        ) {
                            if (isBenchmarkingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = NeonGreen,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("A comparar $serversCount servidor(es)...", color = NeonGreen, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Filled.AutoAwesome, null, tint = NeonGreen)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Selecionar Melhor Servidor ($serversCount)", color = NeonGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (result != null) {
                    BenchmarkResultCard(result = result, rankIndex = null)
                }

                if (benchmarkResults.isNotEmpty()) {
                    Text(
                        text = "RANKING INTELIGENTE DE SERVIDORES",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                    benchmarkResults.forEachIndexed { idx, item ->
                        BenchmarkResultCard(result = item, rankIndex = idx + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkResultCard(
    result: ServerTestResult,
    rankIndex: Int?
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (result.success) NeonGreenContainer else CrimsonContainer,
        border = BorderStroke(1.dp, if (result.success) NeonGreen else CrimsonAlert)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (result.success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = if (result.success) NeonGreen else CrimsonAlert
                    )
                    Text(
                        text = if (rankIndex != null) "#$rankIndex ${result.serverName}" else result.serverName.ifBlank { "Resultado" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                if (result.success) {
                    Text(
                        text = "${result.compositeScore}/100",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen,
                        fontSize = 15.sp
                    )
                }
            }

            if (result.success) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ping: ${result.latencyMs}ms", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = TextPrimary)
                    Text("Estab: ${result.tcpStabilityPercent}%", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = ElectricBlue)
                    Text("SSH: ${result.sshSetupMs}ms", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = TextPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Transf: ${if (result.transferSpeedBps > 0) formatSpeed(result.transferSpeedBps) else "N/D"}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = NeonGreen
                    )
                    Text(
                        text = "Perda: ${String.format("%.0f%%", result.packetLossPercent)}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = when (result.tlsCompatible) {
                            true -> "TLS: OK"
                            false -> "TLS: Erro"
                            null -> "SSH Direto"
                        },
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = ElectricBlue
                    )
                }
            } else if (!result.errorMessage.isNullOrBlank()) {
                Text(result.errorMessage, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }

            result.details.forEach { step ->
                Text(
                    text = "• $step",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
