package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AdBlockUiState
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdBlockCustomRulesCard(
    adBlockUi: AdBlockUiState,
    onAddBlacklist: (String) -> Unit,
    onRemoveBlacklist: (String) -> Unit,
    onAddWhitelist: (String) -> Unit,
    onRemoveWhitelist: (String) -> Unit
) {
    var domainInput by remember { mutableStateOf("") }
    var ruleTab by remember { mutableIntStateOf(0) }

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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { ruleTab = 0 },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (ruleTab == 0) CrimsonAlert else CyberBorder)
                ) {
                    Icon(
                        Icons.Filled.Block,
                        null,
                        tint = if (ruleTab == 0) CrimsonAlert else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Bloquear (${adBlockUi.blacklistCount})",
                        color = if (ruleTab == 0) CrimsonAlert else TextSecondary,
                        fontSize = 12.sp
                    )
                }
                OutlinedButton(
                    onClick = { ruleTab = 1 },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (ruleTab == 1) NeonGreen else CyberBorder)
                ) {
                    Icon(
                        Icons.Filled.GppGood,
                        null,
                        tint = if (ruleTab == 1) NeonGreen else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Permitir (${adBlockUi.whitelistCount})",
                        color = if (ruleTab == 1) NeonGreen else TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    label = {
                        Text(if (ruleTab == 0) "Domínio para bloquear" else "Exceção (Whitelist)")
                    },
                    placeholder = { Text("ex: ads.site.com") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("adblock_domain_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (ruleTab == 0) CrimsonAlert else NeonGreen,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Button(
                    onClick = {
                        if (ruleTab == 0) onAddBlacklist(domainInput) else onAddWhitelist(domainInput)
                        domainInput = ""
                    },
                    enabled = domainInput.isNotBlank(),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("adblock_domain_add_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ruleTab == 0) CrimsonAlert else NeonGreen,
                        contentColor = PitchBlack
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Adicionar")
                }
            }

            val activeList = if (ruleTab == 0) adBlockUi.blacklist else adBlockUi.whitelist
            if (activeList.isEmpty()) {
                Text(
                    text = if (ruleTab == 0) {
                        "Nenhum domínio extra na lista negra manual."
                    } else {
                        "Sem exceções configuradas. Toque em 'Permitir' num bloqueio recente se um app precisar de acesso."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeList.forEach { domain ->
                        Surface(
                            shape = CircleShape,
                            color = PitchBlack.copy(alpha = 0.55f),
                            border = BorderStroke(
                                1.dp,
                                if (ruleTab == 0) CrimsonAlert.copy(alpha = 0.6f) else NeonGreen.copy(alpha = 0.6f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = domain,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                )
                                IconButton(
                                    onClick = {
                                        if (ruleTab == 0) onRemoveBlacklist(domain) else onRemoveWhitelist(domain)
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Remover $domain",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdBlockRecentEventsCard(
    adBlockUi: AdBlockUiState,
    onAddWhitelist: (String) -> Unit,
    onClearRecent: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CyberCard,
        border = BorderStroke(1.dp, CyberBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BLOQUEIOS RECENTES EM TEMPO REAL",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberPulse
                )
                if (adBlockUi.recentEvents.isNotEmpty()) {
                    TextButton(onClick = onClearRecent) {
                        Text("Limpar", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }

            if (adBlockUi.recentEvents.isEmpty()) {
                Text(
                    text = "Ligue a VPN e abra qualquer APK ou navegador para ver os pedidos de anúncios bloqueados aqui.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            } else {
                val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                adBlockUi.recentEvents.take(25).forEach { ev ->
                    val layerColor = when (ev.layer) {
                        "DNS" -> NeonGreen
                        "DoT", "DoH" -> AmberPulse
                        else -> ElectricBlue
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = PitchBlack.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, CyberBorder.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ev.host,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${ev.layer} • ${fmt.format(Date(ev.timestampMs))}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    color = layerColor
                                )
                            }
                            if (!ev.host.startsWith("Porta ")) {
                                TextButton(onClick = { onAddWhitelist(ev.host) }) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        null,
                                        tint = NeonGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Permitir", color = NeonGreen, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
