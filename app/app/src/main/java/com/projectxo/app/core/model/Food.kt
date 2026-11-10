package com.projectxo.app.core.model

import com.projectxo.app.core.data.FoodDraft
import com.projectxo.app.core.data.FoodTotals

/** Правила ввода еды (docs/DESIGN.md, «Еда»). Числа только здесь. */
object FoodRules {
    const val MAX_KCAL = 5000
    const val MAX_MACRO_G = 500
    const val MAX_NAME_LENGTH = 40

    const val KCAL_PER_G_PROTEIN = 4
    const val KCAL_PER_G_FAT = 9
    const val KCAL_PER_G_CARBS = 4

    /** Калории по БЖУ: подсказка, когда число калорий не введено. */
    fun estimateKcal(proteinG: Int, fatG: Int, carbsG: Int): Int =
        proteinG * KCAL_PER_G_PROTEIN + fatG * KCAL_PER_G_FAT + carbsG * KCAL_PER_G_CARBS

    fun isValid(draft: FoodDraft): Boolean =
        draft.kcal in 1..MAX_KCAL &&
            draft.proteinG in 0..MAX_MACRO_G && draft.fatG in 0..MAX_MACRO_G && draft.carbsG in 0..MAX_MACRO_G
}

/** Быстрый шаблон: название берётся из ресурсов по [id] (strings.xml, food_tpl_*). */
data class FoodTemplate(val id: Int, val kcal: Int, val proteinG: Int, val fatG: Int, val carbsG: Int)

object FoodTemplates {
    val ALL = listOf(
        FoodTemplate(1, 320, 10, 6, 58),
        FoodTemplate(2, 540, 42, 12, 64),
        FoodTemplate(3, 180, 24, 8, 4),
        FoodTemplate(4, 80, 0, 0, 19),
        FoodTemplate(5, 610, 34, 24, 62),
    )
}

/** Подпись сегодняшних запасов: сколько ещё до цели и перебор. */
object FoodMath {
    fun left(totals: FoodTotals, goalKcal: Long): Long = (goalKcal - totals.kcal).coerceAtLeast(0)
    fun over(totals: FoodTotals, goalKcal: Long): Long = (totals.kcal - goalKcal).coerceAtLeast(0)

    /**
     * Высота слоёв квадрата в долях полной высоты: белки, жиры, углеводы снизу вверх.
     * Доля слоя = его энергия / сумма энергий макро, всё умножено на долю цели. Перебор обрезается по 1.
     */
    fun layerFractions(totals: FoodTotals, goalKcal: Long): Triple<Float, Float, Float> {
        val p = totals.proteinG * FoodRules.KCAL_PER_G_PROTEIN.toFloat()
        val f = totals.fatG * FoodRules.KCAL_PER_G_FAT.toFloat()
        val c = totals.carbsG * FoodRules.KCAL_PER_G_CARBS.toFloat()
        val sum = p + f + c
        if (sum <= 0f || goalKcal <= 0) return Triple(0f, 0f, 0f)
        val fill = (totals.kcal.toFloat() / goalKcal).coerceIn(0f, 1f)
        return Triple(fill * p / sum, fill * f / sum, fill * c / sum)
    }

    /** Насколько показывать штриховку перебора, 0..1: полная при +25 % к цели. */
    fun overRatio(totals: FoodTotals, goalKcal: Long): Float =
        if (goalKcal <= 0) 0f else (over(totals, goalKcal).toFloat() / (goalKcal * 0.25f)).coerceIn(0f, 1f)
}
