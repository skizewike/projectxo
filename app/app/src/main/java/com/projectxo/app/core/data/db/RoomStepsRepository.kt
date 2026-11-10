package com.projectxo.app.core.data.db

import com.projectxo.app.core.data.HourSteps
import com.projectxo.app.core.data.SensorReading
import com.projectxo.app.core.data.StepsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomStepsRepository(private val dao: StepsDao) : StepsRepository {
    override fun hoursFor(date: LocalDate): Flow<List<Int>> =
        dao.hoursFor(date.toString()).map { rows ->
            val result = MutableList(HOURS_IN_DAY) { 0 }
            rows.forEach { if (it.hour in 0 until HOURS_IN_DAY) result[it.hour] = it.steps }
            result
        }

    override fun totalFor(date: LocalDate): Flow<Int> = dao.totalFor(date.toString())

    override fun totalsBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        dao.totalsBetween(from.toString(), to.toString()).map { it.toDateMap() }

    override suspend fun addSteps(parts: List<HourSteps>) {
        val valid = parts.filter { it.steps > 0 && it.hour in 0 until HOURS_IN_DAY }
        if (valid.isEmpty()) return
        dao.addAll(valid.map { StepsHourEntity(it.date.toString(), it.hour, it.steps) })
    }

    override suspend fun removeSteps(parts: List<HourSteps>) {
        val valid = parts.filter { it.steps > 0 && it.hour in 0 until HOURS_IN_DAY }
        if (valid.isEmpty()) return
        dao.removeAll(valid.map { StepsHourEntity(it.date.toString(), it.hour, it.steps) })
    }

    override suspend fun lastReading(): SensorReading? =
        dao.lastReading()?.let { SensorReading(it.counter, it.timestampMs) }

    override suspend fun saveReading(reading: SensorReading) =
        dao.saveReading(SensorReadingEntity(0, reading.counter, reading.timestampMs))

    private companion object {
        const val HOURS_IN_DAY = 24
    }
}
