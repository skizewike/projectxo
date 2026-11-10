package com.projectxo.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.DayLog
import com.projectxo.app.core.data.DayLogRepository
import com.projectxo.app.core.data.FoodEntry
import com.projectxo.app.core.data.FoodRepository
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.data.WaterEntry
import com.projectxo.app.core.data.WaterRepository
import com.projectxo.app.core.model.BalanceHistory
import com.projectxo.app.core.model.BalanceMath
import com.projectxo.app.core.model.DayNote
import com.projectxo.app.core.model.DayTotals
import com.projectxo.app.core.model.DayWeight
import com.projectxo.app.core.model.HistoryGoals
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.Units
import com.projectxo.app.core.model.WellbeingTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class CalendarMode { Month, Year }

/** Подробности выбранного дня. */
data class DayDetail(
    val date: LocalDate,
    val totals: DayTotals,
    val goals: HistoryGoals,
    val balance: Float,
    val log: DayLog?,
    val waterEntries: List<WaterEntry>,
    val foodEntries: List<FoodEntry>,
)

/** То, что человек правит в окне самочувствия, пока не нажал «Сохранить». */
data class DayLogDraft(
    val feel: Int? = null,
    val tags: Set<String> = emptySet(),
    val weightKg: Double? = null,
    val note: String = "",
)

data class CalendarUiState(
    val today: LocalDate,
    /** Первый доступный день: день первого запуска. Раньше выбрать нельзя (Р41). */
    val start: LocalDate,
    val selected: LocalDate,
    val mode: CalendarMode = CalendarMode.Month,
    val balances: Map<LocalDate, Float> = emptyMap(),
    /** Дни, в которые есть заметка (точка под числом). */
    val logged: Set<LocalDate> = emptySet(),
    val modules: Set<Module> = Module.entries.toSet(),
    val order: List<Module> = Module.entries,
    val units: Units = Units(),
    val detail: DayDetail? = null,
    val draft: DayLogDraft? = null,
    /** Частые отметки пользователя за последние дни; пока истории нет — набор по умолчанию. */
    val frequent: List<WellbeingTag> = WellbeingTag.DEFAULT_FREQUENT,
    val loaded: Boolean = false,
) {
    fun isSelectable(d: LocalDate) = !d.isBefore(start) && !d.isAfter(today)
}

/**
 * Календарь (Р31, Р48): месяц и год с кольцами «Баланса дня», итог выбранного дня,
 * самочувствие, вес и заметка. Будущие дни и дни до первого запуска недоступны.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    history: BalanceHistory,
    water: WaterRepository,
    food: FoodRepository,
    private val dayLog: DayLogRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
    initial: LocalDate? = null,
) : ViewModel() {

    private val today = clock.today()
    private val start: LocalDate = settings.settings.value.startEpochDay
        ?.let { LocalDate.ofEpochDay(it) }?.takeIf { !it.isAfter(today) } ?: today

    private val selected = MutableStateFlow(initial?.coerceIn(start, today) ?: today)
    private val mode = MutableStateFlow(CalendarMode.Month)
    private val draft = MutableStateFlow<DayLogDraft?>(null)

    private val balances = combine(
        history.totalsBetween(start, today), history.goals(), settings.settings,
    ) { totals, goals, s -> totals.mapValues { (_, t) -> BalanceMath.balance(t, goals, s.modules) } }

    private val logs = dayLog.observeBetween(start, today)

    private val detail = selected.flatMapLatest { d ->
        combine(
            history.totalsBetween(d, d), history.goals(), settings.settings,
            water.entriesFor(d), food.entriesFor(d), dayLog.observe(d),
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val totals = (values[0] as Map<LocalDate, DayTotals>)[d] ?: DayTotals()
            val goals = values[1] as HistoryGoals
            val s = values[2] as com.projectxo.app.core.model.AppSettings
            @Suppress("UNCHECKED_CAST")
            DayDetail(
                date = d, totals = totals, goals = goals,
                balance = BalanceMath.balance(totals, goals, s.modules),
                log = values[5] as DayLog?,
                waterEntries = values[3] as List<WaterEntry>,
                foodEntries = values[4] as List<FoodEntry>,
            )
        }
    }

    val state: StateFlow<CalendarUiState> = combine(
        combine(selected, mode, draft) { s, m, d -> Triple(s, m, d) },
        balances, logs, detail, settings.settings,
    ) { (sel, m, d), bal, lg, det, s ->
        CalendarUiState(
            today = today, start = start, selected = sel, mode = m,
            balances = bal, logged = lg.keys, modules = s.modules, order = s.moduleOrder, units = s.units,
            detail = det, draft = d, frequent = frequent(lg), loaded = true,
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        CalendarUiState(today = today, start = start, selected = selected.value),
    )

    fun select(date: LocalDate) {
        if (!date.isBefore(start) && !date.isAfter(today)) selected.value = date
    }

    fun setMode(m: CalendarMode) {
        mode.value = m
    }

    fun openPicker() {
        val log = state.value.detail?.log
        draft.value = DayLogDraft(log?.feel, log?.tags ?: emptySet(), log?.weightKg, log?.note ?: "")
    }

    fun closePicker() {
        draft.value = null
    }

    fun setFeel(feel: Int?) = draft.update { it.copy(feel = if (it.feel == feel) null else feel) }

    fun toggleTag(tag: WellbeingTag) = draft.update {
        it.copy(tags = if (tag.key in it.tags) it.tags - tag.key else it.tags + tag.key)
    }

    /** Шаг веса вверх или вниз; первый шаг начинается с веса из профиля или [DayWeight.DEFAULT_KG]. */
    fun stepWeight(delta: Double) = draft.update {
        val base = it.weightKg ?: settings.settings.value.profile?.weightKg ?: DayWeight.DEFAULT_KG
        it.copy(weightKg = DayWeight.clamp(base + delta))
    }

    fun clearWeight() = draft.update { it.copy(weightKg = null) }

    fun setNote(text: String) = draft.update { it.copy(note = text.take(DayNote.MAX_LENGTH)) }

    fun save() {
        val d = draft.value ?: return
        val date = selected.value
        draft.value = null
        appScope.launch {
            dayLog.save(DayLog(date, d.feel, d.tags.filter { WellbeingTag.fromKey(it) != null }.toSet(), d.weightKg, d.note))
        }
    }

    private fun MutableStateFlow<DayLogDraft?>.update(f: (DayLogDraft) -> DayLogDraft) {
        value = value?.let(f)
    }

    /** Три самые частые отметки за все дни; добираем набором по умолчанию до четырёх. */
    private fun frequent(logs: Map<LocalDate, DayLog>): List<WellbeingTag> {
        val counts = logs.values.flatMap { it.tags }.groupingBy { it }.eachCount()
        val own = counts.entries.sortedByDescending { it.value }.mapNotNull { WellbeingTag.fromKey(it.key) }.take(FREQUENT_COUNT)
        return (own + WellbeingTag.DEFAULT_FREQUENT).distinct().take(FREQUENT_COUNT)
    }

    private fun LocalDate.coerceIn(min: LocalDate, max: LocalDate): LocalDate = when {
        isBefore(min) -> min
        isAfter(max) -> max
        else -> this
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val FREQUENT_COUNT = 4
    }
}
