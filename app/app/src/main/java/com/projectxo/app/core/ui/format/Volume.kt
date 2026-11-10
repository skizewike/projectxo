package com.projectxo.app.core.ui.format

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.projectxo.app.R
import com.projectxo.app.core.model.VolumeMath
import com.projectxo.app.core.model.VolumeUnit
import java.text.NumberFormat
import java.util.Locale

/** Единицы объёма воды, выбранные в настройках. Задаётся один раз у корня приложения. */
val LocalVolumeUnit = compositionLocalOf { VolumeUnit.Ml }

/** Число без единицы: мл целым, унции с десятой («8,5») или целым, если десятая нулевая. */
fun volumeNumber(locale: Locale, ml: Long, unit: VolumeUnit, wholeOunces: Boolean = false): String {
    val nf = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = if (unit == VolumeUnit.Ml || wholeOunces) 0 else 1
    }
    return nf.format(VolumeMath.display(ml, unit))
}

/** Число без единицы в единицах из настроек. */
@Composable
fun formatVolumeNumber(ml: Long, wholeOunces: Boolean = false): String {
    val locale = rememberFormatLocale()
    val unit = LocalVolumeUnit.current
    return remember(locale, ml, unit, wholeOunces) { volumeNumber(locale, ml, unit, wholeOunces) }
}

/** «мл» или «унц.». */
@Composable
fun volumeUnitLabel(unit: VolumeUnit = LocalVolumeUnit.current): String =
    stringResource(if (unit == VolumeUnit.Ml) R.string.unit_ml else R.string.unit_oz)

/** «250 мл» или «8,5 унц.». */
@Composable
fun formatVolume(ml: Long): String = "${formatVolumeNumber(ml)} ${volumeUnitLabel()}"

/** То же вне Compose: для уведомлений и виджета. */
fun formatVolume(context: Context, ml: Long, unit: VolumeUnit): String {
    val locale = Locale.forLanguageTag(context.getString(R.string.format_locale))
    val label = context.getString(if (unit == VolumeUnit.Ml) R.string.unit_ml else R.string.unit_oz)
    return "${volumeNumber(locale, ml, unit)} $label"
}
