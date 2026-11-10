package com.projectxo.app.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "food_entry", indices = [Index("localDate")])
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** UTC epoch millis. */
    val ts: Long,
    /** День, который видел пользователь, формат yyyy-MM-dd. */
    val localDate: String,
    val name: String,
    val kcal: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
)

/** Результат суммирующего запроса по дню. */
data class FoodTotalsRow(
    val kcal: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
)
