package com.projectxo.app.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.projectxo.app.core.data.BreathingRepository
import com.projectxo.app.core.data.BreathingSession
import com.projectxo.app.core.data.FastingRepository
import com.projectxo.app.core.data.FastingSession
import com.projectxo.app.core.data.HabitRepository
import com.projectxo.app.core.data.SleepNight
import com.projectxo.app.core.data.SleepRepository
import com.projectxo.app.core.data.WorkoutProgram
import com.projectxo.app.core.data.WorkoutRepository
import com.projectxo.app.core.data.WorkoutSessionRecord
import com.projectxo.app.core.model.BreathRhythm
import com.projectxo.app.core.model.Habit
import com.projectxo.app.core.model.HabitCheck
import com.projectxo.app.core.model.HabitFreq
import com.projectxo.app.core.model.HabitKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ——— Сон ———

@Entity(tableName = "sleep_night")
data class SleepNightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long?,
    val rate: Int?,
)

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_night WHERE endMs IS NOT NULL ORDER BY endMs DESC LIMIT :limit")
    fun nights(limit: Int): Flow<List<SleepNightEntity>>

    @Query("SELECT * FROM sleep_night WHERE endMs IS NULL ORDER BY startMs DESC LIMIT 1")
    fun current(): Flow<SleepNightEntity?>

    @Query("SELECT * FROM sleep_night WHERE endMs IS NOT NULL AND endMs >= :fromMs AND endMs < :toMs")
    fun endedBetween(fromMs: Long, toMs: Long): Flow<List<SleepNightEntity>>

    @Insert
    suspend fun insert(e: SleepNightEntity): Long

    @Query("UPDATE sleep_night SET endMs = :endMs WHERE id = :id")
    suspend fun finish(id: Long, endMs: Long)

    @Query("UPDATE sleep_night SET startMs = :startMs WHERE id = :id")
    suspend fun setStart(id: Long, startMs: Long)

    @Query("UPDATE sleep_night SET rate = :rate WHERE id = :id")
    suspend fun setRate(id: Long, rate: Int?)

    @Query("DELETE FROM sleep_night WHERE id = :id")
    suspend fun delete(id: Long)
}

class RoomSleepRepository(private val dao: SleepDao) : SleepRepository {
    private fun SleepNightEntity.toModel() = SleepNight(id, startMs, endMs, rate)

    override fun nights(limit: Int) = dao.nights(limit).map { l -> l.map { it.toModel() } }
    override fun current() = dao.current().map { it?.toModel() }

    override fun minutesByDay(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> {
        val zone = ZoneId.systemDefault()
        return dao.endedBetween(from.atStartOfDay(zone).toInstant().toEpochMilli(), to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            .map { list ->
                list.groupBy { Instant.ofEpochMilli(it.endMs!!).atZone(zone).toLocalDate() }
                    .mapValues { (_, v) -> v.sumOf { ((it.endMs!! - it.startMs) / 60_000L).toInt() } }
            }
    }

    override suspend fun start(startMs: Long) = dao.insert(SleepNightEntity(startMs = startMs, endMs = null, rate = null))
    override suspend fun finish(id: Long, endMs: Long) = dao.finish(id, endMs)
    override suspend fun setStart(id: Long, startMs: Long) = dao.setStart(id, startMs)
    override suspend fun setRate(id: Long, rate: Int?) = dao.setRate(id, rate)
    override suspend fun delete(id: Long) = dao.delete(id)
}

// ——— Голодание ———

@Entity(tableName = "fasting_session")
data class FastingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long?,
    val planHours: Int,
)

@Dao
interface FastingDao {
    @Query("SELECT * FROM fasting_session WHERE endMs IS NULL ORDER BY startMs DESC LIMIT 1")
    fun current(): Flow<FastingEntity?>

    @Query("SELECT * FROM fasting_session WHERE endMs IS NOT NULL ORDER BY endMs DESC LIMIT 1")
    fun last(): Flow<FastingEntity?>

    @Query("SELECT * FROM fasting_session WHERE endMs IS NOT NULL ORDER BY endMs DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<FastingEntity>>

    @Query("SELECT * FROM fasting_session WHERE endMs IS NULL OR endMs >= :fromMs")
    fun relevantSince(fromMs: Long): Flow<List<FastingEntity>>

    @Insert
    suspend fun insert(e: FastingEntity): Long

    @Query("UPDATE fasting_session SET endMs = :endMs WHERE id = :id")
    suspend fun finish(id: Long, endMs: Long)

    @Query("UPDATE fasting_session SET startMs = :startMs WHERE id = :id")
    suspend fun setStart(id: Long, startMs: Long)

    @Query("UPDATE fasting_session SET planHours = :plan WHERE id = :id")
    suspend fun setPlan(id: Long, plan: Int)
}

class RoomFastingRepository(private val dao: FastingDao) : FastingRepository {
    private fun FastingEntity.toModel() = FastingSession(id, startMs, endMs, planHours)

    override fun current() = dao.current().map { it?.toModel() }
    override fun last() = dao.last().map { it?.toModel() }
    override fun recent(limit: Int) = dao.recent(limit).map { l -> l.map { it.toModel() } }

    override fun minutesByDay(from: LocalDate, to: LocalDate, nowMs: Long): Flow<Map<LocalDate, Int>> {
        val zone = ZoneId.systemDefault()
        return dao.relevantSince(from.atStartOfDay(zone).toInstant().toEpochMilli()).map { list ->
            list.groupBy { Instant.ofEpochMilli(it.endMs ?: nowMs).atZone(zone).toLocalDate() }
                .filterKeys { !it.isBefore(from) && !it.isAfter(to) }
                .mapValues { (_, v) -> v.sumOf { (((it.endMs ?: nowMs) - it.startMs) / 60_000L).toInt() } }
        }
    }

    override suspend fun start(startMs: Long, planHours: Int) = dao.insert(FastingEntity(startMs = startMs, endMs = null, planHours = planHours))
    override suspend fun finish(id: Long, endMs: Long) = dao.finish(id, endMs)
    override suspend fun setStart(id: Long, startMs: Long) = dao.setStart(id, startMs)
    override suspend fun setPlan(id: Long, planHours: Int) = dao.setPlan(id, planHours)
}

// ——— Дыхание ———

@Entity(tableName = "breathing_session", indices = [Index("localDate")])
data class BreathingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val localDate: String,
    val seconds: Int,
    val rhythm: String,
    val cycles: Int,
)

@Dao
interface BreathingDao {
    @Query("SELECT * FROM breathing_session WHERE localDate = :date ORDER BY ts DESC")
    fun forDate(date: String): Flow<List<BreathingEntity>>

    @Query("SELECT localDate AS date, SUM(seconds) AS total FROM breathing_session WHERE localDate BETWEEN :from AND :to GROUP BY localDate")
    fun secondsBetween(from: String, to: String): Flow<List<DateTotal>>

    @Insert
    suspend fun insert(e: BreathingEntity): Long
}

class RoomBreathingRepository(private val dao: BreathingDao) : BreathingRepository {
    override fun sessionsFor(date: LocalDate) = dao.forDate(date.toString()).map { l ->
        l.map { BreathingSession(it.id, it.ts, LocalDate.parse(it.localDate), it.seconds, BreathRhythm.fromId(it.rhythm), it.cycles) }
    }

    override fun secondsByDay(from: LocalDate, to: LocalDate) = dao.secondsBetween(from.toString(), to.toString()).map { it.toDateMap() }

    override suspend fun add(timestampMs: Long, date: LocalDate, seconds: Int, rhythm: BreathRhythm, cycles: Int) =
        dao.insert(BreathingEntity(ts = timestampMs, localDate = date.toString(), seconds = seconds, rhythm = rhythm.id, cycles = cycles))
}

// ——— Привычки ———

@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** daily, week или days. */
    val freqType: String,
    val freqN: Int,
    val daysMask: Int,
    val reminderMin: Int?,
    val target: Int,
    val sortOrder: Int,
    /** habit или workout. */
    val kind: String,
)

@Entity(tableName = "habit_check", primaryKeys = ["habitId", "localDate"])
data class HabitCheckEntity(
    val habitId: Long,
    val localDate: String,
    val value: Int,
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit ORDER BY sortOrder, id")
    fun habits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_check WHERE localDate BETWEEN :from AND :to")
    fun checks(from: String, to: String): Flow<List<HabitCheckEntity>>

    @Query("SELECT * FROM habit_check WHERE habitId = :id")
    suspend fun checksOf(id: Long): List<HabitCheckEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM habit")
    suspend fun maxOrder(): Int

    @Insert
    suspend fun insert(e: HabitEntity): Long

    @Update
    suspend fun update(e: HabitEntity)

    @Query("DELETE FROM habit WHERE id = :id")
    suspend fun deleteHabit(id: Long)

    @Query("DELETE FROM habit_check WHERE habitId = :id")
    suspend fun deleteChecks(id: Long)

    @Upsert
    suspend fun upsertCheck(e: HabitCheckEntity)

    @Query("DELETE FROM habit_check WHERE habitId = :id AND localDate = :date")
    suspend fun deleteCheck(id: Long, date: String)

    @Upsert
    suspend fun upsertHabit(e: HabitEntity)

    @Upsert
    suspend fun upsertChecks(list: List<HabitCheckEntity>)

    @Transaction
    suspend fun delete(id: Long) {
        deleteChecks(id)
        deleteHabit(id)
    }
}

class RoomHabitRepository(private val dao: HabitDao) : HabitRepository {
    private fun HabitEntity.toModel() = Habit(
        id = id, name = name,
        freq = when (freqType) {
            "week" -> HabitFreq.PerWeek(freqN.coerceAtLeast(1))
            "days" -> HabitFreq.Days(daysMask)
            else -> HabitFreq.Daily
        },
        reminderMin = reminderMin, target = target.coerceAtLeast(1), sortOrder = sortOrder,
        kind = if (kind == "workout") HabitKind.Workout else HabitKind.Habit,
    )

    private fun Habit.toEntity(newId: Long = id) = HabitEntity(
        id = newId, name = name,
        freqType = when (freq) { HabitFreq.Daily -> "daily"; is HabitFreq.PerWeek -> "week"; is HabitFreq.Days -> "days" },
        freqN = (freq as? HabitFreq.PerWeek)?.n ?: 0,
        daysMask = (freq as? HabitFreq.Days)?.mask ?: 0,
        reminderMin = reminderMin, target = target, sortOrder = sortOrder,
        kind = if (kind == HabitKind.Workout) "workout" else "habit",
    )

    override fun habits() = dao.habits().map { l -> l.map { it.toModel() } }

    override fun checks(from: LocalDate, to: LocalDate) = dao.checks(from.toString(), to.toString()).map { l ->
        l.map { HabitCheck(it.habitId, LocalDate.parse(it.localDate), it.value) }
    }

    override suspend fun add(habit: Habit): Long = dao.insert(habit.copy(sortOrder = dao.maxOrder() + 1).toEntity(newId = 0))
    override suspend fun update(habit: Habit) = dao.update(habit.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun restore(habit: Habit, checks: List<HabitCheck>) {
        dao.upsertHabit(habit.toEntity())
        dao.upsertChecks(checks.map { HabitCheckEntity(it.habitId, it.date.toString(), it.value) })
    }

    override suspend fun setCheck(habitId: Long, date: LocalDate, value: Int) {
        if (value <= 0) dao.deleteCheck(habitId, date.toString()) else dao.upsertCheck(HabitCheckEntity(habitId, date.toString(), value))
    }

    override suspend fun checksOf(habitId: Long) = dao.checksOf(habitId).map { HabitCheck(it.habitId, LocalDate.parse(it.localDate), it.value) }
}

// ——— Тренировки ———

@Entity(tableName = "workout_program")
data class WorkoutProgramEntity(
    @PrimaryKey val exercise: String,
    val level: Int,
    val daysMask: Int,
)

@Entity(tableName = "workout_session", indices = [Index("localDate")])
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localDate: String,
    val exercise: String,
    val level: Int,
    /** Числа через запятую. */
    val plan: String,
    val actual: String,
    val rating: Int?,
    val ts: Long,
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_program")
    fun programs(): Flow<List<WorkoutProgramEntity>>

    @Query("SELECT * FROM workout_session ORDER BY ts DESC LIMIT :limit")
    fun sessions(limit: Int): Flow<List<WorkoutSessionEntity>>

    @Upsert
    suspend fun saveProgram(e: WorkoutProgramEntity)

    @Insert
    suspend fun insertSession(e: WorkoutSessionEntity): Long
}

class RoomWorkoutRepository(private val dao: WorkoutDao) : WorkoutRepository {
    private fun csv(l: List<Int>) = l.joinToString(",")
    private fun ints(s: String) = s.split(',').mapNotNull { it.toIntOrNull() }

    override fun programs() = dao.programs().map { l -> l.map { WorkoutProgram(it.exercise, it.level, it.daysMask) } }

    override fun sessions(limit: Int) = dao.sessions(limit).map { l ->
        l.map { WorkoutSessionRecord(it.id, LocalDate.parse(it.localDate), it.exercise, it.level, ints(it.plan), ints(it.actual), it.rating, it.ts) }
    }

    override suspend fun saveProgram(p: WorkoutProgram) = dao.saveProgram(WorkoutProgramEntity(p.exercise, p.level, p.daysMask))

    override suspend fun addSession(s: WorkoutSessionRecord) =
        dao.insertSession(WorkoutSessionEntity(localDate = s.date.toString(), exercise = s.exercise, level = s.level, plan = csv(s.plan), actual = csv(s.actual), rating = s.rating, ts = s.timestampMs))
}
