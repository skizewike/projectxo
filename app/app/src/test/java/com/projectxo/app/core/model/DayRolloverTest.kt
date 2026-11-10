package com.projectxo.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DayRolloverTest {
    @Test
    fun farFromMidnight_checksEveryMinute() {
        assertEquals(60_000L, DayRollover.nextCheckDelayMs(12 * 3_600_000L))
    }

    @Test
    fun nearMidnight_waitsExactlyToIt() {
        val msOfDay = DayRollover.DAY_MS - 10_000L
        assertEquals(10_500L, DayRollover.nextCheckDelayMs(msOfDay))
    }

    @Test
    fun justAfterMidnight_neverZero() {
        assertEquals(60_000L, DayRollover.nextCheckDelayMs(0L))
        assertEquals(500L, DayRollover.nextCheckDelayMs(DayRollover.DAY_MS + 5_000L))
    }
}
