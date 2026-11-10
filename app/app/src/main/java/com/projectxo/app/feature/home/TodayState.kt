package com.projectxo.app.feature.home

import com.projectxo.app.core.model.DayBalance
import com.projectxo.app.core.model.DefaultGoals
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.ModuleProgress

/** Состояние экрана «Сегодня». Пока без базы: пустой день с целями по умолчанию. */
data class TodayState(val modules: List<ModuleProgress>) {
    val balance: Float get() = DayBalance.of(modules)
    val balancePercent: Int get() = DayBalance.percent(modules)

    companion object {
        val Empty = TodayState(
            listOf(
                ModuleProgress(Module.Water, 0, DefaultGoals.WATER_ML),
                ModuleProgress(Module.Food, 0, DefaultGoals.FOOD_KCAL),
                ModuleProgress(Module.Steps, 0, DefaultGoals.STEPS),
            ),
        )
    }
}
