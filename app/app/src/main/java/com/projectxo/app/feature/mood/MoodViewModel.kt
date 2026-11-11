package com.projectxo.app.feature.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.MoodRepository
import com.projectxo.app.core.model.MoodEntry
import com.projectxo.app.core.model.MoodMath
import com.projectxo.app.core.model.MoodTag
import com.projectxo.app.core.model.UndoPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MoodUiState(
    val date: LocalDate,
    /** Отметки за сегодня, новые сверху (без той, что ждёт окна «Вернуть»). */
    val entries: List<MoodEntry> = emptyList(),
    /** Выбор в форме: пока не выбрано, null. */
    val mood: Int? = null,
    val energy: Int? = null,
    val tags: Set<MoodTag> = emptySet(),
    val pendingDelete: MoodEntry? = null,
) {
    val canSave: Boolean get() = mood != null && energy != null && entries.size < MoodMath.MAX_PER_DAY
}

/**
 * «Самочувствие» (Р74, вариант «Две шкалы»): настроение и энергия по пять делений, пометки «что повлияло».
 * За день можно сделать несколько отметок. Цели и процента нет, в «Баланс дня» модуль не входит.
 * Удаление мягкое: запись прячется сразу, из базы уходит через 4 секунды, если не нажали «Вернуть».
 */
class MoodViewModel(
    private val repo: MoodRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val today = clock.today()
    private val draft = MutableStateFlow(Draft())
    private val pending = MutableStateFlow<MoodEntry?>(null)
    private var commitJob: Job? = null

    private data class Draft(val mood: Int? = null, val energy: Int? = null, val tags: Set<MoodTag> = emptySet())

    val state: StateFlow<MoodUiState> = combine(repo.entriesFor(today), draft, pending) { entries, d, pend ->
        MoodUiState(today, entries.filter { it.id != pend?.id }, d.mood, d.energy, d.tags, pend)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoodUiState(today))

    fun setMood(v: Int) = draft.update { it.copy(mood = MoodMath.clampScale(v)) }

    fun setEnergy(v: Int) = draft.update { it.copy(energy = MoodMath.clampScale(v)) }

    fun toggleTag(t: MoodTag) = draft.update { it.copy(tags = if (t in it.tags) it.tags - t else it.tags + t) }

    /** Записать отметку; форма очищается. */
    fun save() {
        val s = state.value
        val d = draft.value
        val mood = d.mood ?: return
        val energy = d.energy ?: return
        if (!s.canSave) return
        draft.value = Draft()
        val entry = MoodEntry(0, clock.nowMs(), today, mood, energy, d.tags)
        appScope.launch { repo.add(entry) }
    }

    fun delete(entry: MoodEntry) {
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
        appScope.launch { repo.delete(p.id) }
        job?.cancel()
    }

    override fun onCleared() {
        commitPending()
    }
}
