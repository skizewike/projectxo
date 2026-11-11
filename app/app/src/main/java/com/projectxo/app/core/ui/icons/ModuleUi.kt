package com.projectxo.app.core.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.projectxo.app.R
import com.projectxo.app.core.model.Module

/** Название, подпись и значок модуля: один источник для вкладок, «Сегодня», настроек и онбординга. */
object ModuleUi {
    fun nameRes(m: Module): Int = when (m) {
        Module.Water -> R.string.module_water
        Module.Food -> R.string.module_food
        Module.Steps -> R.string.module_steps
        Module.Workouts -> R.string.module_workouts
        Module.Fasting -> R.string.module_fasting
        Module.Sleep -> R.string.module_sleep
        Module.Habits -> R.string.module_habits
        Module.Breathing -> R.string.module_breathing
        Module.Mood -> R.string.module_mood
    }

    fun subRes(m: Module): Int = when (m) {
        Module.Water -> R.string.module_water_sub
        Module.Food -> R.string.module_food_sub
        Module.Steps -> R.string.module_steps_sub
        Module.Workouts -> R.string.module_workouts_sub
        Module.Fasting -> R.string.module_fasting_sub
        Module.Sleep -> R.string.module_sleep_sub
        Module.Habits -> R.string.module_habits_sub
        Module.Breathing -> R.string.module_breathing_sub
        Module.Mood -> R.string.module_mood_sub
    }

    fun icon(m: Module): ImageVector = when (m) {
        Module.Water -> XoIcons.Water
        Module.Food -> XoIcons.Food
        Module.Steps -> XoIcons.Steps
        Module.Workouts -> XoIcons.Dumbbell
        Module.Fasting -> XoIcons.Fasting
        Module.Sleep -> XoIcons.Sleep
        Module.Habits -> XoIcons.Habits
        Module.Breathing -> XoIcons.Breathing
        Module.Mood -> XoIcons.Mood
    }
}
