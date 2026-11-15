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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnSettings
import com.example.ui.AdBlockUiState
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CrimsonAlert
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
import com.example.vpn.AdBlockListSync
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockScreen(
    settings: VpnSettings,
    adBlockUi: AdBlockUiState,
    blockedAdsCount: Long,
    isSyncing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleAggressive: (Boolean) -> Unit,
    onToggleBlockDoh: (Boolean) -> Unit,
    onToggleBlockSniHttp: (Boolean) -> Unit,
    onSyncUrl: (String) -> Unit,
    onClearExternal: () -> Unit,
    onAddBlacklist: (String) -> Unit,
    onRemoveBlacklist: (String) -> Unit,
    onAddWhitelist: (String) -> Unit,
    onRemoveWhitelist: (String) -> Unit,
    onClearRecent: () -> Unit
) {
    var customUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    Scaffold(
        containerColor = PitchBlack,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("AdBlock Ultra • Firewall", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("adblock_back_button")) {
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
                // Cabeçalho de Estado & Estatísticas
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = CyberCardElevated,
                    border = BorderStroke(
                        1.dp,
                        if (settings.adBlockEnabled) NeonGreen.copy(alpha = 0.55f) else CyberBorder
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = if (settings.adBlockEnabled) NeonGreen else TextMuted,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column {
                                    Text(
                                        text = "Escudo Anti-Anúncios Global",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Protege todos os APKs, jogos e navegadores",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = settings.adBlockEnabled,
                                onCheckedChange = onToggleEnabled,
                                modifier = Modifier.testTag("adblock_master_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PitchBlack,
                                    checkedTrackColor = NeonGreen
                                )
                            )
                        }

                        HorizontalDivider(color = CyberBorder.copy(alpha = 0.6f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBlock("Regras Ativas", "${adBlockUi.totalRules}", NeonGreen)
                            StatBlock("Listas Extras", "${adBlockUi.externalCount}", ElectricBlue)
                            StatBlock("Bloqueados", "$blockedAdsCount", AmberPulse)
                        }

                        if (adBlockUi.lastListName.isNotBlank()) {
                            Text(
                                text = "Lista ativa offline: ${adBlockUi.lastListName}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = ElectricBlue
                            )
                        }
                    }
                }

                // Camadas Multi-Nível (Heurística + Anti-DoH/DoT + TLS SNI/HTTP)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CyberCard,
                    border = BorderStroke(
                        1.dp,
                        if (settings.adBlockAggressive) ElectricBlue.copy(alpha = 0.5f) else CyberBorder
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = if (settings.adBlockAggressive) ElectricBlue else TextMuted
                            )
                            Text(
                                text = "Camadas de Proteção Máxima",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }

                        ShieldLayerToggle(
                            title = "Modo Ultra + Heurística Agressiva",
                            subtitle = "Deteta subdomínios, SDKs de jogos/APKs e padrões de banners.",
                            checked = settings.adBlockAggressive,
                            enabled = settings.adBlockEnabled,
                            accent = ElectricBlue,
                            testTag = "adblock_aggressive_switch",
                            onCheckedChange = onToggleAggressive
                        )

                        ShieldLayerToggle(
                            title = "Inspeção Profunda TLS SNI & HTTP (APKs)",
                            subtitle = "Analisa o 1.º pacote TCP para travar anúncios com cache DNS ou IP direto.",
                            checked = settings.adBlockBlockSniHttp,
                            enabled = settings.adBlockEnabled && settings.adBlockAggressive,
                            accent = NeonGreen,
                            testTag = "adblock_sni_http_switch",
                            onCheckedChange = onToggleBlockSniHttp
                        )

                        ShieldLayerToggle(
                            title = "Anti-Bypass DoT (853) & DoH",
                            subtitle = "Bloqueia DNS privado de Chrome/Firefox/Android que tenta furar o filtro.",
                            checked = settings.adBlockBlockDoh,
                            enabled = settings.adBlockEnabled && settings.adBlockAggressive,
                            accent = AmberPulse,
                            testTag = "adblock_doh_switch",
                            onCheckedChange = onToggleBlockDoh
                        )
                    }
                }

                // Listas Comunitárias Massivas (1-Toque HTTPS)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CyberCard,
                    border = BorderStroke(1.dp, CyberBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                Icon(Icons.Filled.CloudDownload, null, tint = NeonGreen)
                                Text(
                                    text = "Listas Comunitárias Fortes",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                            if (adBlockUi.externalCount > 0) {
                                TextButton(onClick = onClearExternal, enabled = !isSyncing) {
                                    Text("Limpar extras", color = CrimsonAlert, fontSize = 12.sp)
                                }
                            }
                        }

                        Text(
                            text = "Descarrega até 250.000 domínios anti-anúncios para uso offline persistente (Hosts, AdGuard e OISD).",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        if (isSyncing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = NeonGreen,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "A sincronizar e compilar regras offline...",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp,
                                    color = NeonGreen
                                )
                            }
                        }

                        AdBlockListSync.presets.forEach { preset ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = PitchBlack.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, CyberBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.title,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = preset.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onSyncUrl(preset.url) },
                                        enabled = !isSyncing,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.7f)),
                                        modifier = Modifier.testTag("sync_preset_${preset.id}")
                                    ) {
                                        Text("Aplicar", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customUrl,
                                onValueChange = { customUrl = it },
                                label = { Text("URL personalizada (hosts / adblock)") },
                                placeholder = { Text("https://...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricBlue,
                                    unfocusedBorderColor = CyberBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            Button(
                                onClick = { onSyncUrl(customUrl) },
                                enabled = !isSyncing && customUrl.isNotBlank(),
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricBlue,
                                    contentColor = PitchBlack
                                )
                            ) {
                                Text("Baixar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                AdBlockCustomRulesCard(
                    adBlockUi = adBlockUi,
                    onAddBlacklist = onAddBlacklist,
                    onRemoveBlacklist = onRemoveBlacklist,
                    onAddWhitelist = onAddWhitelist,
                    onRemoveWhitelist = onRemoveWhitelist
                )

                AdBlockRecentEventsCard(
                    adBlockUi = adBlockUi,
                    onAddWhitelist = onAddWhitelist,
                    onClearRecent = onClearRecent
                )
            }
        }
    }
}

@Composable
private fun ShieldLayerToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    accent: Color,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) TextPrimary else TextMuted
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = PitchBlack,
                checkedTrackColor = accent
            )
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, accent: Color) {
    Column {
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = accent
        )
    }
}
