package com.premiumcar.launcher.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Cores da [PcPalette] como [Color] para uso em Compose. */
object PcColors {
    val Background = Color(PcPalette.BACKGROUND)
    val Surface = Color(PcPalette.SURFACE)
    val SurfaceHigh = Color(PcPalette.SURFACE_HIGH)
    val SurfaceHighest = Color(PcPalette.SURFACE_HIGHEST)
    val Outline = Color(PcPalette.OUTLINE)
    val TextPrimary = Color(PcPalette.TEXT_PRIMARY)
    val TextSecondary = Color(PcPalette.TEXT_SECONDARY)
    val TextDisabled = Color(PcPalette.TEXT_DISABLED)
    val Accent = Color(PcPalette.ACCENT)
    val AccentDim = Color(PcPalette.ACCENT_DIM)
    val OnAccent = Color(PcPalette.ON_ACCENT)
    val Warning = Color(PcPalette.WARNING)
    val Error = Color(PcPalette.ERROR)
    val Success = Color(PcPalette.SUCCESS)
}

private val PcColorScheme = darkColorScheme(
    primary = PcColors.Accent,
    onPrimary = PcColors.OnAccent,
    primaryContainer = PcColors.AccentDim,
    onPrimaryContainer = PcColors.TextPrimary,
    secondary = PcColors.TextSecondary,
    onSecondary = PcColors.Background,
    background = PcColors.Background,
    onBackground = PcColors.TextPrimary,
    surface = PcColors.Surface,
    onSurface = PcColors.TextPrimary,
    surfaceVariant = PcColors.SurfaceHigh,
    onSurfaceVariant = PcColors.TextSecondary,
    outline = PcColors.Outline,
    outlineVariant = PcColors.Outline,
    error = PcColors.Error,
)

/** Tema provisório (Material 3 personalizado). A tipografia definitiva vem na Fase 2. */
@Composable
fun NyxCarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PcColorScheme, content = content)
}
