package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnRuntimeState
import com.example.ui.components.formatDuration
import com.example.ui.components.formatSpeed
import com.example.ui.theme.ChakraPetchFamily
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberCard
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.PitchBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MuribaMcConnectedScreen(
    vpnState: VpnRuntimeState,
    onContinueToDashboard: () -> Unit,
    onDisconnect: () -> Unit
) {
    BackHandler(onBack = onContinueToDashboard)

    val infinite = rememberInfiniteTransition(label = "muriba_mc_energy")
    val wave1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )
    val wave2 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )
    val arcRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arc_rot"
    )
    val reverseRotation by infinite.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(8200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rev_rot"
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020408))
            .testTag("muriba_mc_screen")
    ) {
        // Fundo tecnológico animado por GPU (zero impacto na thread de rede VPN)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * 0.43f
            val maxR = size.minDimension * 0.62f

            // Grelha tecnológica sutil
            val gridStep = 44.dp.toPx()
            val gridColor = ElectricBlue.copy(alpha = 0.055f)
            var x = 0f
            while (x < w) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                x += gridStep
            }
            var y = 0f
            while (y < h) {
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                y += gridStep
            }

            // Brilho radial central neon verde + azul elétrico
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonGreen.copy(alpha = 0.22f * glowPulse),
                        ElectricBlue.copy(alpha = 0.14f * glowPulse),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = maxR * 1.15f
                ),
                radius = maxR * 1.15f,
                center = Offset(cx, cy)
            )

            // Ondas de conexão expansivas
            listOf(wave1, wave2).forEach { progress ->
                val r = maxR * (0.28f + 0.85f * progress)
                val alpha = (1f - progress).coerceIn(0f, 1f) * 0.55f
                drawCircle(
                    color = if (progress < 0.5f) NeonGreen.copy(alpha = alpha) else ElectricBlue.copy(alpha = alpha),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // Anéis de energia rotativos
            val ringRadius1 = maxR * 0.52f
            val ringRadius2 = maxR * 0.68f
            rotate(degrees = arcRotation, pivot = Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeonGreen,
                            ElectricBlue,
                            Color.Transparent
                        ),
                        center = Offset(cx, cy)
                    ),
                    startAngle = 0f,
                    sweepAngle = 200f,
                    useCenter = false,
                    topLeft = Offset(cx - ringRadius1, cy - ringRadius1),
                    size = androidx.compose.ui.geometry.Size(ringRadius1 * 2f, ringRadius1 * 2f),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            rotate(degrees = reverseRotation, pivot = Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            ElectricBlue,
                            NeonGreen.copy(alpha = 0.7f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy)
                    ),
                    startAngle = 90f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - ringRadius2, cy - ringRadius2),
                    size = androidx.compose.ui.geometry.Size(ringRadius2 * 2f, ringRadius2 * 2f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Partículas orbitais de energia
            val orbitCount = 6
            for (i in 0 until orbitCount) {
                val angleRad = Math.toRadians((arcRotation + i * (360.0 / orbitCount)))
                val px = cx + (ringRadius1 * cos(angleRad)).toFloat()
                val py = cy + (ringRadius1 * sin(angleRad)).toFloat()
                drawCircle(
                    color = if (i % 2 == 0) NeonGreen else ElectricBlue,
                    radius = 3.5.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Indicador superior de túnel real ativo
            Surface(
                shape = CircleShape,
                color = NeonGreen.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "TÚNEL SEGURO ATIVO",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonGreen
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Texto central GRANDES LETRAS: MURIBA MC
            Text(
                text = "MURIBA MC",
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = ChakraPetchFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 50.sp,
                    lineHeight = 54.sp,
                    letterSpacing = 4.sp,
                    brush = Brush.linearGradient(
                        colors = listOf(NeonGreen, Color.White, ElectricBlue)
                    ),
                    shadow = Shadow(
                        color = NeonGreen.copy(alpha = 0.85f * glowPulse),
                        offset = Offset.Zero,
                        blurRadius = 36f
                    )
                ),
                modifier = Modifier.testTag("muriba_mc_title")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "ADM MURIBA VPN • ${vpnState.activeSpeedMode.label}",
                style = MaterialTheme.typography.labelLarge,
                color = ElectricBlue,
                textAlign = TextAlign.Center,
                letterSpacing = 1.6.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Cartão de telemetria real da ligação
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                shape = RoundedCornerShape(20.dp),
                color = CyberCard.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, CyberBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Servidor", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text(
                                text = vpnState.serverName.ifBlank { vpnState.serverHost },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Endereço IP", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text(
                                text = vpnState.connectedIp.ifBlank { "A obter..." },
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.ArrowDownward, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                            Text(
                                text = formatSpeed(vpnState.downloadSpeedBps),
                                fontFamily = JetBrainsMonoFamily,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.ArrowUpward, null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                            Text(
                                text = formatSpeed(vpnState.uploadSpeedBps),
                                fontFamily = JetBrainsMonoFamily,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = vpnState.latencyMs?.let { "${it}ms" } ?: formatDuration(vpnState.durationSeconds),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("muriba_mc_disconnect_button"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert)
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("DESLIGAR", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onContinueToDashboard,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(52.dp)
                        .testTag("muriba_mc_dashboard_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = PitchBlack
                    )
                ) {
                    Icon(Icons.Filled.Dashboard, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("VER PAINEL", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
