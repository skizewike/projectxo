package com.projectxo.app.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import com.projectxo.app.core.model.BalanceFirework
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Салют «Баланса дня» на 50, 80 и 100 % (Р81, по образцу владельца): вспышки из лучей и колец из точек
 * вокруг кольца, цвета темы (акцент и чернила). Только линии и точки, без свечения.
 * [trigger] растёт на каждом новом пороге, [tier] — какой порог пройден. «Меньше анимаций» — салюта нет.
 */
@Composable
fun Modifier.balanceFireworks(trigger: Int, tier: Int): Modifier {
    val reduced = LocalReducedMotion.current
    val colors = XoTheme.colors
    val density = LocalDensity.current.density
    val t = remember { Animatable(-1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0 || reduced || tier == 0) return@LaunchedEffect
        t.snapTo(0f)
        t.animateTo(FireworkSpec.totalMs(tier), tween(FireworkSpec.totalMs(tier).toInt(), easing = LinearEasing))
        t.snapTo(-1f)
    }
    return drawWithContent {
        drawContent()
        val ms = t.value
        if (ms >= 0f) drawFireworks(ms, tier, colors.accent, colors.ink, density)
    }
}

internal object FireworkSpec {
    const val BURST_MS = 900f
    const val STAGGER_MS = 170f
    const val RAYS = 12
    const val DOTS = 14

    fun totalMs(tier: Int): Float = BURST_MS + STAGGER_MS * (BalanceFirework.bursts(tier) - 1)
}

/** Детерминированный «случайный» разброс: один и тот же салют при каждом повторе. */
private fun rnd(i: Int): Float {
    val x = sin(i * 127.1f + 311.7f) * 43758.547f
    return x - kotlin.math.floor(x)
}

private fun DrawScope.drawFireworks(ms: Float, tier: Int, accent: Color, ink: Color, density: Float) {
    val n = BalanceFirework.bursts(tier)
    val c = center
    val ringR = size.minDimension / 2f
    val stroke = 2.4f * density
    for (b in 0 until n) {
        val p = (ms - b * FireworkSpec.STAGGER_MS) / FireworkSpec.BURST_MS
        if (p <= 0f || p >= 1f) continue
        // Вспышки ложатся по кругу около кольца, чуть снаружи; у 100 % заходят и внутрь вверх.
        val a = (b / n.toFloat() + rnd(b + tier) * 0.15f) * 2f * PI.toFloat() - PI.toFloat() / 2f
        val d = ringR * (0.75f + 0.45f * rnd(b * 3 + tier))
        val bx = c.x + cos(a) * d
        val by = c.y + sin(a) * d * 0.9f
        val maxR = (18f + 16f * rnd(b * 7 + 1) + (tier - 50) * 0.12f) * density
        val color = if (b % 2 == 0) accent else ink
        val ease = 1f - (1f - p) * (1f - p) * (1f - p)
        val r = maxR * ease
        val alpha = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f
        if (b % 3 == 2) {
            // Кольцо из точек.
            for (k in 0 until FireworkSpec.DOTS) {
                val ka = k * 2f * PI.toFloat() / FireworkSpec.DOTS
                drawCircle(color, radius = stroke * 0.55f, center = Offset(bx + cos(ka) * r, by + sin(ka) * r), alpha = alpha)
            }
        } else {
            // Лучи: в начале длинные от центра, к концу короткие на краю.
            val inner = r * (0.25f + 0.55f * ease)
            for (k in 0 until FireworkSpec.RAYS) {
                val ka = k * 2f * PI.toFloat() / FireworkSpec.RAYS + b
                drawLine(
                    color,
                    start = Offset(bx + cos(ka) * inner, by + sin(ka) * inner),
                    end = Offset(bx + cos(ka) * r, by + sin(ka) * r),
                    strokeWidth = stroke, cap = StrokeCap.Round, alpha = alpha,
                )
            }
        }
    }
}
