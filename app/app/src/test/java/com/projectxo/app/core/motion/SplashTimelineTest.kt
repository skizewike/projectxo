package com.projectxo.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashTimelineTest {

    private val target = RingTarget(cx = 540f, cy = 700f, radius = 262f, stroke = 31f)
    private val g = SplashGeometry(
        xCx = 427f, xCy = 1100f, xHalf = 80f, xStroke = 26f,
        oCx = 647f, oCy = 1100f, oRadius = 80.7f, oStroke = 27.6f,
        target = target, ratio = 0.72f,
    )
    private val eps = 1e-3f

    @Test
    fun fullStartsEmpty() {
        val f = SplashTimeline.frame(0f, SplashMode.Full, reduced = false, g = g)
        assertEquals(0f, f.stroke1, eps)
        assertEquals(0f, f.stroke2, eps)
        assertEquals(0f, f.arcEnd, eps)
        assertEquals(g.oCx, f.ringCx, eps)
        assertTrue(f.home.all { it == 0f })
    }

    @Test
    fun fullLogoIsWrittenAndLoadedBeforeExit() {
        val f = SplashTimeline.frame(1720f, SplashMode.Full, reduced = false, g = g)
        assertEquals(1f, f.stroke1, eps)
        assertEquals(1f, f.stroke2, eps)
        assertEquals(1f, f.arcEnd, eps)
        assertEquals(g.oCx, f.ringCx, eps)
    }

    @Test
    fun fullHonestPauseAt55Percent() {
        val f = SplashTimeline.frame(900f, SplashMode.Full, reduced = false, g = g)
        assertEquals(0.55f, f.arcEnd, eps)
    }

    @Test
    fun arcNeverRunsAheadOfRealLoading() {
        val f = SplashTimeline.frame(1600f, SplashMode.Full, reduced = false, g = g, loadShown = 0.3f)
        assertEquals(0.3f, f.arcEnd, eps)
    }

    @Test
    fun fullEndsExactlyOnTheRing() {
        val f = SplashTimeline.frame(SplashTimings.FULL_MS, SplashMode.Full, reduced = false, g = g)
        assertEquals(target.cx, f.ringCx, eps)
        assertEquals(target.cy, f.ringCy, eps)
        assertEquals(target.radius, f.ringRadius, eps)
        assertEquals(target.stroke, f.ringStroke, eps)
        assertEquals(0.72f, f.arcEnd, eps)
        assertEquals(0f, f.stroke1, eps)
        assertEquals(0f, f.stroke2, eps)
        assertTrue(f.home.all { it == 1f })
    }

    @Test
    fun ringFlightIsAnArcNotALine() {
        // Разные пружины по X и Y: в середине полёта доли пройденного пути по осям различаются.
        val f = SplashTimeline.frame(1950f, SplashMode.Full, reduced = false, g = g)
        val px = (f.ringCx - g.oCx) / (target.cx - g.oCx)
        val py = (f.ringCy - g.oCy) / (target.cy - g.oCy)
        assertTrue("px=$px py=$py", px - py > 0.05f)
    }

    @Test
    fun shortStartsFromFullLogoAndEndsOnRing() {
        val start = SplashTimeline.frame(0f, SplashMode.Short, reduced = false, g = g)
        assertEquals(1f, start.stroke1, eps)
        assertEquals(1f, start.arcEnd, eps)
        val end = SplashTimeline.frame(SplashTimings.SHORT_MS, SplashMode.Short, reduced = false, g = g)
        assertEquals(target.cx, end.ringCx, eps)
        assertEquals(target.cy, end.ringCy, eps)
        assertEquals(0.72f, end.arcEnd, eps)
        assertTrue(end.home.all { it == 1f })
    }

    @Test
    fun reducedMotionHasNoMovement() {
        val hold = SplashTimeline.frame(100f, SplashMode.Full, reduced = true, g = g)
        assertEquals(g.oCx, hold.ringCx, eps)
        assertEquals(1f, hold.stageAlpha, eps)
        val fadedOut = SplashTimeline.frame(449.9f, SplashMode.Full, reduced = true, g = g)
        assertTrue(fadedOut.stageAlpha < 0.01f)
        val end = SplashTimeline.frame(SplashTimings.REDUCED_MS, SplashMode.Full, reduced = true, g = g)
        assertEquals(target.cx, end.ringCx, eps)
        assertEquals(1f, end.stageAlpha, eps)
    }

    @Test
    fun effectiveTimeWaitsForData() {
        assertEquals(1000f, SplashTimeline.effectiveTime(1000f, readyAt = null), eps)
        assertEquals(1700f, SplashTimeline.effectiveTime(2500f, readyAt = null), eps)
        // Готово рано: сдвига нет.
        assertEquals(2000f, SplashTimeline.effectiveTime(2000f, readyAt = 400f), eps)
        // Готово в 2 000 мс: выход начинается в 2 050, то есть сценарное 1 750.
        assertEquals(1750f, SplashTimeline.effectiveTime(2050f, readyAt = 2000f), eps)
    }

    @Test
    fun policyAlwaysFullSplash() {
        assertEquals(SplashMode.Full, SplashPolicy.decide())
    }

    @Test
    fun springSettlesExactly() {
        assertEquals(0f, springProgress(0f, SpringParams(1f, 120f)), eps)
        assertEquals(1f, springProgress(2000f, SpringParams(0.82f, 300f)), 0f)
        assertEquals(1f, Curves.Standard.transform(1f), 0f)
        assertEquals(0.5f, Bezier(0f, 0f, 1f, 1f).transform(0.5f), eps)
    }

    private fun full(t: Float) = SplashTimeline.frame(t, SplashMode.Full, reduced = false, g = g)

    @Test
    fun tagTypesEvenlyBetweenStartAndEnd() {
        assertEquals(0f, full(499f).tagTyped, eps)
        assertEquals(0f, full(300f).tagTyped, eps)
        assertEquals(1f, full(1040f).tagTyped, eps)
        assertEquals(0.5f, full(770f).tagTyped, 0.01f)
    }

    @Test
    fun cursorBlinksAfterTyping() {
        val f = SplashTimings.Full
        assertEquals(0f, full(f.CURSOR_START - 1f).cursorAlpha, eps)
        assertEquals(1f, full(700f).cursorAlpha, eps)
        assertEquals(0f, full(f.CURSOR_END).cursorAlpha, eps)
        assertEquals(0f, full(1600f).cursorAlpha, eps)
        val samples = (1041..1439 step 10).map { full(it.toFloat()).cursorAlpha }
        assertTrue(samples.any { it == 0f })
        assertTrue(samples.any { it == 1f })
    }

    @Test
    fun nibGlowsWhileWritingAndCoolsDown() {
        val f = SplashTimings.Full
        assertEquals(0f, full(40f).nib1, eps)
        assertEquals(1f, full(300f).nib1, eps)
        assertEquals(0f, full(f.stroke1.endInclusive + f.NIB_COOL).nib1, eps)
        val mid = full(f.stroke1.endInclusive + f.NIB_COOL / 2f).nib1
        assertTrue("mid=$mid", mid > 0f && mid < 1f)
        assertEquals(0f, full(f.EXIT_START).nib1, eps)
        assertEquals(0f, full(f.EXIT_START + 50f).nib1, eps)
        assertEquals(1f, full(400f).nib2, eps)
    }

    @Test
    fun shortSplashHasNoTypingOrNib() {
        for (t in listOf(0f, 100f, 400f, 800f)) {
            val fr = SplashTimeline.frame(t, SplashMode.Short, reduced = false, g = g)
            assertEquals(0f, fr.nib1, 0f)
            assertEquals(0f, fr.nib2, 0f)
            assertEquals(0f, fr.tagTyped, 0f)
            assertEquals(0f, fr.cursorAlpha, 0f)
        }
    }
}
