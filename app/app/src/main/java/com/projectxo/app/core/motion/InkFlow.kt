package com.projectxo.app.core.motion

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Чернильный поток (docs/DESIGN.md, 5b п. 1). Все числа здесь, движок рисования их только читает. */
object InkFlowSpec {
    const val COUNT = 56
    const val SPREAD_MS = 240f
    const val TRAVEL_MS = 600f
    const val TOTAL_MS = SPREAD_MS + TRAVEL_MS

    /** Когда долетают первые точки и пора менять значение. */
    const val ARRIVE_MS = 360L

    /** В dp: облако на старте, разброс на финише, размеры точек. */
    const val START_CLOUD_DP = 18f
    const val END_SPREAD_DP = 3f
    const val MIN_RADIUS_DP = 0.9f
    const val MAX_RADIUS_DP = 1.6f

    /** Изгиб пути: доля расстояния. */
    const val CURVE_MIN = 0.18f
    const val CURVE_MAX = 0.28f

    const val FADE_IN = 0.10f
    const val FADE_OUT = 0.18f
}

/**
 * Случайные параметры одного потока. Создаются один раз при запуске, в кадре ничего не выделяется.
 * Генератор детерминированный: один и тот же [seed] даёт один и тот же рисунок (удобно для тестов).
 */
class InkFlowParticles(seed: Int) {
    val delay = FloatArray(InkFlowSpec.COUNT)
    val startX = FloatArray(InkFlowSpec.COUNT)
    val startY = FloatArray(InkFlowSpec.COUNT)
    val endX = FloatArray(InkFlowSpec.COUNT)
    val endY = FloatArray(InkFlowSpec.COUNT)
    val curve = FloatArray(InkFlowSpec.COUNT)
    val radius = FloatArray(InkFlowSpec.COUNT)

    /** Весь поток изгибается в одну сторону: +1 или −1. */
    val side: Float

    init {
        var state = seed * 747796405 + 2891336453L.toInt()
        fun next(): Float {
            // PCG-подобный хеш: быстрый и без объектов.
            state = state * 747796405 + 1442695041
            var w = ((state ushr ((state ushr 28) + 4)) xor state) * 277803737
            w = (w ushr 22) xor w
            return (w ushr 8).toFloat() / (1 shl 24).toFloat()
        }
        side = if (next() < 0.5f) -1f else 1f
        val n = InkFlowSpec.COUNT
        for (i in 0 until n) {
            // Больше точек в начале: облако отрывается плотно, хвост редеет.
            val base = (i.toFloat() / n).pow(1.4f)
            delay[i] = (base + (next() - 0.5f) * 0.04f).coerceIn(0f, 1f) * InkFlowSpec.SPREAD_MS
            val (sx, sy) = inDisk(next(), next())
            startX[i] = sx
            startY[i] = sy
            val (ex, ey) = inDisk(next(), next())
            endX[i] = ex
            endY[i] = ey
            curve[i] = lerp(InkFlowSpec.CURVE_MIN, InkFlowSpec.CURVE_MAX, next())
            radius[i] = lerp(InkFlowSpec.MIN_RADIUS_DP, InkFlowSpec.MAX_RADIUS_DP, next())
        }
    }

    private fun inDisk(u: Float, v: Float): Pair<Float, Float> {
        val r = sqrt(u)
        val a = 2.0 * PI * v
        return Pair((r * cos(a)).toFloat(), (r * sin(a)).toFloat())
    }
}

/** Положение и вид одной точки; заполняется без выделения памяти. */
class InkDot {
    var x = 0f
    var y = 0f
    var radiusDp = 0f
    var alpha = 0f
}

object InkFlowMath {
    /**
     * Точка [i] потока в момент [t] мс. Координаты в пикселях; [dp] — пикселей в одном dp.
     * Возвращает false, если точка ещё не вылетела или уже погасла.
     */
    fun dot(
        p: InkFlowParticles,
        i: Int,
        t: Float,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        dp: Float,
        out: InkDot,
    ): Boolean {
        val local = (t - p.delay[i]) / InkFlowSpec.TRAVEL_MS
        if (local <= 0f || local >= 1f) return false
        val e = Curves.FastOutSlowIn.transform(local)

        val sx = fromX + p.startX[i] * InkFlowSpec.START_CLOUD_DP * dp
        val sy = fromY + p.startY[i] * InkFlowSpec.START_CLOUD_DP * dp
        val ex = toX + p.endX[i] * InkFlowSpec.END_SPREAD_DP * dp
        val ey = toY + p.endY[i] * InkFlowSpec.END_SPREAD_DP * dp

        // Контрольная точка: середина плюс сдвиг перпендикулярно пути.
        val dx = ex - sx
        val dy = ey - sy
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val bend = p.curve[i] * len * p.side
        val cx = (sx + ex) / 2f - dy / len * bend
        val cy = (sy + ey) / 2f + dx / len * bend

        val u = 1f - e
        out.x = u * u * sx + 2f * u * e * cx + e * e * ex
        out.y = u * u * sy + 2f * u * e * cy + e * e * ey
        // К финишу точка чуть мельчает: струя сужается.
        out.radiusDp = p.radius[i] * lerp(1f, 0.7f, e)
        out.alpha = when {
            local < InkFlowSpec.FADE_IN -> local / InkFlowSpec.FADE_IN
            local > 1f - InkFlowSpec.FADE_OUT -> (1f - local) / InkFlowSpec.FADE_OUT
            else -> 1f
        }
        return true
    }
}
