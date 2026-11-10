package com.projectxo.app.core.motion

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkFlowTest {

    private val p = InkFlowParticles(seed = 7)
    private val n = InkFlowSpec.COUNT

    @Test
    fun sameSeedGivesSameArrays() {
        val a = InkFlowParticles(42)
        val b = InkFlowParticles(42)
        assertArrayEquals(a.delay, b.delay, 0f)
        assertArrayEquals(a.startX, b.startX, 0f)
        assertArrayEquals(a.startY, b.startY, 0f)
        assertArrayEquals(a.endX, b.endX, 0f)
        assertArrayEquals(a.endY, b.endY, 0f)
        assertArrayEquals(a.curve, b.curve, 0f)
        assertArrayEquals(a.radius, b.radius, 0f)
        assertEquals(a.side, b.side, 0f)
    }

    @Test
    fun differentSeedsGiveDifferentArrays() {
        val a = InkFlowParticles(1)
        val b = InkFlowParticles(2)
        assertFalse(a.startX.contentEquals(b.startX))
        assertFalse(a.endY.contentEquals(b.endY))
        assertNotEquals(a.delay.toList(), b.delay.toList())
    }

    @Test
    fun delaysAreInRangeAndGrow() {
        assertTrue(p.delay.all { it >= 0f && it <= InkFlowSpec.SPREAD_MS })
        assertTrue("first=${p.delay[0]}", p.delay[0] <= 10f)
        val firstHalf = p.delay.take(n / 2).average()
        val secondHalf = p.delay.drop(n / 2).average()
        assertTrue("$firstHalf vs $secondHalf", firstHalf < secondHalf)
    }

    @Test
    fun radiiAndCloudAreBounded() {
        assertTrue(p.radius.all { it >= InkFlowSpec.MIN_RADIUS_DP && it <= InkFlowSpec.MAX_RADIUS_DP })
        for (i in 0 until n) {
            assertTrue(p.startX[i] * p.startX[i] + p.startY[i] * p.startY[i] <= 1.0001f)
        }
    }

    @Test
    fun dotIsHiddenBeforeAndAfterFlight() {
        val out = InkDot()
        for (i in 0 until n) {
            val d = p.delay[i]
            assertFalse(InkFlowMath.dot(p, i, d, 0f, 0f, 1000f, 0f, 1f, out))
            assertFalse(InkFlowMath.dot(p, i, d - 10f, 0f, 0f, 1000f, 0f, 1f, out))
            assertFalse(InkFlowMath.dot(p, i, d + InkFlowSpec.TRAVEL_MS, 0f, 0f, 1000f, 0f, 1f, out))
            assertFalse(InkFlowMath.dot(p, i, d + InkFlowSpec.TRAVEL_MS + 50f, 0f, 0f, 1000f, 0f, 1f, out))
        }
    }

    @Test
    fun dotLandsNearTargetAndFadesOut() {
        val out = InkDot()
        val dp = 2f
        val tx = 300f
        val ty = 500f
        for (i in 0 until n) {
            val t = p.delay[i] + InkFlowSpec.TRAVEL_MS * 0.999f
            assertTrue(InkFlowMath.dot(p, i, t, 10f, 20f, tx, ty, dp, out))
            val dist = hypot(out.x - tx, out.y - ty)
            assertTrue("i=$i dist=$dist", dist <= InkFlowSpec.END_SPREAD_DP * dp + 1f)
            assertTrue("i=$i alpha=${out.alpha}", out.alpha < 0.05f)
        }
    }

    @Test
    fun dotIsOpaqueMidFlight() {
        val out = InkDot()
        for (i in 0 until n) {
            val t = p.delay[i] + InkFlowSpec.TRAVEL_MS * 0.5f
            assertTrue(InkFlowMath.dot(p, i, t, 0f, 0f, 1000f, 0f, 1f, out))
            assertEquals(1f, out.alpha, 1e-4f)
        }
    }

    @Test
    fun pathIsCurvedNotStraight() {
        val out = InkDot()
        for (i in 0 until n) {
            val t = p.delay[i] + InkFlowSpec.TRAVEL_MS * 0.5f
            assertTrue(InkFlowMath.dot(p, i, t, 0f, 0f, 1000f, 0f, 1f, out))
            // Прямая «старт — финиш» лежит на оси X, расстояние до неё это |y|.
            assertTrue("i=$i off=${abs(out.y)}", abs(out.y) > 0.05f * 1000f)
        }
    }
}
