package com.projectxo.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BreathingMathTest {
    @Test
    fun cycleLengths() {
        assertEquals(8, BreathRhythm.Even.cycleSeconds)
        assertEquals(16, BreathRhythm.Box.cycleSeconds)
        assertEquals(10, BreathRhythm.LongOut.cycleSeconds)
    }

    @Test
    fun stateAt_walksThroughPhases() {
        val r = BreathRhythm.Box
        assertEquals(BreathKind.In, BreathingMath.stateAt(r, 0f).phase.kind)
        assertEquals(BreathKind.Hold, BreathingMath.stateAt(r, 4.5f).phase.kind)
        assertEquals(BreathKind.Out, BreathingMath.stateAt(r, 9f).phase.kind)
        assertEquals(BreathKind.HoldEmpty, BreathingMath.stateAt(r, 13f).phase.kind)
        val second = BreathingMath.stateAt(r, 17f)
        assertEquals(1, second.cycle)
        assertEquals(BreathKind.In, second.phase.kind)
    }

    @Test
    fun level_risesOnInhale_fallsOnExhale() {
        val r = BreathRhythm.Even
        val start = BreathingMath.level(BreathingMath.stateAt(r, 0f))
        val top = BreathingMath.level(BreathingMath.stateAt(r, 3.99f))
        val half = BreathingMath.level(BreathingMath.stateAt(r, 6f))
        assertEquals(BreathingMath.LOW, start, 0.001f)
        assertEquals(1f, top, 0.02f)
        assertTrue(half in 0.3f..0.8f)
        assertEquals(1f, BreathingMath.level(BreathingMath.stateAt(BreathRhythm.Box, 5f)), 0f)
        assertEquals(BreathingMath.LOW, BreathingMath.level(BreathingMath.stateAt(BreathRhythm.Box, 14f)), 0f)
    }

    @Test
    fun cyclesInSession() {
        assertEquals(22, BreathingMath.cyclesIn(BreathRhythm.Even, 3))
        assertEquals(18, BreathingMath.cyclesIn(BreathRhythm.LongOut, 3))
        assertEquals(BreathRhythm.Even, BreathRhythm.fromId("unknown"))
    }
}

class SleepFastingMathTest {
    @Test
    fun sleepGoal_wrapsMidnight() {
        assertEquals(8 * 60, SleepMath.goalMinutes(23 * 60, 7 * 60))
        assertEquals(7 * 60 + 30, SleepMath.goalMinutes(0, 7 * 60 + 30))
    }

    @Test
    fun shortNight_isNotValid() {
        assertFalse(SleepMath.isValidNight(0, 9 * 60_000L))
        assertTrue(SleepMath.isValidNight(0, 10 * 60_000L))
    }

    @Test
    fun regularity_acrossMidnight() {
        // 23:30 и 00:30 — час разницы, а не 23 часа.
        val (avg, sd) = SleepMath.regularity(listOf(23 * 60 + 30, 30))!!
        assertEquals(0, avg % 60 - 0 + (avg % 60)) // 0:00
        assertEquals(30, sd)
        assertNull(SleepMath.regularity(listOf(23 * 60)))
    }

    @Test
    fun fastingBounds() {
        val now = 100 * 3_600_000L
        assertEquals(now - 24 * 3_600_000L, FastingMath.startBounds(now).first)
        assertEquals(now, FastingMath.startBounds(now).last)
        val start = now - 5 * 3_600_000L
        assertEquals(start + 60_000L, FastingMath.endBounds(start, now).first)
        assertEquals(now, FastingMath.endBounds(start, now).last)
        assertTrue(FastingMath.goalReached(start, now, 4))
        assertFalse(FastingMath.goalReached(start, now, 16))
        assertEquals(start + 16 * 3_600_000L, FastingMath.eatingWindowStart(start, 16))
    }
}

class HabitMathTest {
    private val today = LocalDate.of(2026, 11, 11) // среда
    private fun habit(freq: HabitFreq = HabitFreq.Daily, target: Int = 1) = Habit(1, "x", freq, target = target)
    private fun checks(vararg back: Int) = back.map { HabitCheck(1, today.minusDays(it.toLong()), 1) }

    @Test
    fun streak_countsConsecutiveDays_toleratesOneMiss() {
        val h = habit()
        // сегодня, вчера, пропуск, позавчера-3
        val m = HabitMath.Marks(h, checks(0, 1, 3, 4))
        assertEquals(4, HabitMath.streak(h, m, today))
    }

    @Test
    fun streak_breaksAfterTwoMisses() {
        val h = habit()
        val m = HabitMath.Marks(h, checks(0, 1, 4, 5))
        assertEquals(2, HabitMath.streak(h, m, today))
    }

    @Test
    fun streak_todayNotDoneYet_doesNotBreakSeries() {
        val h = habit()
        val m = HabitMath.Marks(h, checks(1, 2, 3))
        assertEquals(3, HabitMath.streak(h, m, today))
    }

    @Test
    fun bestStreak_atLeastCurrent() {
        val h = habit()
        val m = HabitMath.Marks(h, checks(0, 1, 2, 10, 11, 12, 13, 14))
        assertEquals(5, HabitMath.bestStreak(h, m, today))
        assertTrue(HabitMath.bestStreak(h, m, today) >= HabitMath.streak(h, m, today))
    }

    @Test
    fun daysFrequency_plannedOnlyOnSelectedDays() {
        val f = HabitFreq.Days(HabitMath.WORKOUT_DAYS) // пн, ср, пт
        assertTrue(HabitMath.planned(f, today)) // среда
        assertFalse(HabitMath.planned(f, today.plusDays(1))) // четверг
        assertEquals(3, HabitMath.weekTarget(f))
    }

    @Test
    fun perWeek_dueUntilWeeklyTargetReached() {
        val h = habit(HabitFreq.PerWeek(2))
        val mon = today.minusDays(2)
        val partial = HabitMath.Marks(h, listOf(HabitCheck(1, mon, 1)))
        assertTrue(HabitMath.dueOn(h, partial, today))
        val full = HabitMath.Marks(h, listOf(HabitCheck(1, mon, 1), HabitCheck(1, mon.plusDays(1), 1)))
        assertFalse(HabitMath.dueOn(h, full, today))
        assertEquals(2, HabitMath.weekCount(full, today))
    }

    @Test
    fun counter_doneOnlyAtTarget() {
        val h = habit(target = 3)
        val m = HabitMath.Marks(h, listOf(HabitCheck(1, today, 2)))
        assertFalse(m.done(today))
        assertEquals(2, m.value(today))
        assertTrue(HabitMath.Marks(h, listOf(HabitCheck(1, today, 3))).done(today))
    }

    @Test
    fun percent30_andDayProgress() {
        val h = habit()
        val m = HabitMath.Marks(h, checks(*IntArray(15) { it }))
        assertEquals(50, HabitMath.percent30(h, m, today))
        val (done, total) = HabitMath.dayProgress(listOf(h, habit(HabitFreq.Days(HabitMath.dayBit(today.dayOfWeek.plus(1)))).copy(id = 2)), checks(0), today)
        assertEquals(1, done)
        assertEquals(1, total)
        assertNotNull(HabitMath.dayBit(today.dayOfWeek))
    }
}
