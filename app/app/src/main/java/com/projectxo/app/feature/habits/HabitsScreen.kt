package com.projectxo.app.feature.habits

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.projectxo.app.R
import com.projectxo.app.core.model.Habit
import com.projectxo.app.core.model.HabitFreq
import com.projectxo.app.core.model.HabitKind
import com.projectxo.app.core.model.HabitMath
import com.projectxo.app.core.model.HabitsView
import com.projectxo.app.core.ui.components.UndoBar
import com.projectxo.app.core.ui.components.XoHairline
import com.projectxo.app.core.ui.components.XoSegmented
import com.projectxo.app.core.ui.components.XoSwitchRow
import com.projectxo.app.core.model.StreakFlame
import com.projectxo.app.core.ui.components.StreakFlame as StreakFlameIcon
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.formatToday
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.format.timeOfDay
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import java.time.DayOfWeek
import java.time.format.TextStyle

class HabitsActions(
    val onToggle: (HabitItem, Int) -> Unit,
    val onCount: (HabitItem, Int) -> Unit,
    val onSave: (Habit) -> Unit,
    val onView: (HabitsView) -> Unit,
    val onEditMode: (Boolean) -> Unit,
    val onDelete: (HabitItem) -> Unit,
    val onUndo: () -> Unit,
)

/**
 * «Привычки» (Р43, design/habits.html): три вида на выбор в настройках. Отметка — крестик X из логотипа,
 * прописывается двумя штрихами. Серия не обнуляется за один пропуск.
 */
@Composable
fun HabitsScreen(
    state: HabitsUiState,
    actions: HabitsActions,
    modifier: Modifier = Modifier,
    together: com.projectxo.app.feature.shared.TogetherState? = null,
    togetherActions: com.projectxo.app.feature.shared.TogetherActions? = null,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    var editing by rememberSaveable { mutableIntStateOf(NO_EDIT) } // -1 нет, 0 новая, иначе id
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = XoSpacing.screenGutter),
        ) {
            Spacer(Modifier.height(XoSpacing.s5))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.module_habits), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(XoSpacing.headGap))
                    Text(formatToday(state.today), style = type.caption, color = colors.muted)
                }
                IconButton(XoIcons.Settings, stringResource(R.string.habits_settings)) { settingsOpen = true }
                IconButton(XoIcons.Plus, stringResource(R.string.habits_add), ring = true) { editing = NEW }
            }
            Spacer(Modifier.height(XoSpacing.s4))

            if (state.editMode) {
                Row(Modifier.fillMaxWidth().padding(bottom = XoSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.habits_editing), style = type.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = { actions.onEditMode(false) }) { Text(stringResource(R.string.habits_done), style = type.bodyStrong) }
                }
            }
            if (state.items.isEmpty() && state.loaded) {
                Text(stringResource(R.string.habits_empty), style = type.body, color = colors.muted)
            } else {
                if (state.view != HabitsView.Ring) {
                    Summary(state)
                    Spacer(Modifier.height(XoSpacing.s3))
                }
                when (state.view) {
                    HabitsView.Ring -> RingView(state, actions) { editing = it.habit.id.toInt() }
                    HabitsView.List -> ListView(state, actions) { editing = it.habit.id.toInt() }
                    HabitsView.Cards -> CardsView(state, actions) { editing = it.habit.id.toInt() }
                }
            }
            if (together != null && togetherActions != null && together.available && !state.editMode) {
                Spacer(Modifier.height(XoSpacing.s6))
                com.projectxo.app.feature.shared.TogetherSection(together, togetherActions)
            }
            Spacer(Modifier.height(XoSpacing.s8))
        }

        UndoBar(
            visible = state.pendingDelete != null,
            message = stringResource(R.string.habits_deleted),
            action = stringResource(R.string.undo_restore),
            onAction = actions.onUndo,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (editing != NO_EDIT) {
        val item = state.items.firstOrNull { it.habit.id.toInt() == editing }
        HabitEditDialog(item?.habit, onSave = { actions.onSave(it); editing = NO_EDIT }, onDismiss = { editing = NO_EDIT })
    }
    if (settingsOpen) {
        SettingsDialog(
            state, actions,
            onEditList = { settingsOpen = false; actions.onEditMode(true) },
            onDismiss = { settingsOpen = false },
        )
    }
}

private const val NO_EDIT = -1
private const val NEW = 0

@Composable
private fun IconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, ring: Boolean = false, onClick: () -> Unit) {
    val colors = XoTheme.colors
    Box(
        Modifier.size(XoSize.touchMin).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (ring) {
            Box(Modifier.size(XoSize.addButton).border(XoSize.hairline, colors.line, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = colors.ink, modifier = Modifier.size(XoSize.navIcon))
            }
        } else {
            Icon(icon, null, tint = colors.ink, modifier = Modifier.size(XoSize.navIcon))
        }
    }
}

@Composable
private fun Summary(state: HabitsUiState) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val total = state.dueToday.size
    Text(
        stringResource(R.string.habits_summary, formatCount(state.doneCount.toLong()), formatCount(total.toLong())) +
            if (total > 0 && state.doneCount == total) stringResource(R.string.habits_all_done) else "",
        style = type.body, color = colors.ink,
    )
}

/** Крестик X: два штриха прописываются по очереди. */
@Composable
private fun XMark(on: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val reduced = LocalReducedMotion.current
    val p by animateFloatAsState(if (on) 1f else 0f, tween(if (reduced) 0 else 320), label = "xmark")
    Canvas(modifier.size(size)) {
        val w = 1.8.dp.toPx()
        val pad = size.toPx() * 0.28f
        val a = Offset(pad, pad)
        val b = Offset(this.size.width - pad, this.size.height - pad)
        val c = Offset(this.size.width - pad, pad)
        val d = Offset(pad, this.size.height - pad)
        fun lerp(from: Offset, to: Offset, t: Float) = Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
        val t1 = (p * 2f).coerceIn(0f, 1f)
        val t2 = (p * 2f - 1f).coerceIn(0f, 1f)
        if (t1 > 0f) drawLine(colors.ink, a, lerp(a, b, t1), w, StrokeCap.Round)
        if (t2 > 0f) drawLine(colors.ink, c, lerp(c, d, t2), w, StrokeCap.Round)
    }
}

@Composable
private fun Check(item: HabitItem, onToggle: () -> Unit, big: Boolean = false) {
    val colors = XoTheme.colors
    val haptic = LocalHapticFeedback.current
    val on = item.doneToday
    val d = if (big) 44.dp else 36.dp
    val label = stringResource(R.string.habits_mark_a11y, habitName(item.habit))
    Box(
        Modifier
            .size(maxOf(d, XoSize.touchMin))
            .clickable(role = Role.Checkbox) {
                if (!on) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onToggle()
            }
            .semantics { contentDescription = label; selected = on },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(d).border(if (on) 2.dp else XoSize.hairline, if (on) colors.ink else colors.line, CircleShape), contentAlignment = Alignment.Center) {
            XMark(on, d)
        }
    }
}

@Composable
private fun subtitle(item: HabitItem): String {
    val f = item.habit.freq
    return when {
        f is HabitFreq.PerWeek -> stringResource(R.string.habits_week_progress, item.weekCount.toString(), f.n.toString())
        item.streak > 0 -> stringResource(R.string.habits_streak, item.streak.toString()) +
            if (item.best >= 7) stringResource(R.string.habits_percent, item.percent30.toString()) else ""
        else -> stringResource(R.string.habits_start_today)
    }
}

@Composable
private fun RowActions(item: HabitItem, state: HabitsUiState, actions: HabitsActions, onEdit: (HabitItem) -> Unit) {
    val colors = XoTheme.colors
    if (!state.editMode) return
    val delete = stringResource(R.string.habits_delete_a11y, habitName(item.habit))
    val edit = stringResource(R.string.habits_edit_a11y, habitName(item.habit))
    Box(Modifier.size(XoSize.touchMin).clickable(role = Role.Button) { onEdit(item) }.semantics { contentDescription = edit }, contentAlignment = Alignment.Center) {
        Icon(XoIcons.Pen, null, tint = colors.ink, modifier = Modifier.size(18.dp))
    }
    Box(Modifier.size(XoSize.touchMin).clickable(role = Role.Button) { actions.onDelete(item) }.semantics { contentDescription = delete }, contentAlignment = Alignment.Center) {
        Icon(XoIcons.Trash, null, tint = colors.ink, modifier = Modifier.size(20.dp))
    }
}

// ——— 03 «Кольцо дня» (по умолчанию) ———

@Composable
private fun RingView(state: HabitsUiState, actions: HabitsActions, onEdit: (HabitItem) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val due = state.dueToday
    val rest = state.items.filter { it !in due }
    val a11y = stringResource(R.string.habits_ring_a11y, state.doneCount.toString(), due.size.toString())
    Box(Modifier.fillMaxWidth().height(180.dp).clearAndSetSemantics { contentDescription = a11y }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(160.dp)) {
            val sw = 10.dp.toPx()
            val r = size.minDimension / 2f - sw
            val n = due.size.coerceAtLeast(1)
            val gap = 6f
            if (due.isEmpty()) {
                drawCircle(colors.track, r, style = Stroke(sw))
            } else {
                due.forEachIndexed { i, it ->
                    val sweep = 360f / n - gap
                    val start = -90f + i * 360f / n + gap / 2f
                    drawArc(
                        if (it.doneToday) colors.accent else colors.track, start, sweep, false,
                        Offset(size.width / 2f - r, size.height / 2f - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round),
                    )
                    if (!it.doneToday && it.habit.target > 1 && it.valueToday > 0) {
                        drawArc(
                            colors.accent, start, sweep * it.valueToday / it.habit.target, false,
                            Offset(size.width / 2f - r, size.height / 2f - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round), alpha = 0.5f,
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.habits_ring_count, formatCount(state.doneCount.toLong()), formatCount(due.size.toLong())),
                style = type.display.copy(fontSize = type.display.fontSize * 0.6f), color = colors.ink,
            )
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(stringResource(R.string.habits_ring_label), style = type.caption, color = colors.muted)
        }
    }
    due.forEach { HabitRow(it, state, actions, onEdit) }
    if (rest.isNotEmpty()) {
        Spacer(Modifier.height(XoSpacing.s3))
        Text(stringResource(R.string.habits_not_today), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s3))
        rest.forEach { HabitRow(it, state, actions, onEdit, dim = true) }
    }
}

@Composable
private fun HabitRow(item: HabitItem, state: HabitsUiState, actions: HabitsActions, onEdit: (HabitItem) -> Unit, dim: Boolean = false) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val hairline = with(LocalDensity.current) { XoSize.hairline.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (dim) 0.55f else 1f)
            .drawBehind { drawRect(colors.line, size = Size(size.width, hairline)) }
            .padding(vertical = XoSpacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Check(item, { actions.onToggle(item, 0) }, big = true)
        Spacer(Modifier.width(XoSpacing.s2))
        Column(Modifier.weight(1f)) {
            Text(habitName(item.habit), style = type.bodyStrong, color = colors.ink)
            Spacer(Modifier.height(XoSpacing.textGap))
            StreakCaption(item, subtitle(item))
        }
        if (item.habit.target > 1 && !state.editMode) {
            Counter(item, actions)
        }
        RowActions(item, state, actions, onEdit)
    }
}

@Composable
private fun Counter(item: HabitItem, actions: HabitsActions) {
    val less = stringResource(R.string.less)
    val more = stringResource(R.string.more)
    val colors = XoTheme.colors
    val type = XoTheme.type
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(XoSize.touchMin).clickable(role = Role.Button) { actions.onCount(item, -1) }.semantics { contentDescription = less }, contentAlignment = Alignment.Center) {
            Text("−", style = type.subtitle, color = colors.ink)
        }
        Text("${item.valueToday} / ${item.habit.target}", style = type.bodyStrong, color = colors.ink)
        Box(Modifier.size(XoSize.touchMin).clickable(role = Role.Button) { actions.onCount(item, 1) }.semantics { contentDescription = more }, contentAlignment = Alignment.Center) {
            Text("+", style = type.subtitle, color = colors.ink)
        }
    }
}

// ——— 01 «Список и неделя» ———

@Composable
private fun ListView(state: HabitsUiState, actions: HabitsActions, onEdit: (HabitItem) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val hairline = with(LocalDensity.current) { XoSize.hairline.toPx() }
    val locale = rememberFormatLocale()
    state.items.forEach { item ->
        Column(
            Modifier.fillMaxWidth().drawBehind { drawRect(colors.line, size = Size(size.width, hairline)) }.padding(vertical = XoSpacing.s2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(habitName(item.habit), style = type.bodyStrong, color = colors.ink)
                    Spacer(Modifier.height(XoSpacing.textGap))
                    StreakCaption(item, subtitle(item))
                }
                RowActions(item, state, actions, onEdit)
            }
            Row(Modifier.fillMaxWidth().padding(top = XoSpacing.s1), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0 until 7) {
                    val back = 6 - i
                    val date = state.today.minusDays(back.toLong())
                    val planned = HabitMath.planned(item.habit.freq, date)
                    val done = item.marks.done(date)
                    val label = stringResource(R.string.habits_day_a11y, habitName(item.habit), date.dayOfWeek.getDisplayName(TextStyle.FULL, locale))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(XoSize.touchMin)
                                .alpha(if (planned) 1f else 0.35f)
                                .clickable(enabled = planned || done, role = Role.Checkbox) { actions.onToggle(item, back) }
                                .semantics { contentDescription = label; selected = done },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(30.dp).border(if (done) 2.dp else XoSize.hairline, if (done) colors.ink else colors.line, CircleShape), contentAlignment = Alignment.Center) {
                                XMark(done, 30.dp)
                            }
                        }
                        Text(
                            if (back == 0) stringResource(R.string.habits_today_short) else date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                            style = type.scale, color = colors.muted,
                        )
                    }
                }
            }
        }
    }
}

// ——— 02 «Карточки с сеткой» ———

@Composable
private fun CardsView(state: HabitsUiState, actions: HabitsActions, onEdit: (HabitItem) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    state.items.forEach { item ->
        Column(
            Modifier.fillMaxWidth().padding(bottom = XoSpacing.s3).border(XoSize.hairline, colors.line, RoundedCornerShape(18.dp)).padding(XoSpacing.s3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(habitName(item.habit), style = type.bodyStrong, color = colors.ink)
                    Spacer(Modifier.height(XoSpacing.textGap))
                    Text(freqText(item.habit), style = type.caption, color = colors.muted)
                }
                if (item.habit.planned(state)) Check(item, { actions.onToggle(item, 0) }, big = true)
                RowActions(item, state, actions, onEdit)
            }
            Spacer(Modifier.height(XoSpacing.s2))
            Heat(item, state)
            Spacer(Modifier.height(XoSpacing.s2))
            StreakCaption(
                item,
                if (item.best > 0) stringResource(R.string.habits_card_streak, item.streak.toString(), item.best.toString()) +
                    (if (item.best >= 7) stringResource(R.string.habits_percent, item.percent30.toString()) else "")
                else stringResource(R.string.habits_card_empty),
            )
        }
    }
}

/** Подпись привычки с огоньком серии слева (Р81): огонь с 3 дней, приглушённый, пока сегодня не отмечено. */
@Composable
private fun StreakCaption(item: HabitItem, text: String) {
    val level = StreakFlame.level(item.streak)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (level > 0) {
            StreakFlameIcon(level, XoSize.streakFlame, waiting = !item.doneToday)
            Spacer(Modifier.width(XoSpacing.s1))
        }
        Text(text, style = XoTheme.type.caption, color = XoTheme.colors.muted)
    }
}

private fun Habit.planned(state: HabitsUiState): Boolean = HabitMath.planned(freq, state.today)

@Composable
private fun Heat(item: HabitItem, state: HabitsUiState) {
    val colors = XoTheme.colors
    val cols = 18
    val todayRow = state.today.dayOfWeek.value - 1
    Canvas(Modifier.fillMaxWidth().aspectRatio(cols / 7f)) {
        val cell = size.width / cols
        val rad = cell * 0.36f
        for (col in 0 until cols) {
            for (row in 0 until 7) {
                val back = (cols - 1 - col) * 7 + (todayRow - row)
                if (back < 0) continue
                val date = state.today.minusDays(back.toLong())
                val planned = HabitMath.planned(item.habit.freq, date)
                val done = item.marks.done(date)
                val c = when {
                    done -> colors.ink
                    planned -> colors.track
                    else -> colors.line
                }
                drawCircle(c, rad, Offset(col * cell + cell / 2f, row * cell + cell / 2f), alpha = if (planned || done) 1f else 0.5f)
            }
        }
    }
}

@Composable
private fun freqText(h: Habit): String {
    val locale = rememberFormatLocale()
    return when (val f = h.freq) {
        HabitFreq.Daily -> stringResource(R.string.habits_freq_daily)
        is HabitFreq.PerWeek -> stringResource(R.string.habits_freq_week, f.n.toString())
        is HabitFreq.Days -> DayOfWeek.entries.filter { f.mask and HabitMath.dayBit(it) != 0 }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    } + (h.reminderMin?.let { stringResource(R.string.habits_remind_at, timeOfDay(it)) } ?: "")
}

// ——— Диалоги ———

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitEditDialog(habit: Habit?, onSave: (Habit) -> Unit, onDismiss: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    var name by rememberSaveable { mutableStateOf(habit?.name ?: "") }
    var kind by rememberSaveable { mutableIntStateOf(when (habit?.freq) { null, HabitFreq.Daily -> 0; is HabitFreq.PerWeek -> 1; is HabitFreq.Days -> 2 }) }
    var perWeek by rememberSaveable { mutableIntStateOf((habit?.freq as? HabitFreq.PerWeek)?.n ?: 3) }
    var mask by rememberSaveable { mutableIntStateOf((habit?.freq as? HabitFreq.Days)?.mask ?: HabitMath.WORKOUT_DAYS) }
    var remind by rememberSaveable { mutableStateOf(habit?.reminderMin != null) }
    var remindMin by rememberSaveable { mutableIntStateOf(habit?.reminderMin ?: (20 * 60)) }
    var target by rememberSaveable { mutableIntStateOf(habit?.target ?: 1) }
    val freq: HabitFreq = when (kind) {
        1 -> HabitFreq.PerWeek(perWeek)
        2 -> HabitFreq.Days(mask.takeIf { it != 0 } ?: HabitMath.ALL_DAYS)
        else -> HabitFreq.Daily
    }
    val valid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(stringResource(if (habit == null) R.string.habits_new else R.string.habits_edit), style = type.title, color = colors.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(HabitMath.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.habits_name)) }, singleLine = true, textStyle = type.body,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Spacer(Modifier.height(XoSpacing.s3))
                XoSegmented(
                    listOf(0 to stringResource(R.string.habits_kind_daily), 1 to stringResource(R.string.habits_kind_week), 2 to stringResource(R.string.habits_kind_days)),
                    kind, { kind = it },
                )
                Spacer(Modifier.height(XoSpacing.s2))
                if (kind == 1) StepRow(stringResource(R.string.habits_per_week), perWeek.toString(), { perWeek = (perWeek - 1).coerceAtLeast(1) }, { perWeek = (perWeek + 1).coerceAtMost(6) })
                if (kind == 2) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(XoSpacing.s1), verticalArrangement = Arrangement.spacedBy(XoSpacing.s1)) {
                        DayOfWeek.entries.forEach { d ->
                            val on = mask and HabitMath.dayBit(d) != 0
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .border(if (on) 2.dp else XoSize.hairline, if (on) colors.ink else colors.line, CircleShape)
                                    .clickable(role = Role.Checkbox) { mask = mask xor HabitMath.dayBit(d) }
                                    .semantics { selected = on },
                                contentAlignment = Alignment.Center,
                            ) { Text(d.getDisplayName(TextStyle.SHORT, locale), style = type.scale, color = colors.ink) }
                        }
                    }
                }
                XoHairline(Modifier.padding(vertical = XoSpacing.s2))
                StepRow(stringResource(R.string.habits_target), target.toString(), { target = (target - 1).coerceAtLeast(1) }, { target = (target + 1).coerceAtMost(HabitMath.MAX_TARGET) })
                XoSwitchRow(stringResource(R.string.habits_remind), null, remind, { remind = it })
                if (remind) StepRow(stringResource(R.string.habits_remind_time), timeOfDay(remindMin), { remindMin = (remindMin - 30 + 1440) % 1440 }, { remindMin = (remindMin + 30) % 1440 })
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave((habit ?: Habit(0, "", HabitFreq.Daily)).copy(name = name, freq = freq, reminderMin = if (remind) remindMin else null, target = target)) },
            ) { Text(stringResource(R.string.pick_save), style = type.bodyStrong) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), style = type.body) } },
    )
}

@Composable
private fun StepRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val less = stringResource(R.string.less)
    val more = stringResource(R.string.more)
    val colors = XoTheme.colors
    val type = XoTheme.type
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
        Box(Modifier.size(XoSize.touchMin).border(XoSize.hairline, colors.line, CircleShape).clickable(role = Role.Button, onClick = onMinus).semantics { contentDescription = less }, contentAlignment = Alignment.Center) {
            Text("−", style = type.subtitle, color = colors.ink)
        }
        Text(value, style = type.bodyStrong, color = colors.ink, modifier = Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.size(XoSize.touchMin).border(XoSize.hairline, colors.line, CircleShape).clickable(role = Role.Button, onClick = onPlus).semantics { contentDescription = more }, contentAlignment = Alignment.Center) {
            Text("+", style = type.subtitle, color = colors.ink)
        }
    }
}

@Composable
private fun SettingsDialog(state: HabitsUiState, actions: HabitsActions, onEditList: () -> Unit, onDismiss: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.habits_settings), style = type.title, color = colors.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.habits_view), style = type.bodyStrong, color = colors.ink)
                Spacer(Modifier.height(XoSpacing.s2))
                XoSegmented(
                    listOf(
                        HabitsView.Ring to stringResource(R.string.habits_view_ring),
                        HabitsView.List to stringResource(R.string.habits_view_list),
                        HabitsView.Cards to stringResource(R.string.habits_view_cards),
                    ),
                    state.view, actions.onView,
                )
                XoHairline(Modifier.padding(vertical = XoSpacing.s2))
                TextButton(onClick = onEditList) { Text(stringResource(R.string.habits_edit_list), style = type.bodyStrong) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.got_it), style = type.bodyStrong) } },
    )
}


private val KNOWN_NAMES = listOf(R.string.habits_seed_1, R.string.habits_seed_2, R.string.habits_seed_3, R.string.habits_seed_4, R.string.habits_workout_name)

/**
 * Заготовки привычек записываются в базу на языке момента создания. Пока человек их не переименовал,
 * показываем их на текущем языке приложения: после смены языка названия не остаются на старом.
 */
@Composable
internal fun habitName(h: com.projectxo.app.core.model.Habit): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val id = remember(h.name, config) {
        KNOWN_NAMES.firstOrNull { id ->
            listOf("ru", "en").any { lang ->
                val cfg = android.content.res.Configuration(config).apply { setLocale(java.util.Locale.forLanguageTag(lang)) }
                context.createConfigurationContext(cfg).getString(id) == h.name
            }
        }
    }
    return if (id != null) stringResource(id) else h.name
}
