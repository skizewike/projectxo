package com.projectxo.app.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.data.WorkoutProgram
import com.projectxo.app.core.data.WorkoutRepository
import com.projectxo.app.core.data.WorkoutSessionRecord
import com.projectxo.app.core.model.Exercise
import com.projectxo.app.core.model.HabitMath
import com.projectxo.app.core.model.WorkoutCounting
import com.projectxo.app.core.model.WorkoutMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Шаги тренировки: обзор, проверка уровня, подходы с отдыхом, «Как было?», итог. */
sealed interface WorkoutStep {
    data object Overview : WorkoutStep
    data class CheckLevel(val exercise: Exercise, val value: Int, val retest: Boolean = false) : WorkoutStep
    data class Sets(
        val exercise: Exercise,
        val level: Int,
        val plan: List<Int>,
        val actual: List<Int>,
        /** Идёт отдых: момент конца (мс часов телефона) или null. */
        val restEndsAtMs: Long?,
        /** Сколько уже набрано в текущем подходе («Счёт касанием»). */
        val current: Int,
        /** Длина текущего отдыха, секунды: для кольца таймера. */
        val restTotalSec: Int = 0,
        /** Планка: момент начала удержания (после обратного отсчёта) или null. */
        val holdStartMs: Long? = null,
    ) : WorkoutStep {
        val index: Int get() = actual.size
    }
    data class Rate(val exercise: Exercise, val level: Int, val plan: List<Int>, val actual: List<Int>) : WorkoutStep {
        val allDone: Boolean get() = WorkoutMath.allDone(plan, actual)
    }
    data class Done(val exercise: Exercise, val oldLevel: Int, val newLevel: Int, val allDone: Boolean, val total: Int = 0) : WorkoutStep
}

data class WorkoutUiState(
    val step: WorkoutStep = WorkoutStep.Overview,
    val programs: Map<Exercise, WorkoutProgram> = emptyMap(),
    val sessions: List<WorkoutSessionRecord> = emptyList(),
    val counting: WorkoutCounting = WorkoutCounting.Ladder,
    val today: java.time.LocalDate? = null,
    val timer: com.projectxo.app.core.model.WorkoutTimerStyle = com.projectxo.app.core.model.WorkoutTimerStyle.Ring,
    /** Дни плана тренировок: маска пн = 1 … вс = 64. */
    val days: Int = HabitMath.WORKOUT_DAYS,
) {
    /** Тренировок, записанных сегодня. */
    val doneToday: Int get() = sessions.count { it.date == today }
    fun level(e: Exercise): Int? = programs[e]?.level
    fun sessionsOf(e: Exercise): List<WorkoutSessionRecord> = sessions.filter { it.exercise == e.id }

    /**
     * Упражнение, которое делали давнее всех (или ещё не делали): подсказка на обзоре.
     * Выбирается из базовой четвёрки и тех упражнений, которые уже есть в программе, чтобы новичку не предлагали берпи.
     */
    val suggested: Exercise?
        get() = Exercise.entries.filter { it in Exercise.STARTER || programs.containsKey(it) || sessionsOf(it).isNotEmpty() }
            .minByOrNull { e -> sessionsOf(e).maxOfOrNull { it.timestampMs } ?: 0L }
}

/**
 * Домашние тренировки, отдельный модуль (Р73, Р47, docs/SPORT.md): проверка уровня, 5 подходов с отдыхом, «Как было?» и рост нагрузки.
 * Пропуск тренировки ничего не снижает: план ждёт.
 */
class WorkoutViewModel(
    private val repo: WorkoutRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val step = MutableStateFlow<WorkoutStep>(WorkoutStep.Overview)

    val state: StateFlow<WorkoutUiState> = combine(step, repo.programs(), repo.sessions(HISTORY), settings.settings) { st, p, s, set ->
        WorkoutUiState(st, p.mapNotNull { pr -> Exercise.fromId(pr.exercise)?.let { it to pr } }.toMap(), s, set.wave2.workoutCounting, clock.today(), set.wave2.workoutTimer, set.wave2.workoutDays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    fun open(e: Exercise) {
        val s = state.value
        val level = s.level(e)
        val retest = WorkoutMath.needsRetest(s.sessionsOf(e).size)
        if (level == null || retest) step.value = WorkoutStep.CheckLevel(e, level ?: e.defaultCheck, retest = level != null) else begin(e, level)
    }

    fun setCheck(delta: Int) = step.update {
        if (it is WorkoutStep.CheckLevel) {
            val unit = if (it.exercise.seconds) WorkoutMath.PLANK_STEP else 1
            it.copy(value = (it.value + delta * unit).coerceIn(if (it.exercise.seconds) WorkoutMath.PLANK_MIN else 1, WorkoutMath.MAX_LEVEL))
        } else it
    }

    /** Уровень после проверки: сохраняется и сразу начинается тренировка. */
    fun confirmLevel() {
        val st = step.value as? WorkoutStep.CheckLevel ?: return
        appScope.launch {
            val old = state.value.programs[st.exercise]
            repo.saveProgram(WorkoutProgram(st.exercise.id, st.value, old?.daysMask ?: HabitMath.WORKOUT_DAYS))
        }
        begin(st.exercise, st.value)
    }

    private fun begin(e: Exercise, level: Int) {
        val plan = WorkoutMath.plan(level, e)
        step.value = WorkoutStep.Sets(e, level, plan, emptyList(), null, 0)
    }

    /** Один повтор (режим «Счёт касанием»). */
    fun tap() = step.update { if (it is WorkoutStep.Sets && it.restEndsAtMs == null) it.copy(current = it.current + 1) else it }

    /** Убрать последний повтор («Счёт касанием»). */
    fun untap() = step.update { if (it is WorkoutStep.Sets && it.restEndsAtMs == null && it.current > 0) it.copy(current = it.current - 1) else it }

    /** Планка: обратный отсчёт [PREP_SEC] с и удержание. */
    fun startHold() = step.update {
        if (it is WorkoutStep.Sets && it.exercise.seconds && it.restEndsAtMs == null && it.holdStartMs == null) it.copy(holdStartMs = clock.nowMs() + PREP_SEC * 1000L) else it
    }

    /** Планка: отменить обратный отсчёт или остановить удержание. Если удержание шло, записывается, сколько вышло. */
    fun stopHold() {
        val st = step.value as? WorkoutStep.Sets ?: return
        val start = st.holdStartMs ?: return
        val elapsed = ((clock.nowMs() - start) / 1000L).toInt()
        if (elapsed <= 0) step.value = st.copy(holdStartMs = null) else finishSet(elapsed.coerceAtMost(st.plan[st.index]))
    }

    /** Поправить число в подходе («Лесенка»: «−» и «+»). */
    fun adjust(delta: Int) = step.update {
        if (it is WorkoutStep.Sets && it.restEndsAtMs == null && it.holdStartMs == null) {
            val unit = if (it.exercise.seconds) WorkoutMath.PLANK_STEP else 1
            val base = if (it.current == 0) it.plan[it.index] else it.current
            it.copy(current = (base + delta * unit).coerceAtLeast(unit))
        } else it
    }

    /** Подход закончен: [value] — сколько вышло (по умолчанию цель подхода). */
    fun finishSet(override: Int? = null) {
        val st = step.value as? WorkoutStep.Sets ?: return
        if (st.restEndsAtMs != null) return
        val target = st.plan[st.index]
        val value = override ?: if (st.current > 0) st.current else target
        val actual = st.actual + value
        if (actual.size >= st.plan.size) {
            step.value = WorkoutStep.Rate(st.exercise, st.level, st.plan, actual)
        } else {
            val rest = WorkoutMath.restSeconds(st.level, st.exercise)
            step.value = st.copy(actual = actual, current = 0, holdStartMs = null, restTotalSec = rest, restEndsAtMs = clock.nowMs() + rest * 1000L)
        }
    }

    fun addRest() = step.update {
        if (it is WorkoutStep.Sets && it.restEndsAtMs != null) it.copy(restEndsAtMs = it.restEndsAtMs + EXTRA_REST_SEC * 1000L, restTotalSec = it.restTotalSec + EXTRA_REST_SEC) else it
    }

    fun endRest() = step.update { if (it is WorkoutStep.Sets && it.restEndsAtMs != null) it.copy(restEndsAtMs = null, current = 0) else it }

    /** Остановить тренировку без записи. */
    fun abort() {
        step.value = WorkoutStep.Overview
    }

    /** «Как было?» — записать тренировку и обновить уровень. */
    fun rate(rating: Int) {
        val st = step.value as? WorkoutStep.Rate ?: return
        val previousMissed = state.value.sessionsOf(st.exercise).firstOrNull()?.let { !WorkoutMath.allDone(it.plan, it.actual) } ?: false
        val next = WorkoutMath.nextLevel(st.level, st.exercise, st.allDone, rating, previousMissed)
        val today = clock.today()
        val record = WorkoutSessionRecord(0, today, st.exercise.id, st.level, st.plan, st.actual, rating, clock.nowMs())
        appScope.launch {
            repo.addSession(record)
            repo.saveProgram(WorkoutProgram(st.exercise.id, next, state.value.programs[st.exercise]?.daysMask ?: HabitMath.WORKOUT_DAYS))
        }
        step.value = WorkoutStep.Done(st.exercise, st.level, next, st.allDone, st.actual.sum())
    }

    fun setCounting(c: WorkoutCounting) = settings.update { it.copy(wave2 = it.wave2.copy(workoutCounting = c)) }

    fun setTimer(t: com.projectxo.app.core.model.WorkoutTimerStyle) = settings.update { it.copy(wave2 = it.wave2.copy(workoutTimer = t)) }

    /** Включить или выключить день плана тренировок ([bit] из [HabitMath.dayBit]). Хотя бы один день остаётся. */
    fun setDays(mask: Int) {
        if (mask and HabitMath.ALL_DAYS == 0) return
        settings.update { it.copy(wave2 = it.wave2.copy(workoutDays = mask and HabitMath.ALL_DAYS)) }
    }

    fun backToOverview() {
        step.value = WorkoutStep.Overview
    }

    private companion object {
        const val HISTORY = 60
        const val EXTRA_REST_SEC = 30
        const val PREP_SEC = 3
    }
}
