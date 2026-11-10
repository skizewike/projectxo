package com.projectxo.app.core.steps

import com.projectxo.app.core.data.HourSteps
import com.projectxo.app.core.data.SensorReading
import com.projectxo.app.core.data.StepsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StepsMathTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val day = LocalDate.of(2026, 11, 9)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun firstReadingIsBaseline() {
        assertEquals(0, StepsMath.delta(null, 12_345))
    }

    @Test
    fun normalDelta() {
        assertEquals(500, StepsMath.delta(SensorReading(10_000, 0), 10_500))
    }

    @Test
    fun rebootCountsFromZero() {
        assertEquals(300, StepsMath.delta(SensorReading(10_000, 0), 300))
    }

    @Test
    fun implausibleJumpIsIgnored() {
        assertEquals(0, StepsMath.delta(SensorReading(0, 0), 1_000_000))
    }

    @Test
    fun withinOneHourGoesToThatHour() {
        val parts = StepsMath.distribute(400, at(14, 5), at(14, 40), zone)
        assertEquals(listOf(HourSteps(day, 14, 400)), parts)
    }

    @Test
    fun spreadsProportionallyAcrossHoursAndKeepsTotal() {
        // 12:30–14:30: полчаса в 12, час в 13, полчаса в 14 → 1:2:1
        val parts = StepsMath.distribute(1000, at(12, 30), at(14, 30), zone)
        assertEquals(listOf(12, 13, 14), parts.map { it.hour })
        assertEquals(1000, parts.sumOf { it.steps })
        assertEquals(250, parts[0].steps)
        assertEquals(500, parts[1].steps)
        assertEquals(250, parts[2].steps)
    }

    @Test
    fun crossesMidnightIntoNextDay() {
        val parts = StepsMath.distribute(600, at(23, 30), day.plusDays(1).atTime(0, 30).atZone(zone).toInstant().toEpochMilli(), zone)
        assertEquals(day, parts.first().date)
        assertEquals(23, parts.first().hour)
        assertEquals(day.plusDays(1), parts.last().date)
        assertEquals(0, parts.last().hour)
        assertEquals(600, parts.sumOf { it.steps })
    }

    @Test
    fun veryLongGapUsesOnlyLastDay() {
        val parts = StepsMath.distribute(2400, at(0) - 3L * 24 * 3600_000, at(0), zone)
        assertTrue(parts.size <= 25)
        assertEquals(2400, parts.sumOf { it.steps })
    }

    @Test
    fun statsHelpers() {
        val hours = List(24) { if (it == 14) 1800 else if (it == 9) 300 else if (it == 10) 100 else 0 }
        assertEquals(2, StepsMath.activeHours(hours))
        assertEquals(14, StepsMath.bestHour(hours))
        assertNull(StepsMath.bestHour(List(24) { 0 }))
        assertEquals(4.86, StepsMath.distanceKm(6480), 1e-9)
    }

    @Test
    fun recorderWritesDeltaAndUpdatesBaseline() = runTest {
        val repo = MemoryStepsRepo()
        val rec = StepsRecorder(repo) { zone }
        rec.onReading(10_000, at(9, 0))
        assertEquals(0, repo.added.sumOf { it.steps })
        rec.onReading(10_800, at(9, 50))
        assertEquals(800, repo.added.sumOf { it.steps })
        assertEquals(SensorReading(10_800, at(9, 50)), repo.reading)
        // время ушло назад — шаги не пишем, точку отсчёта обновляем
        rec.onReading(11_000, at(9, 10))
        assertEquals(800, repo.added.sumOf { it.steps })
        assertEquals(11_000L, repo.reading?.counter)
    }

    @Test
    fun `two readings at once do not count the same steps twice`() = kotlinx.coroutines.test.runTest {
        val repo = MemoryStepsRepo()
        val rec = StepsRecorder(repo) { zone }
        rec.onReading(10_000, at(9, 0))
        // Экран и фоновая проверка получили одно и то же показание почти одновременно.
        val a = launch { rec.onReading(10_500, at(9, 30)) }
        val b = launch { rec.onReading(10_500, at(9, 31)) }
        a.join(); b.join()
        assertEquals(500, repo.added.sumOf { it.steps })
    }

    private class MemoryStepsRepo : StepsRepository {
        val added = mutableListOf<HourSteps>()
        var reading: SensorReading? = null
        override fun hoursFor(date: LocalDate): Flow<List<Int>> = flowOf(List(24) { 0 })
        override fun totalFor(date: LocalDate): Flow<Int> = flowOf(0)
        override fun totalsBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> = flowOf(emptyMap())
        override suspend fun addSteps(parts: List<HourSteps>) { added += parts }
        override suspend fun removeSteps(parts: List<HourSteps>) = Unit
        override suspend fun lastReading(): SensorReading? {
            // Уступаем поток: так два параллельных показания читают точку отсчёта одновременно.
            kotlinx.coroutines.yield()
            return reading
        }
        override suspend fun saveReading(reading: SensorReading) { this.reading = reading }
    }
}
