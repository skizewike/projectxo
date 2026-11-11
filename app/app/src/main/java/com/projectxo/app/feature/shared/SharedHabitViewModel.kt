package com.projectxo.app.feature.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.HabitRepository
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.data.shared.SharedHabitRepository
import com.projectxo.app.core.model.HabitMath
import com.projectxo.app.core.model.SharedError
import com.projectxo.app.core.model.SharedMath
import com.projectxo.app.core.model.SharedResult
import com.projectxo.app.core.model.SharedRoom
import com.projectxo.app.core.model.SharedView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Одна неделя в виде, который рисуют оба экрана: дни, мои отметки, отметки друга. */
data class SharedWeek(val days: List<LocalDate>, val mine: List<Boolean>, val friend: List<Boolean>) {
    val together: Int get() = mine.indices.count { mine[it] && friend[it] }
    val mineCount: Int get() = mine.count { it }
    val friendCount: Int get() = friend.count { it }
}

data class SharedUiState(
    val today: LocalDate,
    val loaded: Boolean = false,
    /** Комнаты больше нет (вышли или сервер её удалил): экран закрывается. */
    val gone: Boolean = false,
    val room: SharedRoom? = null,
    val friendNick: String? = null,
    val view: SharedView = SharedView.Intersection,
    val week: SharedWeek? = null,
    val streak: Int = 0,
    val best: Int = 0,
    val doneToday: Boolean = false,
    val friendDoneToday: Boolean = false,
    /** Код приглашения, если друг ещё не вошёл и код действует. */
    val code: String? = null,
    val syncing: Boolean = false,
    val syncError: SharedError? = null,
    val codeError: SharedError? = null,
)

/**
 * Экран общей привычки (Р79). Свои отметки пишутся в обычную привычку и сразу уходят на сервер,
 * отметки друга приходят при открытии экрана, раз в минуту, пока он открыт, и в фоне раз в полчаса.
 */
class SharedHabitViewModel(
    private val roomId: String,
    private val shared: SharedHabitRepository,
    private val habits: HabitRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val today = clock.today()
    private val local = MutableStateFlow(Local())

    private data class Local(val syncing: Boolean = false, val syncError: SharedError? = null, val codeError: SharedError? = null, val gone: Boolean = false)

    val state: StateFlow<SharedUiState> = combine(
        shared.state(roomId),
        habits.habits(),
        habits.checks(today.minusDays(HabitMath.HISTORY_DAYS.toLong()), today),
        settings.settings,
        local,
    ) { st, allHabits, checks, s, l ->
        val room = st?.room ?: return@combine SharedUiState(today, loaded = true, gone = true)
        val target = allHabits.firstOrNull { it.id == room.habitId }?.target ?: 1
        val mine = checks.filter { it.habitId == room.habitId && it.value >= target }.map { it.date }.toSet()
        val friendDays = st.daysOf(st.friend?.memberId)
        val days = SharedMath.lastDays(today)
        val codeValid = room.code != null && room.codeExpiresMs > clock.nowMs() && st.members.size < 2
        SharedUiState(
            today = today, loaded = true, gone = l.gone, room = room, friendNick = st.friend?.nick,
            view = s.wave2.sharedView,
            week = SharedWeek(days, SharedMath.flags(days, mine), SharedMath.flags(days, friendDays)),
            streak = SharedMath.streak(mine, friendDays, room.freqMask, today),
            best = SharedMath.bestStreak(mine, friendDays, room.freqMask, today),
            doneToday = today in mine, friendDoneToday = today in friendDays,
            code = if (codeValid) room.code else null,
            syncing = l.syncing, syncError = l.syncError, codeError = l.codeError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SharedUiState(today))

    init {
        refresh()
    }

    /** Отправить свои отметки и забрать чужие. Ошибка сети не страшна: показываем последнее сохранённое. */
    fun refresh() {
        if (local.value.syncing) return
        viewModelScope.launch {
            local.update { it.copy(syncing = true) }
            val r = shared.sync(roomId, today)
            local.update {
                it.copy(
                    syncing = false,
                    syncError = (r as? SharedResult.Fail)?.error?.takeIf { e -> e != SharedError.Gone },
                    gone = it.gone || (r as? SharedResult.Fail)?.error == SharedError.Gone,
                )
            }
        }
    }

    /** Отметить или снять сегодняшний день. */
    fun toggleToday() {
        val room = state.value.room ?: return
        val was = state.value.doneToday
        appScope.launch {
            val target = habits.habits().first().firstOrNull { it.id == room.habitId }?.target ?: 1
            habits.setCheck(room.habitId, today, if (was) 0 else target)
            shared.syncHabit(room.habitId, today)
        }
    }

    fun setView(v: SharedView) = settings.update { it.copy(wave2 = it.wave2.copy(sharedView = v)) }

    fun newCode() {
        viewModelScope.launch {
            local.update { it.copy(codeError = null) }
            val r = shared.newCode(roomId)
            local.update { it.copy(codeError = (r as? SharedResult.Fail)?.error, gone = it.gone || (r as? SharedResult.Fail)?.error == SharedError.Gone) }
        }
    }

    /** Выйти из общей привычки: на телефоне она остаётся обычной. */
    fun leave() {
        appScope.launch {
            shared.leave(roomId)
            local.update { it.copy(gone = true) }
        }
    }
}
