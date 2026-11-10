package com.projectxo.app.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.projectxo.app.core.data.DayLog
import com.projectxo.app.core.data.DayLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

@Entity(tableName = "day_log")
data class DayLogEntity(
    /** День, формат yyyy-MM-dd. */
    @PrimaryKey val localDate: String,
    val feel: Int?,
    /** Ключи тегов через запятую. */
    val tags: String,
    /** Вес в десятых долях кг (718 = 71,8 кг): без дробных чисел в базе. */
    val weightTenths: Int?,
    val note: String,
)

/** Строка суммирующего запроса по дням. */
data class DateTotal(val date: String, val total: Int)

internal fun List<DateTotal>.toDateMap(): Map<LocalDate, Int> = associate { LocalDate.parse(it.date) to it.total }

@Dao
interface DayLogDao {
    @Query("SELECT * FROM day_log WHERE localDate = :date")
    fun observe(date: String): Flow<DayLogEntity?>

    @Query("SELECT * FROM day_log WHERE localDate BETWEEN :from AND :to")
    fun observeBetween(from: String, to: String): Flow<List<DayLogEntity>>

    @Upsert
    suspend fun upsert(entity: DayLogEntity)

    @Query("DELETE FROM day_log WHERE localDate = :date")
    suspend fun delete(date: String)
}

class RoomDayLogRepository(private val dao: DayLogDao) : DayLogRepository {
    override fun observe(date: LocalDate): Flow<DayLog?> = dao.observe(date.toString()).map { it?.toModel() }

    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DayLog>> =
        dao.observeBetween(from.toString(), to.toString()).map { rows -> rows.map { it.toModel() }.associateBy { it.date } }

    override suspend fun save(log: DayLog) {
        if (log.isEmpty) {
            dao.delete(log.date.toString())
        } else {
            dao.upsert(
                DayLogEntity(
                    localDate = log.date.toString(),
                    feel = log.feel,
                    tags = log.tags.sorted().joinToString(","),
                    weightTenths = log.weightKg?.let { Math.round(it * 10).toInt() },
                    note = log.note.trim(),
                ),
            )
        }
    }

    private fun DayLogEntity.toModel() = DayLog(
        date = LocalDate.parse(localDate),
        feel = feel,
        tags = tags.split(',').filter { it.isNotEmpty() }.toSet(),
        weightKg = weightTenths?.let { it / 10.0 },
        note = note,
    )
}
