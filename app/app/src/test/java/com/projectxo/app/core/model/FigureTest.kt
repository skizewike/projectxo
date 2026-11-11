package com.projectxo.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FigureTest {
    private val allSpecs = Exercise.entries.map { it to it.figure() } +
        PushupVariant.entries.map { Exercise.Pushups to ExerciseFigureData.specs.getValue("Pushups/${it.name}") }

    @Test fun catalogHasTwentyFourExercisesWithUniqueIds() {
        assertEquals(24, Exercise.entries.size)
        assertEquals(24, Exercise.entries.map { it.id }.toSet().size)
    }

    @Test fun legacyIdsStayTheSame() {
        assertEquals("pushups", Exercise.Pushups.id)
        assertEquals("squats", Exercise.Squats.id)
        assertEquals("plank", Exercise.Plank.id)
        assertEquals("crunches", Exercise.Crunches.id)
        assertEquals(Exercise.Plank, Exercise.fromId("plank"))
    }

    @Test fun everyGroupHasExercises() {
        ExerciseGroup.entries.forEach { assertTrue(it.name, Exercise.of(it).isNotEmpty()) }
        assertEquals(Exercise.entries.size, ExerciseGroup.entries.sumOf { Exercise.of(it).size })
    }

    @Test fun everyExerciseHasFigure() {
        Exercise.entries.forEach { assertNotNull(it.name, ExerciseFigureData.specs[if (it == Exercise.Pushups) "Pushups/Classic" else it.name]) }
        assertEquals(26, ExerciseFigureData.specs.size)
    }

    @Test fun pushupFigureFollowsLevel() {
        assertTrue(Exercise.Pushups.figure(3).deco == FigureDeco.Wall)
        assertTrue(Exercise.Pushups.figure(8).deco == FigureDeco.None)
        assertTrue(Exercise.Pushups.figure(8).a.leg[0].x == 72f) // с колен: колено на полу
        assertEquals(ExerciseFigureData.specs.getValue("Pushups/Classic"), Exercise.Pushups.figure(30))
    }

    @Test fun poseShapesMatchBetweenStartAndEnd() {
        allSpecs.forEach { (e, s) ->
            val b = s.b ?: return@forEach
            assertEquals(e.name, s.a.arm.size, b.arm.size)
            assertEquals(e.name, s.a.leg.size, b.leg.size)
            assertEquals(e.name, s.a.arm2 == null, b.arm2 == null)
            assertEquals(e.name, s.a.leg2 == null, b.leg2 == null)
        }
    }

    @Test fun pointsStayInsideCanvas() {
        allSpecs.forEach { (e, s) ->
            val sc = s.scale
            (listOf(s.a) + listOfNotNull(s.b)).forEach { pose ->
                pose.allPoints.forEach { p ->
                    // Масштаб фигуры считается от пола по центру холста.
                    val x = FigureMath.W / 2f * (1 - sc) + p.x * sc
                    val y = FigureMath.FLOOR * (1 - sc) + p.y * sc
                    assertTrue("${e.name} x=$x", x in 0f..FigureMath.W)
                    assertTrue("${e.name} y=$y", y in -1f..FigureMath.H)
                }
            }
        }
    }

    @Test fun limbLengthsStayHuman() {
        // Звенья фигуры не должны «растягиваться»: между позами длина звена меняется не больше чем на 35 %.
        fun len(a: Pt, b: Pt) = Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
        allSpecs.forEach { (e, s) ->
            val b = s.b ?: return@forEach
            val pairs = listOf(
                len(s.a.s, s.a.p) to len(b.s, b.p),
                len(s.a.p, s.a.leg[0]) to len(b.p, b.leg[0]),
                len(s.a.s, s.a.arm[0]) to len(b.s, b.arm[0]),
            )
            pairs.forEach { (x, y) -> assertTrue("${e.name} $x $y", y / x in 0.65..1.35) }
        }
    }

    @Test fun poseInterpolationEndsAreExact() {
        allSpecs.forEach { (_, s) ->
            assertEquals(s.a.s, FigureMath.pose(s, 0f).s)
            s.b?.let { assertEquals(it.p, FigureMath.pose(s, 1f).p) }
        }
    }

    @Test fun phaseStaysInRangeAndHoldsAtEnds() {
        val moving = Exercise.Squats.figure()
        assertEquals(0f, FigureMath.phase(moving, 0.1f), 1e-6f)
        assertEquals(1f, FigureMath.phase(moving, 1.8f), 1e-6f)
        assertEquals(0f, FigureMath.phase(moving, FigureMath.CYCLE_SEC), 1e-6f)
        for (i in 0..320) assertTrue(FigureMath.phase(moving, i / 100f) in 0f..1f)
        val hold = Exercise.Plank.figure()
        assertNull(hold.b)
        for (i in 0..320) assertTrue(FigureMath.phase(hold, i / 100f) in 0f..1f)
    }

    @Test fun secondsExercisesAreHolds() {
        assertTrue(Exercise.Plank.seconds && Exercise.SidePlank.seconds && Exercise.WallSit.seconds)
        assertEquals(3, Exercise.entries.count { it.seconds })
    }

    @Test fun planWorksForEveryExercise() {
        Exercise.entries.forEach { e ->
            val plan = WorkoutMath.plan(e.defaultCheck, e)
            assertEquals(WorkoutMath.SETS, plan.size)
            assertTrue(e.name, plan.all { it >= 1 })
        }
    }
}
