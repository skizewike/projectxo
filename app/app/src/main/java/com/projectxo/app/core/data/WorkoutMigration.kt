package com.projectxo.app.core.data

import com.projectxo.app.core.model.HabitFreq
import com.projectxo.app.core.model.HabitKind
import com.projectxo.app.core.model.Module
import kotlinx.coroutines.flow.first

/**
 * Р73: тренировки стали отдельным модулем. Привычка «Тренировка» из «Привычек» превращается в дни плана модуля,
 * а сама привычка удаляется (записи тренировок остаются). Выполняется при каждом запуске, но работает один раз:
 * после удаления привычки делать нечего.
 */
object WorkoutMigration {
    suspend fun run(habits: HabitRepository, settings: SettingsRepository) {
        val legacy = habits.habits().first().filter { it.kind == HabitKind.Workout }
        if (legacy.isEmpty()) return
        val mask = (legacy.first().freq as? HabitFreq.Days)?.mask?.takeIf { it != 0 }
        settings.update {
            it.copy(
                modules = it.modules + Module.Workouts,
                wave2 = if (mask != null) it.wave2.copy(workoutDays = mask) else it.wave2,
            )
        }
        legacy.forEach { habits.delete(it.id) }
    }
}
