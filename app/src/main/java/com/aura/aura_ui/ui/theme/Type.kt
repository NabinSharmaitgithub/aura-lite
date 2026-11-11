package com.aura.aura_ui.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography.
 *
 * Product families are Plus Jakarta Sans (display/body) and JetBrains Mono (technical).
 * They are declared as the app's default families here. To ship the exact typefaces, drop
 * the declared TTFs into `res/font/` and swap the two families below for:
 *
 *   FontFamily(Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal), ...)
 *   FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal), ...)
 *
 * Until then the platform's closest families are used so the build stays asset-free.
 */
object AuraFonts {
    val Display: FontFamily = FontFamily.SansSerif
    val Technical: FontFamily = FontFamily.Monospace
}

private fun style(
    size: Int,
    weight: FontWeight,
    tracking: Double,
    family: FontFamily = AuraFonts.Display,
    lineHeight: Int = (size * 1.3).toInt(),
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

val AuraTypography = Typography(
    displayLarge = style(54, FontWeight.ExtraBold, -1.4, lineHeight = 60),
    displayMedium = style(42, FontWeight.Bold, -1.0, lineHeight = 48),
    displaySmall = style(34, FontWeight.Bold, -0.8, lineHeight = 40),
    headlineLarge = style(30, FontWeight.Bold, -0.6, lineHeight = 36),
    headlineMedium = style(26, FontWeight.SemiBold, -0.4, lineHeight = 32),
    headlineSmall = style(22, FontWeight.SemiBold, -0.2, lineHeight = 28),
    titleLarge = style(20, FontWeight.SemiBold, -0.2, lineHeight = 26),
    titleMedium = style(16, FontWeight.SemiBold, -0.1, lineHeight = 22),
    titleSmall = style(14, FontWeight.SemiBold, 0.0, lineHeight = 20),
    bodyLarge = style(16, FontWeight.Normal, 0.0, lineHeight = 24),
    bodyMedium = style(14, FontWeight.Normal, 0.0, lineHeight = 21),
    bodySmall = style(12, FontWeight.Normal, 0.1, lineHeight = 17),
    labelLarge = style(14, FontWeight.SemiBold, -0.1, lineHeight = 18),
    labelMedium = style(12, FontWeight.SemiBold, 0.1, lineHeight = 16),
    labelSmall = style(11, FontWeight.SemiBold, 0.2, lineHeight = 14),
)

/** Technical style for ids, counts, durations and tool names. */
val MonoStyle = TextStyle(
    fontFamily = AuraFonts.Technical,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

val MonoStyleSmall = TextStyle(
    fontFamily = AuraFonts.Technical,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    lineHeight = 14.sp,
)
