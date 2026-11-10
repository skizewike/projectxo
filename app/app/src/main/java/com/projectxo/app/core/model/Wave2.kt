package com.projectxo.app.core.model

import java.time.DayOfWeek
import java.time.LocalDate

// Модели и расчёты модулей волны 2: дыхание, сон, голодание, привычки. Чистый Kotlin без android.*.

// ——— Дыхание ———

enum class BreathKind { In, Hold, Out, HoldEmpty }

data class BreathPhase(val kind: BreathKind, val seconds: Int)

/** Ритмы (Р52): ровное 4–4, квадрат 4–4–4–4, длинный выдох 4–6. */
enum class BreathRhythm(val id: String, val phases: List<BreathPhase>) {
    Even("even", listOf(BreathPhase(BreathKind.In, 4), BreathPhase(BreathKind.Out, 4))),
    Box(
        "box",
        listOf(
            BreathPhase(BreathKind.In, 4), BreathPhase(BreathKind.Hold, 4),
            BreathPhase(BreathKind.Out, 4), BreathPhase(BreathKind.HoldEmpty, 4),
        ),
    ),
    LongOut("long", listOf(BreathPhase(BreathKind.In, 4), BreathPhase(BreathKind.Out, 6))),
    ;

    val cycleSeconds: Int get() = phases.sumOf { it.seconds }

    companion object {
        fun fromId(id: String?): BreathRhythm = entries.firstOrNull { it.id == id } ?: Even
    }
}

data class BreathState(val index: Int, val phase: BreathPhase, val progress: Float, val cycle: Int)

object BreathingMath {
    val DURATIONS_MIN = listOf(1, 3, 5)
    const val DEFAULT_MIN = 3
    const val LOW = 0.15f

    /** Какая фаза в секунду [tSec] от начала. */
    fun stateAt(rhythm: BreathRhythm, tSec: Float): BreathState {
        val cycle = rhythm.cycleSeconds
        val t = tSec.coerceAtLeast(0f)
        val inCycle = t % cycle
        var acc = 0
        rhythm.phases.forEachIndexed { i, p ->
            if (inCycle < acc + p.seconds) return BreathState(i, p, (inCycle - acc) / p.seconds, (t / cycle).toInt())
            acc += p.seconds
        }
        return BreathState(0, rhythm.phases.first(), 0f, (t / cycle).toInt())
    }

    private fun ease(x: Float): Float =
        if (x < 0.5f) 2 * x * x else 1 - Math.pow((-2 * x + 2).toDouble(), 2.0).toFloat() / 2

    /** Наполнение 0,15..1: растёт на вдохе, падает на выдохе, на задержках держится. */
    fun level(s: BreathState): Float = when (s.phase.kind) {
        BreathKind.In -> LOW + (1 - LOW) * ease(s.progress)
        BreathKind.Out -> 1 - (1 - LOW) * ease(s.progress)
        BreathKind.Hold -> 1f
        BreathKind.HoldEmpty -> LOW
    }

    /** Сколько полных циклов в сеансе на [minutes] минут. */
    fun cyclesIn(rhythm: BreathRhythm, minutes: Int): Int = minutes * 60 / rhythm.cycleSeconds
}

// ——— Сон ———

object SleepMath {
    const val MIN_VALID_MS = 10 * 60_000L
    const val MIN_GOAL_MIN = 7 * 60
    const val DAY_MIN = 24 * 60

    fun goalMinutes(bedMin: Int, wakeMin: Int): Int = ((wakeMin - bedMin) % DAY_MIN + DAY_MIN) % DAY_MIN

    /** Короткое нажатие («Ложусь» и сразу «Встаю») сном не считается (Р42). */
    fun isValidNight(startMs: Long, endMs: Long): Boolean = endMs - startMs >= MIN_VALID_MS

    /** Отбой в минутах от 12:00 (чтобы 23:30 и 00:30 лежали рядом). */
    private fun bedAxis(startMinOfDay: Int): Int = if (startMinOfDay < 12 * 60) startMinOfDay + DAY_MIN else startMinOfDay

    /** Средний отбой (минуты суток) и разброс в минутах; null, если ночей меньше двух. */
    fun regularity(startMinsOfDay: List<Int>): Pair<Int, Int>? {
        if (startMinsOfDay.size < 2) return null
        val axis = startMinsOfDay.map(::bedAxis)
        val avg = axis.average()
        val sd = Math.sqrt(axis.sumOf { (it - avg) * (it - avg) } / axis.size)
        return (Math.round(avg).toInt() % DAY_MIN) to Math.round(sd).toInt()
    }
}

// ——— Голодание ———

object FastingMath {
    val PLANS = listOf(12, 14, 16, 18)
    const val DEFAULT_PLAN = 16
    const val MAX_BACK_MS = 24 * 3_600_000L
    const val WINDOW_WARN_MS = 15 * 60_000L
    private const val MIN_MS = 60_000L

    /** Границы выбора времени начала: не раньше суток назад, не позже «сейчас». */
    fun startBounds(nowMs: Long): LongRange = (nowMs - MAX_BACK_MS)..nowMs

    /** Границы времени конца: не раньше начала (плюс минута) и не позже «сейчас». */
    fun endBounds(startMs: Long, nowMs: Long): LongRange = (startMs + MIN_MS)..maxOf(startMs + MIN_MS, nowMs)

    fun goalReached(startMs: Long, nowMs: Long, planHours: Int): Boolean = nowMs - startMs >= planHours * 3_600_000L

    /** Окно еды открывается через [planHours] часов после начала. */
    fun eatingWindowStart(startMs: Long, planHours: Int): Long = startMs + planHours * 3_600_000L
}

// ——— Привычки ———

enum class HabitKind { Habit, Workout }

sealed interface HabitFreq {
    data object Daily : HabitFreq
    data class PerWeek(val n: Int) : HabitFreq

    /** [mask]: бит 0 — понедельник … бит 6 — воскресенье. */
    data class Days(val mask: Int) : HabitFreq
}

data class Habit(
    val id: Long,
    val name: String,
    val freq: HabitFreq,
    /** Напоминание, минуты от полуночи, или null. */
    val reminderMin: Int? = null,
    /** Сколько раз за день для «счётчика»; 1 — обычная отметка. */
    val target: Int = 1,
    val sortOrder: Int = 0,
    val kind: HabitKind = HabitKind.Habit,
)

/** Отметка за день: [value] — сколько раз (для обычной привычки 1). */
data class HabitCheck(val habitId: Long, val date: LocalDate, val value: Int)

object HabitMath {
    const val HISTORY_DAYS = 126
    const val MAX_NAME_LENGTH = 40
    const val MAX_TARGET = 20
    const val ALL_DAYS = 0b1111111
    const val WORKOUT_DAYS = 0b0010101 // пн, ср, пт

    fun dayBit(d: DayOfWeek): Int = 1 shl (d.value - 1)

    fun planned(f: HabitFreq, date: LocalDate): Boolean = when (f) {
        HabitFreq.Daily -> true
        is HabitFreq.PerWeek -> true
        is HabitFreq.Days -> f.mask and dayBit(date.dayOfWeek) != 0
    }

    fun weekTarget(f: HabitFreq): Int = when (f) {
        HabitFreq.Daily -> 7
        is HabitFreq.PerWeek -> f.n
        is HabitFreq.Days -> Integer.bitCount(f.mask)
    }

    /** Отметки одной привычки, быстро по дате. */
    class Marks(private val h: Habit, checks: Collection<HabitCheck>) {
        private val byDate = checks.filter { it.habitId == h.id }.associate { it.date to it.value }
        fun value(d: LocalDate): Int = byDate[d] ?: 0
        fun done(d: LocalDate): Boolean = value(d) >= h.target
    }

    /** Сколько отметок на этой неделе (с понедельника) до [date] включительно. */
    fun weekCount(m: Marks, date: LocalDate): Int {
        var n = 0
        var d = date.with(DayOfWeek.MONDAY)
        while (!d.isAfter(date)) {
            if (m.done(d)) n++
            d = d.plusDays(1)
        }
        return n
    }

    /** Нужна ли привычка в этот день: по расписанию и не закрыта недельная норма («N раз в неделю»). */
    fun dueOn(h: Habit, m: Marks, date: LocalDate): Boolean {
        if (!planned(h.freq, date)) return false
        val f = h.freq
        return !(f is HabitFreq.PerWeek && weekCount(m, date) >= f.n && !m.done(date))
    }

    /** Серия: подряд выполненные дни по расписанию; один пропуск серию не обрывает (Р37). */
    fun streak(h: Habit, m: Marks, today: LocalDate): Int {
        var s = 0
        var miss = 0
        var d = if (m.done(today)) today else today.minusDays(1)
        repeat(HISTORY_DAYS) {
            if (planned(h.freq, d)) {
                if (m.done(d)) {
                    s++
                    miss = 0
                } else {
                    miss++
                    if (miss > 1) return s
                }
            }
            d = d.minusDays(1)
        }
        return s
    }

    fun bestStreak(h: Habit, m: Marks, today: LocalDate): Int {
        var best = 0
        var s = 0
        var miss = 0
        var d = today.minusDays(HISTORY_DAYS.toLong())
        while (!d.isAfter(today)) {
            if (planned(h.freq, d)) {
                if (m.done(d)) {
                    s++
                    miss = 0
                    best = maxOf(best, s)
                } else {
                    miss++
                    if (miss > 1) s = 0
                }
            }
            d = d.plusDays(1)
        }
        return best
    }

    /** Процент выполненных дней по расписанию за последние 30 дней. */
    fun percent30(h: Habit, m: Marks, today: LocalDate): Int {
        var planned = 0
        var done = 0
        for (i in 0 until 30) {
            val d = today.minusDays(i.toLong())
            if (!planned(h.freq, d)) continue
            planned++
            if (m.done(d)) done++
        }
        return if (planned == 0) 0 else Math.round(done * 100f / planned)
    }

    /** Сколько привычек сделано из нужных на [date]. */
    fun dayProgress(habits: List<Habit>, checks: Collection<HabitCheck>, date: LocalDate): Pair<Int, Int> {
        var done = 0
        var total = 0
        habits.forEach { h ->
            val m = Marks(h, checks)
            if (dueOn(h, m, date) || m.done(date)) {
                total++
                if (m.done(date)) done++
            }
        }
        return done to total
    }
}

/** Цели новых модулей по умолчанию. */
object Wave2Goals {
    const val BREATHING_SEC = 180
}
