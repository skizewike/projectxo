package com.projectxo.app.feature.breathing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.BreathingRepository
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.model.BreathRhythm
import com.projectxo.app.core.model.BreathingMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Итог сеанса для экрана. */
data class BreathSummary(val seconds: Int, val cycles: Int)

data class BreathingUiState(
    val date: LocalDate,
    val rhythm: BreathRhythm = BreathRhythm.Even,
    val minutes: Int = BreathingMath.DEFAULT_MIN,
    /** Момент старта сеанса (мс часов телефона) или null, если не идёт. */
    val startedAtMs: Long? = null,
    val summary: BreathSummary? = null,
    val todaySeconds: Int = 0,
    val todaySessions: Int = 0,
) {
    val running: Boolean get() = startedAtMs != null
}

/**
 * Дыхание (Р52, Р54): ровное дыхание по ритму 1, 3 или 5 минут. Сеанс считается по часам телефона от [BreathingUiState.startedAtMs];
 * записывается, когда закончился или остановлен после 20 секунд.
 */
class BreathingViewModel(
    private val repo: BreathingRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val today = clock.today()
    private val local = MutableStateFlow(Local())

    private data class Local(val startedAtMs: Long? = null, val summary: BreathSummary? = null)

    val state: StateFlow<BreathingUiState> = combine(local, settings.settings, repo.sessionsFor(today)) { l, s, sessions ->
        BreathingUiState(
            date = today, rhythm = s.wave2.breathRhythm, minutes = s.wave2.breathMinutes,
            startedAtMs = l.startedAtMs, summary = l.summary,
            todaySeconds = sessions.sumOf { it.seconds }, todaySessions = sessions.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BreathingUiState(today))

    fun setRhythm(r: BreathRhythm) = settings.update { it.copy(wave2 = it.wave2.copy(breathRhythm = r)) }

    fun setMinutes(m: Int) = settings.update { it.copy(wave2 = it.wave2.copy(breathMinutes = m)) }

    fun start() {
        local.value = Local(startedAtMs = clock.nowMs())
    }

    /** Остановить сеанс вручную. Записывается, если прошло хотя бы [MIN_RECORD_SEC] секунд. */
    fun stop() {
        val l = local.value
        val startedAt = l.startedAtMs ?: return
        val sec = ((clock.nowMs() - startedAt) / 1000L).toInt()
        local.update { Local() }
        if (sec >= MIN_RECORD_SEC) record(sec)
    }

    /** Сеанс дошёл до конца: записать и показать итог. */
    fun complete() {
        val l = local.value
        if (l.startedAtMs == null) return
        record(state.value.minutes * 60, showSummary = true)
    }

    private fun record(seconds: Int, showSummary: Boolean = false) {
        val s = state.value
        val cycles = (seconds / s.rhythm.cycleSeconds).coerceAtLeast(0)
        val at = clock.nowMs()
        appScope.launch { repo.add(at, today, seconds, s.rhythm, cycles) }
        local.value = Local(summary = if (showSummary) BreathSummary(seconds, cycles) else null)
    }

    private companion object {
        const val MIN_RECORD_SEC = 20
    }
}
