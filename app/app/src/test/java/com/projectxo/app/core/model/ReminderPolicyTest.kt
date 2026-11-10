package com.projectxo.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {
    private val hour = 60 * 60_000L
    private val r = Reminders(water = true, dailySummary = true, quietFromMin = 22 * 60, quietToMin = 8 * 60)

    private fun water(
        nowMin: Int = 14 * 60,
        total: Int = 500,
        goal: Long = 2000,
        lastWaterAgoMin: Int? = 180,
        lastReminderAgoMin: Int? = null,
        reminders: Reminders = r,
        enabled: Boolean = true,
    ): WaterReminderInput {
        val now = 100 * hour
        return WaterReminderInput(
            nowMin, now, reminders, enabled, total, goal,
            lastWaterAgoMin?.let { now - it * 60_000L }, lastReminderAgoMin?.let { now - it * 60_000L },
        )
    }

    @Test
    fun quietHours_wrapMidnight() {
        assertTrue(ReminderPolicy.inQuietHours(23 * 60, 22 * 60, 8 * 60))
        assertTrue(ReminderPolicy.inQuietHours(2 * 60, 22 * 60, 8 * 60))
        assertTrue(ReminderPolicy.inQuietHours(22 * 60, 22 * 60, 8 * 60))
        assertFalse(ReminderPolicy.inQuietHours(8 * 60, 22 * 60, 8 * 60))
        assertFalse(ReminderPolicy.inQuietHours(14 * 60, 22 * 60, 8 * 60))
    }

    @Test
    fun quietHours_sameDayRange_andEqualBounds() {
        assertTrue(ReminderPolicy.inQuietHours(13 * 60, 13 * 60, 15 * 60))
        assertFalse(ReminderPolicy.inQuietHours(15 * 60, 13 * 60, 15 * 60))
        assertFalse(ReminderPolicy.inQuietHours(3 * 60, 5 * 60, 5 * 60))
    }

    @Test
    fun water_remindsWhenLongSinceLastDrink() {
        assertTrue(ReminderPolicy.shouldRemindWater(water(lastWaterAgoMin = 130)))
        assertFalse(ReminderPolicy.shouldRemindWater(water(lastWaterAgoMin = 90)))
    }

    @Test
    fun water_notTwiceWithinTwoHours() {
        assertFalse(ReminderPolicy.shouldRemindWater(water(lastWaterAgoMin = 300, lastReminderAgoMin = 60)))
        assertTrue(ReminderPolicy.shouldRemindWater(water(lastWaterAgoMin = 300, lastReminderAgoMin = 121)))
    }

    @Test
    fun water_neverWhenGoalReachedOrOff() {
        assertFalse(ReminderPolicy.shouldRemindWater(water(total = 2000)))
        assertFalse(ReminderPolicy.shouldRemindWater(water(reminders = r.copy(water = false))))
        assertFalse(ReminderPolicy.shouldRemindWater(water(enabled = false)))
    }

    @Test
    fun water_neverInQuietHours() {
        assertFalse(ReminderPolicy.shouldRemindWater(water(nowMin = 23 * 60)))
        assertFalse(ReminderPolicy.shouldRemindWater(water(nowMin = 6 * 60)))
    }

    @Test
    fun water_noDrinkYet_waitsTwoHoursAfterWaking() {
        assertFalse(ReminderPolicy.shouldRemindWater(water(nowMin = 9 * 60, lastWaterAgoMin = null)))
        assertTrue(ReminderPolicy.shouldRemindWater(water(nowMin = 10 * 60, lastWaterAgoMin = null)))
    }

    @Test
    fun summary_onceAfterNinePm_notInQuiet() {
        assertFalse(ReminderPolicy.shouldSendSummary(20 * 60 + 59, r, null, 10))
        assertTrue(ReminderPolicy.shouldSendSummary(21 * 60, r, null, 10))
        assertFalse(ReminderPolicy.shouldSendSummary(21 * 60 + 30, r, 10, 10))
        assertTrue(ReminderPolicy.shouldSendSummary(21 * 60 + 30, r, 9, 10))
        assertFalse(ReminderPolicy.shouldSendSummary(22 * 60 + 15, r, null, 10))
        assertFalse(ReminderPolicy.shouldSendSummary(21 * 60, r.copy(dailySummary = false), null, 10))
    }

    @Test
    fun summary_quietHoursStartingEarlierBlocksIt() {
        val early = r.copy(quietFromMin = 21 * 60)
        assertFalse(ReminderPolicy.shouldSendSummary(21 * 60 + 5, early, null, 10))
    }

    @Test
    fun anyEnabled_needsEnabledModuleForWater() {
        assertTrue(ReminderPolicy.anyEnabled(r, Module.entries.toSet()))
        assertTrue(ReminderPolicy.anyEnabled(r.copy(dailySummary = false), Module.entries.toSet()))
        assertFalse(ReminderPolicy.anyEnabled(r.copy(dailySummary = false), setOf(Module.Food)))
        assertFalse(ReminderPolicy.anyEnabled(Reminders(water = false, dailySummary = false), Module.BASE))
        assertTrue(ReminderPolicy.anyEnabled(Reminders(water = false, dailySummary = false), setOf(Module.Habits)))
        assertEquals(120, ReminderPolicy.WATER_GAP_MIN)
    }
}
