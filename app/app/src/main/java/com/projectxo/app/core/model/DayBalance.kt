package com.projectxo.app.core.model

/** Модули волны 1. Различаются формой метки и подписью, не цветом. */
enum class Module {
    Water, Food, Steps, Fasting, Sleep, Habits, Breathing,
    ;

    companion object {
        /** Модули первой волны: включены по умолчанию. */
        val BASE: Set<Module> = setOf(Water, Food, Steps)
    }
}

/** Прогресс одного модуля за день в базовых единицах (мл, ккал, шаги). */
data class ModuleProgress(val module: Module, val value: Long, val goal: Long) {
    /** Доля выполнения цели, ограниченная 100 %. */
    val fraction: Float
        get() = if (goal <= 0) 0f else (value.toFloat() / goal).coerceIn(0f, 1f)
}

/** Цели по умолчанию (docs/PRODUCT.md, «Первый запуск»). */
object DefaultGoals {
    const val WATER_ML = 2000L
    const val FOOD_KCAL = 2000L
    const val PROTEIN_G = 100L
    const val FAT_G = 70L
    const val CARBS_G = 250L
    const val STEPS = 8000L
}

/**
 * «Баланс дня» (рабочее название, DECISIONS.md, В1).
 * Рабочая формула, вариант 1 из В2: среднее по включённым модулям, каждый не больше 100 %.
 * Менять формулу только здесь.
 */
object DayBalance {
    fun of(parts: List<ModuleProgress>): Float =
        if (parts.isEmpty()) 0f else parts.sumOf { it.fraction.toDouble() }.toFloat() / parts.size

    fun percent(parts: List<ModuleProgress>): Int = Math.round(of(parts) * 100f)
}
