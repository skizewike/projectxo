package com.projectxo.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.data.WaterRepository
import com.projectxo.app.core.model.BalanceHistory
import com.projectxo.app.core.model.BalanceMath
import com.projectxo.app.core.model.UndoPolicy
import com.projectxo.app.core.model.WaterPortions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Последнее быстрое добавление с «Сегодня», которое ещё можно отменить. */
data class QuickAdd(val entryId: Long, val ml: Int)

/**
 * «Сегодня»: включённые модули в порядке из настроек, «Баланс дня» по ним же.
 * Состояние null, пока не пришли первые данные: по нему заставка понимает, что загрузка закончена.
 */
class TodayViewModel(
    history: BalanceHistory,
    private val water: WaterRepository,
    settings: SettingsRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
    /** Главная порция в мл для текущих единиц: 250 мл или 8 унц. */
    private val primaryMl: () -> Int = { WaterPortions.PRIMARY },
) : ViewModel() {

    val date: LocalDate = clock.today()

    val state: StateFlow<TodayState?> = combine(
        history.totalsBetween(date, date), history.goals(), settings.settings,
    ) { totals, goals, s ->
        TodayState(BalanceMath.progress(totals[date], goals, s.modules, s.moduleOrder))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _undo = MutableStateFlow<QuickAdd?>(null)
    val undo: StateFlow<QuickAdd?> = _undo.asStateFlow()
    private var undoJob: Job? = null

    /** «+» в шапке: одна порция воды одним касанием. [delayMs] — пока летят чернила (DESIGN.md, 5b п. 1). */
    fun quickAddWater(delayMs: Long = 0L) {
        viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val ml = primaryMl()
            val id = water.add(ml, clock.nowMs(), date)
            undoJob?.cancel()
            _undo.value = QuickAdd(id, ml)
            undoJob = launch {
                delay(UndoPolicy.WINDOW_MS)
                _undo.value = null
            }
        }
    }

    fun undoQuickAdd() {
        val q = _undo.value ?: return
        undoJob?.cancel()
        _undo.value = null
        appScope.launch { water.delete(q.entryId) }
    }
}
