package com.madhusudhan.biosphereai.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Accent: one clinical green, used for "in range" and primary actions.
val Green = Color(0xFF2F7D32)
val GreenDeep = Color(0xFF1B5E20)
val GreenSoft = Color(0xFF7BC67E)

// Light: soft grey page, white cards.
val LightBackground = Color(0xFFF2F3F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEBEDEF)
val LightInk = Color(0xFF16181A)
val LightInkSecondary = Color(0xFF5F6368)
val LightOutline = Color(0xFFDADCE0)

// Dark: graphite (brushed-metal greys, no blue tint).
val GraphiteBackground = Color(0xFF121314)
val GraphiteSurface = Color(0xFF1C1D1F)
val GraphiteSurfaceVariant = Color(0xFF26282B)
val DarkInk = Color(0xFFECEDEE)
val DarkInkSecondary = Color(0xFFA9ACB0)
val DarkOutline = Color(0xFF34373B)

// Dark: pure black for AMOLED screens.
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0E0F10)
val AmoledSurfaceVariant = Color(0xFF1A1B1D)

/** Colours with a fixed meaning in a CGM app (glucose status, chart marks). */
@Immutable
data class BioColors(
    val inRange: Color,
    val low: Color,
    val urgentLow: Color,
    val high: Color,
    val forecast: Color,
    val cloudForecast: Color,
    val targetBand: Color,
    val futureShade: Color,
    val meal: Color,
    val insulin: Color,
    val exercise: Color,
)

val LightBioColors = BioColors(
    inRange = Green,
    low = Color(0xFFD93025),
    urgentLow = Color(0xFFA50E0E),
    high = Color(0xFFC98A00),
    forecast = Color(0xFF55606E),
    cloudForecast = Color(0xFF2B6CB0),
    targetBand = Color(0x1A2F7D32),
    futureShade = Color(0x0A16181A),
    meal = Color(0xFFD9822B),
    insulin = Color(0xFF2B6CB0),
    exercise = Color(0xFF00897B),
)

val DarkBioColors = BioColors(
    inRange = GreenSoft,
    low = Color(0xFFFF6B5E),
    urgentLow = Color(0xFFFF3B30),
    high = Color(0xFFFFC233),
    forecast = Color(0xFFA3ACB8),
    cloudForecast = Color(0xFF7FB2E5),
    targetBand = Color(0x247BC67E),
    futureShade = Color(0x0DFFFFFF),
    meal = Color(0xFFFFA552),
    insulin = Color(0xFF7FB2E5),
    exercise = Color(0xFF4DB6AC),
)

val LocalBioColors = staticCompositionLocalOf { LightBioColors }
