package com.projectxo.app.core.model

import com.projectxo.app.core.data.DayLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ProfileStatsTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val goals = HistoryGoals(waterMl = 2000, kcal = 2000, steps = 8000)

    @Test
    fun rangeStart_clipsToFirstDay() {
        val start = today.minusDays(3)
        assertEquals(start, ProfileStats.rangeStart(ProfilePeriod.Week, start, today))
        assertEquals(start, ProfileStats.rangeStart(ProfilePeriod.All, start, today))
        val old = today.minusDays(100)
        assertEquals(today.minusDays(6), ProfileStats.rangeStart(ProfilePeriod.Week, old, today))
        assertEquals(today.minusDays(29), ProfileStats.rangeStart(ProfilePeriod.Month, old, today))
        assertEquals(old, ProfileStats.rangeStart(ProfilePeriod.All, old, today))
    }

    @Test
    fun rangeStart_firstDayIsToday_givesSingleDay() {
        assertEquals(listOf(today), ProfileStats.datesBetween(today, today))
        assertEquals(today, ProfileStats.rangeStart(ProfilePeriod.Month, today, today))
    }

    @Test
    fun movingAverage_usesOnlyMarkedDaysInWindow() {
        val v = listOf(70.0, null, 72.0, null, null, null, null, null, 74.0)
        val t = ProfileStats.movingAverage(v, window = 3)
        assertEquals(70.0, t[0]!!, 1e-9)
        assertEquals(70.0, t[1]!!, 1e-9)
        assertEquals(71.0, t[2]!!, 1e-9)
        assertEquals(72.0, t[3]!!, 1e-9)
        assertNull(t[5])
        assertEquals(74.0, t[8]!!, 1e-9)
    }

    @Test
    fun feelCounts_andHalves() {
        val feels = listOf(0, 1, null, 3, 4, 4)
        assertEquals(listOf(1, 1, 0, 1, 2), ProfileStats.feelCounts(feels))
        val (first, second) = ProfileStats.goodShareHalves(feels)
        assertEquals(0f, first!!, 0f)
        assertEquals(1f, second!!, 0f)
        assertNull(ProfileStats.goodShareHalves(listOf(null, null)).first)
    }

    @Test
    fun build_slicesPeriod_keepsTrendFromFullHistory() {
        val start = today.minusDays(20)
        val logs = mapOf(
            start to DayLog(start, weightKg = 80.0, feel = 2),
            today.minusDays(2) to DayLog(today.minusDays(2), weightKg = 78.0),
        )
        val totals = mapOf(
            today to DayTotals(waterMl = 3000, kcal = 1500, steps = 9000),
            today.minusDays(1) to DayTotals(waterMl = 1000, kcal = 0, steps = 100),
        )
        val d = ProfileStats.build(ProfilePeriod.Week, start, today, totals, logs, goals)
        assertEquals(7, d.dates.size)
        assertEquals(today.minusDays(6), d.dates.first())
        assertEquals(today, d.dates.last())
        // Вода: цель 2000 → 0,5 и потолок 1,1
        assertEquals(0.5f, d.waterRatio[5], 0f)
        assertEquals(1.1f, d.waterRatio[6], 0f)
        assertEquals(9000, d.steps.last())
        assertEquals(78.0, d.weightKg[4]!!, 0f.toDouble())
        // Тренд за период опирается на весь ряд: в окне 7 дней до среды есть и 78, но 80 уже выпал.
        assertEquals(78.0, d.weightTrend[4]!!, 1e-9)
        assertEquals(78.0, d.latestWeightKg!!, 0.0)
        assertEquals(80.0, d.firstWeightKg!!, 0.0)
    }

    @Test
    fun build_newUser_oneDay_noData() {
        val d = ProfileStats.build(ProfilePeriod.All, today, today, emptyMap(), emptyMap(), goals)
        assertEquals(1, d.dates.size)
        assertNull(d.latestWeightKg)
        assertEquals(listOf(0), d.steps)
        assertEquals(listOf(0f), d.waterRatio)
    }
}
