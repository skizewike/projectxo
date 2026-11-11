package com.projectxo.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** «Баланс дня» и тренировки (Р73): только в дни плана, перевыполнение не больше 100 %. */
class WorkoutBalanceTest {
    private val goals = HistoryGoals(waterMl = 2000, kcal = 2000, steps = 8000)
    private val modules = setOf(Module.Water, Module.Workouts)

    private fun workoutProgress(t: DayTotals?) =
        BalanceMath.progress(t, goals, modules).firstOrNull { it.module == Module.Workouts }

    @Test fun plannedDayWithoutWorkoutCountsAsZeroOfOne() {
        val p = workoutProgress(DayTotals(waterMl = 2000, workoutDay = true))!!
        assertEquals(0L, p.value)
        assertEquals(1L, p.goal)
        assertEquals(0.5f, DayBalance.of(BalanceMath.progress(DayTotals(waterMl = 2000, workoutDay = true), goals, modules)), 1e-6f)
    }

    @Test fun restDayIsNotCountedAtAll() {
        assertNull(workoutProgress(DayTotals(waterMl = 1000, workoutDay = false)))
        assertEquals(0.5f, BalanceMath.balance(DayTotals(waterMl = 1000), goals, modules), 1e-6f)
    }

    @Test fun extraWorkoutOnRestDayIsABonusOfOneHundredPercent() {
        val p = workoutProgress(DayTotals(workoutsDone = 1, workoutDay = false))!!
        assertEquals(1f, p.fraction, 1e-6f)
    }

    @Test fun severalWorkoutsDoNotExceedOneHundredPercent() {
        val p = workoutProgress(DayTotals(workoutsDone = 3, workoutDay = true))!!
        assertEquals(1L, p.value)
        assertEquals(1f, p.fraction, 1e-6f)
    }

    @Test fun moduleIsOffWhenNotEnabled() {
        val parts = BalanceMath.progress(DayTotals(workoutsDone = 1, workoutDay = true), goals, setOf(Module.Water))
        assertEquals(listOf(Module.Water), parts.map { it.module })
    }

    @Test fun workoutsSitRightAfterStepsInDefaultOrder() {
        val order = Module.entries
        assertEquals(order.indexOf(Module.Steps) + 1, order.indexOf(Module.Workouts))
    }
}
