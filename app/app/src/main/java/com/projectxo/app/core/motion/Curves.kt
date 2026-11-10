package com.projectxo.app.core.motion

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

// Чистый Kotlin без android.* и Compose: та же математика, что в design/intro-opus.html.
// Поэтому заставку можно проверять обычными тестами и перенести в Compose Multiplatform.

internal fun clamp01(x: Float): Float = x.coerceIn(0f, 1f)

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Доля времени t внутри интервала [range.start, range.endInclusive], от 0 до 1. */
internal fun seg(t: Float, range: ClosedFloatingPointRange<Float>): Float =
    clamp01((t - range.start) / (range.endInclusive - range.start))

/** Кубическая кривая Безье, как CSS cubic-bezier и Compose CubicBezierEasing. */
class Bezier(x1: Float, y1: Float, x2: Float, y2: Float) {
    private val cx = 3f * x1
    private val bx = 3f * (x2 - x1) - cx
    private val ax = 1f - cx - bx
    private val cy = 3f * y1
    private val by = 3f * (y2 - y1) - cy
    private val ay = 1f - cy - by

    private fun sx(t: Float) = ((ax * t + bx) * t + cx) * t
    private fun sy(t: Float) = ((ay * t + by) * t + cy) * t
    private fun dx(t: Float) = (3f * ax * t + 2f * bx) * t + cx

    fun transform(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var t = x
        for (i in 0 until 8) {
            val e = sx(t) - x
            val d = dx(t)
            if (abs(e) < 1e-6f || abs(d) < 1e-6f) break
            t -= e / d
        }
        if (t !in 0f..1f || abs(sx(t) - x) > 1e-4f) {
            var lo = 0f
            var hi = 1f
            t = x
            repeat(40) {
                if (sx(t) < x) lo = t else hi = t
                t = (lo + hi) / 2f
            }
        }
        return sy(t)
    }
}

/** Кривые из docs/DESIGN.md, раздел 5. */
object Curves {
    val Standard = Bezier(0.2f, 0f, 0f, 1f)
    val FastOutSlowIn = Bezier(0.4f, 0f, 0.2f, 1f)
    val EmphasizedDecelerate = Bezier(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = Bezier(0.3f, 0f, 0.8f, 0.15f)
}

/** Пружина с массой 1, как spring(dampingRatio, stiffness) в Compose. */
data class SpringParams(val dampingRatio: Float, val stiffness: Float)

/**
 * Положение пружины от 0 к 1 через [ms] миллисекунд после старта.
 * Когда амплитуда падает ниже 0,002, возвращает ровно 1, чтобы последний кадр совпадал с конечным состоянием.
 */
fun springProgress(ms: Float, spring: SpringParams): Float {
    if (ms <= 0f) return 0f
    val t = ms / 1000.0
    val w = sqrt(spring.stiffness.toDouble())
    val z = spring.dampingRatio.toDouble()
    val x: Double
    val envelope: Double
    if (z < 1.0) {
        val wd = w * sqrt(1 - z * z)
        val e = exp(-z * w * t)
        x = 1 - e * (cos(wd * t) + (z * w / wd) * sin(wd * t))
        envelope = e * (1 + z * w / wd)
    } else {
        val e = exp(-w * t)
        x = 1 - e * (1 + w * t)
        envelope = e * (1 + w * t)
    }
    return if (envelope < 2e-3) 1f else x.toFloat()
}
