package com.aura.aura_ui.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MONO — "Monochrome plus a drop of blood."
 *
 * Every colour in the product comes from here. No raw hex anywhere else.
 */
object Mono {
    // Canvas / ink
    val Canvas = Color(0xFFFAFAF8)
    val CanvasHigh = Color(0xFFFFFFFF)
    val Outline = Color(0xFFE8E8E4)
    val OutlineStrong = Color(0xFFD6D6D1)
    val TextPrimary = Color(0xFF111111)
    val TextSecondary = Color(0xFF6B6B68)
    val Ink = Color(0xFF0A0A0A)
    val InkSoft = Color(0xFF171717)
    val InkPressed = Color(0xFF262626)
    val TextOnInk = Color(0xFFF5F5F2)
    val TextOnInkDim = Color(0xFF9C9C98)
    val OutlineOnInk = Color(0xFF2E2E2B)
    val HairlineOnInk = Color(0x33FFFFFF)

    // Semantics — red is an alarm, never an accent.
    val Blood = Color(0xFFA31621)
    val BloodPressed = Color(0xFF7A1019)
    val BloodContainer = Color(0xFFFBEBEC)

    // The only green in the product.
    val ToggleGreen = Color(0xFF34C759)
    val ToggleTrackOff = Color(0xFFE4E4E1)
    val ToggleTrackOffDark = Color(0xFF3A3A3A)

    // Dark twin
    val CanvasDark = Color(0xFF0C0C0B)
    val CardDark = Color(0xFF171716)
    val ChipDark = Color(0xFF222220)
    val OutlineDark = Color(0xFF2C2C2A)
    val TextPrimaryDark = Color(0xFFF2F2EF)
    val TextSecondaryDark = Color(0xFFA0A09A)
    val BloodContainerDark = Color(0xFF2A0E11)

    // Spacing
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val ScreenH: Dp = 24.dp

    // Motion (milliseconds)
    const val MotionFast = 150
    const val MotionNormal = 220
    const val MotionSlow = 400
}

/** Semantic palette handed to every screen. */
@Immutable
data class MonoScheme(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkPressed: Color,
    val textOnInk: Color,
    val textOnInkDim: Color,
    val outlineOnInk: Color,
    val blood: Color,
    val bloodPressed: Color,
    val bloodContainer: Color,
    val switchOn: Color,
    val switchOff: Color,
    val successDot: Color,
) {
    companion object {
        val Light = MonoScheme(
            isDark = false,
            canvas = Mono.Canvas,
            surface = Mono.CanvasHigh,
            surfaceElevated = Mono.CanvasHigh,
            outline = Mono.Outline,
            outlineStrong = Mono.OutlineStrong,
            textPrimary = Mono.TextPrimary,
            textSecondary = Mono.TextSecondary,
            ink = Mono.Ink,
            inkSoft = Mono.InkSoft,
            inkPressed = Mono.InkPressed,
            textOnInk = Mono.TextOnInk,
            textOnInkDim = Mono.TextOnInkDim,
            outlineOnInk = Mono.OutlineOnInk,
            blood = Mono.Blood,
            bloodPressed = Mono.BloodPressed,
            bloodContainer = Mono.BloodContainer,
            switchOn = Mono.ToggleGreen,
            switchOff = Mono.ToggleTrackOff,
            successDot = Mono.ToggleGreen,
        )

        val Dark = MonoScheme(
            isDark = true,
            canvas = Mono.CanvasDark,
            surface = Mono.CardDark,
            surfaceElevated = Mono.ChipDark,
            outline = Mono.OutlineDark,
            outlineStrong = Mono.OutlineOnInk,
            textPrimary = Mono.TextPrimaryDark,
            textSecondary = Mono.TextSecondaryDark,
            ink = Mono.CanvasHigh,
            inkSoft = Mono.ChipDark,
            inkPressed = Mono.OutlineStrong,
            textOnInk = Mono.Ink,
            textOnInkDim = Mono.TextSecondary,
            outlineOnInk = Mono.Outline,
            blood = Mono.Blood,
            bloodPressed = Mono.BloodPressed,
            bloodContainer = Mono.BloodContainerDark,
            switchOn = Mono.ToggleGreen,
            switchOff = Mono.ToggleTrackOffDark,
            successDot = Mono.ToggleGreen,
        )
    }
}

val LocalMonoScheme = staticCompositionLocalOf { MonoScheme.Light }

/**
 * ARGB ints for the handful of platform surfaces (WebView, Canvas, InputMethod views) that
 * cannot take a Compose [Color]. Keeping the conversion here preserves the rule that no colour
 * literal lives outside this file.
 */
object MonoArgb {
    val canvas: Int = Mono.Canvas.toArgb()
    val ink: Int = Mono.Ink.toArgb()
    val textPrimary: Int = Mono.TextPrimary.toArgb()
    val textSecondary: Int = Mono.TextSecondary.toArgb()
    val textOnInk: Int = Mono.TextOnInk.toArgb()
    val textOnInkDim: Int = Mono.TextOnInkDim.toArgb()
    val blood: Int = Mono.Blood.toArgb()
    val hairlineOnInk: Int = Mono.HairlineOnInk.toArgb()
}

/** Radius tokens. */
object ShapeTokens {
    val Card: Dp = 24.dp
    val CardSmall: Dp = 16.dp
    val Chip: Dp = 12.dp
    val Pill: Dp = 100.dp
}
