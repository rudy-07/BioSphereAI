package com.madhusudhan.biosphereai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.madhusudhan.biosphereai.R

/** Manrope (SIL Open Font License, see assets/licenses/manrope_OFL.txt), bundled as one variable font. */
@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) = Font(
    R.font.manrope,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Manrope = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

private val Base = Typography()

private fun TextStyle.m(weight: FontWeight? = null, tracking: Double? = null) = copy(
    fontFamily = Manrope,
    fontWeight = weight ?: fontWeight,
    letterSpacing = tracking?.sp ?: letterSpacing,
)

val Typography = Typography(
    displayLarge = Base.displayLarge.m(FontWeight.Bold),
    displayMedium = Base.displayMedium.m(FontWeight.Bold),
    displaySmall = Base.displaySmall.m(FontWeight.Bold),
    headlineLarge = Base.headlineLarge.m(FontWeight.Bold),
    headlineMedium = Base.headlineMedium.m(FontWeight.Bold),
    headlineSmall = Base.headlineSmall.m(FontWeight.SemiBold),
    titleLarge = Base.titleLarge.m(FontWeight.Bold, -0.2),
    titleMedium = Base.titleMedium.m(FontWeight.SemiBold),
    titleSmall = Base.titleSmall.m(FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.m(FontWeight.Normal),
    bodyMedium = Base.bodyMedium.m(FontWeight.Normal),
    bodySmall = Base.bodySmall.m(FontWeight.Normal),
    labelLarge = Base.labelLarge.m(FontWeight.SemiBold),
    labelMedium = Base.labelMedium.m(FontWeight.SemiBold),
    labelSmall = Base.labelSmall.m(FontWeight.SemiBold),
)

/** The large current-glucose number. Tabular figures so it doesn't jump as digits change. */
val HeroNumber = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 84.sp,
    lineHeight = 88.sp,
    letterSpacing = (-3).sp,
    fontFeatureSettings = "tnum",
)

/** Medium numbers in cards (forecasts, blood pressure). */
val CardNumber = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum",
)
