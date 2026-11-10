package com.projectxo.app.feature.steps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.data.GoalsRepository
import com.projectxo.app.core.data.HourSteps
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.data.StepsRepository
import com.projectxo.app.core.model.DefaultGoals
import com.projectxo.app.core.model.UndoPolicy
import com.projectxo.app.core.steps.StepsMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StepsUiState(
    val date: LocalDate,
    /** 24 числа, индекс = час. */
    val hours: List<Int> = List(24) { 0 },
    val goal: Long = DefaultGoals.STEPS,
    val loaded: Boolean = false,
    /** Длина шага в метрах: из роста в профиле или 0,75 м (Р26). */
    val strideM: Double = StepsMath.STRIDE_M,
    /** Только что добавленные вручную шаги, которые ещё можно отменить. */
    val pendingUndo: Int? = null,
) {
    val total: Int get() = hours.sum()
    val left: Long get() = (goal - total).coerceAtLeast(0)
    val fraction: Float get() = if (goal <= 0) 0f else (total.toFloat() / goal).coerceIn(0f, 1f)
}

/**
 * Экран «Ходьба». Шаги пишет датчик (StepsLive, StepsWorker); вручную можно добавить прогулку без телефона
 * или шаги на телефоне без датчика. Ручная запись сразу идёт в базу (в [appScope]),
 * «Отменить» снимает её в течение UndoPolicy.WINDOW_MS.
 */
class StepsViewModel(
    private val steps: StepsRepository,
    goals: GoalsRepository,
    settings: SettingsRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {
    private val today = clock.today()
    private val undo = MutableStateFlow<HourSteps?>(null)
    private var undoJob: Job? = null
    private var writeJob: Job? = null

    val state: StateFlow<StepsUiState> = combine(
        steps.hoursFor(today),
        goals.goal(GoalType.Steps),
        settings.settings,
        undo,
    ) { hours, goal, s, u ->
        StepsUiState(
            date = today,
            hours = hours,
            goal = goal,
            loaded = true,
            strideM = StepsMath.strideM(s.profile?.heightCm),
            pendingUndo = u?.steps,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StepsUiState(date = today))

    /** Добавить [count] шагов в текущий час. Вне 1..[StepsMath.MAX_MANUAL] — ничего. */
    fun addManual(count: Int) {
        if (count !in 1..StepsMath.MAX_MANUAL) return
        val now = Instant.ofEpochMilli(clock.nowMs()).atZone(zone())
        // Экран открыт со вчера, а уже за полночь: пишем в день экрана, в последний час.
        val hour = if (now.toLocalDate() == today) now.hour else LAST_HOUR
        val part = HourSteps(today, hour, count)
        undoJob?.cancel()
        undo.value = part
        val previous = writeJob
        writeJob = appScope.launch { previous?.join(); steps.addSteps(listOf(part)) }
        undoJob = viewModelScope.launch {
            delay(UndoPolicy.WINDOW_MS)
            undo.value = null
        }
    }

    fun undoManual() {
        val part = undo.value ?: return
        undoJob?.cancel()
        undoJob = null
        undo.value = null
        // Снимаем только после того, как запись дошла до базы.
        val previous = writeJob
        writeJob = appScope.launch { previous?.join(); steps.removeSteps(listOf(part)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val LAST_HOUR = 23
    }
}
