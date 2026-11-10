package com.projectxo.app.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goal")
data class GoalEntity(
    /** Имя GoalType. */
    @PrimaryKey val type: String,
    val target: Long,
)
