package com.projectxo.app.core.model

import java.time.LocalDate

/** Огонёк серии (Р80): растёт на 3, 10 и 30 днях подряд. Ниже 3 огонька нет. */
object StreakFlame {
    val THRESHOLDS = intArrayOf(3, 10, 30)

    /** 0 — нет огня, 1..3 — уровень. */
    fun level(streak: Int): Int = THRESHOLDS.count { streak >= it }
}

/**
 * Серия кольца «Баланс дня»: дни подряд, когда кольцо дошло до 100 %.
 * Сегодня ещё не закрыто — серия считается до вчера и не обрывается (серия ждёт, Р80).
 */
object BalanceStreak {
    fun count(percentByDay: Map<LocalDate, Int>, today: LocalDate): Int {
        var d = if ((percentByDay[today] ?: 0) >= 100) today else today.minusDays(1)
        var n = 0
        while ((percentByDay[d] ?: 0) >= 100) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** Сегодня кольцо ещё не закрыто, а серия есть: огонёк серый. */
    fun waiting(percentByDay: Map<LocalDate, Int>, today: LocalDate): Boolean = (percentByDay[today] ?: 0) < 100
}

/** Салют «Баланса дня» (Р80): пороги 50, 80, 100 %. Чем выше порог, тем больше вспышек. */
object BalanceFirework {
    val THRESHOLDS = intArrayOf(50, 80, 100)

    /** Самый высокий порог, пройденный при росте с [from] до [to], или 0. */
    fun crossed(from: Int, to: Int): Int = THRESHOLDS.lastOrNull { from < it && to >= it } ?: 0

    /** Сколько вспышек у порога. */
    fun bursts(threshold: Int): Int = when (threshold) {
        100 -> 7
        80 -> 4
        50 -> 2
        else -> 0
    }
}
