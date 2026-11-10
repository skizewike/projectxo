package com.projectxo.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StepsDao {
    @Query("SELECT * FROM steps_hour WHERE localDate = :date")
    fun hoursFor(date: String): Flow<List<StepsHourEntity>>

    @Query("SELECT COALESCE(SUM(steps), 0) FROM steps_hour WHERE localDate = :date")
    fun totalFor(date: String): Flow<Int>

    @Query("SELECT localDate AS date, SUM(steps) AS total FROM steps_hour WHERE localDate BETWEEN :from AND :to GROUP BY localDate")
    fun totalsBetween(from: String, to: String): Flow<List<DateTotal>>

    /** Сколько строк обновлено: 0 — часа ещё нет. */
    @Query("UPDATE steps_hour SET steps = steps + :steps WHERE localDate = :date AND hour = :hour")
    suspend fun addToHour(date: String, hour: Int, steps: Int): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHour(part: StepsHourEntity)

    /**
     * Прибавить шаги к часам. Без `INSERT … ON CONFLICT DO UPDATE`: этот синтаксис есть только в SQLite 3.24+,
     * то есть с Android 11, а приложение работает с Android 8 (minSdk 26). На Android 9 запрос ронял приложение.
     */
    @Transaction
    suspend fun addAll(parts: List<StepsHourEntity>) {
        parts.forEach { if (addToHour(it.localDate, it.hour, it.steps) == 0) insertHour(it) }
    }

    @Query("UPDATE steps_hour SET steps = MAX(0, steps - :steps) WHERE localDate = :date AND hour = :hour")
    suspend fun subtractFromHour(date: String, hour: Int, steps: Int)

    @Query("DELETE FROM steps_hour WHERE steps <= 0")
    suspend fun dropEmptyHours()

    @Transaction
    suspend fun removeAll(parts: List<StepsHourEntity>) {
        parts.forEach { subtractFromHour(it.localDate, it.hour, it.steps) }
        dropEmptyHours()
    }

    @Query("SELECT * FROM sensor_reading WHERE id = 0")
    suspend fun lastReading(): SensorReadingEntity?

    @Upsert
    suspend fun saveReading(reading: SensorReadingEntity)
}
