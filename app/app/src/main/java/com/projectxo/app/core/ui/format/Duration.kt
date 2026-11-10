package com.projectxo.app.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.projectxo.app.R

/** «7 ч 15 мин», «8 ч», «45 мин». */
@Composable
fun formatMinutes(totalMin: Long): String {
    val m = totalMin.coerceAtLeast(0)
    val h = m / 60
    val rest = m % 60
    return when {
        h == 0L -> stringResource(R.string.dur_min, formatCount(rest))
        rest == 0L -> stringResource(R.string.dur_h, formatCount(h))
        else -> stringResource(R.string.dur_h_min, formatCount(h), formatCount(rest))
    }
}

/** Часы:минуты, «09:40». Для таймеров. */
fun clockHm(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0)
    return "%02d:%02d".format(s / 3600, s % 3600 / 60)
}

/** Часы:минуты:секунды. */
fun clockHms(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
}

/** Время суток из минут от полуночи: «23:30». */
fun timeOfDay(minOfDay: Int): String {
    val m = ((minOfDay % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}
