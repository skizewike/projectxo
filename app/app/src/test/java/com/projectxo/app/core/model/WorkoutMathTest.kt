package com.projectxo.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMathTest {
    @Test
    fun plan_followsFactors_roundsDown_minOne() {
        assertEquals(listOf(12, 11, 10, 9, 10), WorkoutMath.plan(20, Exercise.Pushups))
        assertEquals(listOf(1, 1, 1, 1, 1), WorkoutMath.plan(1, Exercise.Squats))
        assertEquals(listOf(3, 2, 2, 2, 2), WorkoutMath.plan(5, Exercise.Crunches))
    }

    @Test
    fun plank_stepsOfFive_minTen() {
        // M = 40 с → 24, 22, 20, 18 → 20, 20, 20, 15; последний 20
        assertEquals(listOf(20, 20, 20, 15, 20), WorkoutMath.plan(40, Exercise.Plank))
        assertEquals(listOf(10, 10, 10, 10, 10), WorkoutMath.plan(10, Exercise.Plank))
    }

    @Test
    fun rest_dependsOnVolume() {
        assertEquals(60, WorkoutMath.restSeconds(20, Exercise.Pushups)) // 12 повторов
        assertEquals(90, WorkoutMath.restSeconds(40, Exercise.Squats)) // 24 повтора
        assertEquals(60, WorkoutMath.restSeconds(80, Exercise.Plank))
    }

    @Test
    fun nextLevel_table() {
        assertEquals(22, WorkoutMath.nextLevel(20, Exercise.Pushups, true, WorkoutRating.EASY, false)) // ×1,12 = 22,4
        assertEquals(21, WorkoutMath.nextLevel(20, Exercise.Pushups, true, WorkoutRating.NORMAL, false)) // ×1,08 = 21,6 → 21
        assertEquals(20, WorkoutMath.nextLevel(20, Exercise.Pushups, true, WorkoutRating.HARD, false))
        assertEquals(20, WorkoutMath.nextLevel(20, Exercise.Pushups, false, WorkoutRating.NORMAL, false))
        assertEquals(18, WorkoutMath.nextLevel(20, Exercise.Pushups, false, WorkoutRating.NORMAL, true)) // ×0,92
    }

    @Test
    fun nextLevel_minimumStep() {
        assertEquals(6, WorkoutMath.nextLevel(5, Exercise.Pushups, true, WorkoutRating.NORMAL, false)) // +1, а не 5,4
        assertEquals(45, WorkoutMath.nextLevel(40, Exercise.Plank, true, WorkoutRating.NORMAL, false)) // ×1,08 = 43 → шаг 5 → 45
    }

    @Test
    fun allDone_needsEverySet() {
        val plan = listOf(5, 5, 4, 4, 4)
        assertTrue(WorkoutMath.allDone(plan, listOf(5, 6, 4, 4, 9)))
        assertFalse(WorkoutMath.allDone(plan, listOf(5, 5, 3, 4, 4)))
        assertFalse(WorkoutMath.allDone(plan, listOf(5, 5, 4, 4)))
    }

    @Test
    fun variants_andRetest() {
        assertEquals(PushupVariant.Wall, WorkoutMath.pushupVariant(3))
        assertEquals(PushupVariant.Knees, WorkoutMath.pushupVariant(8))
        assertEquals(PushupVariant.Classic, WorkoutMath.pushupVariant(15))
        assertTrue(WorkoutMath.needsRetest(6))
        assertFalse(WorkoutMath.needsRetest(5))
        assertFalse(WorkoutMath.needsRetest(0))
    }
}
