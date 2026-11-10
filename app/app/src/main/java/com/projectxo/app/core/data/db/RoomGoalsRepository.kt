package com.projectxo.app.core.data.db

import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.data.GoalsRepository
import com.projectxo.app.core.model.DefaultGoals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomGoalsRepository(private val dao: GoalDao) : GoalsRepository {
    override fun goal(type: GoalType): Flow<Long> =
        dao.observe(type.name).map { it ?: type.default() }

    override suspend fun setGoal(type: GoalType, target: Long) {
        require(target > 0) { "Цель должна быть больше нуля" }
        dao.upsert(GoalEntity(type.name, target))
    }

    private fun GoalType.default(): Long = when (this) {
        GoalType.WaterMl -> DefaultGoals.WATER_ML
        GoalType.FoodKcal -> DefaultGoals.FOOD_KCAL
        GoalType.ProteinG -> DefaultGoals.PROTEIN_G
        GoalType.FatG -> DefaultGoals.FAT_G
        GoalType.CarbsG -> DefaultGoals.CARBS_G
        GoalType.Steps -> DefaultGoals.STEPS
    }
}
