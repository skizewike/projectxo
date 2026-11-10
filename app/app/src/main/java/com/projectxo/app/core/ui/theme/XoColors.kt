package com.projectxo.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Цветовые токены (docs/DESIGN.md, раздел 1). Единственное место с hex-значениями цветов интерфейса. */
@Immutable
data class XoColors(
    val background: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val track: Color,
    val accent: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

val LightXoColors = XoColors(
    background = Color(0xFFF6F3EC),
    ink = Color(0xFF171614),
    muted = Color(0xFF6E6A62),
    line = Color(0xFFDCD7CC),
    track = Color(0xFFE3DED3),
    accent = Color(0xFF2D64D2),
    onAccent = Color(0xFFFFFFFF),
    isDark = false,
)

val DarkXoColors = XoColors(
    background = Color(0xFF141413),
    ink = Color(0xFFECE9E2),
    muted = Color(0xFF8C887F),
    line = Color(0xFF2B2A27),
    track = Color(0xFF252422),
    accent = Color(0xFF6E9BEB),
    onAccent = Color(0xFF141413),
    isDark = true,
)

// Темы 2–4 (Р49): всегда тёмные. Акцент только для колец, полос и кнопок, текст — светлый.
val AcidXoColors = XoColors(
    background = Color(0xFF0A0A0A),
    ink = Color(0xFFF3F3EE),
    muted = Color(0xFF8E8E86),
    line = Color(0xFF262624),
    track = Color(0xFF1C1C1A),
    accent = Color(0xFFB6FF00),
    onAccent = Color(0xFF0A0A0A),
    isDark = true,
)

val VoltXoColors = XoColors(
    background = Color(0xFF101A46),
    ink = Color(0xFFEEF1FF),
    muted = Color(0xFF98A3D3),
    line = Color(0xFF26326E),
    track = Color(0xFF1B265C),
    accent = Color(0xFF245BFF),
    onAccent = Color(0xFFFFFFFF),
    isDark = true,
)

val BloodXoColors = XoColors(
    background = Color(0xFF080808),
    ink = Color(0xFFF2EDED),
    muted = Color(0xFF958B8B),
    line = Color(0xFF2A1E1E),
    track = Color(0xFF1C1414),
    accent = Color(0xFFC1121F),
    onAccent = Color(0xFFFFFFFF),
    isDark = true,
)

/** Высокий контраст: усилены только приглушённые цвета, линии и подложки. */
fun XoColors.highContrast(): XoColors =
    if (isDark) copy(muted = Color(0xFFC2BEB5), line = Color(0xFF55524C), track = Color(0xFF3A3935))
    else copy(muted = Color(0xFF3F3C37), line = Color(0xFFA8A296), track = Color(0xFFCFC9BC))

val LocalXoColors = staticCompositionLocalOf { LightXoColors }
