package com.projectxo.app.core.data.db

import androidx.room.Entity

@Entity(tableName = "steps_hour", primaryKeys = ["localDate", "hour"])
data class StepsHourEntity(
    /** yyyy-MM-dd. */
    val localDate: String,
    /** 0..23 по местному времени. */
    val hour: Int,
    val steps: Int,
)
