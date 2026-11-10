package com.projectxo.app.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT target FROM goal WHERE type = :type")
    fun observe(type: String): Flow<Long?>

    @Upsert
    suspend fun upsert(goal: GoalEntity)
}
