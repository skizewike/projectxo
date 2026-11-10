package com.projectxo.app.core.steps

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Шаги по акселерометру для телефонов без датчика шагов (Р65). Чистый Kotlin: проверяется обычными тестами.
 *
 * Как считает:
 * 1. Длина вектора ускорения; медленный фильтр отделяет силу тяжести, быстрый сглаживает дрожь.
 * 2. Шаг — пик сигнала выше порога. Порог подстраивается под силу последних шагов, но не ниже [MIN_THRESHOLD].
 * 3. Между шагами от [MIN_STEP_MS] до [MAX_STEP_MS]: быстрее — тряска, медленнее — не ходьба.
 * 4. Ходьба подтверждается после [CONFIRM_STEPS] шагов подряд; тогда они засчитываются разом.
 *    Отдельные толчки (телефон взяли, положили) так не попадают в шаги.
 */
class MotionStepDetector {
    private var gravity = Float.NaN
    private var lastSampleNs = -1L
    private var smooth = 0f
    private var prev = 0f
    private var rising = false
    private var peak = 0f
    private var peakNs = 0L
    private var lastStepNs = -1L
    private var pending = 0
    private var confirmed = false
    private var level = START_LEVEL

    /**
     * Новое показание акселерометра (м/с², время в нс).
     * Возвращает, сколько шагов засчитать сейчас: 0, 1 или [CONFIRM_STEPS] в момент подтверждения ходьбы.
     */
    fun onSample(timestampNs: Long, x: Float, y: Float, z: Float): Int {
        val m = sqrt(x * x + y * y + z * z)
        if (gravity.isNaN()) gravity = m
        // Фильтры по времени, а не по числу показаний: телефоны присылают от 15 до 200 показаний в секунду,
        // и на частых телефонах фильтр «по показаниям» гасил саму ходьбу.
        val dtS = if (lastSampleNs < 0 || timestampNs <= lastSampleNs) NOMINAL_DT_S
        else ((timestampNs - lastSampleNs) / 1e9f).coerceAtMost(MAX_DT_S)
        lastSampleNs = timestampNs
        gravity += alpha(dtS, GRAVITY_TAU_S) * (m - gravity)
        smooth += alpha(dtS, SMOOTH_TAU_S) * ((m - gravity) - smooth)
        val s = smooth

        var counted = 0
        if (s > prev) {
            rising = true
            if (s > peak) {
                peak = s
                peakNs = timestampNs
            }
        } else if (rising && s < prev) {
            // Вершина пройдена.
            rising = false
            if (peak >= threshold()) counted = onPeak(peakNs, peak)
            peak = 0f
        }
        prev = s

        // Долгая пауза: ходьба закончилась, следующий раз снова подтверждаем.
        if (lastStepNs >= 0 && timestampNs - lastStepNs > MAX_STEP_MS * NS_PER_MS) {
            pending = 0
            confirmed = false
            lastStepNs = -1L
        }
        return counted
    }

    private fun alpha(dtS: Float, tauS: Float): Float = 1f - exp(-dtS / tauS)

    private fun threshold(): Float = max(MIN_THRESHOLD, level * LEVEL_SHARE)

    private fun onPeak(at: Long, height: Float): Int {
        val gapMs = if (lastStepNs < 0) -1L else (at - lastStepNs) / NS_PER_MS
        if (gapMs in 0 until MIN_STEP_MS) {
            // Слишком часто для шага. Во время ходьбы это лишний пик внутри шага — пропускаем;
            // до подтверждения — тряска: начинаем подтверждение заново.
            if (!confirmed) {
                pending = 0
                lastStepNs = at
            }
            return 0
        }
        lastStepNs = at
        level += LEVEL_ALPHA * (height - level)
        if (confirmed) return 1
        pending++
        if (pending >= CONFIRM_STEPS) {
            confirmed = true
            val n = pending
            pending = 0
            return n
        }
        return 0
    }

    companion object {
        const val MIN_STEP_MS = 250L
        const val MAX_STEP_MS = 2_000L
        const val CONFIRM_STEPS = 6

        /** Самый слабый пик, который ещё считаем шагом, м/с². */
        const val MIN_THRESHOLD = 1.1f
        private const val START_LEVEL = 2.5f
        private const val LEVEL_SHARE = 0.45f
        private const val LEVEL_ALPHA = 0.2f
        /** Постоянные времени фильтров, с: при 50 показаниях в секунду это прежние 0,02 и 0,35. */
        private const val GRAVITY_TAU_S = 0.99f
        private const val SMOOTH_TAU_S = 0.0464f
        private const val NOMINAL_DT_S = 0.02f
        private const val MAX_DT_S = 0.2f
        private const val NS_PER_MS = 1_000_000L
    }
}
