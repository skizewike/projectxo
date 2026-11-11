package com.projectxo.app.core.model

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/** Точка на холсте фигуры 160×100. */
data class Pt(val x: Float, val y: Float)

/**
 * Поза фигуры, вид сбоку: [s] плечо, [p] таз, [arm] — локоть и кисть, [leg] — колено, лодыжка, носок.
 * [arm2] и [leg2] — дальние конечности (рисуются светлее).
 */
data class FigurePose(
    val s: Pt,
    val p: Pt,
    val arm: List<Pt>,
    val leg: List<Pt>,
    val arm2: List<Pt>? = null,
    val leg2: List<Pt>? = null,
) {
    /** Центр головы: от таза через плечо ещё на [FigureMath.HEAD_OFFSET]. */
    val head: Pt
        get() {
            val dx = s.x - p.x
            val dy = s.y - p.y
            val d = hypot(dx, dy).let { if (it == 0f) 1f else it }
            return Pt(s.x + dx / d * FigureMath.HEAD_OFFSET, s.y + dy / d * FigureMath.HEAD_OFFSET)
        }

    /** Все точки позы, для проверок и расчёта границ. */
    val allPoints: List<Pt> get() = listOf(s, p) + arm + leg + (arm2 ?: emptyList()) + (leg2 ?: emptyList()) + head
}

/** Опора, которая рисуется вместе с фигурой. */
enum class FigureDeco { None, Wall, WallLeft, Bench, Bar, Step }

/** Фигура упражнения: поза [a] — начало, [b] — конец движения; если [b] нет, упражнение «держим». */
data class FigureSpec(val a: FigurePose, val b: FigurePose?, val deco: FigureDeco, val scale: Float = 1f)

object FigureMath {
    const val W = 160f
    const val H = 100f
    const val FLOOR = 88f
    const val HEAD_R = 6.4f
    const val HEAD_OFFSET = 9.5f

    /** Длина цикла «держим → идём → держим → идём назад», секунды. */
    const val CYCLE_SEC = 3.2f

    private fun lerp(a: Pt, b: Pt, k: Float) = Pt(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k)
    private fun lerp(a: List<Pt>, b: List<Pt>, k: Float) = a.indices.map { lerp(a[it], b[it], k) }

    /** Поза между началом (k = 0) и концом (k = 1). Без позы B фигура «дышит»: таз и плечо чуть поднимаются. */
    fun pose(spec: FigureSpec, k: Float): FigurePose {
        val a = spec.a
        val b = spec.b ?: a.copy(s = Pt(a.s.x, a.s.y - 0.8f), p = Pt(a.p.x, a.p.y - 1.6f))
        return FigurePose(
            s = lerp(a.s, b.s, k),
            p = lerp(a.p, b.p, k),
            arm = lerp(a.arm, b.arm, k),
            leg = lerp(a.leg, b.leg, k),
            arm2 = if (a.arm2 != null && b.arm2 != null) lerp(a.arm2, b.arm2, k) else null,
            leg2 = if (a.leg2 != null && b.leg2 != null) lerp(a.leg2, b.leg2, k) else null,
        )
    }

    private fun ease(x: Float): Float =
        if (x < 0.5f) 4f * x * x * x else 1f - Math.pow((-2.0 * x + 2.0), 3.0).toFloat() / 2f

    /**
     * Доля движения в момент [timeSec]: 0,4 с в начале, 1,2 с к концу, 0,4 с в конце, 1,2 с назад.
     * Для «держим» (без позы B) — медленное дыхание.
     */
    fun phase(spec: FigureSpec, timeSec: Float): Float {
        if (spec.b == null) return 0.5f + 0.5f * sin(timeSec * PI.toFloat() * 0.9f)
        val t = timeSec % CYCLE_SEC
        return when {
            t < 0.4f -> 0f
            t < 1.6f -> ease((t - 0.4f) / 1.2f)
            t < 2.0f -> 1f
            else -> 1f - ease((t - 2.0f) / 1.2f)
        }
    }
}
