package com.projectxo.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CelebrationsTest {
    private val today = LocalDate.of(2026, 11, 11)

    @Test fun flameLevels() {
        assertEquals(0, StreakFlame.level(2))
        assertEquals(1, StreakFlame.level(3))
        assertEquals(1, StreakFlame.level(9))
        assertEquals(2, StreakFlame.level(10))
        assertEquals(3, StreakFlame.level(30))
        assertEquals(3, StreakFlame.level(400))
    }

    @Test fun balanceStreakCountsTodayWhenClosed() {
        val m = (0..4).associate { today.minusDays(it.toLong()) to 100 }
        assertEquals(5, BalanceStreak.count(m, today))
        assertFalse(BalanceStreak.waiting(m, today))
    }

    @Test fun balanceStreakWaitsForToday() {
        val m = mapOf(today to 40, today.minusDays(1) to 100, today.minusDays(2) to 100, today.minusDays(3) to 70)
        assertEquals(2, BalanceStreak.count(m, today))
        assertTrue(BalanceStreak.waiting(m, today))
    }

    @Test fun balanceStreakBreaksOnGap() {
        val m = mapOf(today.minusDays(2) to 100)
        assertEquals(0, BalanceStreak.count(m, today))
    }

    @Test fun fireworkThresholds() {
        assertEquals(0, BalanceFirework.crossed(10, 49))
        assertEquals(50, BalanceFirework.crossed(49, 50))
        assertEquals(80, BalanceFirework.crossed(40, 85))
        assertEquals(100, BalanceFirework.crossed(79, 100))
        assertEquals(0, BalanceFirework.crossed(100, 100))
        assertEquals(0, BalanceFirework.crossed(90, 60))
        assertTrue(BalanceFirework.bursts(100) > BalanceFirework.bursts(80))
        assertTrue(BalanceFirework.bursts(80) > BalanceFirework.bursts(50))
    }
}
