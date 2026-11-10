package com.projectxo.app.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Одна строка (id всегда 0): последнее показание датчика шагов. */
@Entity(tableName = "sensor_reading")
data class SensorReadingEntity(
    @PrimaryKey val id: Int = 0,
    val counter: Long,
    val timestampMs: Long,
)
