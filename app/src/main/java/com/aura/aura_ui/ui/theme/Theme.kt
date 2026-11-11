package com.aura.aura_ui.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun schemeFor(mono: MonoScheme) = if (mono.isDark) {
    darkColorScheme(
        primary = mono.ink,
        onPrimary = mono.textOnInk,
        secondary = mono.inkSoft,
        onSecondary = mono.textOnInk,
        background = mono.canvas,
        onBackground = mono.textPrimary,
        surface = mono.surface,
        onSurface = mono.textPrimary,
        surfaceVariant = mono.surfaceElevated,
        onSurfaceVariant = mono.textSecondary,
        outline = mono.outline,
        error = mono.blood,
        onError = MonoScheme.Light.textOnInk,
        errorContainer = mono.bloodContainer,
    )
} else {
    lightColorScheme(
        primary = mono.ink,
        onPrimary = mono.textOnInk,
        secondary = mono.inkSoft,
        onSecondary = mono.textOnInk,
        background = mono.canvas,
        onBackground = mono.textPrimary,
        surface = mono.surface,
        onSurface = mono.textPrimary,
        surfaceVariant = mono.surfaceElevated,
        onSurfaceVariant = mono.textSecondary,
        outline = mono.outline,
        error = mono.blood,
        onError = MonoScheme.Light.textOnInk,
        errorContainer = mono.bloodContainer,
    )
}

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val mono = if (darkTheme) MonoScheme.Dark else MonoScheme.Light
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalMonoScheme provides mono) {
        MaterialTheme(
            colorScheme = schemeFor(mono),
            typography = AuraTypography,
            content = content,
        )
    }
}
