package com.projectxo.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Шрифт. По DESIGN.md это Inter, вшитый в приложение. Пока файлов шрифта нет, используется
 * системный шрифт. Замена в одной строке: FontFamily(Font(R.font.inter_regular), ...).
 */
val XoFontFamily: FontFamily = FontFamily.Default

private const val TABULAR = "tnum"

/** Типографика (docs/DESIGN.md, раздел 2). Межстрочные интервалы увеличены 2026-11-10: строки и подписи не впритык. */
@Immutable
data class XoType(
    val display: TextStyle,
    val displayUnit: TextStyle,
    val displayM: TextStyle,
    val displayUnitM: TextStyle,
    val scale: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    val tab: TextStyle,
    val tabActive: TextStyle,
    val label: TextStyle,
    val tagline: TextStyle,
)

val DefaultXoType = XoType(
    display = TextStyle(
        fontFamily = XoFontFamily, fontSize = 56.sp, lineHeight = 56.sp,
        fontWeight = FontWeight.Medium, letterSpacing = (-0.035).em, fontFeatureSettings = TABULAR,
    ),
    displayUnit = TextStyle(
        fontFamily = XoFontFamily, fontSize = 26.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.Normal, fontFeatureSettings = TABULAR,
    ),
    displayM = TextStyle(
        fontFamily = XoFontFamily, fontSize = 48.sp, lineHeight = 48.sp,
        fontWeight = FontWeight.Medium, letterSpacing = (-0.035).em, fontFeatureSettings = TABULAR,
    ),
    displayUnitM = TextStyle(
        fontFamily = XoFontFamily, fontSize = 22.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Normal, fontFeatureSettings = TABULAR,
    ),
    scale = TextStyle(
        fontFamily = XoFontFamily, fontSize = 11.sp, lineHeight = 13.sp,
        fontWeight = FontWeight.Normal, fontFeatureSettings = TABULAR,
    ),
    title = TextStyle(
        fontFamily = XoFontFamily, fontSize = 26.sp, lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.015).em,
    ),
    subtitle = TextStyle(
        fontFamily = XoFontFamily, fontSize = 20.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em,
    ),
    body = TextStyle(
        fontFamily = XoFontFamily, fontSize = 15.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Normal, fontFeatureSettings = TABULAR,
    ),
    bodyStrong = TextStyle(
        fontFamily = XoFontFamily, fontSize = 15.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR,
    ),
    caption = TextStyle(
        fontFamily = XoFontFamily, fontSize = 13.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Normal, fontFeatureSettings = TABULAR,
    ),
    tab = TextStyle(
        fontFamily = XoFontFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal,
    ),
    tabActive = TextStyle(
        fontFamily = XoFontFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
    ),
    label = TextStyle(
        fontFamily = XoFontFamily, fontSize = 11.sp, lineHeight = 14.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.12.em,
    ),
    tagline = TextStyle(
        fontFamily = XoFontFamily, fontSize = 13.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Normal, letterSpacing = 0.04.em,
    ),
)

val LocalXoType = staticCompositionLocalOf { DefaultXoType }
