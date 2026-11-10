package com.projectxo.app.core.model

/**
 * Когда проверять, не наступил ли новый день (полночь при открытом приложении).
 * Проверка раз в минуту, а перед полуночью — точно к ней, чтобы «Сегодня» переключалось сразу.
 */
object DayRollover {
    const val CHECK_MS = 60_000L
    const val SLACK_MS = 500L
    const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Через сколько мс проверять в следующий раз; [msOfDay] — миллисекунды от начала суток. */
    fun nextCheckDelayMs(msOfDay: Long): Long = minOf(CHECK_MS, DAY_MS - msOfDay + SLACK_MS).coerceAtLeast(SLACK_MS)
}
