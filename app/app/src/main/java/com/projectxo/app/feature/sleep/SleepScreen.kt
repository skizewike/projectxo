package com.projectxo.app.feature.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.data.SleepNight
import com.projectxo.app.core.ui.components.TimeAdjustDialog
import com.projectxo.app.core.ui.components.XoHairline
import com.projectxo.app.core.ui.components.XoPrimaryButton
import com.projectxo.app.core.ui.components.XoSwitch
import com.projectxo.app.core.ui.components.XoTextButton
import com.projectxo.app.core.ui.components.clockText
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.formatMinutes
import com.projectxo.app.core.ui.format.formatToday
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.format.timeOfDay
import com.projectxo.app.core.ui.icons.WellbeingIcons
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.abs

/** Действия экрана «Сон». */
class SleepActions(
    val onBed: () -> Unit,
    val onWake: () -> Unit,
    val onEditStart: (Long) -> Unit,
    val onWakeEarlier: (Long) -> Unit,
    val onBedEarlier: (Long) -> Unit,
    val onSchedule: (Int, Int) -> Unit,
    val onRemind: (Boolean) -> Unit,
    val onRate: (Int) -> Unit,
    val onSkipRating: () -> Unit,
    val onRateLast: () -> Unit,
    val onAlarm: (Int, Int) -> Unit,
)

/**
 * «Сон», вариант «Полумесяц» (Р42, design/sleep.html): полумесяц заполняется по часам сна до цели.
 * Время идёт по часам телефона: человек только ложится и встаёт.
 */
@Composable
fun SleepScreen(state: SleepUiState, actions: SleepActions, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.sleeping) {
        while (state.sleeping) {
            nowMs = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }
    var scheduleOpen by rememberSaveable { mutableStateOf(false) }
    var wakeEarlierOpen by rememberSaveable { mutableStateOf(false) }
    var startEditOpen by rememberSaveable { mutableStateOf(false) }

    val cur = state.current
    val last = state.last
    val shownMs: Long = when {
        cur != null -> (nowMs - cur.startMs).coerceAtLeast(0)
        last != null -> (last.endMs ?: last.startMs) - last.startMs
        else -> 0L
    }
    val goalMs = state.goalMin * 60_000L

    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XoSpacing.screenGutter),
    ) {
        Spacer(Modifier.height(XoSpacing.s5))
        Text(stringResource(R.string.module_sleep), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(XoSpacing.headGap))
        Text(formatToday(java.time.LocalDate.now()), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s4))

        Moon(fraction = if (goalMs > 0) (shownMs.toFloat() / goalMs).coerceIn(0f, 1f) else 0f, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(XoSpacing.s3))

        // Итог: «Спокойной ночи» / «Прошлая ночь».
        Text(
            stringResource(if (cur != null) R.string.sleep_state_night else R.string.sleep_state_last),
            style = type.caption, color = colors.muted,
        )
        Spacer(Modifier.height(XoSpacing.textGap))
        Text(
            if (cur == null && last == null) stringResource(R.string.pick_weight_none) else formatMinutes(shownMs / 60_000L),
            style = type.display, color = colors.ink,
        )
        Spacer(Modifier.height(XoSpacing.s2))
        when {
            cur != null -> Text(
                stringResource(R.string.sleep_cap_going, clockText(cur.startMs), timeOfDay(state.wakeMin)),
                style = type.caption, color = colors.muted,
            )
            last != null -> {
                val rated = last.rate?.let { stringResource(R.string.sleep_cap_rated, rateWord(it).lowercase()) } ?: ""
                Text(
                    stringResource(R.string.sleep_cap_range, clockText(last.startMs), clockText(last.endMs ?: last.startMs)) + rated,
                    style = type.caption, color = colors.muted,
                )
                val diffMin = (shownMs - goalMs) / 60_000L
                Text(
                    when {
                        abs(diffMin) < 10 -> stringResource(R.string.sleep_cap_exact)
                        diffMin > 0 -> stringResource(R.string.sleep_cap_over, formatMinutes(diffMin))
                        else -> stringResource(R.string.sleep_cap_under, formatMinutes(-diffMin))
                    },
                    style = type.caption, color = colors.ink,
                )
            }
            else -> Text(stringResource(R.string.sleep_cap_none), style = type.caption, color = colors.muted)
        }

        Spacer(Modifier.height(XoSpacing.s4))
        XoHairline()
        InfoRow(
            stringResource(R.string.sleep_row_schedule),
            "${timeOfDay(state.bedMin)} — ${timeOfDay(state.wakeMin)} · ${formatMinutes(state.goalMin.toLong())}",
            onClick = { scheduleOpen = true },
        )
        if (cur != null) {
            InfoRow(stringResource(R.string.sleep_row_started), clockText(cur.startMs), onClick = { startEditOpen = true })
        }
        state.regularity?.let { (avg, sd) ->
            InfoRow(stringResource(R.string.sleep_row_regularity), stringResource(R.string.sleep_regularity_value, timeOfDay(avg), sd.toString()))
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = XoSize.rowHeight - 8.dp).clickable(role = Role.Switch) { actions.onRemind(!state.remind) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.sleep_row_remind), style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
            XoSwitch(state.remind)
        }
        XoHairline()

        if (state.nights.size >= 2) {
            Spacer(Modifier.height(XoSpacing.s4))
            Text(stringResource(R.string.sleep_week_title), style = type.caption, color = colors.muted, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(XoSpacing.s2))
            NightsChart(state.nights.take(7).reversed(), state.bedMin, state.wakeMin)
        }

        if (state.nights.isNotEmpty() && cur == null) {
            XoTextButton(stringResource(R.string.sleep_rate_again), actions.onRateLast)
        }

        Spacer(Modifier.height(XoSpacing.s4))
        if (cur == null) {
            XoPrimaryButton(stringResource(R.string.sleep_bed), actions.onBed)
            XoTextButton(stringResource(R.string.sleep_alarm, timeOfDay(state.wakeMin)), { actions.onAlarm(state.wakeMin / 60, state.wakeMin % 60) })
        } else {
            XoPrimaryButton(stringResource(R.string.sleep_wake), actions.onWake)
            XoTextButton(stringResource(R.string.sleep_wake_earlier), { wakeEarlierOpen = true })
        }
        Spacer(Modifier.height(XoSpacing.s3))
        Text(stringResource(R.string.sleep_note), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s7))
    }

    val now = System.currentTimeMillis()
    if (wakeEarlierOpen && cur != null) {
        TimeAdjustDialog(
            title = stringResource(R.string.sleep_wake_when),
            hint = stringResource(R.string.sleep_wake_hint, clockText(cur.startMs)),
            initialMs = now,
            range = (cur.startMs + 60_000L)..maxOf(cur.startMs + 60_000L, now),
            quick = listOf(stringResource(R.string.time_30_ago) to now - 30 * 60_000L, stringResource(R.string.time_hour_ago) to now - 3_600_000L, stringResource(R.string.time_now) to now),
            onConfirm = { wakeEarlierOpen = false; actions.onWakeEarlier(it) },
            onDismiss = { wakeEarlierOpen = false },
        )
    }
    if (startEditOpen && cur != null) {
        TimeAdjustDialog(
            title = stringResource(R.string.sleep_started_when),
            hint = stringResource(R.string.sleep_started_hint),
            initialMs = cur.startMs,
            range = (now - 24 * 3_600_000L)..now,
            quick = listOf(stringResource(R.string.time_hour_ago) to now - 3_600_000L, stringResource(R.string.time_now) to now),
            onConfirm = { startEditOpen = false; actions.onEditStart(it) },
            onDismiss = { startEditOpen = false },
        )
    }
    if (scheduleOpen) {
        ScheduleDialog(state.bedMin, state.wakeMin, { b, w -> actions.onSchedule(b, w) }) { scheduleOpen = false }
    }
    if (state.ratingFor != null) RatingDialog(actions.onRate, actions.onSkipRating)
}

@Composable
private fun rateWord(i: Int): String = stringResource(
    when (i) { 0 -> R.string.sleep_rate_0; 1 -> R.string.sleep_rate_1; 2 -> R.string.sleep_rate_2; 3 -> R.string.sleep_rate_3; else -> R.string.sleep_rate_4 },
)

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
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

/** Полумесяц: контур и заполнение снизу вверх до [fraction] цели. Контур — как у значка сна (сетка 24). */
@Composable
private fun Moon(fraction: Float, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val path = remember {
        PathParser().parsePathString("M18.5 15.5A8 8 0 1 1 9 4.6a6.5 6.5 0 0 0 9.5 10.9z").toPath().apply { fillType = PathFillType.NonZero }
    }
    val a11y = stringResource(R.string.sleep_moon_a11y, Math.round(fraction * 100).toString())
    Canvas(modifier.size(MOON_BOX).clearAndSetSemantics { contentDescription = a11y }) {
        val k = size.minDimension / 16f * 0.9f
        val offX = (size.width - 16f * k) / 2f - 4f * k // контур занимает x≈4..19, y≈4..20 в сетке 24
        val offY = (size.height - 16f * k) / 2f - 4f * k
        translate(offX, offY) {
            scale(k, pivot = Offset.Zero) {
                drawPath(path, colors.track)
                if (fraction > 0.001f) clipRect(0f, 20f - 16f * fraction, 24f, 24f) { drawPath(path, colors.accent) }
                drawPath(path, colors.ink, style = Stroke(1.2f / k * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Ночи недели: полосы на шкале 21:00–10:00, серым окно плана. */
@Composable
private fun NightsChart(nights: List<SleepNight>, bedMin: Int, wakeMin: Int) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val measurer = rememberTextMeasurer()
    val locale = rememberFormatLocale()
    val rowH = 26.dp
    val a11y = stringResource(R.string.sleep_week_a11y, nights.size.toString())
    Canvas(Modifier.fillMaxWidth().height(rowH * nights.size + 22.dp).clearAndSetSemantics { contentDescription = a11y }) {
        val labelW = 34.dp.toPx()
        val x0 = labelW
        val w = size.width - labelW
        val from = 21 * 60
        val to = 34 * 60
        fun x(m: Int): Float = x0 + w * (m - from).toFloat() / (to - from)
        fun axis(minOfDay: Int, next: Boolean): Int = if (minOfDay < 12 * 60 || next) minOfDay + 1440 else minOfDay
        val planS = axis(bedMin, false)
        val planE = axis(wakeMin, true)
        drawRect(colors.track, Offset(x(planS), 0f), Size(x(planE) - x(planS), rowH.toPx() * nights.size), alpha = 0.6f)
        val zone = ZoneId.systemDefault()
        nights.forEachIndexed { i, n ->
            val s = Instant.ofEpochMilli(n.startMs).atZone(zone)
            val e = Instant.ofEpochMilli(n.endMs ?: n.startMs).atZone(zone)
            val sm = axis(s.hour * 60 + s.minute, false)
            val em = axis(e.hour * 60 + e.minute, true)
            val y = i * rowH.toPx() + 8.dp.toPx()
            drawRoundRect(
                if (i == nights.lastIndex) colors.accent else colors.ink,
                Offset(x(sm.coerceIn(from, to)), y), Size((x(em.coerceIn(from, to)) - x(sm.coerceIn(from, to))).coerceAtLeast(4.dp.toPx()), 10.dp.toPx()),
                androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
            )
            val name = (e.dayOfWeek ?: DayOfWeek.MONDAY).getDisplayName(TextStyle.SHORT, locale)
            val l = measurer.measure(name, type.scale)
            drawText(l, color = colors.muted, topLeft = Offset(0f, y - 1.dp.toPx()))
        }
        listOf(21, 24, 27, 30, 33).forEach { h ->
            val l = measurer.measure(timeOfDay(h * 60), type.scale)
            drawText(l, color = colors.muted, topLeft = Offset(x(h * 60) - l.size.width / 2f, rowH.toPx() * nights.size + 2.dp.toPx()))
        }
    }
}

@Composable
private fun ScheduleDialog(bedMin: Int, wakeMin: Int, onChange: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.sleep_schedule_title), style = type.title, color = colors.ink) },
        text = {
            Column {
                Stepper(stringResource(R.string.sleep_bed_time), timeOfDay(bedMin), { onChange(bedMin - STEP, wakeMin) }, { onChange(bedMin + STEP, wakeMin) })
                Stepper(stringResource(R.string.sleep_wake_time), timeOfDay(wakeMin), { onChange(bedMin, wakeMin - STEP) }, { onChange(bedMin, wakeMin + STEP) })
                Spacer(Modifier.height(XoSpacing.s3))
                Text(stringResource(R.string.sleep_goal_line, formatMinutes(com.projectxo.app.core.model.SleepMath.goalMinutes(bedMin, wakeMin).toLong())), style = type.bodyStrong, color = colors.ink)
                Spacer(Modifier.height(XoSpacing.textGap))
                Text(stringResource(R.string.sleep_goal_hint), style = type.caption, color = colors.muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.got_it), style = type.bodyStrong) } },
    )
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val less = stringResource(R.string.less)
    val more = stringResource(R.string.more)
    Row(Modifier.fillMaxWidth().padding(vertical = XoSpacing.s1), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
        RoundButton("−", less, onMinus)
        Text(value, style = type.bodyStrong, color = colors.ink, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        RoundButton("+", more, onPlus)
    }
}

@Composable
private fun RoundButton(symbol: String, label: String, onClick: () -> Unit) {
    val colors = XoTheme.colors
    Box(
        Modifier.size(XoSize.touchMin).border(XoSize.hairline, colors.line, CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = XoTheme.type.subtitle, color = colors.ink) }
}

/** «Как спалось?» пятью лицами (Р42). */
@Composable
private fun RatingDialog(onRate: (Int) -> Unit, onSkip: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    AlertDialog(
        onDismissRequest = onSkip,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.sleep_rate_title), style = type.title, color = colors.ink) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0..4) {
                    val word = rateWord(i)
                    Column(
                        Modifier.weight(1f).clickable(role = Role.Button) { onRate(i) }.padding(vertical = XoSpacing.s1)
                            .semantics { contentDescription = word },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(44.dp).background(colors.track, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(WellbeingIcons.face(i), null, tint = colors.ink, modifier = Modifier.size(28.dp))
                        }
                        Text(word, style = type.scale, color = colors.muted, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onSkip) { Text(stringResource(R.string.sleep_rate_skip), style = type.body) } },
    )
}

private const val STEP = 15
private const val TICK_MS = 15_000L
private val MOON_BOX = 180.dp
