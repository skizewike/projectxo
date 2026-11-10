package com.projectxo.app.core.model

import com.projectxo.app.core.data.DayLog
import java.time.LocalDate

/** Период графиков профиля (design/settings.html, Р50). */
enum class ProfilePeriod(val days: Int?) { Week(7), Month(30), All(null) }

/** Данные графиков: по одному значению на день периода, от первого дня к сегодняшнему. */
data class ProfileData(
    val period: ProfilePeriod,
    val dates: List<LocalDate>,
    /** Вес дня из календаря; null — не отмечен. */
    val weightKg: List<Double?>,
    /** Скользящее среднее веса за 7 дней, посчитанное по всей истории, а не только по периоду. */
    val weightTrend: List<Double?>,
    val steps: List<Int>,
    /** Вода как доля цели, до 1,1. */
    val waterRatio: List<Float>,
    val kcal: List<Int>,
    val feels: List<Int?>,
    /** Последний и первый вес за всё время: для «с начала». */
    val latestWeightKg: Double?,
    val firstWeightKg: Double?,
)

object ProfileStats {
    const val TREND_WINDOW = 7
    const val WATER_RATIO_CAP = 1.1f

    /** Первый день периода: не раньше первого дня с XO (Р41). */
    fun rangeStart(period: ProfilePeriod, start: LocalDate, today: LocalDate): LocalDate {
        val days = period.days ?: return start
        val from = today.minusDays(days - 1L)
        return if (from.isBefore(start)) start else from
    }

    fun datesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
        val n = java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt() + 1
        return List(n.coerceAtLeast(0)) { from.plusDays(it.toLong()) }
    }

    /**
     * Среднее по последним [window] позициям (включая текущую). Считаются только отмеченные дни;
     * если в окне ничего нет — null.
     */
    fun movingAverage(values: List<Double?>, window: Int = TREND_WINDOW): List<Double?> = values.indices.map { i ->
        val slice = values.subList(maxOf(0, i - window + 1), i + 1).filterNotNull()
        if (slice.isEmpty()) null else slice.average()
    }

    /** Сколько дней каждой оценки: индекс = оценка 0..4. */
    fun feelCounts(feels: List<Int?>): List<Int> {
        val out = MutableList(Feel.COUNT) { 0 }
        feels.forEach { f -> if (f != null && f in Feel.MIN..Feel.MAX) out[f]++ }
        return out
    }

    /** Доля «хороших» дней (оценка 3–4) среди отмеченных: первая и вторая половина периода. Null, если отметок нет. */
    fun goodShareHalves(feels: List<Int?>): Pair<Float?, Float?> {
        val mid = feels.size / 2
        fun share(part: List<Int?>): Float? {
            val marked = part.filterNotNull()
            return if (marked.isEmpty()) null else marked.count { it >= 3 }.toFloat() / marked.size
        }
        return share(feels.subList(0, mid)) to share(feels.subList(mid, feels.size))
    }

    fun <T> average(values: List<T>, selector: (T) -> Double): Double? =
        if (values.isEmpty()) null else values.sumOf(selector) / values.size

    fun build(
        period: ProfilePeriod,
        start: LocalDate,
        today: LocalDate,
        totals: Map<LocalDate, DayTotals>,
        logs: Map<LocalDate, DayLog>,
        goals: HistoryGoals,
    ): ProfileData {
        val allDates = datesBetween(start, today)
        val allWeights = allDates.map { logs[it]?.weightKg }
        val allTrend = movingAverage(allWeights)
        val from = rangeStart(period, start, today)
        val offset = java.time.temporal.ChronoUnit.DAYS.between(start, from).toInt().coerceAtLeast(0)
        val dates = allDates.subList(offset, allDates.size)
        return ProfileData(
            period = period,
            dates = dates,
            weightKg = allWeights.subList(offset, allWeights.size),
            weightTrend = allTrend.subList(offset, allTrend.size),
            steps = dates.map { totals[it]?.steps ?: 0 },
            waterRatio = dates.map { d ->
                if (goals.waterMl <= 0) 0f
                else ((totals[d]?.waterMl ?: 0).toFloat() / goals.waterMl).coerceAtMost(WATER_RATIO_CAP)
            },
            kcal = dates.map { totals[it]?.kcal ?: 0 },
            feels = dates.map { logs[it]?.feel },
            latestWeightKg = allWeights.lastOrNull { it != null },
            firstWeightKg = allWeights.firstOrNull { it != null },
        )
    }
}
