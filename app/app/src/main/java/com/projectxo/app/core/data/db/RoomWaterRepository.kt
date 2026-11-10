package com.projectxo.app.core.data.db

import com.projectxo.app.core.data.WaterEntry
import com.projectxo.app.core.data.WaterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomWaterRepository(private val dao: WaterDao) : WaterRepository {
    override fun entriesFor(date: LocalDate): Flow<List<WaterEntry>> =
        dao.entriesFor(date.toString()).map { list -> list.map { it.toModel() } }

    override fun totalFor(date: LocalDate): Flow<Int> = dao.totalFor(date.toString())

    override fun totalsBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        dao.totalsBetween(from.toString(), to.toString()).map { it.toDateMap() }

    override suspend fun add(ml: Int, timestampMs: Long, date: LocalDate): Long =
        dao.insert(WaterEntryEntity(ts = timestampMs, localDate = date.toString(), ml = ml))

    override suspend fun delete(id: Long) = dao.deleteById(id)

    private fun WaterEntryEntity.toModel() =
        WaterEntry(id = id, timestampMs = ts, localDate = LocalDate.parse(localDate), ml = ml)
}
