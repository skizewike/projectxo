package com.projectxo.app.core.model

import com.projectxo.app.core.data.FoodDraft
import com.projectxo.app.core.data.FoodTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodMathTest {
    private val goal = 2000L

    @Test
    fun emptyDay_allLayersZero() {
        val (p, f, c) = FoodMath.layerFractions(FoodTotals(), goal)
        assertEquals(0f, p, 0f)
        assertEquals(0f, f, 0f)
        assertEquals(0f, c, 0f)
    }

    @Test
    fun layers_sumToGoalFraction() {
        val t = FoodTotals(kcal = 1000, proteinG = 50, fatG = 30, carbsG = 120)
        val (p, f, c) = FoodMath.layerFractions(t, goal)
        assertEquals(0.5f, p + f + c, 0.0001f)
    }

    @Test
    fun layers_splitByEnergyNotGrams() {
        // 25 г белка = 100 ккал, 10 г жира = 90 ккал, 25 г углеводов = 100 ккал.
        val t = FoodTotals(kcal = 290, proteinG = 25, fatG = 10, carbsG = 25)
        val (p, f, c) = FoodMath.layerFractions(t, 290)
        assertEquals(100f / 290f, p, 0.0001f)
        assertEquals(90f / 290f, f, 0.0001f)
        assertEquals(100f / 290f, c, 0.0001f)
    }

    @Test
    fun overGoal_squareStaysFull() {
        val t = FoodTotals(kcal = 2600, proteinG = 100, fatG = 80, carbsG = 300)
        val (p, f, c) = FoodMath.layerFractions(t, goal)
        assertEquals(1f, p + f + c, 0.0001f)
    }

    @Test
    fun noMacros_noLayersEvenWithKcal() {
        val (p, f, c) = FoodMath.layerFractions(FoodTotals(kcal = 500), goal)
        assertEquals(0f, p + f + c, 0f)
    }

    @Test
    fun leftAndOver() {
        assertEquals(1500L, FoodMath.left(FoodTotals(kcal = 500), goal))
        assertEquals(0L, FoodMath.over(FoodTotals(kcal = 500), goal))
        assertEquals(0L, FoodMath.left(FoodTotals(kcal = 2100), goal))
        assertEquals(100L, FoodMath.over(FoodTotals(kcal = 2100), goal))
    }

    @Test
    fun overRatio_fullAtQuarterOver() {
        assertEquals(0f, FoodMath.overRatio(FoodTotals(kcal = 2000), goal), 0f)
        assertEquals(0.5f, FoodMath.overRatio(FoodTotals(kcal = 2250), goal), 0.0001f)
        assertEquals(1f, FoodMath.overRatio(FoodTotals(kcal = 3000), goal), 0f)
    }

    @Test
    fun estimateKcal_usesAtwater() {
        assertEquals(4 * 10 + 9 * 6 + 4 * 58, FoodRules.estimateKcal(10, 6, 58))
    }

    @Test
    fun validation_bounds() {
        val ok = FoodDraft("x", 100, 0, 0, 0)
        assertTrue(FoodRules.isValid(ok))
        assertFalse(FoodRules.isValid(ok.copy(kcal = 0)))
        assertTrue(FoodRules.isValid(ok.copy(kcal = FoodRules.MAX_KCAL)))
        assertFalse(FoodRules.isValid(ok.copy(kcal = FoodRules.MAX_KCAL + 1)))
        assertTrue(FoodRules.isValid(ok.copy(carbsG = FoodRules.MAX_MACRO_G)))
        assertFalse(FoodRules.isValid(ok.copy(carbsG = FoodRules.MAX_MACRO_G + 1)))
    }
}
