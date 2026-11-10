package com.projectxo.app.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.projectxo.app.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Язык чисел и дат берётся из ресурсов, чтобы совпадать с языком строк. */
@Composable
fun rememberFormatLocale(): Locale {
    val tag = stringResource(R.string.format_locale)
    return remember(tag) { Locale.forLanguageTag(tag) }
}

/** «2 000», «8 000» с неразрывным пробелом по правилам языка. */
@Composable
fun formatCount(value: Long): String {
    val locale = rememberFormatLocale()
    val nf = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    return nf.format(value)
}

/** «Четверг, 5 ноября». */
@Composable
fun formatToday(date: LocalDate): String {
    val locale = rememberFormatLocale()
    val pattern = stringResource(R.string.today_date_pattern)
    val formatter = remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
    return formatter.format(date).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

/**
 * Склонение по языку интерфейса (format_locale), а не по языку телефона:
 * иначе на телефоне с английским языком русские строки склонялись бы по английским правилам («6 480 шага»).
 */
@Composable
fun pluralText(@androidx.annotation.PluralsRes id: Int, count: Int, vararg args: Any): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val locale = rememberFormatLocale()
    val res = remember(context, configuration, locale) {
        val cfg = android.content.res.Configuration(configuration).apply { setLocale(locale) }
        context.createConfigurationContext(cfg).resources
    }
    return res.getQuantityString(id, count, *args)
}

/** Число с фиксированным числом знаков после запятой по правилам языка: «71,8». */
@Composable
fun formatDecimal(value: Double, digits: Int = 1): String {
    val locale = rememberFormatLocale()
    val nf = remember(locale, digits) {
        NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = digits; maximumFractionDigits = digits }
    }
    return nf.format(value)
}
