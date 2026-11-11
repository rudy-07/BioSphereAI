package com.madhusudhan.biosphereai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = IndigoDeep,
    secondary = Cyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEFF4),
    onSecondaryContainer = Color(0xFF053E47),
    background = LightBackground,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightInkSecondary,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightInkSecondary,
    outlineVariant = LightOutline,
    error = Color(0xFFD63B3B),
)

private val DarkColors = darkColorScheme(
    primary = IndigoSoft,
    onPrimary = Color(0xFF0B1150),
    primaryContainer = Color(0xFF2E3A8C),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = CyanSoft,
    onSecondary = Color(0xFF00363E),
    secondaryContainer = Color(0xFF0B4C57),
    onSecondaryContainer = Color(0xFFCDEFF4),
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkSecondary,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    outline = DarkInkSecondary,
    outlineVariant = DarkOutline,
    error = Color(0xFFFF6B6B),
)

@Composable
fun BioSphereAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBioColors provides if (darkTheme) DarkBioColors else LightBioColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content,
        )
    }
}
