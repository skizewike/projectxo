package com.projectxo.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_entry WHERE localDate = :date ORDER BY ts DESC, id DESC")
    fun entriesFor(date: String): Flow<List<FoodEntryEntity>>

    @Query(
        "SELECT COALESCE(SUM(kcal), 0) AS kcal, COALESCE(SUM(protein), 0) AS protein, " +
            "COALESCE(SUM(fat), 0) AS fat, COALESCE(SUM(carbs), 0) AS carbs " +
            "FROM food_entry WHERE localDate = :date",
    )
    fun totalsFor(date: String): Flow<FoodTotalsRow>

    @Query("SELECT localDate AS date, SUM(kcal) AS total FROM food_entry WHERE localDate BETWEEN :from AND :to GROUP BY localDate")
    fun kcalBetween(from: String, to: String): Flow<List<DateTotal>>

    @Insert
    suspend fun insert(entry: FoodEntryEntity): Long

    @Query("DELETE FROM food_entry WHERE id = :id")
    suspend fun deleteById(id: Long)
}
