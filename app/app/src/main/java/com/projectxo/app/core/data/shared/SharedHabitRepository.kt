package com.projectxo.app.core.data.shared

import com.projectxo.app.core.model.Habit
import com.projectxo.app.core.model.SharedResult
import com.projectxo.app.core.model.SharedRoom
import com.projectxo.app.core.model.SharedState
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Общие привычки (Р79). Свои отметки живут в обычной привычке на телефоне, чужие приходят с сервера.
 * Без сети всё продолжает работать: отметки уйдут при следующей синхронизации.
 */
interface SharedHabitRepository {
    /** Есть ли адрес сервера в этой сборке. Нет — раздел «Вместе» не показывается. */
    val available: Boolean

    /** Отвечает ли сервер прямо сейчас. Пока нет, раздел «Вместе» скрыт: у человека не должно быть кнопок, которые не работают. */
    suspend fun reachable(): Boolean

    fun rooms(): Flow<List<SharedRoom>>

    fun state(roomId: String): Flow<SharedState?>

    /** Сделать привычку [habit] общей: создаёт комнату на сервере и возвращает её с кодом приглашения. */
    suspend fun create(habit: Habit, nick: String, today: LocalDate): SharedResult<SharedRoom>

    /** Войти по коду друга: на телефоне появляется своя привычка с тем же названием и расписанием. */
    suspend fun join(code: String, nick: String, today: LocalDate): SharedResult<SharedRoom>

    /** Отправить свои новые отметки и забрать чужие. */
    suspend fun sync(roomId: String, today: LocalDate): SharedResult<Unit>

    suspend fun syncAll(today: LocalDate)

    /** Если привычка общая, отправить отметки сразу; иначе ничего. */
    suspend fun syncHabit(habitId: Long, today: LocalDate)

    /** Новый код приглашения вместо просроченного. */
    suspend fun newCode(roomId: String): SharedResult<String>

    /** Выйти из общей привычки: с сервера уходят мои отметки, на телефоне привычка остаётся обычной. */
    suspend fun leave(roomId: String)

    /** Привычку удалили: если она была общей, выйти из комнаты. */
    suspend fun habitDeleted(habitId: Long)
}
