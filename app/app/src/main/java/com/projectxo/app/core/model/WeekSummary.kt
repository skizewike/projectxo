package com.projectxo.app.core.model

import java.time.LocalDate

/** Среднее по одному модулю за неделю, доля цели в процентах (каждый день не выше 100). */
data class ModuleAverage(val module: Module, val percent: Int)

/**
 * Итог недели: последние 7 дней, считая сегодня. Дни раньше первого дня с XO не входят (Р41).
 * [previousPercent] — предыдущие 7 дней, если в них есть дни с XO. Без оценок и упрёков: только числа.
 */
data class WeekSummary(
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
    val averagePercent: Int,
    val daysComplete: Int,
    val bestDay: LocalDate?,
    val bestPercent: Int,
    val previousPercent: Int?,
    val perModule: List<ModuleAverage>,
)

object WeekSummaryBuilder {
    const val WEEK = 7

    fun build(
        today: LocalDate,
        start: LocalDate,
        totals: Map<LocalDate, DayTotals>,
        goals: HistoryGoals,
        modules: Set<Module>,
        order: List<Module> = Module.entries,
    ): WeekSummary? {
        if (modules.isEmpty()) return null
        val from = today.minusDays(WEEK - 1L)
        val days = (0 until WEEK).map { from.plusDays(it.toLong()) }.filter { !it.isBefore(start) }
        if (days.isEmpty()) return null

        fun balance(d: LocalDate) = BalanceMath.balance(totals[d], goals, modules)
        val balances = days.associateWith { balance(it) }
        val average = balances.values.average()
        val best = balances.maxByOrNull { it.value }

        val perModule = order.filter { it in modules }.mapNotNull { m ->
            val fractions = days.mapNotNull { d ->
                BalanceMath.progress(totals[d], goals, setOf(m), order).firstOrNull()?.fraction
            }
            // Привычки считаются только в дни, когда они запланированы.
            if (fractions.isEmpty()) null else ModuleAverage(m, percent(fractions.average()))
        }

        val prevFrom = from.minusDays(WEEK.toLong())
        val prevDays = (0 until WEEK).map { prevFrom.plusDays(it.toLong()) }.filter { !it.isBefore(start) }
        val previous = if (prevDays.isEmpty()) null else percent(prevDays.map { balance(it) }.average())

        return WeekSummary(
            from = from, to = today, days = days.size,
            averagePercent = percent(average),
            daysComplete = balances.values.count { it >= 1f },
            bestDay = best?.takeIf { it.value > 0f }?.key,
            bestPercent = percent(best?.value?.toDouble() ?: 0.0),
            previousPercent = previous,
            perModule = perModule,
        )
    }

    private fun percent(x: Double): Int = Math.round(x * 100).toInt()
}
