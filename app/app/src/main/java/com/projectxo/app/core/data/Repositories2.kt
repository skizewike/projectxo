package com.projectxo.app.core.data

import com.projectxo.app.core.model.BreathRhythm
import com.projectxo.app.core.model.Habit
import com.projectxo.app.core.model.HabitCheck
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// Контракты хранилища модулей волны 2. Как и в Repositories.kt: чистый Kotlin, без android.*.

/** Ночь сна. [endMs] == null, пока человек спит. */
data class SleepNight(val id: Long, val startMs: Long, val endMs: Long?, val rate: Int?)

interface SleepRepository {
    /** Завершённые ночи, новые первыми. */
    fun nights(limit: Int): Flow<List<SleepNight>>

    /** Ночь, которая идёт сейчас, или null. */
    fun current(): Flow<SleepNight?>

    /** Минуты сна по дням: ночь относится ко дню подъёма. */
    fun minutesByDay(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>

    suspend fun start(startMs: Long): Long
    suspend fun finish(id: Long, endMs: Long)
    suspend fun setStart(id: Long, startMs: Long)
    suspend fun setRate(id: Long, rate: Int?)
    suspend fun delete(id: Long)
}

/** Голодание. [endMs] == null, пока идёт. */
data class FastingSession(val id: Long, val startMs: Long, val endMs: Long?, val planHours: Int)

interface FastingRepository {
    fun current(): Flow<FastingSession?>

    /** Последнее законченное голодание или null. */
    fun last(): Flow<FastingSession?>

    fun recent(limit: Int): Flow<List<FastingSession>>

    /** Минуты голодания по дням (день конца; идущее считается днём «сейчас»). */
    fun minutesByDay(from: LocalDate, to: LocalDate, nowMs: Long): Flow<Map<LocalDate, Int>>

    suspend fun start(startMs: Long, planHours: Int): Long
    suspend fun finish(id: Long, endMs: Long)
    suspend fun setStart(id: Long, startMs: Long)
    suspend fun setPlan(id: Long, planHours: Int)
}

data class BreathingSession(
    val id: Long,
    val timestampMs: Long,
    val date: LocalDate,
    val seconds: Int,
    val rhythm: BreathRhythm,
    val cycles: Int,
)

interface BreathingRepository {
    fun sessionsFor(date: LocalDate): Flow<List<BreathingSession>>
    fun secondsByDay(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>
    suspend fun add(timestampMs: Long, date: LocalDate, seconds: Int, rhythm: BreathRhythm, cycles: Int): Long
}

interface HabitRepository {
    fun habits(): Flow<List<Habit>>

    /** Отметки за период (включительно). */
    fun checks(from: LocalDate, to: LocalDate): Flow<List<HabitCheck>>

    suspend fun add(habit: Habit): Long
    suspend fun update(habit: Habit)

    /** Удалить привычку и все её отметки. */
    suspend fun delete(id: Long)

    /** Вернуть удалённую привычку с её отметками (кнопка «Вернуть»). */
    suspend fun restore(habit: Habit, checks: List<HabitCheck>)

    /** Поставить значение отметки за день; 0 — снять. */
    suspend fun setCheck(habitId: Long, date: LocalDate, value: Int)

    /** Все отметки одной привычки (для «Вернуть» после удаления). */
    suspend fun checksOf(habitId: Long): List<HabitCheck>
}

/** Подход тренировки. */
data class WorkoutSessionRecord(
    val id: Long,
    val date: LocalDate,
    val exercise: String,
    val level: Int,
    val plan: List<Int>,
    val actual: List<Int>,
    /** 0 легко, 1 нормально, 2 тяжело. */
    val rating: Int?,
    val timestampMs: Long,
)

/** Программа упражнения: уровень M и дни недели. */
data class WorkoutProgram(val exercise: String, val level: Int, val daysMask: Int)

interface WorkoutRepository {
    fun programs(): Flow<List<WorkoutProgram>>
    fun sessions(limit: Int): Flow<List<WorkoutSessionRecord>>
    suspend fun saveProgram(p: WorkoutProgram)
    suspend fun addSession(s: WorkoutSessionRecord): Long
}
