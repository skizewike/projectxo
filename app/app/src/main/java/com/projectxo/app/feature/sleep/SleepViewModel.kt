package com.projectxo.app.feature.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.data.SleepNight
import com.projectxo.app.core.data.SleepRepository
import com.projectxo.app.core.model.SleepMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class SleepUiState(
    /** Ночь, которая идёт, или null. */
    val current: SleepNight? = null,
    /** Завершённые ночи, новые первыми. */
    val nights: List<SleepNight> = emptyList(),
    val bedMin: Int = 23 * 60,
    val wakeMin: Int = 7 * 60,
    val remind: Boolean = true,
    /** После подъёма: id ночи, для которой спрашиваем «Как спалось?». */
    val ratingFor: Long? = null,
    val loaded: Boolean = false,
) {
    val sleeping: Boolean get() = current != null
    val last: SleepNight? get() = nights.firstOrNull()
    val goalMin: Int get() = SleepMath.goalMinutes(bedMin, wakeMin)

    /** Средний отбой и разброс по последним семи ночам. */
    val regularity: Pair<Int, Int>?
        get() = SleepMath.regularity(
            nights.take(7).map { n ->
                Instant.ofEpochMilli(n.startMs).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
            },
        )
}

/**
 * Сон (Р36, Р42): таймер «Ложусь спать» / «Встаю» по часам телефона. Время начала и конца можно поправить.
 * Нажатие короче 10 минут сном не считается. Цель по умолчанию 8 часов, не меньше 7.
 */
class SleepViewModel(
    private val repo: SleepRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val ratingFor = MutableStateFlow<Long?>(null)

    val state: StateFlow<SleepUiState> = combine(repo.current(), repo.nights(NIGHTS_SHOWN), settings.settings, ratingFor) { cur, nights, s, r ->
        SleepUiState(cur, nights, s.wave2.sleepBedMin, s.wave2.sleepWakeMin, s.wave2.sleepRemind, r, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepUiState())

    /** «Ложусь спать». [startMs] — если поправили время. */
    fun goToBed(startMs: Long = clock.nowMs()) {
        if (state.value.sleeping) return
        appScope.launch { repo.start(startMs) }
    }

    /** «Встаю». Возвращает false, если ночь короче 10 минут и не записана. */
    fun wake(endMs: Long = clock.nowMs()) {
        val cur = state.value.current ?: return
        appScope.launch {
            if (SleepMath.isValidNight(cur.startMs, endMs)) {
                repo.finish(cur.id, endMs)
                ratingFor.value = cur.id
            } else {
                repo.delete(cur.id)
            }
        }
    }

    /** Исправить время отбоя идущей ночи. */
    fun editStart(startMs: Long) {
        val cur = state.value.current ?: return
        appScope.launch { repo.setStart(cur.id, startMs) }
    }

    fun rate(rate: Int?) {
        val id = ratingFor.value ?: return
        ratingFor.update { null }
        if (rate != null) appScope.launch { repo.setRate(id, rate) }
    }

    fun skipRating() {
        ratingFor.value = null
    }

    /** Оценить уже записанную ночь заново (по нажатию на «спалось …»). */
    fun rateLast() {
        ratingFor.value = state.value.last?.id
    }

    /** Плановые отбой и подъём; цель не меньше 7 часов (Р36). */
    fun setSchedule(bedMin: Int, wakeMin: Int) {
        val b = ((bedMin % SleepMath.DAY_MIN) + SleepMath.DAY_MIN) % SleepMath.DAY_MIN
        val w = ((wakeMin % SleepMath.DAY_MIN) + SleepMath.DAY_MIN) % SleepMath.DAY_MIN
        if (SleepMath.goalMinutes(b, w) < SleepMath.MIN_GOAL_MIN) return
        settings.update { it.copy(wave2 = it.wave2.copy(sleepBedMin = b, sleepWakeMin = w)) }
    }

    fun setRemind(on: Boolean) = settings.update { it.copy(wave2 = it.wave2.copy(sleepRemind = on)) }

    private companion object {
        const val NIGHTS_SHOWN = 14
    }
}
