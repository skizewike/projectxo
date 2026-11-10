package com.projectxo.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.R
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.model.BalanceHistory
import com.projectxo.app.core.model.BalanceMath
import com.projectxo.app.core.ui.components.MiniRing
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** День полосы недели: «Баланс дня» и доступность (Р31, Р41: до первого запуска и будущее недоступны). */
data class WeekDay(val date: LocalDate, val balance: Float, val selectable: Boolean, val isToday: Boolean)

/** Полоса недели под заголовком «Сегодня»: неделя с понедельника по воскресенье. */
class WeekViewModel(
    history: BalanceHistory,
    settings: SettingsRepository,
    clock: AppClock,
) : ViewModel() {
    private val today = clock.today()
    private val monday = today.with(DayOfWeek.MONDAY)
    private val start = settings.settings.value.startEpochDay?.let { LocalDate.ofEpochDay(it) }?.takeIf { !it.isAfter(today) } ?: today

    val week: StateFlow<List<WeekDay>> = combine(
        history.totalsBetween(monday, monday.plusDays(6)), history.goals(), settings.settings,
    ) { totals, goals, s ->
        (0L..6L).map { i ->
            val d = monday.plusDays(i)
            val ok = !d.isBefore(start) && !d.isAfter(today)
            WeekDay(d, if (ok) BalanceMath.balance(totals[d], goals, s.modules) else 0f, ok, d == today)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), placeholder())

    /**
     * Неделя без значений до прихода данных. Полоса есть с первого кадра, поэтому кольцо «Баланс дня»
     * не съезжает вниз, когда данные загрузились (иначе O из заставки садилось мимо кольца).
     */
    private fun placeholder(): List<WeekDay> = (0L..6L).map { i ->
        val d = monday.plusDays(i)
        WeekDay(d, 0f, !d.isBefore(start) && !d.isAfter(today), d == today)
    }
}

@Composable
fun WeekStrip(days: List<WeekDay>, onOpen: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    val pattern = stringResource(R.string.calendar_day_pattern)
    val formatter = java.time.format.DateTimeFormatter.ofPattern(pattern, locale)
    Row(modifier.fillMaxWidth()) {
        days.forEach { d ->
            val percent = Math.round(d.balance * 100)
            val label = formatter.format(d.date)
            val a11y = if (d.selectable) stringResource(R.string.calendar_day_a11y, label, percent.toString())
            else stringResource(R.string.calendar_day_off_a11y, label)
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = XoSize.touchMin + 14.dp)
                    .then(if (d.selectable) Modifier.clickable(role = Role.Button) { onOpen(d.date) } else Modifier)
                    .clearAndSetSemantics { contentDescription = a11y; role = Role.Button },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                    style = type.scale, color = if (d.isToday) colors.ink else colors.muted, maxLines = 1,
                )
                Spacer(Modifier.height(XoSpacing.s1))
                Box(contentAlignment = Alignment.Center) {
                    if (d.selectable) MiniRing(d.balance, 34.dp, 2.dp)
                    else MiniRing(0f, 34.dp, 2.dp, modifier = Modifier.alpha(0.5f))
                    Text(
                        d.date.dayOfMonth.toString(),
                        style = if (d.isToday) type.bodyStrong.copy(fontSize = type.caption.fontSize) else type.caption,
                        color = if (d.selectable) colors.ink else colors.muted,
                    )
                }
            }
        }
    }
}
