package com.projectxo.app.core.motion

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCelebrationTest {
    private val s = GoalCelebrationSpec

    @Test
    fun traceHead_startsAtZero_endsAtPerimeterPlusLength_andGrows() {
        assertEquals(0f, GoalCelebration.traceHead(0f, 600f, 110f), 1e-4f)
        assertEquals(710f, GoalCelebration.traceHead(s.TRACE_MS, 600f, 110f), 1e-3f)
        assertEquals(710f, GoalCelebration.traceHead(s.TRACE_MS + 500f, 600f, 110f), 1e-3f)
        var prev = -1f
        var t = 0f
        while (t <= s.TRACE_MS) {
            val v = GoalCelebration.traceHead(t, 600f, 110f)
            assertTrue("t=$t", v >= prev)
            prev = v
            t += 10f
        }
    }

    @Test
    fun traceAlpha_fullThenFades() {
        assertEquals(1f, GoalCelebration.traceAlpha(0f), 0f)
        assertEquals(1f, GoalCelebration.traceAlpha(s.TRACE_MS), 0f)
        assertEquals(0f, GoalCelebration.traceAlpha(s.TRACE_MS + s.TRACE_FADE_MS), 0f)
        assertEquals(0f, GoalCelebration.traceAlpha(s.TRACE_MS + s.TRACE_FADE_MS + 100f), 0f)
        val mid = GoalCelebration.traceAlpha(s.TRACE_MS + s.TRACE_FADE_MS / 2)
        assertTrue(mid > 0f && mid < 1f)
    }

    @Test
    fun waveBump_windowPerDivision() {
        for (i in 0..3) {
            val start = s.WAVE_START_MS + i * s.WAVE_STEP_MS
            assertEquals(0f, GoalCelebration.waveBump(start - 1f, i), 0f)
            assertEquals(0f, GoalCelebration.waveBump(start, i), 1e-6f)
            assertEquals(1f, GoalCelebration.waveBump(start + s.WAVE_TICK_MS / 2, i), 1e-4f)
            assertEquals(0f, GoalCelebration.waveBump(start + s.WAVE_TICK_MS + 1f, i), 0f)
        }
        val t = s.WAVE_START_MS + 1f
        assertTrue(GoalCelebration.waveBump(t, 0) > 0f)
        assertEquals(0f, GoalCelebration.waveBump(t, 1), 0f)
    }

    @Test
    fun solidProgress_zeroThenOne() {
        assertEquals(0f, GoalCelebration.solidProgress(0f), 0f)
        assertEquals(0f, GoalCelebration.solidProgress(s.SOLID_FROM_MS), 0f)
        assertEquals(1f, GoalCelebration.solidProgress(s.SOLID_FROM_MS + s.SOLID_MS), 1e-6f)
        assertEquals(1f, GoalCelebration.solidProgress(s.TOTAL_MS + 500f), 1e-6f)
    }

    @Test
    fun dip_zeroOutsideWindow_hasExtremes() {
        val end = s.DIP_DELAY_MS + s.DIP_MS
        assertEquals(0f, GoalCelebration.dipOffsetDp(0f), 0f)
        assertEquals(0f, GoalCelebration.dipOffsetDp(s.DIP_DELAY_MS), 0f)
        assertEquals(0f, GoalCelebration.dipOffsetDp(end), 0f)
        assertEquals(0f, GoalCelebration.dipOffsetDp(end + 100f), 0f)
        var max = -100f
        var min = 100f
        var t = 0f
        while (t <= s.TOTAL_MS) {
            val v = GoalCelebration.dipOffsetDp(t)
            if (v > max) max = v
            if (v < min) min = v
            val sc = GoalCelebration.dipScaleY(t)
            assertTrue("scale t=$t", sc >= s.DIP_SCALE - 1e-6f && sc <= 1f)
            if (t <= s.DIP_DELAY_MS || t >= end) assertEquals(1f, sc, 0f)
            t += 5f
        }
        assertEquals(s.DIP_DOWN_DP, max, 0.3f)
        assertEquals(-s.DIP_UP_DP, min, 0.3f)
    }

    @Test
    fun vesselScale_top() {
        assertEquals(2500, VesselScale.top(0f))
        assertEquals(2500, VesselScale.top(2000f))
        assertEquals(3000, VesselScale.top(2600f))
        var prev = 0
        var level = 0f
        while (level <= 6000f) {
            val top = VesselScale.top(level)
            assertTrue(top >= prev)
            assertTrue(top >= level + 250f)
            prev = top
            level += 10f
        }
    }

    @Test
    fun vesselScale_labelStep() {
        assertEquals(500, VesselScale.labelStep(2500))
        assertEquals(500, VesselScale.labelStep(3000))
        assertEquals(1000, VesselScale.labelStep(3500))
        assertEquals(1000, VesselScale.labelStep(5000))
    }

    @Test
    fun vesselScale_dragAmount() {
        assertEquals(0, VesselScale.dragAmount(500f, 1000))
        assertEquals(0, VesselScale.dragAmount(1000f, 1000))
        assertEquals(250, VesselScale.dragAmount(1250f, 1000))
        assertEquals(250, VesselScale.dragAmount(1252f, 1000))
        assertEquals(260, VesselScale.dragAmount(1257f, 1000))
        assertEquals(3000, VesselScale.dragAmount(9000f, 1000))
        assertEquals(0, VesselScale.dragAmount(0f, 0))
    }

    @Test
    fun roundRect_length() {
        val expected = 2f * (84f - 42f) + 2f * (548f - 42f) + 2f * PI.toFloat() * 21f
        assertEquals(expected, RoundRectPerimeter.length(84f, 548f, 21f), 1e-3f)
    }

    @Test
    fun roundRect_points() {
        val w = 84f
        val h = 548f
        val r = 21f
        val out = FloatArray(2)
        RoundRectPerimeter.point(0f, 0f, 0f, w, h, r, out)
        assertEquals(r, out[0], 1e-3f)
        assertEquals(0f, out[1], 1e-3f)

        val quarter = PI.toFloat() * r / 2f
        RoundRectPerimeter.point((w - 2 * r) + quarter, 0f, 0f, w, h, r, out)
        assertEquals(w, out[0], 1e-2f)
        assertEquals(r, out[1], 1e-2f)

        val len = RoundRectPerimeter.length(w, h, r)
        RoundRectPerimeter.point(len / 2f, 0f, 0f, w, h, r, out)
        assertTrue("y=${out[1]}", out[1] >= h - r - 0.01f)

        val end = FloatArray(2)
        RoundRectPerimeter.point(len, 0f, 0f, w, h, r, end)
        assertEquals(r, end[0], 1e-2f)
        assertEquals(0f, end[1], 1e-2f)
    }

    @Test
    fun ringWaveRunsClockwiseWithinCelebration() {
        val s = GoalCelebrationSpec
        assertEquals(0f, GoalCelebration.ringWaveBump(s.WAVE_START_MS, 0), 1e-4f)
        assertEquals(1f, GoalCelebration.ringWaveBump(s.WAVE_START_MS + s.WAVE_TICK_MS / 2, 0), 1e-3f)
        // Деление 30 стартует позже деления 0, последнее (59) заканчивается до конца анимации.
        assertEquals(0f, GoalCelebration.ringWaveBump(s.WAVE_START_MS + s.WAVE_TICK_MS / 2, 30), 1e-4f)
        val lastEnd = s.WAVE_START_MS + 59 * s.RING_WAVE_STEP_MS + s.WAVE_TICK_MS
        assertTrue(lastEnd <= s.TOTAL_MS)
        assertEquals(0f, GoalCelebration.ringWaveBump(lastEnd + 1f, 59), 1e-4f)
    }
}
