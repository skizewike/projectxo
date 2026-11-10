package com.projectxo.app.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "water_entry", indices = [Index("localDate")])
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** UTC epoch millis. */
    val ts: Long,
    /** День, который видел пользователь, формат yyyy-MM-dd. */
    val localDate: String,
    val ml: Int,
)
