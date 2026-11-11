package com.projectxo.app.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.HabitRepository
import com.projectxo.app.core.data.shared.Api
import com.projectxo.app.core.data.shared.Credentials
import com.projectxo.app.core.data.shared.RoomsApi
import com.projectxo.app.core.data.shared.SharedHabitRepository
import com.projectxo.app.core.model.Habit
import com.projectxo.app.core.model.SharedError
import com.projectxo.app.core.model.SharedMath
import com.projectxo.app.core.model.SharedMember
import com.projectxo.app.core.model.SharedResult
import com.projectxo.app.core.model.SharedRoom
import com.projectxo.app.core.model.SharedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

@Entity(tableName = "shared_room")
data class SharedRoomEntity(
    @PrimaryKey val roomId: String,
    val habitId: Long,
    val memberId: String,
    /** Секрет участника: нужен, чтобы писать и читать комнату. Лежит только в закрытой папке приложения. */
    val secret: String,
    val title: String,
    val freqMask: Int,
    val myNick: String,
    val code: String?,
    val codeExpires: Long,
    val lastSync: Long,
)

@Entity(tableName = "shared_member", primaryKeys = ["roomId", "memberId"])
data class SharedMemberEntity(val roomId: String, val memberId: String, val nick: String)

@Entity(tableName = "shared_mark", primaryKeys = ["roomId", "memberId", "localDate"])
data class SharedMarkEntity(val roomId: String, val memberId: String, val localDate: String)

@Dao
interface SharedDao {
    @Query("SELECT * FROM shared_room ORDER BY rowid")
    fun rooms(): Flow<List<SharedRoomEntity>>

    @Query("SELECT * FROM shared_room WHERE roomId = :id")
    fun room(id: String): Flow<SharedRoomEntity?>

    @Query("SELECT * FROM shared_room WHERE roomId = :id")
    suspend fun roomOnce(id: String): SharedRoomEntity?

    @Query("SELECT * FROM shared_room WHERE habitId = :habitId")
    suspend fun roomOfHabit(habitId: Long): SharedRoomEntity?

    @Query("SELECT * FROM shared_room")
    suspend fun roomsOnce(): List<SharedRoomEntity>

    @Query("SELECT * FROM shared_member WHERE roomId = :id ORDER BY rowid")
    fun members(id: String): Flow<List<SharedMemberEntity>>

    @Query("SELECT * FROM shared_mark WHERE roomId = :id")
    fun marks(id: String): Flow<List<SharedMarkEntity>>

    @Query("SELECT localDate FROM shared_mark WHERE roomId = :id AND memberId = :memberId")
    suspend fun markDaysOf(id: String, memberId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRoom(e: SharedRoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMembers(list: List<SharedMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMarks(list: List<SharedMarkEntity>)

    @Query("DELETE FROM shared_member WHERE roomId = :id")
    suspend fun clearMembers(id: String)

    @Query("DELETE FROM shared_mark WHERE roomId = :id")
    suspend fun clearMarks(id: String)

    @Query("DELETE FROM shared_room WHERE roomId = :id")
    suspend fun deleteRoomRow(id: String)

    @Query("UPDATE shared_room SET code = :code, codeExpires = :expires WHERE roomId = :id")
    suspend fun setCode(id: String, code: String?, expires: Long)

    @Query("UPDATE shared_room SET lastSync = :ms WHERE roomId = :id")
    suspend fun setSynced(id: String, ms: Long)

    @Query("UPDATE shared_room SET title = :title, freqMask = :mask WHERE roomId = :id")
    suspend fun setInfo(id: String, title: String, mask: Int)

    @Transaction
    suspend fun replaceState(id: String, members: List<SharedMemberEntity>, marks: List<SharedMarkEntity>) {
        clearMembers(id)
        clearMarks(id)
        putMembers(members)
        putMarks(marks)
    }

    @Transaction
    suspend fun deleteRoom(id: String) {
        clearMembers(id)
        clearMarks(id)
        deleteRoomRow(id)
    }
}

class DbSharedHabitRepository(
    private val dao: SharedDao,
    private val api: RoomsApi,
    private val habits: HabitRepository,
    private val clock: AppClock,
) : SharedHabitRepository {
    /** Синхронизации идут по очереди: экран, фон и отметка не должны писать одно и то же одновременно. */
    private val lock = Mutex()

    override val available: Boolean get() = api.configured

    private fun SharedRoomEntity.toModel() = SharedRoom(roomId, habitId, title, freqMask, memberId, myNick, code, codeExpires, lastSync)
    private fun SharedRoomEntity.creds() = Credentials(memberId, secret)

    override suspend fun reachable(): Boolean = api.ping()

    override fun rooms(): Flow<List<SharedRoom>> = dao.rooms().map { l -> l.map { it.toModel() } }

    override fun state(roomId: String): Flow<SharedState?> =
        combine(dao.room(roomId), dao.members(roomId), dao.marks(roomId)) { room, members, marks ->
            room ?: return@combine null
            SharedState(
                room.toModel(),
                members.map { SharedMember(it.memberId, it.nick, it.memberId == room.memberId) },
                marks.groupBy { it.memberId }.mapValues { (_, l) -> l.mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }.toSet() },
            )
        }

    override suspend fun create(habit: Habit, nick: String, today: LocalDate): SharedResult<SharedRoom> {
        val clean = SharedMath.cleanNick(nick)
        if (clean.isEmpty()) return SharedResult.Fail(SharedError.BadInput)
        if (dao.roomOfHabit(habit.id) != null) return SharedResult.Fail(SharedError.BadInput)
        val mask = SharedMath.maskOf(habit.freq)
        return when (val r = api.createRoom(habit.name, mask, clean)) {
            is Api.Err -> SharedResult.Fail(r.error)
            is Api.Ok -> {
                val c = r.value
                val e = SharedRoomEntity(c.roomId, habit.id, c.creds.memberId, c.creds.secret, habit.name, mask, clean, c.code, c.codeExpiresMs, 0L)
                dao.putRoom(e)
                dao.putMembers(listOf(SharedMemberEntity(c.roomId, c.creds.memberId, clean)))
                sync(c.roomId, today)
                SharedResult.Ok((dao.roomOnce(c.roomId) ?: e).toModel())
            }
        }
    }

    override suspend fun join(code: String, nick: String, today: LocalDate): SharedResult<SharedRoom> {
        val clean = SharedMath.cleanNick(nick)
        val normalized = SharedMath.normalizeCode(code)
        if (clean.isEmpty() || normalized.length != SharedMath.CODE_LENGTH) return SharedResult.Fail(SharedError.BadInput)
        if (dao.roomsOnce().any { it.code == normalized }) return SharedResult.Fail(SharedError.BadInput)
        return when (val r = api.join(normalized, clean)) {
            is Api.Err -> SharedResult.Fail(r.error)
            is Api.Ok -> {
                val j = r.value
                val habitId = habits.add(Habit(0, j.title, SharedMath.freqOf(j.freqMask), reminderMin = null))
                val e = SharedRoomEntity(j.roomId, habitId, j.creds.memberId, j.creds.secret, j.title, j.freqMask, clean, null, 0L, 0L)
                dao.putRoom(e)
                dao.putMembers(j.members.map { SharedMemberEntity(j.roomId, it.id, it.nick) })
                sync(j.roomId, today)
                SharedResult.Ok((dao.roomOnce(j.roomId) ?: e).toModel())
            }
        }
    }

    override suspend fun sync(roomId: String, today: LocalDate): SharedResult<Unit> = lock.withLock {
        val room = dao.roomOnce(roomId) ?: return@withLock SharedResult.Fail(SharedError.Gone)
        if (!api.configured) return@withLock SharedResult.Fail(SharedError.NotConfigured)
        val creds = room.creds()
        // 1. Свои отметки: что изменилось с прошлой отправки.
        val habit = habits.habits().first().firstOrNull { it.id == room.habitId }
        if (habit == null) {
            dao.deleteRoom(roomId)
            return@withLock SharedResult.Fail(SharedError.Gone)
        }
        val from = today.minusDays(SharedMath.SYNC_DAYS.toLong())
        val local = habits.checksOf(room.habitId).filter { it.value >= habit.target && !it.date.isBefore(from) && !it.date.isAfter(today) }.map { it.date }.toSet()
        val server = dao.markDaysOf(roomId, room.memberId).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.filter { !it.isBefore(from) }.toSet()
        for (d in (local - server).sorted() + (server - local).sorted()) {
            when (val r = api.putMark(roomId, creds, d, done = d in local)) {
                is Api.Err -> return@withLock fail(roomId, r.error)
                is Api.Ok -> Unit
            }
        }
        // 2. Чужие отметки и состав комнаты.
        when (val r = api.room(roomId, creds)) {
            is Api.Err -> fail(roomId, r.error)
            is Api.Ok -> {
                val d = r.value
                dao.replaceState(
                    roomId,
                    d.members.map { SharedMemberEntity(roomId, it.id, it.nick) },
                    d.marks.map { SharedMarkEntity(roomId, it.memberId, it.day.toString()) },
                )
                dao.setInfo(roomId, d.title, d.freqMask)
                if (d.members.size >= 2) dao.setCode(roomId, null, 0L)
                dao.setSynced(roomId, clock.nowMs())
                SharedResult.Ok(Unit)
            }
        }
    }

    /** Сервер не знает комнату или секрет: комнаты больше нет. На телефоне привычка остаётся обычной. */
    private suspend fun fail(roomId: String, error: SharedError): SharedResult<Unit> {
        if (error == SharedError.Gone) dao.deleteRoom(roomId)
        return SharedResult.Fail(error)
    }

    override suspend fun syncAll(today: LocalDate) {
        dao.roomsOnce().forEach { sync(it.roomId, today) }
    }

    override suspend fun syncHabit(habitId: Long, today: LocalDate) {
        val room = dao.roomOfHabit(habitId) ?: return
        sync(room.roomId, today)
    }

    override suspend fun newCode(roomId: String): SharedResult<String> {
        val room = dao.roomOnce(roomId) ?: return SharedResult.Fail(SharedError.Gone)
        return when (val r = api.newCode(roomId, room.creds())) {
            is Api.Err -> {
                if (r.error == SharedError.Gone) dao.deleteRoom(roomId)
                SharedResult.Fail(r.error)
            }
            is Api.Ok -> {
                dao.setCode(roomId, r.value.code, r.value.expiresMs)
                SharedResult.Ok(r.value.code)
            }
        }
    }

    override suspend fun leave(roomId: String) {
        val room = dao.roomOnce(roomId) ?: return
        // Если сети нет, комната на сервере доживёт до уборки; на телефоне выход сразу.
        api.leave(roomId, room.creds())
        dao.deleteRoom(roomId)
    }

    override suspend fun habitDeleted(habitId: Long) {
        dao.roomOfHabit(habitId)?.let { leave(it.roomId) }
    }
}
