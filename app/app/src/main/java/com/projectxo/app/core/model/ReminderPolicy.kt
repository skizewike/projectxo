package com.projectxo.app.core.model

/** Что известно о воде на момент проверки. Время — минуты от полуночи и миллисекунды UTC. */
data class WaterReminderInput(
    val nowMin: Int,
    val nowMs: Long,
    val reminders: Reminders,
    val waterEnabled: Boolean,
    val totalMl: Int,
    val goalMl: Long,
    /** Время последней записи воды за сегодня или null. */
    val lastWaterMs: Long?,
    /** Время последнего показанного напоминания или null. */
    val lastReminderMs: Long?,
)

/**
 * Когда напоминать (Р58, docs/PRODUCT.md, «Напоминания»). Мягко: не чаще раза в 2 часа, только если давно
 * не отмечали воду, цель дня не выполнена и сейчас не тихие часы. Без напоминаний о «невыполненном».
 */
object ReminderPolicy {
    const val WATER_GAP_MIN = 120
    const val SUMMARY_AT_MIN = 21 * 60
    const val DAY_MIN = 24 * 60

    /** Тихие часы по кругу суток: 22:00–08:00 включает полночь. Равные границы — тихих часов нет. */
    fun inQuietHours(nowMin: Int, fromMin: Int, toMin: Int): Boolean = when {
        fromMin == toMin -> false
        fromMin < toMin -> nowMin in fromMin until toMin
        else -> nowMin >= fromMin || nowMin < toMin
    }

    fun shouldRemindWater(i: WaterReminderInput): Boolean {
        val r = i.reminders
        if (!r.water || !i.waterEnabled) return false
        if (i.goalMl <= 0 || i.totalMl >= i.goalMl) return false
        if (inQuietHours(i.nowMin, r.quietFromMin, r.quietToMin)) return false
        val sinceReminder = i.lastReminderMs?.let { (i.nowMs - it) / MS_PER_MIN }
        if (sinceReminder != null && sinceReminder < WATER_GAP_MIN) return false
        val sinceWater = i.lastWaterMs?.let { (i.nowMs - it) / MS_PER_MIN }
        if (sinceWater != null) return sinceWater >= WATER_GAP_MIN
        // Воды сегодня ещё не было: ждём 2 часа после конца тихих часов.
        val sinceWake = ((i.nowMin - r.quietToMin) % DAY_MIN + DAY_MIN) % DAY_MIN
        return sinceWake >= WATER_GAP_MIN
    }

    /** Итог дня: один раз в сутки после 21:00 и не в тихие часы. */
    fun shouldSendSummary(nowMin: Int, reminders: Reminders, lastSentEpochDay: Long?, todayEpochDay: Long): Boolean {
        if (!reminders.dailySummary) return false
        if (lastSentEpochDay == todayEpochDay) return false
        if (nowMin < SUMMARY_AT_MIN) return false
        return !inQuietHours(nowMin, reminders.quietFromMin, reminders.quietToMin)
    }

    const val WEEKLY_AT_MIN = 19 * 60

    /** Итог недели: по воскресеньям после 19:00, один раз и не в тихие часы. */
    fun shouldSendWeekly(nowMin: Int, dayOfWeek: java.time.DayOfWeek, reminders: Reminders, lastSentEpochDay: Long?, todayEpochDay: Long): Boolean {
        if (!reminders.weeklySummary) return false
        if (dayOfWeek != java.time.DayOfWeek.SUNDAY) return false
        if (lastSentEpochDay == todayEpochDay) return false
        if (nowMin < WEEKLY_AT_MIN) return false
        return !inQuietHours(nowMin, reminders.quietFromMin, reminders.quietToMin)
    }

    /** Нужна ли вообще фоновая проверка. */
    fun anyEnabled(reminders: Reminders, modules: Set<Module>): Boolean =
        (reminders.water && Module.Water in modules) || reminders.dailySummary || reminders.weeklySummary ||
            Module.Sleep in modules || Module.Habits in modules || Module.Fasting in modules

    private const val MS_PER_MIN = 60_000L
}
