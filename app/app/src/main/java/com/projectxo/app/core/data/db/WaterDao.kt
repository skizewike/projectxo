package com.projectxo.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_entry WHERE localDate = :date ORDER BY ts DESC, id DESC")
    fun entriesFor(date: String): Flow<List<WaterEntryEntity>>

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water_entry WHERE localDate = :date")
    fun totalFor(date: String): Flow<Int>

    @Query("SELECT localDate AS date, SUM(ml) AS total FROM water_entry WHERE localDate BETWEEN :from AND :to GROUP BY localDate")
    fun totalsBetween(from: String, to: String): Flow<List<DateTotal>>

    @Insert
    suspend fun insert(entry: WaterEntryEntity): Long

    @Query("DELETE FROM water_entry WHERE id = :id")
    suspend fun deleteById(id: Long)
}
