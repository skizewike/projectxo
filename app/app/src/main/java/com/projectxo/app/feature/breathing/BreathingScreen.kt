package com.projectxo.app.feature.breathing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.model.BreathKind
import com.projectxo.app.core.model.BreathRhythm
import com.projectxo.app.core.model.BreathingMath
import com.projectxo.app.core.ui.components.XoHairline
import com.projectxo.app.core.ui.components.XoPrimaryButton
import com.projectxo.app.core.ui.components.XoSegmented
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.formatToday
import com.projectxo.app.core.ui.format.pluralText
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import kotlin.math.max

/**
 * «Дыхание», вариант «Волна» (Р54, design/breathing-variants.html): слева след уровня, справа точка.
 * Вдох — точка поднимается, выдох — опускается, задержка — линия идёт ровно.
 */
@Composable
fun BreathingScreen(
    state: BreathingUiState,
    onRhythm: (BreathRhythm) -> Unit,
    onMinutes: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current

    // Часы телефона: тик на каждый кадр, пока идёт сеанс.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.startedAtMs) {
        val started = state.startedAtMs ?: return@LaunchedEffect
        var lastIndex = -1
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            nowMs = System.currentTimeMillis()
            val elapsed = (nowMs - started) / 1000f
            if (elapsed >= state.minutes * 60) {
                onComplete()
                break
            }
            val idx = BreathingMath.stateAt(state.rhythm, elapsed).let { it.cycle * 10 + it.index }
            if (idx != lastIndex) {
                if (lastIndex >= 0) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                lastIndex = idx
            }
        }
    }

    val started = state.startedAtMs
    val elapsed = if (started == null) 0f else ((nowMs - started) / 1000f).coerceAtLeast(0f)
    val s = BreathingMath.stateAt(state.rhythm, elapsed)
    val level = if (started == null) BreathingMath.LOW else BreathingMath.level(s)

    val phaseName = when {
        started != null -> phaseLabel(s.phase.kind)
        state.summary != null -> stringResource(R.string.breath_done)
        else -> stringResource(R.string.breath_ready)
    }
    val phaseSub = when {
        started != null -> max(1, kotlin.math.ceil(s.phase.seconds * (1 - s.progress)).toInt()).toString()
        state.summary != null -> stringResource(R.string.breath_done_sub)
        else -> stringResource(R.string.breath_ready_sub)
    }

    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XoSpacing.screenGutter),
    ) {
        Spacer(Modifier.height(XoSpacing.s5))
        Text(stringResource(R.string.module_breathing), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(XoSpacing.headGap))
        Text(formatToday(state.date), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s5))

        // Фаза и счёт секунд. Озвучивается TalkBack при смене.
        Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(phaseName, style = type.title, color = colors.ink)
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(phaseSub, style = type.body, color = colors.muted)
        }
        Spacer(Modifier.height(XoSpacing.s3))

        // «Волна»: уровень во времени.
        Canvas(Modifier.fillMaxWidth().height(WAVE_HEIGHT)) {
            val w = size.width
            val pad = 8.dp.toPx()
            val base = size.height - pad
            val amp = size.height - 2 * pad
            val dotX = w * DOT_X
            val span = state.rhythm.cycleSeconds * TRAIL_CYCLES
            drawLine(colors.line, Offset(0f, base), Offset(w, base), 1.dp.toPx())
            if (reduced) {
                // Без движения: полоса прогресса текущей фазы.
                drawLine(colors.track, Offset(0f, base - 2.dp.toPx()), Offset(w, base - 2.dp.toPx()), 3.dp.toPx())
                if (started != null) drawLine(colors.accent, Offset(0f, base - 2.dp.toPx()), Offset(w * s.progress, base - 2.dp.toPx()), 3.dp.toPx())
                return@Canvas
            }
            val path = Path()
            var first = true
            var x = 0f
            while (x <= dotX) {
                val tt = elapsed - (dotX - x) / dotX * span
                if (started != null && tt >= 0f) {
                    val lv = BreathingMath.level(BreathingMath.stateAt(state.rhythm, tt))
                    val y = base - amp * lv
                    if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                }
                x += 3.dp.toPx()
            }
            if (!first) drawPath(path, colors.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(colors.accent, 9.dp.toPx(), Offset(dotX, base - amp * level))
        }

        Spacer(Modifier.height(XoSpacing.s3))
        if (started != null) {
            val left = (state.minutes * 60 - elapsed).toLong().coerceAtLeast(0)
            Text(stringResource(R.string.breath_left, "%d:%02d".format(left / 60, left % 60)), style = type.caption, color = colors.muted)
        } else {
            val total = state.todaySeconds
            Text(
                if (total == 0) stringResource(R.string.breath_today_none)
                else stringResource(R.string.breath_today, formatCount((total / 60).toLong()), formatCount(state.todaySessions.toLong())),
                style = type.caption, color = colors.muted,
            )
        }

        state.summary?.let { sum ->
            Spacer(Modifier.height(XoSpacing.s4))
            XoHairline()
            Column(Modifier.padding(vertical = XoSpacing.s3)) {
                Text(
                    stringResource(R.string.breath_summary, formatCount((sum.seconds / 60).toLong()), pluralText(R.plurals.breath_cycles, sum.cycles, sum.cycles)),
                    style = type.bodyStrong, color = colors.ink,
                )
                Spacer(Modifier.height(XoSpacing.textGap))
                Text(stringResource(R.string.breath_summary_sub), style = type.caption, color = colors.muted)
            }
            XoHairline()
        }

        if (started == null) {
            Spacer(Modifier.height(XoSpacing.s4))
            XoSegmented(
                BreathRhythm.entries.map { it to rhythmLabel(it) },
                state.rhythm, onRhythm,
            )
            Spacer(Modifier.height(XoSpacing.s2))
            XoSegmented(
                BreathingMath.DURATIONS_MIN.map { it to stringResource(R.string.breath_minutes, it.toString()) },
                state.minutes, onMinutes,
            )
        }

        Spacer(Modifier.height(XoSpacing.s5))
        if (started == null) XoPrimaryButton(stringResource(R.string.breath_start), onStart)
        else XoPrimaryButton(stringResource(R.string.breath_stop), onStop)

        Spacer(Modifier.height(XoSpacing.s3))
        Text(stringResource(R.string.breath_note), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s7))
    }
}

@Composable
private fun phaseLabel(k: BreathKind): String = stringResource(
    when (k) {
        BreathKind.In -> R.string.breath_in
        BreathKind.Hold, BreathKind.HoldEmpty -> R.string.breath_hold
        BreathKind.Out -> R.string.breath_out
    },
)

@Composable
private fun rhythmLabel(r: BreathRhythm): String {
    val name = stringResource(
        when (r) {
            BreathRhythm.Even -> R.string.breath_rhythm_even
            BreathRhythm.Box -> R.string.breath_rhythm_box
            BreathRhythm.LongOut -> R.string.breath_rhythm_long
        },
    )
    return "$name ${r.phases.joinToString("–") { it.seconds.toString() }}"
}

private val WAVE_HEIGHT = 200.dp
private const val DOT_X = 0.75f
private const val TRAIL_CYCLES = 1.6f
