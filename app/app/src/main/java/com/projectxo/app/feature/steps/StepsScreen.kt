package com.projectxo.app.feature.steps

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.projectxo.app.core.ui.format.pluralText
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.projectxo.app.R
import com.projectxo.app.core.motion.GoalCelebration
import com.projectxo.app.core.motion.GoalCelebrationSpec
import com.projectxo.app.core.steps.StepsMath
import com.projectxo.app.core.ui.components.RollingNumber
import com.projectxo.app.core.ui.components.UndoBar
import com.projectxo.app.core.ui.components.XoTextButton
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.formatToday
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.motion.animatedValue
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime

/** Что показывать вместо графика, если шаги недоступны. */
enum class StepsAccess {
    Granted, NeedPermission, OpenSettings, NoSensor,

    /** Нет датчика шагов, но можно считать по движению (Р65): ещё не включено. */
    MotionOff,

    /** Счёт по движению идёт. */
    MotionOn,
}

/** «Ходьба»: «Часы дня» (docs/DESIGN.md, 5d). */
@Composable
fun StepsScreen(
    state: StepsUiState,
    access: StepsAccess,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
    onAddManual: (Int) -> Unit = {},
    onUndoManual: () -> Unit = {},
    onStopMotion: () -> Unit = {},
    /** Фоновый счёт включён (телефон с датчиком шагов, Р67). */
    backgroundOn: Boolean = true,
    onStartBackground: () -> Unit = {},
    /** Текущий час; в превью и тестах подставляется. */
    nowHour: Int? = null,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val hourNow by produceState(nowHour ?: LocalTime.now().hour, nowHour) {
        if (nowHour != null) return@produceState
        while (true) {
            value = LocalTime.now().hour
            delay(MINUTE_MS)
        }
    }

    // Анимация цели: один раз при переходе через цель (5c, 5d).
    val celebration = remember { Animatable(-1f) }
    var prevTotal by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.total, state.loaded) {
        if (!state.loaded) return@LaunchedEffect
        val p = prevTotal
        prevTotal = state.total
        if (p != null && p < state.goal && state.total >= state.goal) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            if (!reduced) {
                celebration.snapTo(0f)
                celebration.animateTo(GoalCelebrationSpec.TOTAL_MS, tween(GoalCelebrationSpec.TOTAL_MS.toInt(), easing = LinearEasing))
                celebration.snapTo(-1f)
            }
        }
    }

    var showManual by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XoSpacing.screenGutter),
    ) {
        Spacer(Modifier.height(XoSpacing.s5))
        Text(stringResource(R.string.tab_steps), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(XoSpacing.headGap))
        Text(formatToday(state.date), style = type.caption, color = colors.muted)
        if (access == StepsAccess.MotionOn) {
            Spacer(Modifier.height(XoSpacing.headGap))
            Text(stringResource(R.string.motion_on), style = type.caption, color = colors.muted)
        }

        if (access != StepsAccess.Granted && access != StepsAccess.MotionOn) {
            // Без датчика или разрешения экран остаётся рабочим: шаги можно добавить вручную.
            Spacer(Modifier.height(XoSpacing.s6))
            NoAccess(access, onAllow, onManual = { showManual = true })
        }

        Spacer(Modifier.height(XoSpacing.s5))
        StepsTotal(state, celebrationMs = { celebration.value })
        Spacer(Modifier.height(XoSpacing.s6))
        HourBars(state.hours, hourNow, celebrationMs = { celebration.value })
        Spacer(Modifier.height(XoSpacing.s5))
        GoalLine(state, celebrationMs = { celebration.value })
        Spacer(Modifier.height(XoSpacing.s6))
        StatsRows(state.hours, state.strideM)
        Spacer(Modifier.height(XoSpacing.s3))
        // Без датчика та же кнопка уже стоит наверху.
        if (access != StepsAccess.NoSensor) XoTextButton(stringResource(R.string.steps_manual_open), onClick = { showManual = true })
        if (access == StepsAccess.MotionOn) XoTextButton(stringResource(R.string.motion_stop), onClick = onStopMotion)
        // С датчиком: в фоне шаги считает служба с уведомлением, её можно выключить и включить.
        if (access == StepsAccess.Granted) {
            if (backgroundOn) {
                XoTextButton(stringResource(R.string.steps_bg_stop), onClick = onStopMotion)
            } else {
                XoTextButton(stringResource(R.string.steps_bg_start), onClick = onStartBackground)
            }
        }
        // Место под полосу отмены, чтобы она не закрывала кнопку.
        Spacer(Modifier.height(XoSize.touchMin + XoSpacing.s5))
    }

    val undone = state.pendingUndo
    UndoBar(
        visible = undone != null,
        message = stringResource(R.string.steps_manual_added, formatCount((undone ?: 0).toLong()), pluralText(R.plurals.steps_unit, undone ?: 0)),
        action = stringResource(R.string.undo_cancel),
        onAction = onUndoManual,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    }

    if (showManual) {
        StepsManualDialog(
            onConfirm = { count -> showManual = false; onAddManual(count) },
            onDismiss = { showManual = false },
        )
    }
}

@Composable
private fun StepsTotal(state: StepsUiState, celebrationMs: () -> Float) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val dp = LocalDensity.current.density
    val goal = formatCount(state.goal)
    val done = state.left == 0L
    val over = state.total - state.goal
    val first = when {
        done -> stringResource(R.string.goal_done_title)
        state.total == 0 -> stringResource(R.string.goal_today_only, goal)
        else -> stringResource(R.string.steps_left, formatCount(state.left), goal)
    }
    val second = when {
        !done -> null
        over > 0 -> stringResource(R.string.steps_over, formatCount(over), goal)
        else -> stringResource(R.string.steps_exact, goal)
    }
    val unit = pluralText(R.plurals.steps_unit, state.total)
    val a11y = stringResource(R.string.steps_total_a11y, formatCount(state.total.toLong()), listOfNotNull(first, second).joinToString(". "))
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = a11y }) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(XoSpacing.s2),
            modifier = Modifier.graphicsLayer {
                val t = celebrationMs()
                if (t >= 0f) {
                    translationY = GoalCelebration.dipOffsetDp(t) * dp
                    scaleY = GoalCelebration.dipScaleY(t)
                    transformOrigin = TransformOrigin(0f, 1f)
                }
            },
        ) {
            RollingNumber(
                value = state.total.toLong(), text = formatCount(state.total.toLong()),
                style = type.display, color = colors.ink, modifier = Modifier.alignByBaseline(),
            )
            Text(unit, style = type.displayUnitM, color = colors.muted, modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(XoSpacing.s3))
        if (second == null) {
            Text(first, style = type.caption, color = colors.muted)
        } else {
            Text(first, style = type.caption.copy(fontWeight = type.bodyStrong.fontWeight), color = colors.ink)
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(second, style = type.caption, color = colors.muted)
        }
    }
}

/** Столбики 06–23; шаги с 00 до 06 входят в первый столбик (5d). */
@Composable
private fun HourBars(hours: List<Int>, hourNow: Int, celebrationMs: () -> Float) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val measurer = rememberTextMeasurer()
    val from = XoSize.BARS_FROM_HOUR
    val bars = remember(hours) { List(24 - from) { i -> if (i == 0) hours.take(from + 1).sum() else hours[from + i] } }
    // Каждый столбик доезжает до нового значения на springSettle.
    val shown = bars.map { animatedValue(it.toFloat()).value }
    val scaleMax = maxOf(XoSize.BARS_MIN_SCALE, bars.maxOrNull() ?: 0).toFloat()
    val best = StepsMath.bestHour(hours)
    val a11y = if (best == null) stringResource(R.string.steps_hours_empty_a11y)
    else stringResource(R.string.steps_hours_a11y, stringResource(R.string.steps_hour_range, hh(best), hh(best + 1)))
    val labels = remember(from) { (0 until 24 - from).filter { it % XoSize.BARS_LABEL_EVERY == 0 }.associateWith { hh(from + it) } }
    val density = LocalDensity.current
    val labelGap = with(density) { XoSpacing.s2.toPx() }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(XoSize.barsHeight + XoSpacing.s5)
            .clearAndSetSemantics { contentDescription = a11y },
    ) {
        val n = bars.size
        val gap = XoSize.barGap.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        val h = XoSize.barsHeight.toPx()
        val r = CornerRadius(minOf(XoSize.barRadius.toPx(), bw / 2f))
        val t = celebrationMs()
        for (i in 0 until n) {
            val hour = from + i
            val x = i * (bw + gap)
            val lift = if (t >= 0f) GoalCelebration.waveBump(t, i) * XoSize.barLift.toPx() else 0f
            val bh = maxOf(XoSize.barMin.toPx(), h * shown[i] / scaleMax) + lift
            val (color, alpha) = when {
                hour > hourNow -> colors.track to 1f
                hour == hourNow -> colors.accent to 1f
                else -> colors.ink to XoSize.PAST_BAR_ALPHA
            }
            drawRoundRect(color, topLeft = Offset(x, h - bh), size = Size(bw, bh), cornerRadius = r, alpha = alpha)
            labels[i]?.let { text ->
                val layout = measurer.measure(text, type.scale)
                drawText(layout, color = colors.muted, topLeft = Offset(x + bw / 2f - layout.size.width / 2f, h + labelGap))
            }
        }
    }
}

@Composable
private fun GoalLine(state: StepsUiState, celebrationMs: () -> Float) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val shownFraction by animatedValue(state.fraction)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(XoSize.goalLine + XoSize.traceLift * 2)
                .drawBehind {
                    val y = size.height - XoSize.goalLine.toPx()
                    val lh = XoSize.goalLine.toPx()
                    drawRect(colors.track, topLeft = Offset(0f, y), size = Size(size.width, lh))
                    if (shownFraction > 0f) drawRect(colors.ink, topLeft = Offset(0f, y), size = Size(size.width * shownFraction, lh))
                    // След над линией в анимации цели.
                    val t = celebrationMs()
                    if (t >= 0f && t < GoalCelebrationSpec.TRACE_MS + GoalCelebrationSpec.TRACE_FADE_MS) {
                        val len = GoalCelebrationSpec.TRACE_LENGTH_DP * density
                        val head = GoalCelebration.traceHead(t, size.width, len)
                        val fade = GoalCelebration.traceAlpha(t)
                        val parts = GoalCelebrationSpec.TRACE_PARTS
                        val ty = y - XoSize.traceLift.toPx()
                        for (k in 0 until parts) {
                            val a0 = (head - len * (k + 1) / parts).coerceIn(0f, size.width)
                            val a1 = (head - len * k / parts).coerceIn(0f, size.width)
                            if (a1 <= a0) continue
                            drawLine(
                                colors.ink, Offset(a0, ty), Offset(a1, ty), GoalCelebrationSpec.TRACE_WIDTH_DP * density,
                                StrokeCap.Round, alpha = (1f - k.toFloat() / parts) * fade,
                            )
                        }
                    }
                },
        )
        Spacer(Modifier.height(XoSpacing.s2))
        Row(Modifier.fillMaxWidth()) {
            Text(formatCount(0), style = type.scale, color = colors.muted, modifier = Modifier.weight(1f))
            Text(formatCount(state.goal), style = type.scale, color = colors.muted, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun StatsRows(hours: List<Int>, strideM: Double) {
    val locale = rememberFormatLocale()
    val oneDecimal = remember(locale) { NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 } }
    val total = hours.sum()
    val best = StepsMath.bestHour(hours)
    val rows = listOf(
        stringResource(R.string.steps_distance) to stringResource(R.string.steps_distance_value, oneDecimal.format(StepsMath.distanceKm(total, strideM))),
        stringResource(R.string.steps_active_hours) to formatCount(StepsMath.activeHours(hours).toLong()),
        stringResource(R.string.steps_best_hour) to
            (if (best == null) stringResource(R.string.steps_none) else stringResource(R.string.steps_hour_range, hh(best), hh(best + 1))),
    )
    rows.forEachIndexed { i, (label, value) -> StatRow(label, value, isLast = i == rows.lastIndex) }
}

@Composable
private fun StatRow(label: String, value: String, isLast: Boolean) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val hairline = with(LocalDensity.current) { XoSize.hairline.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = XoSize.touchMin)
            .drawBehind {
                drawRect(colors.line, size = Size(size.width, hairline))
                if (isLast) drawRect(colors.line, Offset(0f, size.height - hairline), Size(size.width, hairline))
            }
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type.caption, color = colors.muted, modifier = Modifier.weight(1f))
        Text(value, style = type.bodyStrong, color = colors.ink)
    }
}

@Composable
private fun NoAccess(access: StepsAccess, onAllow: () -> Unit, onManual: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val strokePx = with(LocalDensity.current) { XoSize.iconStroke.toPx() }
    val triangle = remember { Path() }
    val noSensor = access == StepsAccess.NoSensor
    val motion = access == StepsAccess.MotionOff
    // Треугольник — только когда нужно действие с разрешением; без датчика — значок телефона.
    if (noSensor || motion) {
        androidx.compose.material3.Icon(com.projectxo.app.core.ui.icons.XoIcons.Phone, null, tint = colors.ink, modifier = Modifier.size(XoSize.permIcon))
    } else Canvas(Modifier.size(XoSize.permIcon)) {
        val s = size.minDimension
        val stroke = Stroke(width = strokePx, join = StrokeJoin.Round, cap = StrokeCap.Round)
        triangle.reset()
        triangle.moveTo(s / 2f, s * 0.15f)
        triangle.lineTo(s * 0.88f, s * 0.8f)
        triangle.lineTo(s * 0.12f, s * 0.8f)
        triangle.close()
        drawPath(triangle, colors.ink, style = stroke)
    }
    Spacer(Modifier.height(XoSpacing.s5))
    Text(
        stringResource(if (noSensor || motion) R.string.steps_no_sensor_title else R.string.steps_perm_title),
        style = type.subtitle, color = colors.ink,
    )
    Spacer(Modifier.height(XoSpacing.s3))
    Text(
        stringResource(
            when {
                noSensor -> R.string.steps_no_sensor_text
                motion -> R.string.motion_text
                else -> R.string.steps_perm_text
            },
        ),
        style = type.body, color = colors.muted,
    )
    Spacer(Modifier.height(XoSpacing.s5))
    val label = stringResource(
        when (access) {
            StepsAccess.NoSensor -> R.string.steps_manual_add
            StepsAccess.MotionOff -> R.string.motion_start
            StepsAccess.OpenSettings -> R.string.steps_perm_settings
            else -> R.string.steps_perm_allow
        },
    )
    Box(
        Modifier
            .heightIn(min = XoSize.touchMin)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.ink)
            .clickable(onClick = if (noSensor) onManual else onAllow)
            .semantics { role = Role.Button }
            .padding(horizontal = XoSpacing.s5),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = type.bodyStrong, color = colors.background)
    }
}

private fun hh(hour: Int): String = (hour % 24).toString().padStart(2, '0')

private const val MINUTE_MS = 60_000L

@Preview(name = "Светлая", showBackground = true, widthDp = 360, heightDp = 780)
@Preview(name = "Тёмная", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 360, heightDp = 780)
@Preview(name = "Крупный текст", fontScale = 1.3f, showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun StepsPreview() {
    val hours = List(24) { h -> listOf(0, 0, 0, 0, 0, 0, 0, 120, 640, 880, 310, 260, 1420, 980, 1870).getOrElse(h) { 0 } }
    XoTheme {
        Box(Modifier.background(XoTheme.colors.background)) {
            StepsScreen(StepsUiState(LocalDate.of(2026, 11, 9), hours, loaded = true), StepsAccess.Granted, onAllow = {}, nowHour = 14)
        }
    }
}
