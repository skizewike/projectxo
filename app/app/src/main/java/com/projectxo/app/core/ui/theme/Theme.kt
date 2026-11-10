package com.projectxo.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.projectxo.app.core.model.ThemeChoice

/**
 * Тема XO. Material 3 только как основа: его схема заполняется нашими токенами,
 * динамический цвет выключен. В коде фич используйте XoTheme.colors и XoTheme.type.
 */
@Composable
fun XoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    theme: ThemeChoice = ThemeChoice.Warm,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val base = when (theme) {
        ThemeChoice.Warm -> if (darkTheme) DarkXoColors else LightXoColors
        ThemeChoice.Acid -> AcidXoColors
        ThemeChoice.Volt -> VoltXoColors
        ThemeChoice.Blood -> BloodXoColors
    }
    val colors = if (highContrast) base.highContrast() else base
    val type = DefaultXoType
    // Размер текста приложения поверх системного: множим fontScale, размеры в dp не трогаем.
    val density = LocalDensity.current
    val scaled = remember(density, textScale) { Density(density.density, density.fontScale * textScale) }

    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.ink,
            surface = colors.background, onSurface = colors.ink,
            surfaceVariant = colors.track, onSurfaceVariant = colors.muted,
            outline = colors.line, outlineVariant = colors.line,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.ink,
            surface = colors.background, onSurface = colors.ink,
            surfaceVariant = colors.track, onSurfaceVariant = colors.muted,
            outline = colors.line, outlineVariant = colors.line,
        )
    }
    val typography = Typography(
        displayLarge = type.display,
        titleLarge = type.title,
        bodyLarge = type.body,
        bodyMedium = type.body,
        bodySmall = type.caption,
        labelMedium = type.tab,
        labelSmall = type.label,
    )

    CompositionLocalProvider(LocalXoColors provides colors, LocalXoType provides type, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

object XoTheme {
    val colors: XoColors
        @Composable @ReadOnlyComposable get() = LocalXoColors.current
    val type: XoType
        @Composable @ReadOnlyComposable get() = LocalXoType.current
}
