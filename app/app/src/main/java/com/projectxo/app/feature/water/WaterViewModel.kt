package com.projectxo.app.feature.water

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.data.GoalsRepository
import com.projectxo.app.core.data.WaterEntry
import com.projectxo.app.core.data.WaterRepository
import com.projectxo.app.core.model.DefaultGoals
import com.projectxo.app.core.model.UndoPolicy
import com.projectxo.app.core.model.WaterPortions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WaterUiState(
    val date: LocalDate,
    val totalMl: Int = 0,
    val goalMl: Long = DefaultGoals.WATER_ML,
    val entries: List<WaterEntry> = emptyList(),
    val loaded: Boolean = false,
    /** Запись, удаление которой ещё можно отменить. */
    val pendingDelete: WaterEntry? = null,
) {
    val fraction: Float get() = if (goalMl <= 0) 0f else (totalMl.toFloat() / goalMl).coerceIn(0f, 1f)
    val leftMl: Long get() = (goalMl - totalMl).coerceAtLeast(0)
}

/**
 * Экран «Вода». Удаление мягкое (скилл room-local-data): запись сразу прячется,
 * а из базы уходит через UndoPolicy.WINDOW_MS, если не нажали «Вернуть».
 * Запись в базу идёт в [appScope], чтобы она не потерялась при закрытии экрана.
 */
class WaterViewModel(
    private val water: WaterRepository,
    goals: GoalsRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val today = clock.today()
    private val pending = MutableStateFlow<WaterEntry?>(null)
    private var commitJob: Job? = null

    val state: StateFlow<WaterUiState> = combine(
        water.entriesFor(today),
        water.totalFor(today),
        goals.goal(GoalType.WaterMl),
        pending,
    ) { entries, total, goal, p ->
        val hidden = p?.takeIf { e -> entries.any { it.id == e.id } }
        WaterUiState(
            date = today,
            totalMl = total - (hidden?.ml ?: 0),
            goalMl = goal,
            entries = if (hidden == null) entries else entries.filterNot { it.id == hidden.id },
            loaded = true,
            pendingDelete = p,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WaterUiState(date = today))

    /** [delayMs] — пока летят чернила (DESIGN.md, 5b п. 1); время записи берётся в момент нажатия. */
    fun add(ml: Int, delayMs: Long = 0L) {
        if (!WaterPortions.isValid(ml)) return
        val at = clock.nowMs()
        appScope.launch {
            if (delayMs > 0) delay(delayMs)
            water.add(ml, at, today)
        }
    }

    fun delete(entry: WaterEntry) {
        commitPending()
        pending.value = entry
        commitJob = viewModelScope.launch {
            delay(UndoPolicy.WINDOW_MS)
            commitPending()
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
        appScope.launch { water.delete(p.id) }
        job?.cancel()
    }

    override fun onCleared() {
        commitPending()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
