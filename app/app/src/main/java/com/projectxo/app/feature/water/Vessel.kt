package com.projectxo.app.feature.water

import com.projectxo.app.core.ui.format.volumeUnitLabel
import com.projectxo.app.core.ui.format.volumeNumber
import com.projectxo.app.core.ui.format.formatVolume
import com.projectxo.app.core.ui.format.LocalVolumeUnit
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.projectxo.app.R
import com.projectxo.app.core.motion.GoalCelebration
import com.projectxo.app.core.motion.GoalCelebrationSpec
import com.projectxo.app.core.motion.RoundRectPerimeter
import com.projectxo.app.core.motion.VesselScale
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.motion.InkTarget
import com.projectxo.app.core.ui.motion.InkTargets
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoTheme
import java.text.NumberFormat
import kotlin.math.abs

/**
 * Мерный сосуд (docs/DESIGN.md, 5a) с анимацией выполненной цели «Обход и шкала» (5c).
 * [celebrationMs] — время с момента пересечения цели в мс, или отрицательное, если анимации нет.
 * [onPour] — своя порция жестом: объём и точка на экране, откуда полетят чернила.
 */
@Composable
fun Vessel(
    totalMl: Int,
    goalMl: Long,
    celebrationMs: () -> Float,
    onPour: (ml: Int, from: Offset) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val locale = rememberFormatLocale()
    val nf = remember(locale) { NumberFormat.getIntegerInstance(locale) }

    // Уровень на пружине с лёгким перелётом: вода «доливается» и садится.
    val level = remember { Animatable(totalMl.toFloat()) }
    LaunchedEffect(totalMl, reduced) {
        if (reduced) level.snapTo(totalMl.toFloat())
        else level.animateTo(totalMl.toFloat(), spring(dampingRatio = LEVEL_DAMPING, stiffness = LEVEL_STIFFNESS))
    }
    var dragMl by remember { mutableStateOf<Float?>(null) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentTotal by rememberUpdatedState(totalMl)
    val currentPour by rememberUpdatedState(onPour)

    val px = with(density) {
        object {
            val radius = XoSize.vesselRadius.toPx()
            val tick = XoSize.vesselTick.toPx()
            val inset = XoSize.vesselLabelInset.toPx()
            val hide = XoSize.vesselLabelHide.toPx()
            val dash = XoSize.goalDash.toPx()
            val solid = XoSize.goalSolid.toPx()
            val hairline = XoSize.hairline.toPx()
            val handleW = XoSize.handleWidth.toPx()
            val handleH = XoSize.handleHeight.toPx()
            val handleLift = XoSize.handleLift.toPx()
            val sagMax = XoSize.surfaceSagMax.toPx()
            val pillH = XoSize.dragPillHeight.toPx()
            val pillLift = XoSize.dragPillLift.toPx()
            val dp = density.density
        }
    }

    fun top(): Int = VesselScale.top(maxOf(currentTotal.toFloat(), level.value))
    fun yOf(ml: Float, h: Float): Float = h - h * (ml / top()).coerceIn(0f, 1f)

    // Цель чернильного потока: центр поверхности воды.
    InkTarget(InkTargets.WATER_LEVEL) {
        val c = coords ?: return@InkTarget Offset.Unspecified
        val p = c.positionInRoot()
        Offset(p.x + c.size.width / 2f, p.y + yOf(level.value, c.size.height.toFloat()))
    }

    val volumeUnit = LocalVolumeUnit.current
    val unitLabel = volumeUnitLabel(volumeUnit)
    val a11y = stringResource(R.string.vessel_a11y, formatVolume(totalMl.toLong()), formatVolume(goalMl))
    val customLabel = stringResource(R.string.vessel_custom_action)
    val dragAmountTpl = stringResource(R.string.vessel_drag_amount, "%s")
    val dragHigher = stringResource(R.string.vessel_drag_higher)

    Box(
        modifier
            .onGloballyPositioned { coords = it }
            .semantics {
                contentDescription = a11y
                customActions = listOf(CustomAccessibilityAction(customLabel) { onCustom(); true })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val h = size.height.toFloat()
                    fun mlAt(y: Float) = ((h - y) / h).coerceIn(0f, 1f) * top()
                    dragMl = mlAt(down.position.y)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        dragMl = mlAt(change.position.y)
                        change.consume()
                    }
                    val target = dragMl ?: return@awaitEachGesture
                    dragMl = null
                    val amount = VesselScale.dragAmount(target, currentTotal)
                    val c = coords
                    if (amount > 0 && c != null) {
                        val p = c.positionInRoot()
                        currentPour(amount, Offset(p.x + c.size.width / 2f, p.y + yOf(target, h)))
                    }
                }
            }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(px.radius))) }
                val water = Path()
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(px.dash, px.dash))
                val border = Stroke(width = px.hairline)
                val point = FloatArray(2)
                val perimeter = RoundRectPerimeter.length(w, h, px.radius)
                val traceLen = GoalCelebrationSpec.TRACE_LENGTH_DP * px.dp
                onDrawBehind {
                    val topMl = top()
                    val sy = yOf(level.value, h)
                    val sag = (level.velocity * SAG_PER_ML_S * px.dp).coerceIn(-px.sagMax, px.sagMax)
                    val cel = celebrationMs()
                    val reached = currentTotal >= goalMl

                    clipPath(clip) {
                        drawRect(colors.track)
                        water.reset()
                        water.moveTo(0f, sy)
                        water.quadraticTo(w / 2f, sy + sag, w, sy)
                        water.lineTo(w, h)
                        water.lineTo(0f, h)
                        water.close()
                        drawPath(water, colors.accent)

                        // Шкала: штрихи справа, числа слева; на воде цвет фона.
                        val step = VesselScale.labelStep(topMl)
                        var ml = step
                        while (ml < topMl) {
                            val ty = yOf(ml.toFloat(), h)
                            val under = ty > sy
                            val col = if (under) colors.background else colors.muted
                            val isGoal = ml.toLong() == goalMl
                            if (!isGoal) {
                                drawLine(col, Offset(w - px.tick, ty), Offset(w, ty), px.hairline)
                            }
                            if (abs(ty - sy) > px.hide || isGoal) {
                                val style = if (isGoal) type.scale.copy(fontWeight = FontWeight.SemiBold) else type.scale
                                val layout = measurer.measure(volumeNumber(locale, ml.toLong(), volumeUnit, wholeOunces = true), style)
                                val ly = if (isGoal) ty - layout.size.height - px.solid else ty - layout.size.height / 2f
                                drawText(layout, color = col, topLeft = Offset(px.inset, ly))
                            }
                            ml += step
                        }

                        // Линия цели: пунктир, после выполнения сплошная (5c).
                        val gy = yOf(goalMl.toFloat(), h)
                        if (gy > 0f && gy < h) {
                            val solid = when {
                                !reached -> 0f
                                cel >= 0f && cel < GoalCelebrationSpec.TOTAL_MS -> GoalCelebration.solidProgress(cel)
                                else -> 1f
                            }
                            val col = if (gy > sy) colors.background else colors.ink
                            if (solid < 1f) {
                                drawLine(col, Offset(w * solid, gy), Offset(w, gy), px.hairline, pathEffect = dashEffect)
                            }
                            if (solid > 0f) drawLine(col, Offset(0f, gy), Offset(w * solid, gy), px.solid)
                        }

                        // Волна делений снизу вверх.
                        if (cel >= 0f && cel < GoalCelebrationSpec.TOTAL_MS) {
                            var i = 0
                            var wm = GoalCelebrationSpec.WAVE_EVERY_ML
                            while (wm < topMl) {
                                val bump = GoalCelebration.waveBump(cel, i)
                                if (bump > 0f) {
                                    val ty = yOf(wm.toFloat(), h)
                                    val len = (GoalCelebrationSpec.WAVE_BASE_DP + GoalCelebrationSpec.WAVE_EXTRA_DP * bump) * px.dp
                                    drawLine(
                                        if (ty > sy) colors.background else colors.ink,
                                        Offset(w - len, ty), Offset(w, ty), px.hairline,
                                        alpha = WAVE_MIN_ALPHA + (1f - WAVE_MIN_ALPHA) * bump,
                                    )
                                }
                                wm += GoalCelebrationSpec.WAVE_EVERY_ML
                                i++
                            }
                        }

                        // Ручка на поверхности: сосуд можно тянуть.
                        if (dragMl == null) {
                            drawRoundRect(
                                colors.background,
                                topLeft = Offset(w / 2f - px.handleW / 2f, sy - px.handleLift),
                                size = Size(px.handleW, px.handleH),
                                cornerRadius = CornerRadius(px.handleH / 2f),
                                alpha = HANDLE_ALPHA,
                            )
                        }
                    }
                    drawRoundRect(colors.line, cornerRadius = CornerRadius(px.radius), style = border)

                    // След по контуру.
                    if (cel >= 0f && cel < GoalCelebrationSpec.TRACE_MS + GoalCelebrationSpec.TRACE_FADE_MS) {
                        val head = GoalCelebration.traceHead(cel, perimeter, traceLen)
                        val fade = GoalCelebration.traceAlpha(cel)
                        val parts = GoalCelebrationSpec.TRACE_PARTS
                        val width = GoalCelebrationSpec.TRACE_WIDTH_DP * px.dp
                        for (k in 0 until parts) {
                            val a0 = (head - traceLen * (k + 1) / parts).coerceIn(0f, perimeter)
                            val a1 = (head - traceLen * k / parts).coerceIn(0f, perimeter)
                            if (a1 <= a0) continue
                            val alpha = (1f - k.toFloat() / parts) * fade
                            var prevX = 0f
                            var prevY = 0f
                            for (j in 0..TRACE_STEPS) {
                                RoundRectPerimeter.point(a0 + (a1 - a0) * j / TRACE_STEPS, 0f, 0f, w, h, px.radius, point)
                                if (j > 0) {
                                    drawLine(colors.ink, Offset(prevX, prevY), Offset(point[0], point[1]), width, StrokeCap.Round, alpha = alpha)
                                }
                                prevX = point[0]
                                prevY = point[1]
                            }
                        }
                    }

                    // Перетаскивание: пунктир на новой отметке и плашка с объёмом.
                    dragMl?.let { d ->
                        val dy = yOf(d, h)
                        drawLine(colors.ink, Offset(-px.inset / 2f, dy), Offset(w + px.inset / 2f, dy), px.hairline, pathEffect = dashEffect)
                        val amount = VesselScale.dragAmount(d, currentTotal)
                        val label = if (amount > 0) dragAmountTpl.format("${volumeNumber(locale, amount.toLong(), volumeUnit)} $unitLabel") else dragHigher
                        val layout = measurer.measure(label, type.caption.copy(fontWeight = FontWeight.SemiBold))
                        val pw = layout.size.width + px.pillH
                        val left = (w - pw) / 2f
                        drawRoundRect(
                            colors.ink, topLeft = Offset(left, dy - px.pillLift),
                            size = Size(pw, px.pillH), cornerRadius = CornerRadius(px.pillH / 2f),
                        )
                        drawText(
                            layout, color = colors.background,
                            topLeft = Offset(left + px.pillH / 2f, dy - px.pillLift + (px.pillH - layout.size.height) / 2f),
                        )
                    }
                }
            },
    )
}

private const val LEVEL_DAMPING = 0.82f
private const val LEVEL_STIFFNESS = 220f
private const val SAG_PER_ML_S = 0.004f
private const val WAVE_MIN_ALPHA = 0.1f
private const val HANDLE_ALPHA = 0.9f
private const val TRACE_STEPS = 3

