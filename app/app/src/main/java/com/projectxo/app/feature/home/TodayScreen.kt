package com.projectxo.app.feature.home

import com.projectxo.app.core.ui.format.formatVolume
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import com.projectxo.app.core.motion.InkFlowSpec
import com.projectxo.app.core.ui.motion.InkAnchor
import com.projectxo.app.core.ui.motion.InkTargets
import com.projectxo.app.core.ui.motion.LocalInkFlow
import com.projectxo.app.core.ui.motion.inkAnchor
import com.projectxo.app.core.ui.splash.LocalSplash
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.projectxo.app.core.model.WaterPortions
import com.projectxo.app.core.ui.components.UndoBar
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.motion.pressScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.projectxo.app.R
import com.projectxo.app.core.ui.format.formatToday
import com.projectxo.app.core.ui.splash.EntranceSlot
import com.projectxo.app.core.ui.splash.entrance
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import java.time.LocalDate

/** «Сегодня»: одно большое кольцо «Баланс дня», под ним строки включённых модулей. */
@Composable
fun TodayScreen(
    state: TodayState,
    date: LocalDate,
    /** Аргумент — через сколько мс записать: столько летят чернила (0 — сразу). */
    onQuickAdd: (Long) -> Unit,
    modifier: Modifier = Modifier,
    undo: QuickAdd? = null,
    onUndo: () -> Unit = {},
    week: List<WeekDay> = emptyList(),
    onOpenCalendar: (LocalDate) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenModule: (com.projectxo.app.core.model.Module) -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        TodayContent(state, date, onQuickAdd, week, onOpenCalendar, onOpenSettings, onOpenModule)
        // Последнее непустое сообщение держим, пока полоса уезжает.
        var lastMl by remember { mutableIntStateOf(WaterPortions.PRIMARY) }
        if (undo != null) lastMl = undo.ml
        UndoBar(
            visible = undo != null,
            message = stringResource(R.string.undo_added, formatVolume(lastMl.toLong())),
            action = stringResource(R.string.undo_cancel),
            onAction = onUndo,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TodayContent(
    state: TodayState,
    date: LocalDate,
    onQuickAdd: (Long) -> Unit,
    week: List<WeekDay>,
    onOpenCalendar: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModule: (com.projectxo.app.core.model.Module) -> Unit,
) {
    // Печать цели: модуль дошёл до цели, пока пользователь смотрит на экран.
    val splash = LocalSplash.current
    val completed = state.modules.count { it.fraction >= 1f }
    var prevCompleted by remember { mutableStateOf<Int?>(null) }
    var seal by remember { mutableIntStateOf(0) }
    var celebrate by remember { mutableIntStateOf(0) }
    var prevPercent by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(completed, state.balancePercent, splash.done) {
        if (!splash.done) return@LaunchedEffect
        val p = prevCompleted
        val pp = prevPercent
        val full = pp != null && pp < 100 && state.balancePercent >= 100
        // «Баланс дня» 100 %: полная анимация; один модуль: только волна по делениям (DESIGN.md 5c.1).
        if (full) {
            celebrate++
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        } else if (p != null && completed > p) {
            seal++
        }
        prevCompleted = completed
        prevPercent = state.balancePercent
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XoSpacing.screenGutter),
    ) {
        Spacer(Modifier.height(XoSpacing.s5))
        TodayHeader(date, onQuickAdd, onOpenSettings, Modifier.entrance(EntranceSlot.HEADER))
        if (week.isNotEmpty()) {
            Spacer(Modifier.height(XoSpacing.s3))
            WeekStrip(week, onOpenCalendar, Modifier.entrance(EntranceSlot.HEADER))
        }
        Spacer(Modifier.height(XoSpacing.s5))
        DayBalanceRing(
            ratio = state.balance,
            percent = state.balancePercent,
            sealTrigger = seal,
            celebrateTrigger = celebrate,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(XoSpacing.s7))
        state.modules.forEachIndexed { i, m ->
            ModuleRow(m, entranceIndex = EntranceSlot.ROW_FIRST + minOf(i, EntranceSlot.ROW_LAST - EntranceSlot.ROW_FIRST), isLast = i == state.modules.lastIndex, onClick = { onOpenModule(m.module) })
        }
        Spacer(Modifier.height(XoSpacing.s5))
    }
}

@Composable
private fun TodayHeader(date: LocalDate, onQuickAdd: (Long) -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.today_title), style = type.title, color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(formatToday(date), style = type.caption, color = colors.muted)
        }
        SettingsButton(onOpenSettings)
        QuickAddButton(onQuickAdd)
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val colors = XoTheme.colors
    val label = stringResource(R.string.settings_open)
    Box(
        Modifier
            .size(XoSize.touchMin)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(com.projectxo.app.core.ui.icons.XoIcons.Settings, null, tint = colors.ink, modifier = Modifier.size(XoSize.navIcon))
    }
}

@Composable
private fun QuickAddButton(onClick: (Long) -> Unit) {
    val ink = LocalInkFlow.current
    val anchor = remember { InkAnchor() }
    val colors = XoTheme.colors
    val label = stringResource(R.string.quick_add, formatVolume(WaterPortions.primaryMl(com.projectxo.app.core.ui.format.LocalVolumeUnit.current).toLong()))
    val stroke = with(LocalDensity.current) { XoSize.plusStroke.toPx() }
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(XoSize.touchMin)
            .inkAnchor(anchor)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                val flying = ink.launch(anchor.center, InkTargets.TODAY_RING)
                onClick(if (flying) InkFlowSpec.ARRIVE_MS else 0L)
            }
            .clearAndSetSemantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(XoSize.addButton)
                .border(XoSize.hairline, colors.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(XoSize.plus)) {
                drawLine(colors.ink, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke, StrokeCap.Butt)
                drawLine(colors.ink, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke, StrokeCap.Butt)
            }
        }
    }
}

@Preview(name = "Светлая", showBackground = true, widthDp = 360, heightDp = 780)
@Preview(name = "Тёмная", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 360, heightDp = 780)
@Preview(name = "Крупный текст", fontScale = 1.3f, showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun TodayPreview() {
    XoTheme {
        Box(Modifier.background(XoTheme.colors.background)) {
            TodayScreen(TodayState.Empty, LocalDate.of(2026, 11, 5), onQuickAdd = { _ -> })
        }
    }
}
