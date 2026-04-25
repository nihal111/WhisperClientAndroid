package com.wispr.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = darkColorScheme(
    background = DeepDark,
    surface = SurfaceCard,
    surfaceVariant = SurfaceVariant,
    primary = BrandBlue,
    primaryContainer = BrandBlueContainer,
    secondary = AccentTeal,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = Outline,
)

@Composable
fun WhisperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = WhisperTypography,
        content = content,
    )
}
