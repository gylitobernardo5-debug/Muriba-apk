package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val MuribaDarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = PitchBlack,
    primaryContainer = NeonGreenContainer,
    onPrimaryContainer = NeonGreen,
    secondary = ElectricBlue,
    onSecondary = PitchBlack,
    secondaryContainer = ElectricBlueContainer,
    onSecondaryContainer = ElectricBlue,
    tertiary = AmberPulse,
    onTertiary = PitchBlack,
    background = PitchBlack,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = CyberCard,
    surfaceContainerHigh = CyberCardElevated,
    outline = CyberBorder,
    error = CrimsonAlert,
    onError = TextPrimary,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonAlert
)

val MuribaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MuribaVpnTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MuribaDarkColorScheme,
        typography = Typography,
        shapes = MuribaShapes,
        content = content
    )
}
