package com.projectxo.app.core.plan

import com.projectxo.app.core.model.Activity
import com.projectxo.app.core.model.Profile
import com.projectxo.app.core.model.Sex
import com.projectxo.app.core.model.WeightGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Значения сверены с docs/PLAN-CALC.md и с превью design/onboarding.html. */
class PlanCalculatorTest {
    private fun profile(
        sex: Sex = Sex.Female, age: Int = 32, h: Int = 168, w: Double = 74.0,
        act: Activity = Activity.OneTwo, goal: WeightGoal? = null,
    ) = Profile(sex, age, h, w, act, goal)

    @Test fun bmiCategoriesFollowWho() {
        assertEquals(BmiCategory.Under, PlanCalculator.category(18.49))
        assertEquals(BmiCategory.Normal, PlanCalculator.category(18.5))
        assertEquals(BmiCategory.Normal, PlanCalculator.category(24.99))
        assertEquals(BmiCategory.Over, PlanCalculator.category(25.0))
        assertEquals(BmiCategory.Obese1, PlanCalculator.category(30.0))
        assertEquals(BmiCategory.Obese2, PlanCalculator.category(35.0))
        assertEquals(BmiCategory.Obese3, PlanCalculator.category(40.0))
    }

    @Test fun mifflinStJeorForWoman() {
        // 10·74 + 6,25·168 − 5·32 − 161 = 1469
        assertEquals(1469.0, PlanCalculator.restingKcal(profile()), 0.01)
    }

    @Test fun unspecifiedSexIsAverageOfBoth() {
        val m = PlanCalculator.restingKcal(profile(sex = Sex.Male))
        val f = PlanCalculator.restingKcal(profile(sex = Sex.Female))
        assertEquals((m + f) / 2, PlanCalculator.restingKcal(profile(sex = Sex.Unspecified)), 0.01)
    }

    @Test fun overweightLoseIsMinus15Percent() {
        val p = PlanCalculator.calculate(profile())
        assertEquals(WeightGoal.Lose, p.goal) // ИМТ 26,2 — подсказка «снизить»
        assertEquals(1720, p.kcal) // 1469 × 1,375 × 0,85 = 1717 → 1720
        assertEquals(100, p.proteinG) // (1,2 + 0,2) × 70,56 кг (вес при ИМТ 25)
        assertEquals(55, p.fatG)
        assertEquals(200, p.carbsG)
        assertEquals(2200, p.waterMl)
        assertEquals(8000, p.steps)
        assertTrue(p.paceKgPerWeek < 0)
    }

    @Test fun underweightCannotLoseAndGetsGain() {
        val p = PlanCalculator.calculate(profile(w = 50.0, h = 175, goal = WeightGoal.Lose))
        assertFalse(PlanCalculator.canLose(p.bmi))
        assertEquals(WeightGoal.Gain, p.goal)
    }

    @Test fun obesityClass3Minus20PercentAndCappedWater() {
        val p = PlanCalculator.calculate(profile(sex = Sex.Male, h = 170, w = 130.0, act = Activity.FiveSix))
        assertEquals(BmiCategory.Obese3, p.bmiCategory)
        assertEquals(3050, p.kcal)
        assertEquals(130, p.proteinG)
        assertEquals(3500, p.waterMl)
    }

    @Test fun loseNeverBelowRestingOrSexFloor() {
        val p = PlanCalculator.calculate(profile(w = 52.0, h = 150, age = 70, act = Activity.None, goal = WeightGoal.Lose))
        assertTrue(p.kcal >= p.floorKcal - 5)
        assertTrue(p.kcal >= 1200)
    }

    @Test fun keepHasZeroPace() {
        val p = PlanCalculator.calculate(profile(w = 60.0, goal = WeightGoal.Keep))
        assertEquals(0.0, p.paceKgPerWeek, 0.0)
    }

    @Test fun sixtyAndOlderWalk7000() {
        assertEquals(7000, PlanCalculator.calculate(profile(age = 60)).steps)
        assertEquals(8000, PlanCalculator.calculate(profile(age = 59)).steps)
    }

    @Test fun waterNotBelowEfsaDrinkingPart() {
        assertEquals(2000, PlanCalculator.calculate(profile(sex = Sex.Male, w = 55.0, h = 170)).waterMl)
        assertEquals(1600, PlanCalculator.calculate(profile(sex = Sex.Female, w = 45.0, h = 155)).waterMl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun minorsAreRejected() {
        PlanCalculator.calculate(profile(age = 17))
    }

    @Test fun unitConversions() {
        assertEquals(5 to 6, UnitMath.cmToFtIn(168))
        assertEquals(163.1, UnitMath.kgToLb(74.0), 0.1)
        assertEquals(74.0, UnitMath.lbToKg(UnitMath.kgToLb(74.0)), 1e-9)
    }
}
