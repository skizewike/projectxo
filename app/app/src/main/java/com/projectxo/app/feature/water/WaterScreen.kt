package com.projectxo.app.feature.water

import com.projectxo.app.core.ui.format.LocalVolumeUnit
import com.projectxo.app.core.ui.format.volumeUnitLabel
import com.projectxo.app.core.ui.format.formatVolumeNumber
import com.projectxo.app.core.ui.format.formatVolume
import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.projectxo.app.R
import com.projectxo.app.core.data.WaterEntry
import com.projectxo.app.core.motion.GoalCelebration
import com.projectxo.app.core.motion.GoalCelebrationSpec
import com.projectxo.app.core.motion.InkFlowSpec
import com.projectxo.app.core.model.WaterPortions
import com.projectxo.app.core.ui.components.RollingNumber
import com.projectxo.app.core.ui.components.UndoBar
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.formatToday
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.icons.PortionIcon
import com.projectxo.app.core.ui.motion.InkAnchor
import com.projectxo.app.core.ui.motion.InkTargets
import com.projectxo.app.core.ui.motion.LocalInkFlow
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.motion.inkAnchor
import com.projectxo.app.core.ui.motion.pressScale
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Показываем на экране три последние записи (DESIGN.md, 5a). */
private const val RECENT_COUNT = 3

/**
 * «Вода»: мерный сосуд, компоновка «Колонка» (docs/DESIGN.md, 5a; анимация цели 5c).
 * Сосуд слева, справа итог, подпись, плитки 2 × 2 и три последние записи.
 */
@Composable
fun WaterScreen(
    state: WaterUiState,
    /** (мл, через сколько мс записать: столько летят чернила). */
    onAdd: (Int, Long) -> Unit,
    onDelete: (WaterEntry) -> Unit,
    onUndoDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val ink = LocalInkFlow.current
    var showCustom by rememberSaveable { mutableStateOf(false) }

    // Анимация выполненной цели: только в момент перехода через цель, пока экран открыт.
    val celebration = remember { Animatable(-1f) }
    var prevTotal by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.totalMl, state.loaded) {
        if (!state.loaded) return@LaunchedEffect
        val p = prevTotal
        prevTotal = state.totalMl
        if (p != null && p < state.goalMl && state.totalMl >= state.goalMl) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            if (!reduced) {
                celebration.snapTo(0f)
                celebration.animateTo(
                    GoalCelebrationSpec.TOTAL_MS,
                    tween(GoalCelebrationSpec.TOTAL_MS.toInt(), easing = LinearEasing),
                )
                celebration.snapTo(-1f)
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = XoSpacing.screenGutter),
        ) {
            Spacer(Modifier.height(XoSpacing.s5))
            Text(
                stringResource(R.string.tab_water), style = type.title, color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(formatToday(state.date), style = type.caption, color = colors.muted)
            Spacer(Modifier.height(XoSpacing.s5))

            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = XoSize.vesselBottomGap),
            ) {
                Vessel(
                    totalMl = state.totalMl,
                    goalMl = state.goalMl,
                    celebrationMs = { celebration.value },
                    onPour = { ml, from ->
                        val flying = ink.launch(from, InkTargets.WATER_LEVEL)
                        onAdd(ml, if (flying) InkFlowSpec.ARRIVE_MS else 0L)
                    },
                    onCustom = { showCustom = true },
                    modifier = Modifier
                        .width(XoSize.vesselWidth)
                        .fillMaxHeight(),
                )
                Spacer(Modifier.width(XoSize.columnGap))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(XoSpacing.s1))
                    WaterTotal(state, celebrationMs = { celebration.value })
                    Spacer(Modifier.height(XoSpacing.s5))
                    PortionTiles(onAdd)
                    Spacer(Modifier.height(XoSpacing.s6))
                    RecentEntries(state, onDelete)
                }
            }
        }

        UndoBar(
            visible = state.pendingDelete != null,
            message = stringResource(R.string.undo_deleted),
            action = stringResource(R.string.undo_restore),
            onAction = onUndoDelete,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showCustom) {
        CustomPortionDialog(
            onConfirm = { ml ->
                showCustom = false
                onAdd(ml, 0L)
            },
            onDismiss = { showCustom = false },
        )
    }
}

@Composable
private fun WaterTotal(state: WaterUiState, celebrationMs: () -> Float) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val dp = LocalDensity.current.density
    val goal = formatVolume(state.goalMl)
    val over = state.totalMl - state.goalMl
    val done = state.leftMl == 0L
    val firstLine = if (done) stringResource(R.string.water_done_title) else stringResource(R.string.water_left, formatVolumeNumber(state.leftMl), goal)
    val secondLine = when {
        !done -> null
        over > 0 -> stringResource(R.string.water_over, formatVolume(over), goal)
        else -> stringResource(R.string.water_exact, goal)
    }
    val a11y = stringResource(
        R.string.water_total_a11y,
        formatVolume(state.totalMl.toLong()),
        listOfNotNull(firstLine, secondLine).joinToString(". "),
    )

    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = a11y }) {
        // «Приседание» числа в анимации цели (5c).
        Row(
            horizontalArrangement = Arrangement.spacedBy(XoSpacing.s1),
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
                value = state.totalMl.toLong(), text = formatVolumeNumber(state.totalMl.toLong()),
                style = type.displayM, color = colors.ink, modifier = Modifier.alignByBaseline(),
            )
            Text(
                volumeUnitLabel(), style = type.displayUnitM, color = colors.muted,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(XoSpacing.s3))
        if (secondLine == null) {
            Text(firstLine, style = type.caption, color = colors.muted)
        } else {
            Text(firstLine, style = type.caption.copy(fontWeight = type.bodyStrong.fontWeight), color = colors.ink)
            Text(secondLine, style = type.caption, color = colors.muted)
        }
    }
}

@Composable
private fun PortionTiles(onAdd: (Int, Long) -> Unit) {
    val unit = LocalVolumeUnit.current
    Column(verticalArrangement = Arrangement.spacedBy(XoSize.tileGap)) {
        WaterPortions.TILES.indices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(XoSize.tileGap)) {
                pair.forEach { i ->
                    Tile(WaterPortions.tileMl(i, unit), WaterPortions.TILES[i], i == WaterPortions.PRIMARY_INDEX, onAdd, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Tile(ml: Int, iconMl: Int, primary: Boolean, onAdd: (Int, Long) -> Unit, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val haptic = LocalHapticFeedback.current
    val ink = LocalInkFlow.current
    val anchor = remember { InkAnchor() }
    val interaction = remember { MutableInteractionSource() }
    val label = formatVolume(ml.toLong())
    val a11y = stringResource(R.string.portion_add_a11y, label)
    val shape = RoundedCornerShape(XoSize.tileRadius)
    Column(
        modifier = modifier
            .height(XoSize.tileHeight)
            .inkAnchor(anchor)
            .pressScale(interaction)
            .border(XoSize.hairline, if (primary) colors.ink else colors.line, shape)
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                val flying = ink.launch(anchor.center, InkTargets.WATER_LEVEL)
                onAdd(ml, if (flying) InkFlowSpec.ARRIVE_MS else 0L)
            }
            .clearAndSetSemantics { contentDescription = a11y; role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PortionIcon(iconMl)
        Spacer(Modifier.height(XoSpacing.s2))
        Text(label, style = if (primary) type.bodyStrong else type.body, color = colors.ink)
    }
}

@Composable
private fun RecentEntries(state: WaterUiState, onDelete: (WaterEntry) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    Text(
        stringResource(R.string.water_list_title), style = type.caption, color = colors.muted,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(XoSpacing.s2))
    if (state.loaded && state.entries.isEmpty()) {
        Text(stringResource(R.string.water_empty, formatVolume(WaterPortions.primaryMl(LocalVolumeUnit.current).toLong())), style = type.body, color = colors.muted)
        return
    }
    // Новые записи появляются раскрытием; те, что были при открытии экрана, стоят сразу.
    val known = remember { mutableSetOf<Long>() }
    val firstFill = known.isEmpty()
    val recent = state.entries.take(RECENT_COUNT)
    recent.forEachIndexed { i, entry ->
        key(entry.id) {
            val appear = remember { Animatable(if (firstFill || reduced || entry.id in known) 1f else 0f) }
            LaunchedEffect(Unit) {
                if (appear.value < 1f) appear.animateTo(1f, tween(XoMotion.STATE_MS, easing = XoMotion.EmphasizedDecelerate))
            }
            EntryRow(
                entry = entry,
                isLast = i == recent.lastIndex,
                onDelete = { onDelete(entry) },
                modifier = Modifier.graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * -size.height / 3f },
            )
        }
    }
    recent.forEach { known += it.id }
}

@Composable
private fun EntryRow(entry: WaterEntry, isLast: Boolean, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val density = LocalDensity.current
    val hairline = with(density) { XoSize.hairline.toPx() }
    val iconStroke = with(density) { XoSize.deleteIconStroke.toPx() }
    val pattern = stringResource(R.string.time_pattern)
    val locale = rememberFormatLocale()
    val formatter = remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
    val time = remember(entry.timestampMs, formatter) {
        formatter.format(Instant.ofEpochMilli(entry.timestampMs).atZone(ZoneId.systemDefault()))
    }
    val ml = formatVolume(entry.ml.toLong())
    val rowA11y = stringResource(R.string.water_entry_a11y, ml, time)
    val deleteA11y = stringResource(R.string.delete_entry_a11y, ml, time)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = XoSize.entryRowHeight)
            .drawBehind {
                drawRect(colors.line, size = Size(size.width, hairline))
                if (isLast) drawRect(colors.line, Offset(0f, size.height - hairline), Size(size.width, hairline))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clearAndSetSemantics { contentDescription = rowA11y },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, style = type.caption, color = colors.muted)
            Spacer(Modifier.size(XoSpacing.s4))
            Text(ml, style = type.bodyStrong, color = colors.ink)
        }
        Box(
            Modifier
                .size(XoSize.touchMin)
                .clickable(onClick = onDelete)
                .clearAndSetSemantics { contentDescription = deleteA11y; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(XoSize.deleteIcon)) {
                drawLine(colors.muted, Offset.Zero, Offset(size.width, size.height), iconStroke, StrokeCap.Butt)
                drawLine(colors.muted, Offset(size.width, 0f), Offset(0f, size.height), iconStroke, StrokeCap.Butt)
            }
        }
    }
}

@Preview(name = "Светлая", showBackground = true, widthDp = 360, heightDp = 780)
@Preview(name = "Тёмная", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 360, heightDp = 780)
@Preview(name = "Крупный текст", fontScale = 1.3f, showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun WaterPreview() {
    val date = LocalDate.of(2026, 11, 9)
    val base = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val entries = listOf(
        WaterEntry(3, base + 5 * 3_600_000L, date, 350),
        WaterEntry(2, base + 2 * 3_600_000L, date, 250),
        WaterEntry(1, base, date, 600),
    )
    XoTheme {
        Box(Modifier.background(XoTheme.colors.background)) {
            WaterScreen(
                state = WaterUiState(date = date, totalMl = 1200, entries = entries, loaded = true),
                onAdd = { _, _ -> }, onDelete = {}, onUndoDelete = {},
            )
        }
    }
}
