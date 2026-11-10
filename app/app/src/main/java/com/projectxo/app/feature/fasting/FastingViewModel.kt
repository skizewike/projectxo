package com.projectxo.app.feature.fasting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.FastingRepository
import com.projectxo.app.core.data.FastingSession
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.model.FastingMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FastingUiState(
    val current: FastingSession? = null,
    val last: FastingSession? = null,
    val recent: List<FastingSession> = emptyList(),
    val planHours: Int = FastingMath.DEFAULT_PLAN,
    val safetyAccepted: Boolean = false,
    val loaded: Boolean = false,
) {
    val fasting: Boolean get() = current != null
}

/**
 * Голодание (Р28, Р34): настоящий таймер по часам телефона. Человек только начинает и заканчивает,
 * время начала и конца можно поправить. В базе — начало и конец, «прошло» считается от часов.
 * Закончить раньше цели можно: запись сохраняется как есть, без «провала».
 */
class FastingViewModel(
    private val repo: FastingRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val state: StateFlow<FastingUiState> = combine(repo.current(), repo.last(), repo.recent(RECENT_SHOWN), settings.settings) { cur, last, recent, s ->
        FastingUiState(cur, last, recent, cur?.planHours ?: s.wave2.fastingPlanHours, s.wave2.fastingSafetyAccepted, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FastingUiState())

    /** Начать. [startMs] — если поправили время (не раньше суток назад, не позже «сейчас»). */
    fun start(startMs: Long = clock.nowMs()) {
        if (state.value.fasting) return
        val now = clock.nowMs()
        val bounds = FastingMath.startBounds(now)
        val s = startMs.coerceIn(bounds.first, bounds.last)
        val plan = state.value.planHours
        appScope.launch { repo.start(s, plan) }
    }

    /** Закончить. [endMs] — если поправили время (не раньше начала, не позже «сейчас»). */
    fun finish(endMs: Long = clock.nowMs()) {
        val cur = state.value.current ?: return
        val b = FastingMath.endBounds(cur.startMs, clock.nowMs())
        val e = endMs.coerceIn(b.first, b.last)
        appScope.launch { repo.finish(cur.id, e) }
    }

    fun editStart(startMs: Long) {
        val cur = state.value.current ?: return
        val now = clock.nowMs()
        val b = FastingMath.startBounds(now)
        appScope.launch { repo.setStart(cur.id, startMs.coerceIn(b.first, b.last)) }
    }

    fun setPlan(hours: Int) {
        if (hours !in FastingMath.PLANS) return
        settings.update { it.copy(wave2 = it.wave2.copy(fastingPlanHours = hours)) }
        val cur = state.value.current ?: return
        appScope.launch { repo.setPlan(cur.id, hours) }
    }

    fun acceptSafety() = settings.update { it.copy(wave2 = it.wave2.copy(fastingSafetyAccepted = true)) }

    private companion object {
        const val RECENT_SHOWN = 7
    }
}
