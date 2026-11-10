package com.projectxo.app.core.plan

import com.projectxo.app.core.model.Activity
import com.projectxo.app.core.model.Profile
import com.projectxo.app.core.model.Sex
import com.projectxo.app.core.model.WeightGoal
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Категории ИМТ по ВОЗ для взрослых. */
enum class BmiCategory { Under, Normal, Over, Obese1, Obese2, Obese3 }

/** Личный план. Все числа — ориентиры, не медицинские назначения. */
data class Plan(
    val bmi: Double,
    val bmiCategory: BmiCategory,
    val restingKcal: Int,
    val dailyKcal: Int,
    val goal: WeightGoal,
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val waterMl: Int,
    val steps: Int,
    /** Ожидаемый темп, кг в неделю: минус — снижение. 0 при «держать». */
    val paceKgPerWeek: Double,
    /** Нижняя граница калорий при снижении (для объяснения «Как посчитали»). */
    val floorKcal: Int,
    /** Вес, от которого считается белок: фактический или при ИМТ 25. */
    val referenceWeightKg: Double,
    val proteinPerKg: Double,
)

/**
 * Расчёт по docs/PLAN-CALC.md. Менять формулы только здесь и вместе с документом и тестами.
 * Источники: Миффлин — Сан Жеор (1990), ВОЗ (ИМТ), ISSN 2017 (белок), EFSA 2010 (вода), Paluch 2022 (шаги).
 */
object PlanCalculator {
    const val MIN_AGE = 18
    const val MAX_AGE = 100
    const val MIN_HEIGHT_CM = 120
    const val MAX_HEIGHT_CM = 230
    const val MIN_WEIGHT_KG = 30.0
    const val MAX_WEIGHT_KG = 300.0

    fun bmi(weightKg: Double, heightCm: Int): Double {
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    fun category(bmi: Double): BmiCategory = when {
        bmi < 18.5 -> BmiCategory.Under
        bmi < 25.0 -> BmiCategory.Normal
        bmi < 30.0 -> BmiCategory.Over
        bmi < 35.0 -> BmiCategory.Obese1
        bmi < 40.0 -> BmiCategory.Obese2
        else -> BmiCategory.Obese3
    }

    /** Подсказка цели по ИМТ: ниже нормы — набрать, выше — снизить. */
    fun suggestedGoal(bmi: Double): WeightGoal = when {
        bmi < 18.5 -> WeightGoal.Gain
        bmi >= 25.0 -> WeightGoal.Lose
        else -> WeightGoal.Keep
    }

    /** «Снизить» недоступно при ИМТ ниже 18,5. */
    fun canLose(bmi: Double): Boolean = bmi >= 18.5

    fun activityFactor(a: Activity): Double = when (a) {
        Activity.None -> 1.2
        Activity.OneTwo -> 1.375
        Activity.ThreeFour -> 1.55
        Activity.FiveSix -> 1.725
        Activity.Daily -> 1.9
    }

    private fun proteinBase(a: Activity): Double = when (a) {
        Activity.None -> 1.0
        Activity.OneTwo -> 1.2
        Activity.ThreeFour -> 1.4
        Activity.FiveSix, Activity.Daily -> 1.6
    }

    /** Обмен в покое, формула Миффлина — Сан Жеора. «Не указывать» — среднее двух формул. */
    fun restingKcal(p: Profile): Double {
        val sexK = when (p.sex) { Sex.Male -> 5.0; Sex.Female -> -161.0; Sex.Unspecified -> -78.0 }
        return 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.ageYears + sexK
    }

    fun calculate(p: Profile): Plan {
        require(p.ageYears in MIN_AGE..MAX_AGE) { "План считается только для взрослых" }
        val bmi = bmi(p.weightKg, p.heightCm)
        val cat = category(bmi)
        val resting = restingKcal(p)
        val daily = resting * activityFactor(p.activity)

        var goal = p.goal ?: suggestedGoal(bmi)
        if (goal == WeightGoal.Lose && !canLose(bmi)) goal = WeightGoal.Gain

        val k = when (goal) {
            WeightGoal.Keep -> 1.0
            WeightGoal.Gain -> 1.10
            WeightGoal.Lose -> when {
                bmi >= 30 -> 0.80
                bmi >= 25 -> 0.85
                else -> 0.90
            }
        }
        val sexFloor = when (p.sex) { Sex.Male -> 1500.0; Sex.Female -> 1200.0; Sex.Unspecified -> 1350.0 }
        val floor = max(resting, sexFloor)
        var kcalRaw = daily * k
        if (goal == WeightGoal.Lose) kcalRaw = max(kcalRaw, floor)
        val kcal = round10(kcalRaw)

        val hM = p.heightCm / 100.0
        val refW = if (bmi > 25) 25 * hM * hM else p.weightKg
        val perKg = proteinBase(p.activity) + if (goal == WeightGoal.Lose) 0.2 else 0.0
        val proteinRaw = min(perKg * refW, kcal * 0.35 / 4)
        val fatRaw = max(kcal * 0.30 / 9, 0.6 * refW)
        val carbsRaw = max(0.0, (kcal - proteinRaw * 4 - fatRaw * 9) / 4)

        val sexWaterMin = when (p.sex) { Sex.Male -> 2000.0; Sex.Female -> 1600.0; Sex.Unspecified -> 1800.0 }
        val water = round50(max((p.weightKg * 30).coerceIn(1500.0, 3500.0), sexWaterMin))
        val steps = if (p.ageYears >= 60) 7000 else 8000
        val pace = if (goal == WeightGoal.Keep) 0.0 else (kcal - daily) * 7 / 7700

        return Plan(
            bmi = bmi, bmiCategory = cat, restingKcal = resting.roundToInt(), dailyKcal = daily.roundToInt(),
            goal = goal, kcal = kcal, proteinG = round5(proteinRaw), fatG = round5(fatRaw), carbsG = round5(carbsRaw),
            waterMl = water, steps = steps, paceKgPerWeek = pace, floorKcal = floor.roundToInt(),
            referenceWeightKg = refW, proteinPerKg = perKg,
        )
    }

    private fun round10(x: Double) = (x / 10).roundToInt() * 10
    private fun round5(x: Double) = (x / 5).roundToInt() * 5
    private fun round50(x: Double) = (x / 50).roundToInt() * 50
}

/** Перевод единиц для показа и ввода. Хранится всегда кг, см, мл. */
object UnitMath {
    const val LB_PER_KG = 2.20462
    const val CM_PER_INCH = 2.54
    const val ML_PER_OZ = 29.5735

    fun kgToLb(kg: Double) = kg * LB_PER_KG
    fun lbToKg(lb: Double) = lb / LB_PER_KG
    fun mlToOz(ml: Int) = ml / ML_PER_OZ

    /** Рост в футах и дюймах: 168 см → (5, 6). */
    fun cmToFtIn(cm: Int): Pair<Int, Int> {
        val inches = (cm / CM_PER_INCH).roundToInt()
        return inches / 12 to inches % 12
    }
}
