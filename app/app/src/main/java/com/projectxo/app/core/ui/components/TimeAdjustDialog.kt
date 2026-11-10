package com.projectxo.app.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.projectxo.app.R
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Подпись дня для момента: «сегодня», «вчера» или короткая дата. */
@Composable
fun dayWord(ms: Long, todayMs: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val today: LocalDate = Instant.ofEpochMilli(todayMs).atZone(zone).toLocalDate()
    val locale = rememberFormatLocale()
    val pattern = stringResource(R.string.calendar_day_short_pattern)
    return when (d) {
        today -> stringResource(R.string.time_today)
        today.minusDays(1) -> stringResource(R.string.time_yesterday)
        else -> remember(d, pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale).format(d) }
    }
}

/** «23:40» по часам телефона. */
@Composable
fun clockText(ms: Long): String {
    val locale = rememberFormatLocale()
    val pattern = stringResource(R.string.time_pattern)
    return remember(ms, pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())) }
}

/**
 * Выбор момента кнопками «−15 −5 +5 +15» и быстрыми вариантами (design/fasting-timer.html, design/sleep.html).
 * Значение всегда держится в [range]. Время идёт по часам телефона, поэтому проверка границ — здесь.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeAdjustDialog(
    title: String,
    hint: String,
    initialMs: Long,
    range: LongRange,
    quick: List<Pair<String, Long>>,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    var pick by rememberSaveable { mutableLongStateOf(initialMs.coerceIn(range.first, range.last)) }
    fun move(deltaMin: Int) { pick = (pick + deltaMin * 60_000L).coerceIn(range.first, range.last) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(title, style = type.title, color = colors.ink) },
        text = {
            Column {
                Text(hint, style = type.caption, color = colors.muted)
                Spacer(Modifier.height(XoSpacing.s4))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(clockText(pick), style = type.display, color = colors.ink)
                    Spacer(Modifier.height(XoSpacing.textGap))
                    Text(dayWord(pick), style = type.caption, color = colors.muted, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(XoSpacing.s3))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XoSpacing.s2)) {
                    listOf(-15, -5, 5, 15).forEach { d ->
                        Pill(if (d > 0) "+$d" else "−${-d}", Modifier.weight(1f)) { move(d) }
                    }
                }
                if (quick.isNotEmpty()) {
                    Spacer(Modifier.height(XoSpacing.s3))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(XoSpacing.s2), verticalArrangement = Arrangement.spacedBy(XoSpacing.s2)) {
                        quick.forEach { (label, ms) -> Pill(label) { pick = ms.coerceIn(range.first, range.last) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pick) }) { Text(stringResource(R.string.pick_save), style = type.bodyStrong) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), style = type.body) } },
    )
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = XoTheme.colors
    Row(
        modifier
            .heightIn(min = XoSize.touchMin)
            .border(XoSize.hairline, colors.line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = XoSpacing.s3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) { Text(text, style = XoTheme.type.caption, color = colors.ink) }
}
