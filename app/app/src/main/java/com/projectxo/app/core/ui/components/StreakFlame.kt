package com.projectxo.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.withFrameMillis
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Огонёк серии, вариант 01 «Пламя» (Р80, превью `design/streak-flame-variants.html`).
 * Одна заливка акцентом; уровень 2 — светлая сердцевина (вырез), уровень 3 — боковой язык и искры.
 * [waiting] — сегодня не отмечено: огонёк приглушённый и неподвижный. Без свечения и размытия.
 */
@Composable
fun StreakFlame(level: Int, size: Dp, modifier: Modifier = Modifier, waiting: Boolean = false) {
    if (level <= 0) return
    val colors = XoTheme.colors
    val reduced = LocalReducedMotion.current
    val still = reduced || waiting
    val time = remember { mutableFloatStateOf(1.3f) }
    if (!still) {
        LaunchedEffect(Unit) {
            val start = withFrameMillis { it }
            while (true) withFrameMillis { time.floatValue = 1.3f + (it - start) / 1000f }
        }
    }
    val path = remember { Path() }
    val pts = remember { FloatArray(FlameShape.POINTS * 2) }
    val color = if (waiting) colors.muted else colors.accent
    Canvas(
        modifier
            .size(size)
            // Отдельный слой: вырез сердцевины прорезает только огонёк, а не фон под ним.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val t = if (still) 1.3f else time.floatValue
        drawFlame(level.coerceIn(1, 3), t, color, if (still) 0f else 1f, path, pts)
    }
}

private fun DrawScope.drawFlame(lvl: Int, t: Float, color: Color, motion: Float, path: Path, pts: FloatArray) {
    val s = size.minDimension
    val cx = size.width / 2f
    val by = size.height * 0.98f
    val w = s * W[lvl - 1]
    val h = s * H[lvl - 1]
    val amp = s * 0.05f * lvl * motion
    val sp = 3.5f + lvl * 1.2f
    fun fill(cx0: Float, by0: Float, w0: Float, h0: Float, a: Float, sp0: Float, ph: Float, lean: Float, blend: BlendMode = BlendMode.SrcOver) {
        FlameShape.build(path, pts, cx0, by0, w0, h0, t, a, sp0, ph, lean)
        drawPath(path, color, blendMode = blend)
    }
    if (lvl >= 3) fill(cx + w * 0.42f, by, w * 0.42f, h * 0.5f, amp * 0.7f, sp + 0.7f, 1.7f, 0.4f)
    fill(cx + w * 0.06f, by, w * 0.9f, h, amp, sp, 0f, 0.22f)
    fill(cx - w * 0.24f, by - h * 0.1f, w * 0.44f, h * 0.6f, amp * 0.8f, sp + 0.9f, 2.1f, -0.35f)
    if (lvl >= 2) fill(cx + w * 0.04f, by - s * 0.05f, w * 0.36f, h * 0.46f, amp * 0.5f, sp + 1f, 0.8f, 0.15f, BlendMode.Clear)
    if (lvl >= 3 && motion > 0f) {
        for (i in 0 until 4) {
            val f = ((t * 0.55f + i / 4f) % 1f)
            drawCircle(
                color, radius = s * 0.03f * (1f - f * 0.6f), alpha = 1f - f,
                center = Offset(cx + sin(t * 2f + i * 2.3f) * w * 0.45f, by - h * (0.75f + 0.55f * f)),
            )
        }
    }
}

private val W = floatArrayOf(0.5f, 0.62f, 0.72f)
private val H = floatArrayOf(0.7f, 0.86f, 1.0f)

/** Контур одного языка пламени: низ скруглён, верх сужается и колеблется. Общий для огонька и салюта. */
internal object FlameShape {
    private const val N = 16
    const val POINTS = 2 * N

    fun build(path: Path, p: FloatArray, cx: Float, by: Float, w: Float, h: Float, t: Float, amp: Float, sp: Float, ph: Float, lean: Float) {
        val hh = h * (1f + 0.05f * sin(t * sp * 1.3f + ph))
        // Правый край снизу вверх, затем левый сверху вниз; нижняя и верхняя точки общие.
        for (i in 0..N) {
            val s = i.toFloat() / N
            var hw = if (s < 0.3f) sqrt(1f - (1f - s / 0.3f).pow(2)) else cos((s - 0.3f) / 0.7f * PI.toFloat() / 2f).coerceAtLeast(0f).pow(1.25f)
            hw *= w / 2f * (1f + 0.07f * sin(t * sp * 2.1f + s * 6f + ph))
            val sx = amp * s.pow(1.7f) * sin(t * sp + s * 3.2f + ph) + lean * w * s * s
            val y = by - s * hh
            if (i < N) { p[2 * i] = cx + sx + hw; p[2 * i + 1] = y }
            if (i > 0) { val j = 2 * N - i; p[2 * j] = cx + sx - hw; p[2 * j + 1] = y }
        }
        // Сглаживание: кривые через середины соседних точек.
        path.reset()
        val n = N * 2
        path.moveTo((p[2 * (n - 1)] + p[0]) / 2f, (p[2 * (n - 1) + 1] + p[1]) / 2f)
        for (i in 0 until n) {
            val k = (i + 1) % n
            path.quadraticTo(p[2 * i], p[2 * i + 1], (p[2 * i] + p[2 * k]) / 2f, (p[2 * i + 1] + p[2 * k + 1]) / 2f)
        }
        path.close()
    }
}
