package com.madhusudhan.biosphereai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEFD9),
    onPrimaryContainer = GreenDeep,
    secondary = LightInkSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightInk,
    background = LightBackground,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightInkSecondary,
    surfaceContainer = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = LightSurfaceVariant,
    outline = LightInkSecondary,
    outlineVariant = LightOutline,
    error = Color(0xFFD93025),
)

private fun darkColors(background: Color, surface: Color, surfaceVariant: Color) = darkColorScheme(
    primary = GreenSoft,
    onPrimary = Color(0xFF0B2E0D),
    primaryContainer = Color(0xFF1F3A21),
    onPrimaryContainer = Color(0xFFCDEBCB),
    secondary = DarkInkSecondary,
    onSecondary = background,
    secondaryContainer = surfaceVariant,
    onSecondaryContainer = DarkInk,
    background = background,
    onBackground = DarkInk,
    surface = surface,
    onSurface = DarkInk,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = DarkInkSecondary,
    surfaceContainer = surface,
    surfaceContainerLow = surface,
    surfaceContainerHigh = surfaceVariant,
    surfaceContainerHighest = surfaceVariant,
    outline = DarkInkSecondary,
    outlineVariant = DarkOutline,
    error = Color(0xFFFF6B5E),
)

private val GraphiteColors = darkColors(GraphiteBackground, GraphiteSurface, GraphiteSurfaceVariant)
private val AmoledColors = darkColors(AmoledBackground, AmoledSurface, AmoledSurfaceVariant)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun BioSphereAITheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(mode)
    val scheme = when {
        !dark -> LightColors
        pureBlack -> AmoledColors
        else -> GraphiteColors
    }
    CompositionLocalProvider(LocalBioColors provides if (dark) DarkBioColors else LightBioColors) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
