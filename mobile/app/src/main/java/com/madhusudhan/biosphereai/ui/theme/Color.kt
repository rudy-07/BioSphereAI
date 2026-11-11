package com.madhusudhan.biosphereai.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand
val Indigo = Color(0xFF3D4FD6)
val IndigoDeep = Color(0xFF2A389E)
val IndigoSoft = Color(0xFF8E9BFF)
val Cyan = Color(0xFF0E8FA3)
val CyanSoft = Color(0xFF52CBDB)

// Surfaces
val LightBackground = Color(0xFFF4F5FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE8EAF4)
val LightInk = Color(0xFF12152B)
val LightInkSecondary = Color(0xFF555A75)
val LightOutline = Color(0xFFD3D6E4)

val DarkBackground = Color(0xFF0D1020)
val DarkSurface = Color(0xFF171B2E)
val DarkSurfaceVariant = Color(0xFF22273E)
val DarkInk = Color(0xFFEDEEF6)
val DarkInkSecondary = Color(0xFFB2B6CC)
val DarkOutline = Color(0xFF353B57)

/** Colours with a fixed meaning in a CGM app (glucose status, chart marks). */
@Immutable
data class BioColors(
    val inRange: Color,
    val low: Color,
    val urgentLow: Color,
    val high: Color,
    val forecast: Color,
    val targetBand: Color,
    val futureShade: Color,
    val meal: Color,
    val insulin: Color,
    val exercise: Color,
)

val LightBioColors = BioColors(
    inRange = Indigo,
    low = Color(0xFFD63B3B),
    urgentLow = Color(0xFFA31A1A),
    high = Color(0xFFD98A00),
    forecast = Cyan,
    targetBand = Color(0x1F3D4FD6),
    futureShade = Color(0x0D12152B),
    meal = Color(0xFFD98A00),
    insulin = Indigo,
    exercise = Color(0xFF168A5C),
)

val DarkBioColors = BioColors(
    inRange = IndigoSoft,
    low = Color(0xFFFF6B6B),
    urgentLow = Color(0xFFFF4D4D),
    high = Color(0xFFFFB938),
    forecast = CyanSoft,
    targetBand = Color(0x298E9BFF),
    futureShade = Color(0x0FFFFFFF),
    meal = Color(0xFFFFB938),
    insulin = IndigoSoft,
    exercise = Color(0xFF4CD39A),
)

val LocalBioColors = staticCompositionLocalOf { LightBioColors }
