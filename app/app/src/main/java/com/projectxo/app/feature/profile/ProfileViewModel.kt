package com.projectxo.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.DayLogRepository
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.BalanceHistory
import com.projectxo.app.core.model.HistoryGoals
import com.projectxo.app.core.model.ProfileData
import com.projectxo.app.core.model.ProfilePeriod
import com.projectxo.app.core.model.ProfileStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ProfileGraphsState(
    val data: ProfileData,
    val goals: HistoryGoals,
    val settings: AppSettings,
    val today: LocalDate,
    /** Дней с первого запуска, включая сегодняшний. */
    val daysWithXo: Int,
    /** Итог последних 7 дней; null, пока нечего показывать. */
    val week: com.projectxo.app.core.model.WeekSummary? = null,
    val loaded: Boolean = false,
)

/** Графики профиля (Р50): вес и тренд, шаги, вода, калории, самочувствие за неделю, месяц или всё время. */
class ProfileViewModel(
    history: BalanceHistory,
    dayLog: DayLogRepository,
    private val settings: SettingsRepository,
    clock: AppClock,
) : ViewModel() {
    private val today = clock.today()
    private val start: LocalDate = settings.settings.value.startEpochDay
        ?.let { LocalDate.ofEpochDay(it) }?.takeIf { !it.isAfter(today) } ?: today

    private val period = MutableStateFlow(ProfilePeriod.Week)
    val selectedPeriod: StateFlow<ProfilePeriod> = period

    val state: StateFlow<ProfileGraphsState> = combine(
        period, history.totalsBetween(start, today), history.goals(), dayLog.observeBetween(start, today), settings.settings,
    ) { p, totals, goals, logs, s ->
        ProfileGraphsState(
            data = ProfileStats.build(p, start, today, totals, logs, goals),
            goals = goals, settings = s, today = today,
            daysWithXo = (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceAtLeast(1),
            week = com.projectxo.app.core.model.WeekSummaryBuilder.build(today, start, totals, goals, s.modules, s.moduleOrder),
            loaded = true,
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        ProfileGraphsState(
            ProfileStats.build(ProfilePeriod.Week, start, today, emptyMap(), emptyMap(), HistoryGoals(1, 1, 1)),
            HistoryGoals(1, 1, 1), settings.settings.value, today, 1,
        ),
    )

    fun setPeriod(p: ProfilePeriod) {
        period.value = p
    }
}
