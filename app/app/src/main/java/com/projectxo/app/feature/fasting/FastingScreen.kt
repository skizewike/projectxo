package com.projectxo.app.feature.fasting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.data.FastingSession
import com.projectxo.app.core.model.FastingMath
import com.projectxo.app.core.ui.components.TimeAdjustDialog
import com.projectxo.app.core.ui.components.XoHairline
import com.projectxo.app.core.ui.components.XoPrimaryButton
import com.projectxo.app.core.ui.components.XoTextButton
import com.projectxo.app.core.ui.components.clockText
import com.projectxo.app.core.ui.components.dayWord
import com.projectxo.app.core.ui.format.clockHms
import com.projectxo.app.core.ui.format.formatMinutes
import com.projectxo.app.core.ui.format.formatToday
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FastingActions(
    val onStart: (Long?) -> Unit,
    val onFinish: (Long?) -> Unit,
    val onEditStart: (Long) -> Unit,
    val onPlan: (Int) -> Unit,
    val onAcceptSafety: () -> Unit,
)

/**
 * «Голодание», вариант «Сутки» (Р34, design/fasting-timer.html): циферблат на 24 часа, дуга от начала до «сейчас»,
 * внешняя дуга — окно голодания до цели. Время считается от часов телефона.
 */
@Composable
fun FastingScreen(state: FastingUiState, actions: FastingActions, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val haptic = LocalHapticFeedback.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.fasting) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    var safetyOpen by rememberSaveable { mutableStateOf(false) }
    var startOpen by rememberSaveable { mutableStateOf(false) }
    var endOpen by rememberSaveable { mutableStateOf(false) }

    val cur = state.current
    val goalMs = state.planHours * 3_600_000L
    val elapsed = if (cur != null) (nowMs - cur.startMs).coerceAtLeast(0) else 0L
    val reached = cur != null && elapsed >= goalMs

    // Вибрация в момент, когда цель пройдена, пока экран открыт.
    var wasReached by remember(cur?.id) { mutableStateOf(reached) }
    LaunchedEffect(reached) {
        if (reached && !wasReached) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        wasReached = reached
    }

    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XoSpacing.screenGutter),
    ) {
        Spacer(Modifier.height(XoSpacing.s5))
        Text(stringResource(R.string.module_fasting), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
        Text(formatToday(java.time.LocalDate.now()), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s4))

        Dial(cur, state.planHours, nowMs, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(XoSpacing.s3))

        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    when {
                        cur == null -> R.string.fast_state_window
                        reached -> R.string.fast_state_past
                        else -> R.string.fast_state_going
                    },
                ),
                style = type.caption, color = colors.muted,
            )
            if (cur != null) {
                Text(clockHms(elapsed / 1000), style = type.display, color = colors.ink)
                Text(
                    if (reached) stringResource(R.string.fast_cap_done, state.planHours.toString(), formatMinutes((elapsed - goalMs) / 60_000L))
                    else stringResource(R.string.fast_cap_left, formatMinutes((goalMs - elapsed + 59_999L) / 60_000L), state.planHours.toString()),
                    style = type.caption, color = colors.ink,
                )
            } else {
                val l = state.last
                Text(
                    if (l?.endMs != null) clockHms((l.endMs - l.startMs) / 1000).substring(0, 5) else stringResource(R.string.pick_weight_none),
                    style = type.display, color = colors.ink,
                )
                Text(
                    if (l?.endMs != null) stringResource(R.string.fast_cap_last, dayWord(l.endMs), clockText(l.endMs))
                    else stringResource(R.string.fast_cap_none),
                    style = type.caption, color = colors.muted,
                )
            }
        }

        Spacer(Modifier.height(XoSpacing.s4))
        XoHairline()
        if (cur != null) {
            Row2(stringResource(R.string.fast_row_start), "${dayWord(cur.startMs)}, ${clockText(cur.startMs)}", onClick = { startOpen = true })
            Row2(
                stringResource(R.string.fast_row_window),
                "${clockText(FastingMath.eatingWindowStart(cur.startMs, state.planHours))}–${clockText(cur.startMs + 24 * 3_600_000L)}",
            )
        } else {
            Row2(stringResource(R.string.fast_row_can), stringResource(R.string.fast_row_can_value))
            Row2(stringResource(R.string.fast_row_tip), stringResource(R.string.fast_row_tip_value))
        }
        XoHairline()

        Spacer(Modifier.height(XoSpacing.s4))
        PlanChips(state.planHours, actions.onPlan)

        Spacer(Modifier.height(XoSpacing.s4))
        if (cur == null) {
            XoPrimaryButton(stringResource(R.string.fast_start), { if (state.safetyAccepted) actions.onStart(null) else safetyOpen = true })
            XoTextButton(stringResource(R.string.fast_start_earlier), { if (state.safetyAccepted) startOpen = true else safetyOpen = true })
        } else {
            XoPrimaryButton(stringResource(R.string.fast_finish), { actions.onFinish(null) })
            XoTextButton(stringResource(R.string.fast_finish_earlier), { endOpen = true })
        }

        if (state.recent.isNotEmpty()) {
            Spacer(Modifier.height(XoSpacing.s5))
            Text(stringResource(R.string.fast_history), style = type.caption, color = colors.muted, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(XoSpacing.s1))
            state.recent.forEach { HistoryRow(it) }
        }

        Spacer(Modifier.height(XoSpacing.s3))
        Text(stringResource(R.string.fast_note), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s7))
    }

    if (safetyOpen) SafetyDialog(
        onAccept = { safetyOpen = false; actions.onAcceptSafety(); actions.onStart(null) },
        onDismiss = { safetyOpen = false },
    )

    val now = System.currentTimeMillis()
    if (startOpen) {
        val bounds = FastingMath.startBounds(now)
        val hourAgo = now - 3_600_000L
        val supper = java.time.LocalDateTime.now().withHour(20).withMinute(0).withSecond(0).withNano(0).let {
            val z = it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (z > now) z - 24 * 3_600_000L else z
        }
        TimeAdjustDialog(
            title = stringResource(R.string.fast_start_when),
            hint = stringResource(R.string.fast_start_hint),
            initialMs = cur?.startMs ?: now,
            range = bounds,
            quick = listOf(stringResource(R.string.time_hour_ago) to hourAgo, stringResource(R.string.fast_after_supper) to supper, stringResource(R.string.time_now) to now),
            onConfirm = { ms -> startOpen = false; if (cur == null) actions.onStart(ms) else actions.onEditStart(ms) },
            onDismiss = { startOpen = false },
        )
    }
    if (endOpen && cur != null) {
        val b = FastingMath.endBounds(cur.startMs, now)
        TimeAdjustDialog(
            title = stringResource(R.string.fast_end_when),
            hint = stringResource(R.string.fast_end_hint, clockText(cur.startMs)),
            initialMs = now,
            range = b,
            quick = listOf(stringResource(R.string.time_30_ago) to now - 30 * 60_000L, stringResource(R.string.time_hour_ago) to now - 3_600_000L, stringResource(R.string.time_now) to now),
            onConfirm = { ms -> endOpen = false; actions.onFinish(ms) },
            onDismiss = { endOpen = false },
        )
    }
}

@Composable
private fun Row2(label: String, value: String, onClick: (() -> Unit)? = null) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val hairline = with(LocalDensity.current) { XoSize.hairline.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = XoSize.rowHeight - 8.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .drawBehind { drawRect(colors.line, size = Size(size.width, hairline)) }
            .padding(vertical = XoSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
        Text(value, style = type.bodyStrong, color = colors.ink)
        if (onClick != null) {
            Spacer(Modifier.width(XoSpacing.s2))
            Icon(XoIcons.Pen, null, tint = colors.muted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PlanChips(plan: Int, onPlan: (Int) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XoSpacing.s2)) {
        FastingMath.PLANS.forEach { h ->
            val on = h == plan
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = XoSize.touchMin)
                    .border(if (on) 2.dp else XoSize.hairline, if (on) colors.ink else colors.line, CircleShape)
                    .clickable(role = Role.RadioButton) { onPlan(h) }
                    .semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) { Text("$h:${24 - h}", style = if (on) type.bodyStrong else type.body, color = colors.ink) }
        }
    }
}

@Composable
private fun HistoryRow(s: FastingSession) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val end = s.endMs ?: return
    val hairline = with(LocalDensity.current) { XoSize.hairline.toPx() }
    val done = end - s.startMs >= s.planHours * 3_600_000L
    Row(
        Modifier.fillMaxWidth().drawBehind { drawRect(colors.line, size = Size(size.width, hairline)) }.padding(vertical = XoSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dayWord(end), style = type.caption, color = colors.muted, modifier = Modifier.width(88.dp))
        Text(formatMinutes((end - s.startMs) / 60_000L), style = type.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
        Text(
            if (done) stringResource(R.string.fast_hist_done, s.planHours.toString()) else stringResource(R.string.fast_hist_goal, s.planHours.toString()),
            style = type.caption, color = colors.muted,
        )
    }
}

/** Вопросы безопасности (Р28): без медицинских обещаний. */
@Composable
private fun SafetyDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.fast_safety_title), style = type.title, color = colors.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.fast_safety_lead), style = type.body, color = colors.muted)
                Spacer(Modifier.height(XoSpacing.s3))
                listOf(R.string.fast_safety_1, R.string.fast_safety_2, R.string.fast_safety_3, R.string.fast_safety_4).forEach {
                    Text("• " + stringResource(it), style = type.body, color = colors.ink)
                }
                Spacer(Modifier.height(XoSpacing.s3))
                Text(stringResource(R.string.fast_safety_tail), style = type.caption, color = colors.muted)
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.fast_safety_ok), style = type.bodyStrong) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.fast_safety_no), style = type.body) } },
    )
}

/** Циферблат на 24 часа (design/fasting-timer.html). Ромб — «сейчас», акцентная дуга — прошедшее время. */
@Composable
private fun Dial(cur: FastingSession?, plan: Int, nowMs: Long, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val measurer = rememberTextMeasurer()
    val a11y = stringResource(R.string.fast_dial_a11y)
    val zone = remember { ZoneId.systemDefault() }
    Canvas(modifier.size(DIAL_BOX).clearAndSetSemantics { contentDescription = a11y }) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val sw = 12.dp.toPx()
        val r = size.minDimension / 2f - 34.dp.toPx()
        fun ang(min: Float): Float = -90f + min / 1440f * 360f
        fun minOf(ms: Long): Float = Instant.ofEpochMilli(ms).atZone(zone).let { it.hour * 60f + it.minute + it.second / 60f }
        val box = Size(r * 2, r * 2)
        val tl = Offset(cx - r, cy - r)
        drawArc(colors.track, 0f, 360f, false, tl, box, style = Stroke(sw))
        val outer = r + sw / 2f + 6.dp.toPx()
        val obox = Size(outer * 2, outer * 2)
        val otl = Offset(cx - outer, cy - outer)
        val nowMin = minOf(nowMs)
        if (cur != null) {
            val sm = minOf(cur.startMs)
            val elapsedMin = ((nowMs - cur.startMs) / 60_000f).coerceIn(0f, 1439f)
            val reached = elapsedMin >= plan * 60
            val hair = 1.2.dp.toPx()
            drawArc(
                colors.ink, ang(sm), plan * 60f / 1440f * 360f, false, otl, obox,
                style = Stroke(hair, pathEffect = if (reached) null else PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))),
            )
            drawArc(colors.muted, ang(sm + plan * 60f), (1440f - plan * 60f) / 1440f * 360f, false, otl, obox, style = Stroke(hair))
            if (elapsedMin > 0f) drawArc(colors.accent, ang(sm), elapsedMin / 1440f * 360f, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
        }
        for (h in 0 until 24) {
            val a = (ang(h * 60f)) * PI.toFloat() / 180f
            val r1 = r - sw / 2f - 6.dp.toPx()
            val len = (if (h % 6 == 0) 8 else 4).dp.toPx()
            drawLine(
                if (h % 6 == 0) colors.ink else colors.line,
                Offset(cx + cos(a) * r1, cy + sin(a) * r1), Offset(cx + cos(a) * (r1 - len), cy + sin(a) * (r1 - len)), 1.dp.toPx(),
            )
        }
        listOf(0, 6, 12, 18).forEach { h ->
            val a = ang(h * 60f) * PI.toFloat() / 180f
            val rr = outer + 16.dp.toPx()
            val l = measurer.measure("%02d".format(h), type.scale)
            drawText(l, color = colors.muted, topLeft = Offset(cx + cos(a) * rr - l.size.width / 2f, cy + sin(a) * rr - l.size.height / 2f))
        }
        // Ромб «сейчас».
        val na = ang(nowMin) * PI.toFloat() / 180f
        val px = cx + cos(na) * r
        val py = cy + sin(na) * r
        val d = 8.dp.toPx()
        val diamond = Path().apply { moveTo(px, py - d); lineTo(px + d, py); lineTo(px, py + d); lineTo(px - d, py); close() }
        drawPath(diamond, colors.ink)
        drawPath(diamond, colors.background, style = Stroke(2.dp.toPx()))
    }
}

private val DIAL_BOX = 280.dp
