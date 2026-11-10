package com.projectxo.app.core.motion

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Анимация выполненной цели «Обход и шкала» (docs/DESIGN.md, 5c). Все числа здесь. */
object GoalCelebrationSpec {
    const val TOTAL_MS = 1500f

    const val TRACE_MS = 900f
    const val TRACE_FADE_MS = 200f
    const val TRACE_LENGTH_DP = 110f
    const val TRACE_PARTS = 14
    const val TRACE_WIDTH_DP = 2.5f

    const val WAVE_START_MS = 500f
    const val WAVE_STEP_MS = 28f
    const val WAVE_TICK_MS = 300f
    const val WAVE_EVERY_ML = 125
    const val WAVE_BASE_DP = 6f
    const val WAVE_EXTRA_DP = 16f

    const val DIP_DELAY_MS = 520f
    const val DIP_MS = 620f
    const val DIP_DOWN_DP = 4f
    const val DIP_UP_DP = 3f
    const val DIP_SCALE = 0.97f

    const val SOLID_FROM_MS = 1200f
    const val SOLID_MS = 300f

    /** Кольцо «Баланс дня» (5c.1): волна по 60 делениям по часовой, шаг меньше, чтобы уложиться в то же время. */
    const val RING_WAVE_STEP_MS = 8f
    const val RING_TRACE_GAP_DP = 2f
}

object GoalCelebration {
    private val std = Curves.Standard

    /** Где голова следа на контуре (в единицах длины контура), [perimeter] и длина следа в одних единицах. */
    fun traceHead(t: Float, perimeter: Float, length: Float): Float =
        std.transform(clamp01(t / GoalCelebrationSpec.TRACE_MS)) * (perimeter + length)

    /** Прозрачность следа: 1, затем гаснет за TRACE_FADE_MS. */
    fun traceAlpha(t: Float): Float =
        1f - clamp01((t - GoalCelebrationSpec.TRACE_MS) / GoalCelebrationSpec.TRACE_FADE_MS)

    /** Горб волны для временного деления номер [index] (снизу вверх), 0..1. */
    fun waveBump(t: Float, index: Int): Float {
        val local = (t - GoalCelebrationSpec.WAVE_START_MS - index * GoalCelebrationSpec.WAVE_STEP_MS) /
            GoalCelebrationSpec.WAVE_TICK_MS
        if (local <= 0f || local >= 1f) return 0f
        return sin(PI * local).toFloat()
    }

    /** Горб волны для деления [index] циферблата кольца (по часовой от верхнего), 0..1. */
    fun ringWaveBump(t: Float, index: Int): Float {
        val local = (t - GoalCelebrationSpec.WAVE_START_MS - index * GoalCelebrationSpec.RING_WAVE_STEP_MS) /
            GoalCelebrationSpec.WAVE_TICK_MS
        if (local <= 0f || local >= 1f) return 0f
        return sin(PI * local).toFloat()
    }

    /** Насколько пунктир цели уже стал сплошной линией, 0..1. */
    fun solidProgress(t: Float): Float =
        std.transform(clamp01((t - GoalCelebrationSpec.SOLID_FROM_MS) / GoalCelebrationSpec.SOLID_MS))

    /** «Приседание» числа: сдвиг вниз в dp (отрицательный — вверх). */
    fun dipOffsetDp(t: Float): Float {
        val u = std.transform(clamp01((t - GoalCelebrationSpec.DIP_DELAY_MS) / GoalCelebrationSpec.DIP_MS))
        if (u <= 0f || u >= 1f) return 0f
        val down = GoalCelebrationSpec.DIP_DOWN_DP
        val up = -GoalCelebrationSpec.DIP_UP_DP
        return when {
            u < 0.22f -> lerp(0f, down, u / 0.22f)
            u < 0.55f -> lerp(down, up, (u - 0.22f) / 0.33f)
            else -> lerp(up, 0f, (u - 0.55f) / 0.45f)
        }
    }

    /** Сжатие числа по вертикали во время «приседания». */
    fun dipScaleY(t: Float): Float {
        val u = std.transform(clamp01((t - GoalCelebrationSpec.DIP_DELAY_MS) / GoalCelebrationSpec.DIP_MS))
        if (u <= 0f || u >= 1f) return 1f
        return if (u < 0.22f) lerp(1f, GoalCelebrationSpec.DIP_SCALE, u / 0.22f)
        else lerp(GoalCelebrationSpec.DIP_SCALE, 1f, ((u - 0.22f) / 0.33f).coerceAtMost(1f))
    }
}

/** Шкала мерного сосуда (docs/DESIGN.md, 5a). */
object VesselScale {
    const val MIN_TOP_ML = 2500
    const val HEADROOM_ML = 250
    const val STEP_ML = 500
    const val WIDE_STEP_ML = 1000
    const val WIDE_FROM_ML = 3000
    const val DRAG_STEP_ML = 10
    const val DRAG_MAX_ML = 3000

    /** Верх шкалы: не меньше 2 500 и всегда запас над водой. */
    fun top(level: Float): Int {
        val needed = ceil((level + HEADROOM_ML) / STEP_ML).toInt() * STEP_ML
        return max(MIN_TOP_ML, needed)
    }

    fun labelStep(top: Int): Int = if (top > WIDE_FROM_ML) WIDE_STEP_ML else STEP_ML

    /** Сколько добавить при отпускании на отметке [targetMl]: шаг 10 мл, 0 если ниже уровня. */
    fun dragAmount(targetMl: Float, totalMl: Int): Int {
        val raw = Math.round((targetMl - totalMl) / DRAG_STEP_ML) * DRAG_STEP_ML
        return raw.coerceIn(0, DRAG_MAX_ML)
    }
}

/**
 * Точка на контуре скруглённого прямоугольника на расстоянии [d] по часовой стрелке,
 * начиная с левого конца верхней прямой. Пишет x и y в [out]; без выделения памяти.
 */
object RoundRectPerimeter {
    fun length(w: Float, h: Float, r: Float): Float = 2f * (w - 2f * r) + 2f * (h - 2f * r) + 2f * PI.toFloat() * r

    fun point(d0: Float, x: Float, y: Float, w: Float, h: Float, r: Float, out: FloatArray) {
        val quarter = PI.toFloat() * r / 2f
        val top = w - 2f * r
        val side = h - 2f * r
        val total = length(w, h, r)
        var d = ((d0 % total) + total) % total
        fun arc(cx: Float, cy: Float, start: Float, k: Float) {
            val a = start + k * PI.toFloat() / 2f
            out[0] = cx + cos(a) * r
            out[1] = cy + sin(a) * r
        }
        if (d <= top) { out[0] = x + r + d; out[1] = y; return }
        d -= top
        if (d <= quarter) { arc(x + w - r, y + r, -PI.toFloat() / 2f, d / quarter); return }
        d -= quarter
        if (d <= side) { out[0] = x + w; out[1] = y + r + d; return }
        d -= side
        if (d <= quarter) { arc(x + w - r, y + h - r, 0f, d / quarter); return }
        d -= quarter
        if (d <= top) { out[0] = x + w - r - d; out[1] = y + h; return }
        d -= top
        if (d <= quarter) { arc(x + r, y + h - r, PI.toFloat() / 2f, d / quarter); return }
        d -= quarter
        if (d <= side) { out[0] = x; out[1] = y + h - r - d; return }
        d -= side
        arc(x + r, y + r, PI.toFloat(), (d / quarter).coerceAtMost(1f))
    }
}
