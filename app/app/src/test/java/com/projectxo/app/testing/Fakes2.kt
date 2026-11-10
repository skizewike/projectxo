package com.projectxo.app.testing

import com.projectxo.app.core.data.FastingRepository
import com.projectxo.app.core.data.FastingSession
import com.projectxo.app.core.data.HabitRepository
import com.projectxo.app.core.data.SleepNight
import com.projectxo.app.core.data.SleepRepository
import com.projectxo.app.core.data.WorkoutProgram
import com.projectxo.app.core.data.WorkoutRepository
import com.projectxo.app.core.data.WorkoutSessionRecord
import com.projectxo.app.core.model.Habit
import com.projectxo.app.core.model.HabitCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeSleepRepository : SleepRepository {
    val all = MutableStateFlow<List<SleepNight>>(emptyList())
    private var nextId = 1L

    override fun nights(limit: Int): Flow<List<SleepNight>> =
        all.map { l -> l.filter { it.endMs != null }.sortedByDescending { it.startMs }.take(limit) }

    override fun current(): Flow<SleepNight?> = all.map { l -> l.firstOrNull { it.endMs == null } }

    override fun minutesByDay(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> = all.map { emptyMap() }

    override suspend fun start(startMs: Long): Long {
        val id = nextId++
        all.value = all.value + SleepNight(id, startMs, null, null)
        return id
    }

    override suspend fun finish(id: Long, endMs: Long) = change(id) { it.copy(endMs = endMs) }
    override suspend fun setStart(id: Long, startMs: Long) = change(id) { it.copy(startMs = startMs) }
    override suspend fun setRate(id: Long, rate: Int?) = change(id) { it.copy(rate = rate) }
    override suspend fun delete(id: Long) {
        all.value = all.value.filter { it.id != id }
    }

    private fun change(id: Long, f: (SleepNight) -> SleepNight) {
        all.value = all.value.map { if (it.id == id) f(it) else it }
    }
}

class FakeFastingRepository : FastingRepository {
    val all = MutableStateFlow<List<FastingSession>>(emptyList())
    private var nextId = 1L

    override fun current(): Flow<FastingSession?> = all.map { l -> l.firstOrNull { it.endMs == null } }
    override fun last(): Flow<FastingSession?> = all.map { l -> l.filter { it.endMs != null }.maxByOrNull { it.startMs } }
    override fun recent(limit: Int): Flow<List<FastingSession>> =
        all.map { l -> l.filter { it.endMs != null }.sortedByDescending { it.startMs }.take(limit) }

    override fun minutesByDay(from: LocalDate, to: LocalDate, nowMs: Long): Flow<Map<LocalDate, Int>> = all.map { emptyMap() }

    override suspend fun start(startMs: Long, planHours: Int): Long {
        val id = nextId++
        all.value = all.value + FastingSession(id, startMs, null, planHours)
        return id
    }

    override suspend fun finish(id: Long, endMs: Long) = change(id) { it.copy(endMs = endMs) }
    override suspend fun setStart(id: Long, startMs: Long) = change(id) { it.copy(startMs = startMs) }
    override suspend fun setPlan(id: Long, planHours: Int) = change(id) { it.copy(planHours = planHours) }

    private fun change(id: Long, f: (FastingSession) -> FastingSession) {
        all.value = all.value.map { if (it.id == id) f(it) else it }
    }
}

class FakeHabitRepository : HabitRepository {
    val habitsState = MutableStateFlow<List<Habit>>(emptyList())
    val checksState = MutableStateFlow<List<HabitCheck>>(emptyList())
    private var nextId = 1L

    override fun habits(): Flow<List<Habit>> = habitsState
    override fun checks(from: LocalDate, to: LocalDate): Flow<List<HabitCheck>> =
        checksState.map { l -> l.filter { it.date in from..to } }

    override suspend fun add(habit: Habit): Long {
        val id = nextId++
        habitsState.value = habitsState.value + habit.copy(id = id)
        return id
    }

    override suspend fun update(habit: Habit) {
        habitsState.value = habitsState.value.map { if (it.id == habit.id) habit else it }
    }

    override suspend fun delete(id: Long) {
        habitsState.value = habitsState.value.filter { it.id != id }
        checksState.value = checksState.value.filter { it.habitId != id }
    }

    override suspend fun restore(habit: Habit, checks: List<HabitCheck>) {
        habitsState.value = habitsState.value + habit
        checksState.value = checksState.value + checks
    }

    override suspend fun setCheck(habitId: Long, date: LocalDate, value: Int) {
        val rest = checksState.value.filterNot { it.habitId == habitId && it.date == date }
        checksState.value = if (value <= 0) rest else rest + HabitCheck(habitId, date, value)
    }

    override suspend fun checksOf(habitId: Long): List<HabitCheck> = checksState.value.filter { it.habitId == habitId }
}

class FakeWorkoutRepository : WorkoutRepository {
    val programsState = MutableStateFlow<List<WorkoutProgram>>(emptyList())
    val sessionsState = MutableStateFlow<List<WorkoutSessionRecord>>(emptyList())
    private var nextId = 1L

    override fun programs(): Flow<List<WorkoutProgram>> = programsState
    override fun sessions(limit: Int): Flow<List<WorkoutSessionRecord>> =
        sessionsState.map { l -> l.sortedByDescending { it.timestampMs }.take(limit) }

    override suspend fun saveProgram(p: WorkoutProgram) {
        programsState.value = programsState.value.filterNot { it.exercise == p.exercise } + p
    }

    override suspend fun addSession(s: WorkoutSessionRecord): Long {
        val id = nextId++
        sessionsState.value = sessionsState.value + s.copy(id = id)
        return id
    }
}
