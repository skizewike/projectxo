package com.projectxo.app.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.HabitRepository
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.model.Habit
import com.projectxo.app.core.model.HabitCheck
import com.projectxo.app.core.model.HabitFreq
import com.projectxo.app.core.model.HabitKind
import com.projectxo.app.core.model.HabitMath
import com.projectxo.app.core.model.HabitsView
import com.projectxo.app.core.model.UndoPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Привычка с расчётами для экрана. */
data class HabitItem(
    val habit: Habit,
    val marks: HabitMath.Marks,
    /** Нужна сегодня: по расписанию и не закрыта недельная норма. */
    val due: Boolean,
    val doneToday: Boolean,
    val valueToday: Int,
    val streak: Int,
    val best: Int,
    val percent30: Int,
    val weekCount: Int,
)

data class HabitsUiState(
    val today: LocalDate,
    val items: List<HabitItem> = emptyList(),
    val view: HabitsView = HabitsView.Ring,
    val editMode: Boolean = false,
    /** Привычка, удаление которой ещё можно отменить. */
    val pendingDelete: Habit? = null,
    val loaded: Boolean = false,
) {
    val dueToday: List<HabitItem> get() = items.filter { it.due || it.doneToday }
    val doneCount: Int get() = dueToday.count { it.doneToday }
}

/** Заготовка привычки при первом входе (названия берутся из строк). */
data class HabitSeed(val name: String, val freq: HabitFreq, val reminderMin: Int?)

/**
 * Привычки (Р37, Р43): частота (каждый день, N раз в неделю, по дням), серия без обнуления за один пропуск,
 * лучшая серия, процент за 30 дней, счётчики. Удаление мягкое: «Вернуть» в течение [UndoPolicy.WINDOW_MS].
 */
class HabitsViewModel(
    private val repo: HabitRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
    private val seeds: List<HabitSeed> = emptyList(),
    /** Общие привычки (Р79): отметки сразу уходят другу. Null — режим «Вместе» не подключён. */
    private val shared: com.projectxo.app.core.data.shared.SharedHabitRepository? = null,
) : ViewModel() {
    private val today = clock.today()
    private val edit = MutableStateFlow(false)
    private val pending = MutableStateFlow<Pair<Habit, List<HabitCheck>>?>(null)
    private var commitJob: Job? = null

    init {
        seedIfNeeded()
    }

    private fun seedIfNeeded() {
        viewModelScope.launch {
            val s = settings.settings.value
            if (s.wave2.habitsSeeded) return@launch
            if (repo.habits().first().isEmpty()) seeds.forEach { repo.add(Habit(0, it.name, it.freq, it.reminderMin)) }
            settings.update { it.copy(wave2 = it.wave2.copy(habitsSeeded = true)) }
        }
    }

    val state: StateFlow<HabitsUiState> = combine(
        repo.habits(), repo.checks(today.minusDays(HabitMath.HISTORY_DAYS.toLong() + 7), today), settings.settings, edit, pending,
    ) { habits, checks, s, editing, pend ->
        val hidden = pend?.first?.id
        val items = habits.filter { it.id != hidden }.map { h ->
            val m = HabitMath.Marks(h, checks)
            HabitItem(
                habit = h, marks = m,
                due = HabitMath.dueOn(h, m, today), doneToday = m.done(today), valueToday = m.value(today),
                streak = HabitMath.streak(h, m, today), best = HabitMath.bestStreak(h, m, today),
                percent30 = HabitMath.percent30(h, m, today), weekCount = HabitMath.weekCount(m, today),
            )
        }
        HabitsUiState(today, items, s.wave2.habitsView, editing, pend?.first, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState(today))

    /** Отметить или снять отметку за день, [back] дней назад (0 — сегодня). Будущее и дни не по расписанию игнорируются. */
    fun toggle(item: HabitItem, back: Int) {
        if (back < 0) return
        val date = today.minusDays(back.toLong())
        if (back > 0 && !HabitMath.planned(item.habit.freq, date)) return
        val was = item.marks.done(date)
        val value = if (was) 0 else item.habit.target
        appScope.launch {
            repo.setCheck(item.habit.id, date, value)
            shared?.syncHabit(item.habit.id, today)
        }
    }

    /** Счётчик на сегодня: [delta] шагов, от 0 до цели. */
    fun count(item: HabitItem, delta: Int) {
        val v = (item.valueToday + delta).coerceIn(0, item.habit.target)
        appScope.launch {
            repo.setCheck(item.habit.id, today, v)
            shared?.syncHabit(item.habit.id, today)
        }
    }

    fun save(habit: Habit) {
        val clean = habit.copy(name = habit.name.trim().take(HabitMath.MAX_NAME_LENGTH), target = habit.target.coerceIn(1, HabitMath.MAX_TARGET))
        if (clean.name.isEmpty()) return
        appScope.launch { if (clean.id == 0L) repo.add(clean) else repo.update(clean) }
    }

    fun setView(v: HabitsView) = settings.update { it.copy(wave2 = it.wave2.copy(habitsView = v)) }

    fun setEditMode(on: Boolean) {
        edit.value = on
    }

    fun delete(item: HabitItem) {
        commitPending()
        viewModelScope.launch {
            val checks = repo.checksOf(item.habit.id)
            pending.value = item.habit to checks
            commitJob = launch {
                delay(UndoPolicy.WINDOW_MS)
                commitPending()
            }
        }
    }

    fun undoDelete() {
        commitJob?.cancel()
        commitJob = null
        pending.value = null
    }

    private fun commitPending() {
        val p = pending.value ?: return
        pending.value = null
        val job = commitJob
        commitJob = null
        appScope.launch {
            shared?.habitDeleted(p.first.id)
            repo.delete(p.first.id)
        }
        job?.cancel()
    }

    override fun onCleared() {
        commitPending()
    }
}
