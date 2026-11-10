package com.projectxo.app.feature.food

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.projectxo.app.core.data.FoodTotals
import com.projectxo.app.core.model.FoodMath
import com.projectxo.app.core.motion.GoalCelebration
import com.projectxo.app.core.motion.GoalCelebrationSpec
import com.projectxo.app.core.motion.RoundRectPerimeter
import com.projectxo.app.core.ui.motion.animatedValue
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoTheme

/** Цвета слоёв квадрата. Различаются тоном: белки — ink, жиры — средний тон, углеводы — акцент. */
@Composable
fun foodLayerColors(): Triple<Color, Color, Color> {
    val c = XoTheme.colors
    return Triple(c.ink, lerp(c.background, c.ink, XoSize.FOOD_MID_TONE), c.accent)
}

/**
 * Квадрат дня: заполняется снизу слоями белков, жиров и углеводов (по энергии) до доли цели.
 * Шкала из 9 делений внутри справа, контур цели пунктиром (после цели сплошной), перебор — штриховка над квадратом.
 * [celebrationMs] — время с пересечения цели в мс, отрицательное, если анимации нет (docs/DESIGN.md, 5c).
 */
@Composable
fun FoodSquare(
    totals: FoodTotals,
    goalKcal: Long,
    celebrationMs: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = XoTheme.colors
    val (cp, cf, cc) = foodLayerColors()
    val (p, f, c) = FoodMath.layerFractions(totals, goalKcal)
    val ap by animatedValue(p)
    val af by animatedValue(f)
    val ac by animatedValue(c)
    val over by animatedValue(FoodMath.overRatio(totals, goalKcal))
    val reached = goalKcal > 0 && totals.kcal >= goalKcal

    val density = LocalDensity.current
    val px = with(density) {
        object {
            val side = XoSize.foodSquare.toPx()
            val radius = XoSize.foodSquareRadius.toPx()
            val gap = XoSize.foodLayerGap.toPx()
            val outline = XoSize.foodOutline.toPx()
            val overH = XoSize.foodOverHeight.toPx()
            val overGap = XoSize.foodOverGap.toPx()
            val hatch = XoSize.foodHatchStep.toPx()
            val tickShort = XoSize.foodTickShort.toPx()
            val tickLong = XoSize.foodTickLong.toPx()
            val hairline = XoSize.hairline.toPx()
            val dp = density.density
        }
    }
    val top = XoSize.foodOverHeight + XoSize.foodOverGap
    val point = FloatArray(2)

    Box(modifier.size(XoSize.foodSquare, XoSize.foodSquare + top)) {
        Canvas(Modifier.size(XoSize.foodSquare, XoSize.foodSquare + top)) {
            val s = px.side
            val oy = px.overH + px.overGap
            val cel = celebrationMs()
            val fill = (ap + af + ac).coerceIn(0f, 1f)

            // Слои снизу вверх.
            val clip = Path().apply { addRoundRect(RoundRect(0f, oy, s, oy + s, CornerRadius(px.radius))) }
            clipPath(clip) {
                drawRect(colors.track, Offset(0f, oy), Size(s, s))
                var y = oy + s
                listOf(ap to cp, af to cf, ac to cc).forEachIndexed { i, (frac, col) ->
                    val h = s * frac
                    if (h <= 0f) return@forEachIndexed
                    drawRect(col, Offset(0f, y - h), Size(s, h))
                    y -= h
                    // Просвет между слоями цветом фона, чтобы слои не сливались.
                    if (i < 2 && y > oy + 1f) drawRect(colors.background, Offset(0f, y - px.gap / 2f), Size(s, px.gap))
                }

                // Шкала справа внутри.
                for (i in 1..9) {
                    val ty = oy + s - s * i / 10f
                    val on = i / 10f <= fill
                    val bump = if (cel >= 0f && cel < GoalCelebrationSpec.TOTAL_MS) GoalCelebration.waveBump(cel, i) else 0f
                    val base = if (i == 5) px.tickLong else px.tickShort
                    val len = base + GoalCelebrationSpec.WAVE_EXTRA_DP * 0.4f * px.dp * bump
                    drawLine(
                        if (on) colors.background else colors.muted,
                        Offset(s - len, ty), Offset(s, ty), px.hairline,
                        alpha = if (on) 0.9f else 0.6f,
                    )
                }
            }

            // Контур цели: пунктир, после выполнения сплошной.
            val solid = reached && (cel < 0f || cel >= GoalCelebrationSpec.SOLID_FROM_MS)
            drawRoundRect(
                colors.ink, Offset(-px.outline / 2f, oy - px.outline / 2f), Size(s + px.outline, s + px.outline),
                CornerRadius(px.radius),
                style = Stroke(px.outline, pathEffect = if (solid) null else PathEffect.dashPathEffect(floatArrayOf(4f * px.dp, 4f * px.dp))),
            )

            // Перебор: штриховка над квадратом.
            if (over > 0f) {
                val oh = px.overH * over
                val y0 = oy - px.overGap - oh
                val hatchClip = Path().apply { addRoundRect(RoundRect(0f, y0, s, y0 + oh, CornerRadius(2f * px.dp))) }
                clipPath(hatchClip) {
                    var x = -oh
                    while (x < s + oh) {
                        drawLine(colors.ink, Offset(x, y0 + oh), Offset(x + oh, y0), px.hairline, alpha = XoSize.FOOD_OVER_ALPHA)
                        x += px.hatch
                    }
                }
            }

            // След по контуру при пересечении цели.
            if (cel >= 0f && cel < GoalCelebrationSpec.TRACE_MS + GoalCelebrationSpec.TRACE_FADE_MS) {
                val perimeter = RoundRectPerimeter.length(s, s, px.radius)
                val traceLen = GoalCelebrationSpec.TRACE_LENGTH_DP * px.dp
                val head = GoalCelebration.traceHead(cel, perimeter, traceLen)
                val fadeA = GoalCelebration.traceAlpha(cel)
                val parts = GoalCelebrationSpec.TRACE_PARTS
                val width = GoalCelebrationSpec.TRACE_WIDTH_DP * px.dp
                for (k in 0 until parts) {
                    val a0 = (head - traceLen * (k + 1) / parts).coerceIn(0f, perimeter)
                    val a1 = (head - traceLen * k / parts).coerceIn(0f, perimeter)
                    if (a1 <= a0) continue
                    val alpha = (1f - k.toFloat() / parts) * fadeA
                    var prevX = 0f
                    var prevY = 0f
                    for (j in 0..TRACE_STEPS) {
                        RoundRectPerimeter.point(a0 + (a1 - a0) * j / TRACE_STEPS, 0f, oy, s, s, px.radius, point)
                        if (j > 0) drawLine(colors.ink, Offset(prevX, prevY), Offset(point[0], point[1]), width, StrokeCap.Round, alpha = alpha)
                        prevX = point[0]
                        prevY = point[1]
                    }
                }
            }
        }
    }
}

private const val TRACE_STEPS = 3
