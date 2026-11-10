package com.projectxo.app.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.projectxo.app.core.ui.format.pluralText
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.projectxo.app.R
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.projectxo.app.core.motion.GoalCelebration
import com.projectxo.app.core.motion.GoalCelebrationSpec
import com.projectxo.app.core.motion.RingTarget
import com.projectxo.app.core.ui.components.RollingNumber
import com.projectxo.app.core.ui.motion.InkTarget
import com.projectxo.app.core.ui.motion.InkTargets
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.motion.animatedValue
import com.projectxo.app.core.ui.splash.EntranceSlot
import com.projectxo.app.core.ui.splash.LocalSplash
import com.projectxo.app.core.ui.splash.entrance
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Кольцо «Баланс дня» с циферблатом (docs/DESIGN.md, 4, 5b п. 2–4).
 * Цель полёта O из заставки: сообщает свои координаты в SplashController и рисует дугу только после заставки,
 * чтобы на экране всегда было одно кольцо. «Голова» дуги — цель чернильного потока.
 * [sealTrigger] — увеличивается, когда модуль впервые за день дошёл до цели: по делениям проходит волна.
 * [celebrateTrigger] — увеличивается, когда «Баланс дня» дошёл до 100 %: «Обход и шкала» на кольце (DESIGN.md 5c.1).
 */
@Composable
fun DayBalanceRing(
    ratio: Float,
    percent: Int,
    sealTrigger: Int,
    modifier: Modifier = Modifier,
    celebrateTrigger: Int = 0,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val splash = LocalSplash.current
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val large = density.fontScale >= XoSize.LARGE_TEXT_SCALE
    val diameter = if (large) XoSize.ringDiameterLarge else XoSize.ringDiameter
    val strokeDp = if (large) XoSize.ringStrokeLarge else XoSize.ringStroke
    val px = with(density) {
        object {
            val stroke = strokeDp.toPx()
            val radius = diameter.toPx() / 2f
            val dialInner = diameter.toPx() / 2f + strokeDp.toPx() / 2f + XoSize.dialGap.toPx()
            val tick = XoSize.dialTick.toPx()
            val tickSeal = XoSize.dialTickSeal.toPx()
            val tickWidth = XoSize.dialTickWidth.toPx()
            val traceWidth = GoalCelebrationSpec.TRACE_WIDTH_DP * density.density
            val traceRadius = dialInner + tick + GoalCelebrationSpec.RING_TRACE_GAP_DP * density.density + traceWidth / 2f
            val traceLen = GoalCelebrationSpec.TRACE_LENGTH_DP * density.density
        }
    }
    val boxSize = diameter + strokeDp + (XoSize.dialGap + XoSize.dialTickSeal) * 2

    val a11y = pluralText(R.plurals.day_balance_a11y, percent, percent)
    // Пока идёт заставка, кольцо сразу стоит на значении: туда прилетает O.
    val shownRatio by animatedValue(ratio, snap = !splash.done)
    val trackStroke = remember(px.stroke) { Stroke(width = px.stroke) }
    val arcStroke = remember(px.stroke) { Stroke(width = px.stroke, cap = StrokeCap.Round) }

    // Углы делений считаются один раз.
    val n = XoSize.DIAL_TICKS
    val cosA = remember { FloatArray(n) { i -> cos(2 * PI * i / n - PI / 2).toFloat() } }
    val sinA = remember { FloatArray(n) { i -> sin(2 * PI * i / n - PI / 2).toFloat() } }

    // Follow-through: после посадки O деления проявляются по часовой стрелке.
    val revealTotal = (n - 1) * XoMotion.DIAL_STEP_MS + XoMotion.DIAL_TICK_MS
    val reveal = remember { Animatable(if (splash.done) 1f else 0f) }
    LaunchedEffect(splash.done, reduced) {
        if (!splash.done) return@LaunchedEffect
        if (reduced) reveal.snapTo(1f) else reveal.animateTo(1f, tween(revealTotal, easing = LinearEasing))
    }
    // Печать цели: одна волна по делениям.
    val seal = remember { Animatable(0f) }
    LaunchedEffect(sealTrigger) {
        if (sealTrigger == 0 || reduced) return@LaunchedEffect
        seal.snapTo(0f)
        seal.animateTo(1f, tween(XoMotion.SEAL_MS, easing = XoMotion.Standard))
        seal.snapTo(0f)
    }

    // «Обход и шкала» при 100 % (5c.1).
    val cel = remember { Animatable(-1f) }
    LaunchedEffect(celebrateTrigger) {
        if (celebrateTrigger == 0 || reduced) return@LaunchedEffect
        cel.snapTo(0f)
        cel.animateTo(GoalCelebrationSpec.TOTAL_MS, tween(GoalCelebrationSpec.TOTAL_MS.toInt(), easing = LinearEasing))
        cel.snapTo(-1f)
    }
    val traceStroke = remember(px.traceWidth) { Stroke(width = px.traceWidth, cap = StrokeCap.Round) }

    val center = remember { floatArrayOf(Float.NaN, Float.NaN) }
    InkTarget(InkTargets.TODAY_RING) {
        val a = 2.0 * PI * shownRatio - PI / 2
        Offset(center[0] + px.radius * cos(a).toFloat(), center[1] + px.radius * sin(a).toFloat())
    }

    Box(
        modifier = modifier
            .size(boxSize)
            .clearAndSetSemantics { contentDescription = a11y }
            .onGloballyPositioned { c ->
                val b = c.boundsInRoot()
                center[0] = b.center.x
                center[1] = b.center.y
                splash.target = RingTarget(b.center.x, b.center.y, px.radius, px.stroke)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (!splash.done) return@Canvas
            val c = this.center
            // Циферблат
            val revealMs = reveal.value * revealTotal
            val wave = seal.value
            val head = -XoMotion.SEAL_WIDTH + (1f + 2 * XoMotion.SEAL_WIDTH) * wave
            for (i in 0 until n) {
                val a = ((revealMs - i * XoMotion.DIAL_STEP_MS) / XoMotion.DIAL_TICK_MS).coerceIn(0f, 1f)
                if (a <= 0f) continue
                val frac = i.toFloat() / n
                var len = px.tick
                if (wave > 0f) {
                    val x = (frac - head) / XoMotion.SEAL_WIDTH
                    if (abs(x) < 1f) len += (px.tickSeal - px.tick) * 0.5f * (1f + cos(PI.toFloat() * x))
                }
                val celT = cel.value
                if (celT >= 0f) {
                    len = maxOf(len, px.tick + (px.tickSeal - px.tick) * GoalCelebration.ringWaveBump(celT, i))
                }
                val color = if (frac < shownRatio) colors.ink else colors.line
                drawLine(
                    color = color,
                    start = Offset(c.x + cosA[i] * px.dialInner, c.y + sinA[i] * px.dialInner),
                    end = Offset(c.x + cosA[i] * (px.dialInner + len), c.y + sinA[i] * (px.dialInner + len)),
                    strokeWidth = px.tickWidth,
                    alpha = a,
                )
            }
            // След по окружности снаружи шкалы.
            val celT = cel.value
            if (celT >= 0f && celT < GoalCelebrationSpec.TRACE_MS + GoalCelebrationSpec.TRACE_FADE_MS) {
                val perimeter = (2.0 * PI * px.traceRadius).toFloat()
                val head = GoalCelebration.traceHead(celT, perimeter, px.traceLen)
                val fade = GoalCelebration.traceAlpha(celT)
                val parts = GoalCelebrationSpec.TRACE_PARTS
                for (k in 0 until parts) {
                    val a0 = (head - px.traceLen * (k + 1) / parts).coerceIn(0f, perimeter)
                    val a1 = (head - px.traceLen * k / parts).coerceIn(0f, perimeter)
                    if (a1 <= a0) continue
                    drawArc(
                        color = colors.ink,
                        startAngle = -90f + a0 / perimeter * 360f,
                        sweepAngle = (a1 - a0) / perimeter * 360f,
                        useCenter = false,
                        topLeft = Offset(c.x - px.traceRadius, c.y - px.traceRadius),
                        size = Size(px.traceRadius * 2f, px.traceRadius * 2f),
                        style = traceStroke,
                        alpha = (1f - k.toFloat() / parts) * fade,
                    )
                }
            }
            // Кольцо
            drawCircle(colors.track, radius = px.radius, style = trackStroke)
            if (shownRatio > 0f) {
                drawArc(
                    color = colors.accent, startAngle = -90f, sweepAngle = 360f * shownRatio, useCenter = false,
                    topLeft = Offset(c.x - px.radius, c.y - px.radius), size = Size(px.radius * 2f, px.radius * 2f),
                    style = arcStroke,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.entrance(EntranceSlot.RING_LABEL),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(XoSpacing.s1),
                modifier = Modifier.graphicsLayer {
                    val t = cel.value
                    if (t >= 0f) {
                        translationY = GoalCelebration.dipOffsetDp(t) * density.density
                        scaleY = GoalCelebration.dipScaleY(t)
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                },
            ) {
                RollingNumber(
                    value = percent.toLong(), text = percent.toString(), style = type.display, color = colors.ink,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    stringResource(R.string.percent_sign), style = type.displayUnit, color = colors.muted,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Spacer(Modifier.height(XoSpacing.s2))
            Text(stringResource(R.string.day_balance), style = type.caption, color = colors.muted)
        }
    }
}
