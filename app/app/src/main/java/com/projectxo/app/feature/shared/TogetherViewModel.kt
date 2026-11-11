package com.projectxo.app.feature.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.HabitRepository
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.data.shared.SharedHabitRepository
import com.projectxo.app.core.model.Habit
import com.projectxo.app.core.model.HabitKind
import com.projectxo.app.core.model.SharedError
import com.projectxo.app.core.model.SharedMath
import com.projectxo.app.core.model.SharedResult
import com.projectxo.app.core.model.SharedRoom
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Строка раздела «Вместе» на экране привычек. */
data class SharedBrief(val roomId: String, val title: String, val friendNick: String?, val code: String?)

/** Код приглашения, который нужно показать после создания комнаты. */
data class InviteInfo(val roomId: String, val title: String, val code: String)

data class TogetherState(
    val available: Boolean = false,
    val rooms: List<SharedBrief> = emptyList(),
    /** Привычки, которые ещё не общие и подходят: обычные, не тренировки. */
    val candidates: List<Habit> = emptyList(),
    val nick: String = "",
    val busy: Boolean = false,
    val error: SharedError? = null,
    val invite: InviteInfo? = null,
    /** Успешный вход по коду: открыть экран этой комнаты. Сбрасывается через [TogetherViewModel.opened]. */
    val openRoom: String? = null,
)

/** Раздел «Вместе» на экране привычек: позвать друга, ввести код, список общих привычек (Р79). */
@OptIn(ExperimentalCoroutinesApi::class)
class TogetherViewModel(
    private val shared: SharedHabitRepository,
    private val habits: HabitRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val ui = MutableStateFlow(Ui())

    private data class Ui(val reachable: Boolean = false, val busy: Boolean = false, val error: SharedError? = null, val invite: InviteInfo? = null, val openRoom: String? = null)

    private val briefs = shared.rooms().flatMapLatest { rooms ->
        if (rooms.isEmpty()) flowOf(emptyList<SharedBrief>()) else combine(rooms.map { r -> shared.state(r.roomId) }) { states ->
            rooms.mapIndexed { i, r -> SharedBrief(r.roomId, r.title, states[i]?.friend?.nick, r.code) }
        }
    }

    init {
        // Раздел «Вместе» появляется, только когда сервер отвечает.
        viewModelScope.launch { val up = shared.reachable(); ui.update { it.copy(reachable = up) } }
    }

    val state: StateFlow<TogetherState> = combine(briefs, shared.rooms(), habits.habits(), settings.settings, ui) { b, rooms, all, s, u ->
        val sharedIds = rooms.map { it.habitId }.toSet()
        TogetherState(
            available = shared.available && (u.reachable || rooms.isNotEmpty()), rooms = b,
            candidates = all.filter { it.kind == HabitKind.Habit && it.id !in sharedIds },
            nick = s.wave2.sharedNick, busy = u.busy, error = u.error, invite = u.invite, openRoom = u.openRoom,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TogetherState(available = shared.available))

    private fun rememberNick(nick: String) {
        val clean = SharedMath.cleanNick(nick)
        if (clean.isNotEmpty()) settings.update { it.copy(wave2 = it.wave2.copy(sharedNick = clean)) }
    }

    /** Сделать привычку общей и получить код для друга. */
    fun create(habit: Habit, nick: String) {
        if (ui.value.busy) return
        viewModelScope.launch {
            ui.update { it.copy(busy = true, error = null) }
            when (val r = shared.create(habit, nick, clock.today())) {
                is SharedResult.Fail -> ui.update { it.copy(busy = false, error = r.error) }
                is SharedResult.Ok -> {
                    rememberNick(nick)
                    val room: SharedRoom = r.value
                    ui.update { it.copy(busy = false, invite = room.code?.let { c -> InviteInfo(room.roomId, room.title, c) }) }
                }
            }
        }
    }

    /** Войти по коду друга. */
    fun join(code: String, nick: String) {
        if (ui.value.busy) return
        viewModelScope.launch {
            ui.update { it.copy(busy = true, error = null) }
            when (val r = shared.join(code, nick, clock.today())) {
                is SharedResult.Fail -> ui.update { it.copy(busy = false, error = r.error) }
                is SharedResult.Ok -> {
                    rememberNick(nick)
                    ui.update { it.copy(busy = false, openRoom = r.value.roomId) }
                }
            }
        }
    }

    fun dismissInvite() = ui.update { it.copy(invite = null) }

    fun clearError() = ui.update { it.copy(error = null) }

    fun opened() = ui.update { it.copy(openRoom = null) }

    /** Показать код ещё раз (код привычки, друг пока не вошёл). */
    fun showInvite(brief: SharedBrief) {
        brief.code?.let { c -> ui.update { it.copy(invite = InviteInfo(brief.roomId, brief.title, c)) } }
    }
}
