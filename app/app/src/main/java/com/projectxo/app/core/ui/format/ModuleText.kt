package com.projectxo.app.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.projectxo.app.R
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.ModuleProgress

/** Значение модуля для строки: «1 250», «7 ч 30 мин», «2». Единицы цели — в [moduleGoalText]. */
@Composable
fun moduleValueText(p: ModuleProgress): String = when (p.module) {
    Module.Water -> formatVolumeNumber(p.value)
    Module.Fasting, Module.Sleep -> formatMinutes(p.value)
    Module.Breathing -> formatMinutes(p.value / 60)
    else -> formatCount(p.value)
}

/** Цель модуля: «из 2 000 мл», «из 8 ч», «из 4». */
@Composable
fun moduleGoalText(p: ModuleProgress): String {
    val goal = formatCount(p.goal)
    return when (p.module) {
        Module.Water -> stringResource(R.string.of_ml, formatVolume(p.goal))
        Module.Food -> stringResource(R.string.of_kcal, goal)
        Module.Steps -> pluralText(R.plurals.of_steps, p.goal.toInt(), goal)
        Module.Fasting, Module.Sleep -> stringResource(R.string.of_hours, formatCount(p.goal / 60))
        Module.Habits -> stringResource(R.string.of_habits, goal)
        Module.Breathing -> stringResource(R.string.of_minutes, formatCount(p.goal / 60))
    }
}
