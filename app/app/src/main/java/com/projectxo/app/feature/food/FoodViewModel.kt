package com.projectxo.app.feature.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.FoodDraft
import com.projectxo.app.core.data.FoodEntry
import com.projectxo.app.core.data.FoodRepository
import com.projectxo.app.core.data.FoodTotals
import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.data.GoalsRepository
import com.projectxo.app.core.model.DefaultGoals
import com.projectxo.app.core.model.FoodMath
import com.projectxo.app.core.model.FoodRules
import com.projectxo.app.core.model.UndoPolicy
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

/** Цели дня по еде: калории и граммы белков, жиров, углеводов. */
data class FoodGoals(
    val kcal: Long = DefaultGoals.FOOD_KCAL,
    val proteinG: Long = DefaultGoals.PROTEIN_G,
    val fatG: Long = DefaultGoals.FAT_G,
    val carbsG: Long = DefaultGoals.CARBS_G,
)

data class FoodUiState(
    val date: LocalDate,
    val totals: FoodTotals = FoodTotals(),
    val goals: FoodGoals = FoodGoals(),
    val entries: List<FoodEntry> = emptyList(),
    val loaded: Boolean = false,
    /** Запись, удаление которой ещё можно отменить. */
    val pendingDelete: FoodEntry? = null,
) {
    val leftKcal: Long get() = FoodMath.left(totals, goals.kcal)
    val overKcal: Long get() = FoodMath.over(totals, goals.kcal)
}

/**
 * Экран «Еда». Удаление мягкое, как у воды: запись сразу прячется, а из базы уходит
 * через UndoPolicy.WINDOW_MS, если не нажали «Вернуть». Запись идёт в [appScope], чтобы не потеряться.
 */
class FoodViewModel(
    private val food: FoodRepository,
    goals: GoalsRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
    /** Снимки записей: файл удаляется вместе с записью (Р71). */
    private val photos: com.projectxo.app.core.data.photo.FoodPhotoStore? = null,
) : ViewModel() {

    private val today = clock.today()
    private val pending = MutableStateFlow<FoodEntry?>(null)
    private var commitJob: Job? = null

    private val goalsFlow = combine(
        goals.goal(GoalType.FoodKcal),
        goals.goal(GoalType.ProteinG),
        goals.goal(GoalType.FatG),
        goals.goal(GoalType.CarbsG),
    ) { k, p, f, c -> FoodGoals(k, p, f, c) }

    val state: StateFlow<FoodUiState> = combine(
        food.entriesFor(today),
        food.totalsFor(today),
        goalsFlow,
        pending,
    ) { entries, totals, goals, p ->
        val hidden = p?.takeIf { e -> entries.any { it.id == e.id } }
        FoodUiState(
            date = today,
            totals = if (hidden == null) totals else totals.minus(hidden),
            goals = goals,
            entries = if (hidden == null) entries else entries.filterNot { it.id == hidden.id },
            loaded = true,
            pendingDelete = p,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FoodUiState(date = today))

    /** Добавить запись. Неверные числа молча отбрасываются: форма их и так не пропускает. */
    fun add(draft: FoodDraft) {
        val clean = draft.copy(name = draft.name.trim().take(FoodRules.MAX_NAME_LENGTH))
        if (!FoodRules.isValid(clean)) return
        val at = clock.nowMs()
        appScope.launch { food.add(clean, at, today) }
    }

    fun delete(entry: FoodEntry) {
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
        appScope.launch {
            food.delete(p.id)
            photos?.delete(p.photoFile)
        }
        job?.cancel()
    }

    override fun onCleared() {
        commitPending()
    }

    private fun FoodTotals.minus(e: FoodEntry) =
        FoodTotals(kcal - e.kcal, proteinG - e.proteinG, fatG - e.fatG, carbsG - e.carbsG)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
