package com.projectxo.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SharedMathTest {
    private val today = LocalDate.of(2026, 11, 11) // среда
    private val all = HabitMath.ALL_DAYS
    private fun days(vararg back: Int) = back.map { today.minusDays(it.toLong()) }.toSet()

    @Test fun streakCountsOnlyDaysBothDid() {
        assertEquals(0, SharedMath.streak(emptySet(), emptySet(), all, today))
        assertEquals(3, SharedMath.streak(days(0, 1, 2), days(0, 1, 2), all, today))
        assertEquals(0, SharedMath.streak(days(0, 1, 2), days(5), all, today))
    }

    @Test fun todayWaitingDoesNotBreakTheStreak() {
        assertEquals(2, SharedMath.streak(days(1, 2), days(1, 2), all, today))
        assertEquals(2, SharedMath.streak(days(1, 2, 0), days(1, 2), all, today)) // друг сегодня ещё нет
        assertEquals(3, SharedMath.streak(days(1, 2, 0), days(1, 2, 0), all, today))
    }

    @Test fun oneMissIsForgivenTwoAreNot() {
        assertEquals(4, SharedMath.streak(days(0, 1, 3, 4), days(0, 1, 3, 4), all, today)) // пропуск 2
        assertEquals(2, SharedMath.streak(days(0, 1, 4, 5), days(0, 1, 4, 5), all, today)) // пропуск 2 и 3
    }

    @Test fun restDaysAreNotMisses() {
        val monWedFri = 0b0010101
        // 11 ноября — среда. Предыдущие плановые дни: понедельник 9, пятница 6.
        val d = setOf(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 6))
        assertEquals(3, SharedMath.streak(d, d, monWedFri, today))
    }

    @Test fun bestStreakLooksBack() {
        val both = days(0, 1, 20, 21, 22, 23, 24)
        assertEquals(5, SharedMath.bestStreak(both, both, all, today))
        assertEquals(0, SharedMath.bestStreak(emptySet(), emptySet(), all, today))
        assertTrue(SharedMath.bestStreak(both, both, all, today) >= SharedMath.streak(both, both, all, today))
    }

    @Test fun maskAndFreqRoundTrip() {
        assertEquals(HabitMath.ALL_DAYS, SharedMath.maskOf(HabitFreq.Daily))
        assertEquals(HabitMath.ALL_DAYS, SharedMath.maskOf(HabitFreq.PerWeek(3)))
        assertEquals(0b0010101, SharedMath.maskOf(HabitFreq.Days(0b0010101)))
        assertEquals(HabitMath.ALL_DAYS, SharedMath.maskOf(HabitFreq.Days(0)))
        assertEquals(HabitFreq.Daily, SharedMath.freqOf(127))
        assertEquals(HabitFreq.Days(0b0000011), SharedMath.freqOf(0b0000011))
    }

    @Test fun lastDaysEndToday() {
        val d = SharedMath.lastDays(today)
        assertEquals(7, d.size)
        assertEquals(today, d.last())
        assertEquals(today.minusDays(6), d.first())
        assertEquals(listOf(true, false, true, false, false, false, true), SharedMath.flags(d, setOf(d[0], d[2], d[6])))
    }

    @Test fun codeFormatting() {
        assertEquals("7K4M2Q", SharedMath.normalizeCode(" 7k4-m2q "))
        assertEquals("ABCDEF", SharedMath.normalizeCode("abc-def-ghi"))
        assertEquals("7K4-M2Q", SharedMath.prettyCode("7K4M2Q"))
        assertEquals("ABC", SharedMath.prettyCode("ABC"))
    }

    @Test fun nickIsCleaned() {
        assertEquals("Алекс Иван", SharedMath.cleanNick("  Алекс \n  Иван "))
        assertEquals(SharedMath.MAX_NICK, SharedMath.cleanNick("н".repeat(50)).length)
        assertEquals("", SharedMath.cleanNick("   "))
    }

    @Test fun togetherIsIntersection() {
        assertEquals(days(1), SharedMath.together(days(0, 1), days(1, 2)))
    }

    @Test fun defaultViewIsIntersection() {
        assertEquals(SharedView.Intersection, Wave2Settings().sharedView)
    }
}
