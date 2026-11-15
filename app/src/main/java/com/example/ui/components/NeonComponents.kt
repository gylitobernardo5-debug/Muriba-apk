package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnStatus
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.CyberCardElevated
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.vpn.AdBlockEngine

@Composable
fun StatusPill(
    status: VpnStatus,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent by animateColorAsState(
        targetValue = when (status) {
            VpnStatus.CONNECTED -> NeonGreen
            VpnStatus.CONNECTING -> ElectricBlue
            VpnStatus.RECONNECTING -> AmberPulse
            VpnStatus.DISCONNECTED -> TextSecondary
        },
        label = "status_accent"
    )

    val label = when (status) {
        VpnStatus.CONNECTED -> "CONECTADO"
        VpnStatus.CONNECTING -> "A CONECTAR"
        VpnStatus.RECONNECTING -> "A RECONECTAR"
        VpnStatus.DISCONNECTED -> "DESCONECTADO"
    }

    Surface(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .testTag("status_pill"),
        shape = CircleShape,
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
        }
    }
}

@Composable
fun PowerHeroButton(
    status: VpnStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = status == VpnStatus.CONNECTED
    val isConnecting = status == VpnStatus.CONNECTING || status == VpnStatus.RECONNECTING

    val primaryColor by animateColorAsState(
        targetValue = when (status) {
            VpnStatus.CONNECTED -> NeonGreen
            VpnStatus.CONNECTING -> ElectricBlue
            VpnStatus.RECONNECTING -> AmberPulse
            VpnStatus.DISCONNECTED -> ElectricBlue
        },
        animationSpec = tween(450),
        label = "power_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "power_rings")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 1400 else 6000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "arc_rotation"
    )

    val actionText = if (isActive || isConnecting) "DESLIGAR" else "LIGAR"

    Box(
        modifier = modifier.size(196.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // Outer ambient glow
            val glowAlpha = if (isActive) 0.24f else if (isConnecting) 0.18f else 0.08f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = glowAlpha), Color.Transparent),
                    center = center,
                    radius = maxRadius * pulse
                ),
                radius = maxRadius * pulse,
                center = center
            )

            // Outer ring
            drawCircle(
                color = primaryColor.copy(alpha = if (isActive) 0.4f else 0.18f),
                radius = maxRadius * 0.84f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Rotating energy arc when connecting or connected
            if (isActive || isConnecting) {
                rotate(degrees = rotation, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                ElectricBlue,
                                NeonGreen,
                                Color.Transparent
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 220f,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .size(144.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .testTag("power_toggle_button"),
            shape = CircleShape,
            color = if (isActive) Color(0xFF062618) else CyberCardElevated,
            border = BorderStroke(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = if (isActive) {
                        listOf(NeonGreen, ElectricBlue)
                    } else {
                        listOf(ElectricBlue.copy(alpha = 0.6f), CyberBorder)
                    }
                )
            ),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = actionText,
                    tint = if (isActive) NeonGreen else if (isConnecting) AmberPulse else TextPrimary,
                    modifier = Modifier.size(46.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 15.sp,
                    color = if (isActive) NeonGreen else TextPrimary
                )
            }
        }
    }
}

@Composable
fun SpeedMetricTile(
    label: String,
    speedText: String,
    totalText: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = CyberCard,
        border = BorderStroke(1.dp, CyberBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = speedText,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Total: $totalText",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
fun QuickToggleChip(
    title: String,
    checked: Boolean,
    accent: Color,
    icon: ImageVector,
    testTag: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = if (checked) accent.copy(alpha = 0.14f) else CyberCard,
        border = BorderStroke(1.dp, if (checked) accent.copy(alpha = 0.6f) else CyberBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (checked) accent else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                color = if (checked) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (checked) accent else TextMuted.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
fun DnsConfigDialog(
    currentDns: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var dnsInput by remember(currentDns) { mutableStateOf(currentDns) }
    val presets = listOf(
        "Cloudflare" to "1.1.1.1",
        "Google" to "8.8.8.8",
        "Quad9" to "9.9.9.9",
        "AdGuard" to "94.140.14.14"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        icon = {
            Icon(
                imageVector = Icons.Filled.Dns,
                contentDescription = null,
                tint = ElectricBlue
            )
        },
        title = {
            Text("DNS Personalizado", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "O DNS é encaminhado através do túnel SSH (DNS-over-TCP) para evitar fugas.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = dnsInput,
                    onValueChange = { dnsInput = it },
                    label = { Text("Endereço IP do DNS") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dns_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.take(2).forEach { (name, ip) ->
                        OutlinedButton(
                            onClick = { dnsInput = ip },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CyberBorder)
                        ) {
                            Text("$name", fontSize = 11.sp, color = ElectricBlue, maxLines = 1)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.drop(2).forEach { (name, ip) ->
                        OutlinedButton(
                            onClick = { dnsInput = ip },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CyberBorder)
                        ) {
                            Text("$name", fontSize = 11.sp, color = NeonGreen, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(dnsInput) },
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
fun AdBlockDialog(
    enabled: Boolean,
    blockedCount: Long,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        icon = {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = NeonGreen
            )
        },
        title = {
            Text("AdBlock Ultra", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Filtra DNS, TLS SNI, cabeçalhos HTTP e DoH de redes de anúncios em APKs, jogos e navegadores.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Ativar AdBlock Ultra", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${AdBlockEngine.ruleCount} regras ativas • $blockedCount bloqueados",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp,
                            color = NeonGreen
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PitchBlack,
                            checkedTrackColor = NeonGreen
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = ElectricBlue)
            }
        }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        icon = {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = NeonGreen
            )
        },
        title = {
            Text("ADM MURIBA VPN SIMPLES", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Versão ${com.example.BuildConfig.VERSION_NAME} • EXTREME ENGINE V4", color = ElectricBlue, fontWeight = FontWeight.SemiBold)
                Text(
                    "Cliente VPN Android real com Ultra TUN Buffer Pool, Janela SSH até 4MB, Multi-Túnel paralelo, TLS 1.3 com SNI real, Detector de Gargalos, Benchmark Interno e Cloud Server Infinity.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                    Text("Credenciais protegidas com Android Keystore AES-256", fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                    Text("Compatível com Android 14+ (ARMv7 & ARM64)", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = NeonGreen)
            }
        }
    )
}

fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec < 1024L -> "$bytesPerSec B/s"
        bytesPerSec < 1024L * 1024L -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
        else -> String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return String.format("%02d:%02d:%02d", h, m, s)
}
