package com.projectxo.app.testing

import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.data.GoalsRepository
import com.projectxo.app.core.data.FoodDraft
import com.projectxo.app.core.data.FoodEntry
import com.projectxo.app.core.data.FoodRepository
import com.projectxo.app.core.data.FoodTotals
import com.projectxo.app.core.data.WaterEntry
import com.projectxo.app.core.data.WaterRepository
import com.projectxo.app.core.model.DefaultGoals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeWaterRepository : WaterRepository {
    val all = MutableStateFlow<List<WaterEntry>>(emptyList())
    private var nextId = 1L

    override fun entriesFor(date: LocalDate): Flow<List<WaterEntry>> =
        all.map { list -> list.filter { it.localDate == date }.sortedByDescending { it.id } }

    override fun totalFor(date: LocalDate): Flow<Int> =
        all.map { list -> list.filter { it.localDate == date }.sumOf { it.ml } }

    override suspend fun add(ml: Int, timestampMs: Long, date: LocalDate): Long {
        val id = nextId++
        all.value = all.value + WaterEntry(id, timestampMs, date, ml)
        return id
    }

    override suspend fun delete(id: Long) {
        all.value = all.value.filterNot { it.id == id }
    }

    override fun totalsBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        all.map { list -> list.filter { it.localDate in from..to }.groupBy { it.localDate }.mapValues { (_, v) -> v.sumOf { it.ml } } }
}

class FakeFoodRepository : FoodRepository {
    val all = MutableStateFlow<List<FoodEntry>>(emptyList())
    private var nextId = 1L

    override fun entriesFor(date: LocalDate): Flow<List<FoodEntry>> =
        all.map { list -> list.filter { it.localDate == date }.sortedByDescending { it.id } }

    override fun totalsFor(date: LocalDate): Flow<FoodTotals> = all.map { list ->
        val day = list.filter { it.localDate == date }
        FoodTotals(day.sumOf { it.kcal }, day.sumOf { it.proteinG }, day.sumOf { it.fatG }, day.sumOf { it.carbsG })
    }

    override suspend fun add(draft: FoodDraft, timestampMs: Long, date: LocalDate): Long {
        val id = nextId++
        all.value = all.value + FoodEntry(id, timestampMs, date, draft.name, draft.kcal, draft.proteinG, draft.fatG, draft.carbsG)
        return id
    }

    override suspend fun delete(id: Long) {
        all.value = all.value.filterNot { it.id == id }
    }

    override fun kcalBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        all.map { list -> list.filter { it.localDate in from..to }.groupBy { it.localDate }.mapValues { (_, v) -> v.sumOf { it.kcal } } }
}

class FakeGoalsRepository : GoalsRepository {
    private val goals = MutableStateFlow(
        mapOf(
            GoalType.WaterMl to DefaultGoals.WATER_ML,
            GoalType.FoodKcal to DefaultGoals.FOOD_KCAL,
            GoalType.ProteinG to DefaultGoals.PROTEIN_G,
            GoalType.FatG to DefaultGoals.FAT_G,
            GoalType.CarbsG to DefaultGoals.CARBS_G,
            GoalType.Steps to DefaultGoals.STEPS,
        ),
    )

    override fun goal(type: GoalType): Flow<Long> = goals.map { it.getValue(type) }

    override suspend fun setGoal(type: GoalType, target: Long) {
        goals.value = goals.value + (type to target)
    }
}

class FakeClock(
    var nowMs: Long = 1_000_000L,
    var today: LocalDate = LocalDate.of(2026, 1, 15),
) : AppClock {
    override fun nowMs(): Long = nowMs
    override fun today(): LocalDate = today
}

class FakeStepsRepository : com.projectxo.app.core.data.StepsRepository {
    private val data = kotlinx.coroutines.flow.MutableStateFlow<Map<Pair<java.time.LocalDate, Int>, Int>>(emptyMap())
    private var reading: com.projectxo.app.core.data.SensorReading? = null
    override fun hoursFor(date: java.time.LocalDate) =
        data.map { m -> List(24) { h -> m[date to h] ?: 0 } }
    override fun totalFor(date: java.time.LocalDate) =
        data.map { m -> m.filterKeys { it.first == date }.values.sum() }
    override fun totalsBetween(from: java.time.LocalDate, to: java.time.LocalDate) =
        data.map { m -> m.entries.filter { it.key.first in from..to }.groupBy({ it.key.first }, { it.value }).mapValues { (_, v) -> v.sum() } }
    override suspend fun addSteps(parts: List<com.projectxo.app.core.data.HourSteps>) {
        data.value = data.value.toMutableMap().apply { parts.forEach { p -> merge(p.date to p.hour, p.steps, Int::plus) } }
    }
    override suspend fun removeSteps(parts: List<com.projectxo.app.core.data.HourSteps>) {
        data.value = data.value.toMutableMap().apply {
            parts.forEach { p ->
                val left = (this[p.date to p.hour] ?: 0) - p.steps
                if (left > 0) this[p.date to p.hour] = left else remove(p.date to p.hour)
            }
        }
    }
    override suspend fun lastReading() = reading
    override suspend fun saveReading(reading: com.projectxo.app.core.data.SensorReading) { this.reading = reading }
}

class FakeDayLogRepository : com.projectxo.app.core.data.DayLogRepository {
    val all = kotlinx.coroutines.flow.MutableStateFlow<Map<LocalDate, com.projectxo.app.core.data.DayLog>>(emptyMap())

    override fun observe(date: LocalDate) = all.map { it[date] }

    override fun observeBetween(from: LocalDate, to: LocalDate) =
        all.map { m -> m.filterKeys { it in from..to } }

    override suspend fun save(log: com.projectxo.app.core.data.DayLog) {
        all.value = if (log.isEmpty) all.value - log.date else all.value + (log.date to log)
    }
}
