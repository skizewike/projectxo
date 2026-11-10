package com.projectxo.app.core.steps

import com.projectxo.app.core.data.HourSteps
import com.projectxo.app.core.data.SensorReading
import com.projectxo.app.core.data.StepsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Шаги с датчика `TYPE_STEP_COUNTER` (DECISIONS.md, Р25). Чистый Kotlin: проверяется обычными тестами.
 * Датчик отдаёт общий счётчик с момента включения телефона; приложение считает разницу показаний.
 */
object StepsMath {
    /** Больше этого за один промежуток считаем сбоем датчика и не записываем. */
    const val MAX_PLAUSIBLE_DELTA = 60_000L

    /** Промежуток дольше этого раскладываем только по последним суткам. */
    const val MAX_SPREAD_MS = 24L * 60 * 60 * 1000

    /** Длина шага для оценки расстояния, если рост не указан (Р26). */
    const val STRIDE_M = 0.75

    /** Шаг ≈ 0,415 роста: обычная оценка для ходьбы (Р26, «позже — из роста»). */
    const val STRIDE_PER_HEIGHT = 0.415

    /** Больше за одну ручную запись не принимаем: это уже опечатка. */
    const val MAX_MANUAL = 50_000

    /** Длина шага в метрах по росту; рост вне 100–250 см не учитываем. */
    fun strideM(heightCm: Int?): Double =
        if (heightCm == null || heightCm !in 100..250) STRIDE_M else heightCm * STRIDE_PER_HEIGHT / 100.0

    /** Час с таким числом шагов считается активным. */
    const val ACTIVE_HOUR_STEPS = 250

    /**
     * Сколько шагов прибавилось с прошлого показания.
     * Первое показание — 0 (точка отсчёта). Счётчик меньше прошлого — телефон перезагрузили, считаем с нуля.
     */
    fun delta(last: SensorReading?, current: Long): Long {
        if (last == null || current < 0) return 0
        val d = if (current >= last.counter) current - last.counter else current
        return if (d in 1..MAX_PLAUSIBLE_DELTA) d else 0
    }

    /**
     * Разложить [steps] по часам между [fromMs] и [toMs] пропорционально времени в каждом часе.
     * Округление вниз, остаток уходит в последний час, поэтому сумма частей всегда равна [steps].
     */
    fun distribute(steps: Long, fromMs: Long, toMs: Long, zone: ZoneId): List<HourSteps> {
        if (steps <= 0) return emptyList()
        val end = Instant.ofEpochMilli(toMs)
        val startMs = maxOf(fromMs, toMs - MAX_SPREAD_MS)
        if (startMs >= toMs) return listOf(hourOf(end, zone, steps.toInt()))

        val spans = ArrayList<Pair<Instant, Long>>()
        var cursor = Instant.ofEpochMilli(startMs)
        while (cursor.isBefore(end)) {
            val nextHour = cursor.atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()
            val stop = if (nextHour.isAfter(end)) end else nextHour
            spans += cursor to (stop.toEpochMilli() - cursor.toEpochMilli())
            cursor = stop
        }
        val total = (toMs - startMs).toDouble()
        val parts = ArrayList<HourSteps>(spans.size)
        var given = 0L
        spans.forEachIndexed { i, (at, len) ->
            val share = if (i == spans.lastIndex) steps - given else (steps * (len / total)).toLong()
            given += share
            if (share > 0) parts += hourOf(at, zone, share.toInt())
        }
        return parts
    }

    private fun hourOf(at: Instant, zone: ZoneId, steps: Int): HourSteps {
        val z = at.atZone(zone)
        return HourSteps(z.toLocalDate(), z.hour, steps)
    }

    /** Расстояние в км для подписи «≈ 4,9 км». */
    fun distanceKm(steps: Int, strideM: Double = STRIDE_M): Double = steps * strideM / 1000.0

    fun activeHours(hours: List<Int>): Int = hours.count { it >= ACTIVE_HOUR_STEPS }

    /** Час с наибольшим числом шагов или null, если шагов нет. */
    fun bestHour(hours: List<Int>): Int? {
        val max = hours.maxOrNull() ?: return null
        return if (max <= 0) null else hours.indexOf(max)
    }
}

/** Запись нового показания датчика: разница → по часам → в базу, затем показание как точка отсчёта. */
class StepsRecorder(private val repo: StepsRepository, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    /** Экран и фоновая проверка пишут через один рекордер: по очереди, иначе одну разницу посчитали бы дважды. */
    private val lock = Mutex()

    suspend fun onReading(counter: Long, nowMs: Long) = lock.withLock {
        val last = repo.lastReading()
        // Показание из прошлого (часы перевели назад) — только обновляем точку отсчёта.
        if (last != null && nowMs > last.timestampMs) {
            val d = StepsMath.delta(last, counter)
            if (d > 0) repo.addSteps(StepsMath.distribute(d, last.timestampMs, nowMs, zone()))
        }
        repo.saveReading(SensorReading(counter, nowMs))
    }
}
