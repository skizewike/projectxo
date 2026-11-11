package com.projectxo.app.core.model

import com.projectxo.app.core.data.DayLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekAndCsvTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val goals = HistoryGoals(waterMl = 2000, kcal = 2000, steps = 8000)
    private val modules = setOf(Module.Water, Module.Steps)

    private fun full() = DayTotals(waterMl = 2000, steps = 8000)

    @Test
    fun week_averageBestAndComplete() {
        val totals = mapOf(
            today to full(),
            today.minusDays(1) to DayTotals(waterMl = 1000, steps = 4000), // 50 %
        )
        val w = WeekSummaryBuilder.build(today, today.minusDays(6), totals, goals, modules)!!
        assertEquals(7, w.days)
        assertEquals(1, w.daysComplete)
        // (100 + 50 + 0 * 5) / 7 = 21,4 → 21
        assertEquals(21, w.averagePercent)
        assertEquals(today, w.bestDay)
        assertEquals(100, w.bestPercent)
        assertEquals(listOf(Module.Water, Module.Steps), w.perModule.map { it.module })
        assertNull(w.previousPercent)
    }

    @Test
    fun week_newUserCountsOnlyDaysSinceStart() {
        val w = WeekSummaryBuilder.build(today, today.minusDays(1), mapOf(today to full()), goals, modules)!!
        assertEquals(2, w.days)
        assertEquals(50, w.averagePercent)
    }

    @Test
    fun week_previousWeekIsNeutralNumber() {
        val totals = (0..13).associate { today.minusDays(it.toLong()) to if (it < 7) full() else DayTotals(waterMl = 1000, steps = 4000) }
        val w = WeekSummaryBuilder.build(today, today.minusDays(20), totals, goals, modules)!!
        assertEquals(100, w.averagePercent)
        assertEquals(50, w.previousPercent)
        assertEquals(7, w.daysComplete)
    }

    @Test
    fun week_noDataBeforeStartOrNoModules() {
        assertNull(WeekSummaryBuilder.build(today, today.plusDays(1), emptyMap(), goals, modules))
        assertNull(WeekSummaryBuilder.build(today, today, emptyMap(), goals, emptySet()))
    }

    @Test
    fun csv_headerAndRows() {
        val from = today.minusDays(1)
        val csv = CsvExport.build(
            from, today,
            mapOf(today to DayTotals(waterMl = 1500, kcal = 1800, steps = 5000, sleepMin = 450, habitsDone = 2, habitsTotal = 3, workoutsDone = 1)),
            mapOf(today to DayLog(today, feel = 3, tags = setOf("tired", "calm"), weightKg = 71.84, note = "ok")),
        )
        val lines = csv.removePrefix(CsvExport.BOM).trimEnd().split("\r\n")
        assertEquals(CsvExport.HEADER.joinToString(","), lines[0])
        assertEquals("2026-11-09,0,0,0,0,0,0,0,0,0,,,,", lines[1])
        assertEquals("2026-11-10,1500,1800,5000,450,0,0,2,3,1,3,71.8,calm;tired,ok", lines[2])
        assertTrue(csv.startsWith(CsvExport.BOM))
    }

    @Test
    fun csv_escapesCommasQuotesNewlinesAndFormulas() {
        assertEquals("\"a,b\"", CsvExport.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.cell("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvExport.cell("line1\nline2"))
        assertEquals("'=SUM(A1)", CsvExport.cell("=SUM(A1)"))
        assertEquals("'@cmd", CsvExport.cell("@cmd"))
        assertEquals("-5", CsvExport.cell("-5"))
        assertEquals("'-cmd", CsvExport.cell("-cmd"))
        assertEquals("plain", CsvExport.cell("plain"))
    }

    private val weekly = Reminders(weeklySummary = true)

    @Test
    fun weeklyReminder_onlySundayEveningOncePerDay() {
        val sun = java.time.DayOfWeek.SUNDAY
        assertTrue(ReminderPolicy.shouldSendWeekly(19 * 60, sun, weekly, null, 100))
        assertTrue(!ReminderPolicy.shouldSendWeekly(18 * 60 + 59, sun, weekly, null, 100))
        assertTrue(!ReminderPolicy.shouldSendWeekly(20 * 60, java.time.DayOfWeek.SATURDAY, weekly, null, 100))
        assertTrue(!ReminderPolicy.shouldSendWeekly(20 * 60, sun, weekly, 100, 100))
        assertTrue(ReminderPolicy.shouldSendWeekly(20 * 60, sun, weekly, 93, 100))
        assertTrue(!ReminderPolicy.shouldSendWeekly(20 * 60, sun, Reminders(), null, 100))
    }

    @Test
    fun weeklyReminder_respectsQuietHours_andKeepsWorkerAlive() {
        val sun = java.time.DayOfWeek.SUNDAY
        val quietEvening = weekly.copy(quietFromMin = 19 * 60, quietToMin = 8 * 60)
        assertTrue(!ReminderPolicy.shouldSendWeekly(20 * 60, sun, quietEvening, null, 100))
        assertTrue(ReminderPolicy.anyEnabled(Reminders(water = false, weeklySummary = true), emptySet()))
    }
}
