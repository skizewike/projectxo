package com.projectxo.app.feature.food

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.projectxo.app.R
import com.projectxo.app.core.data.FoodDraft
import com.projectxo.app.core.data.FoodEntry
import com.projectxo.app.core.data.FoodTotals
import com.projectxo.app.core.model.FoodTemplate
import com.projectxo.app.core.model.FoodTemplates
import com.projectxo.app.core.motion.GoalCelebration
import com.projectxo.app.core.motion.GoalCelebrationSpec
import com.projectxo.app.core.ui.components.RollingNumber
import com.projectxo.app.core.ui.components.UndoBar
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.formatToday
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.motion.pressScale
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * «Еда»: квадрат дня, вариант «Под подписью» (docs/DESIGN.md, «Еда»; Р27, Р33).
 * Сверху число калорий и подпись, под ней квадрат слева и белки, жиры, углеводы справа,
 * ниже быстрые шаблоны и все записи дня. Экран прокручивается целиком.
 */
@Composable
fun FoodScreen(
    state: FoodUiState,
    onAdd: (FoodDraft) -> Unit,
    onDelete: (FoodEntry) -> Unit,
    onUndoDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPhoto: () -> Unit = {},
    photos: com.projectxo.app.core.data.photo.FoodPhotoStore? = null,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    var showDialog by rememberSaveable { mutableStateOf(false) }
    // Открытая запись с фото (Р71): лист закрывается сам, если запись удалили.
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Анимация выполненной цели: только в момент перехода через цель, пока экран открыт.
    val celebration = remember { Animatable(-1f) }
    var prevKcal by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.totals.kcal, state.loaded) {
        if (!state.loaded) return@LaunchedEffect
        val p = prevKcal
        prevKcal = state.totals.kcal
        if (p != null && p < state.goals.kcal && state.totals.kcal >= state.goals.kcal) {
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

    val add = { draft: FoodDraft ->
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        onAdd(draft)
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(XoSpacing.s5))
            Header(state.date, onAddClick = { showDialog = true }, Modifier.padding(horizontal = XoSpacing.screenGutter))
            Spacer(Modifier.height(XoSpacing.s5))
            Total(state, { celebration.value }, Modifier.padding(horizontal = XoSpacing.screenGutter))
            Spacer(Modifier.height(XoSpacing.s5))
            // Крупный текст: граммы не помещаются рядом с квадратом, ставим их под ним.
            if (LocalDensity.current.fontScale >= XoSize.LARGE_TEXT_SCALE) {
                Column(Modifier.padding(horizontal = XoSpacing.screenGutter)) {
                    SquareWithLabels(state, { celebration.value })
                    Spacer(Modifier.height(XoSpacing.s4))
                    Macros(state, Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.padding(horizontal = XoSpacing.screenGutter)) {
                    SquareWithLabels(state, { celebration.value })
                    Spacer(Modifier.width(XoSpacing.s5))
                    Macros(state, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(XoSpacing.s5))
            Chips(onTemplate = add, onCustom = { showDialog = true }, onPhoto = onOpenPhoto)
            Spacer(Modifier.height(XoSpacing.s5))
            Entries(state, onDelete, photos, onOpen = { openId = it.id }, Modifier.padding(horizontal = XoSpacing.screenGutter))
            // Запас под полосу «Вернуть», чтобы она не закрывала последнюю запись.
            Spacer(Modifier.height(XoSpacing.s8))
        }

        UndoBar(
            visible = state.pendingDelete != null,
            message = stringResource(R.string.undo_deleted),
            action = stringResource(R.string.undo_restore),
            onAction = onUndoDelete,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    FoodEntrySheet(
        entry = state.entries.firstOrNull { it.id == openId && it.photoFile != null },
        photos = photos,
        onDismiss = { openId = null },
        onDelete = onDelete,
    )

    if (showDialog) {
        FoodEntryDialog(
            onConfirm = { draft ->
                showDialog = false
                add(draft)
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun Header(date: LocalDate, onAddClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val stroke = with(LocalDensity.current) { XoSize.plusStroke.toPx() }
    val label = stringResource(R.string.food_add_a11y)
    val interaction = remember { MutableInteractionSource() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.tab_food), style = type.title, color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(XoSpacing.headGap))
            Text(formatToday(date), style = type.caption, color = colors.muted)
        }
        Box(
            Modifier
                .size(XoSize.touchMin)
                .pressScale(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onAddClick)
                .clearAndSetSemantics { contentDescription = label; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(XoSize.addButton).border(XoSize.hairline, colors.line, CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(XoSize.plus)) {
                    drawLine(colors.ink, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke, StrokeCap.Butt)
                    drawLine(colors.ink, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke, StrokeCap.Butt)
                }
            }
        }
    }
}

@Composable
private fun Total(state: FoodUiState, celebrationMs: () -> Float, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val dp = LocalDensity.current.density
    val goal = formatCount(state.goals.kcal)
    val done = state.leftKcal == 0L
    val firstLine = if (done) stringResource(R.string.food_done_title)
    else stringResource(R.string.food_left, formatCount(state.leftKcal), goal)
    val secondLine = when {
        !done -> null
        state.overKcal > 0 -> stringResource(R.string.food_over, formatCount(state.overKcal))
        else -> stringResource(R.string.food_exact, goal)
    }
    val a11y = stringResource(
        R.string.food_total_a11y,
        formatCount(state.totals.kcal.toLong()),
        listOfNotNull(firstLine, secondLine).joinToString(". "),
    )

    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = a11y }) {
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
                value = state.totals.kcal.toLong(), text = formatCount(state.totals.kcal.toLong()),
                style = type.display, color = colors.ink, modifier = Modifier.alignByBaseline(),
            )
            Text(
                stringResource(R.string.food_unit), style = type.displayUnit, color = colors.muted,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(XoSpacing.s3))
        if (secondLine == null) {
            Text(firstLine, style = type.caption, color = colors.muted)
        } else {
            Text(firstLine, style = type.caption.copy(fontWeight = type.bodyStrong.fontWeight), color = colors.ink)
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(secondLine, style = type.caption, color = colors.muted)
        }
    }
}

@Composable
private fun SquareWithLabels(state: FoodUiState, celebrationMs: () -> Float) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val a11y = stringResource(
        R.string.food_square_a11y, formatCount(state.totals.kcal.toLong()), formatCount(state.goals.kcal),
    )
    Column(Modifier.width(XoSize.foodSquare).clearAndSetSemantics { contentDescription = a11y }) {
        FoodSquare(state.totals, state.goals.kcal, celebrationMs)
        Spacer(Modifier.height(XoSpacing.s2))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", style = type.scale, color = colors.muted)
            Text(formatCount(state.goals.kcal), style = type.scale, color = colors.muted)
        }
    }
}

@Composable
private fun Macros(state: FoodUiState, modifier: Modifier = Modifier) {
    val (cp, cf, cc) = foodLayerColors()
    val t = state.totals
    val g = state.goals
    Column(modifier) {
        MacroRow(R.string.macro_protein, t.proteinG, g.proteinG, cp, isFirst = true)
        MacroRow(R.string.macro_fat, t.fatG, g.fatG, cf)
        MacroRow(R.string.macro_carbs, t.carbsG, g.carbsG, cc, isLast = true)
    }
}

@Composable
private fun MacroRow(
    name: Int,
    grams: Int,
    goal: Long,
    tone: androidx.compose.ui.graphics.Color,
    isFirst: Boolean = false,
    isLast: Boolean = false,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val density = LocalDensity.current
    val hairline = with(density) { XoSize.hairline.toPx() }
    val label = stringResource(name)
    val a11y = stringResource(R.string.macro_a11y, label, formatCount(grams.toLong()), formatCount(goal))
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(colors.line, size = Size(size.width, hairline))
                if (isLast) drawRect(colors.line, Offset(0f, size.height - hairline), Size(size.width, hairline))
            }
            .padding(vertical = XoSpacing.s2)
            .clearAndSetSemantics { contentDescription = a11y },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(XoSize.foodSwatch).background(tone))
            Spacer(Modifier.width(XoSpacing.s2))
            Text(label, style = type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(XoSpacing.s1))
        Row(Modifier.padding(start = XoSize.foodSwatch + XoSpacing.s2), verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.macro_grams, formatCount(grams.toLong())), style = type.bodyStrong, color = colors.ink)
            Spacer(Modifier.width(XoSpacing.s1))
            Text(stringResource(R.string.macro_of, formatCount(goal)), style = type.caption, color = colors.muted)
        }
    }
}

@Composable
private fun templateName(id: Int): String = when (id) {
    1 -> stringResource(R.string.food_tpl_1)
    2 -> stringResource(R.string.food_tpl_2)
    3 -> stringResource(R.string.food_tpl_3)
    4 -> stringResource(R.string.food_tpl_4)
    else -> stringResource(R.string.food_tpl_5)
}

@Composable
private fun Chips(onTemplate: (FoodDraft) -> Unit, onCustom: () -> Unit, onPhoto: () -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = XoSpacing.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(XoSize.chipGap),
    ) {
        // Кнопка-значок «Камера» первой: путь в отдельный экран распознавания (Р69, Р71), остальной модуль без изменений.
        PhotoChip(onPhoto)
        FoodTemplates.ALL.forEach { tpl -> TemplateChip(tpl, onTemplate) }
        CustomChip(onCustom)
    }
}

@Composable
private fun TemplateChip(tpl: FoodTemplate, onTemplate: (FoodDraft) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val interaction = remember { MutableInteractionSource() }
    val name = templateName(tpl.id)
    val kcal = formatCount(tpl.kcal.toLong())
    val a11y = stringResource(R.string.food_template_a11y, name, kcal)
    Row(
        Modifier
            .heightIn(min = XoSize.touchMin)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null) {
                onTemplate(FoodDraft(name, tpl.kcal, tpl.proteinG, tpl.fatG, tpl.carbsG))
            }
            .clearAndSetSemantics { contentDescription = a11y; role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .heightIn(min = XoSize.chipHeight)
                .border(XoSize.hairline, colors.line, RoundedCornerShape(XoSize.chipRadius))
                .padding(horizontal = XoSpacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = type.caption, color = colors.ink, maxLines = 1)
            Spacer(Modifier.width(XoSpacing.s2))
            Text(kcal, style = type.scale, color = colors.muted)
        }
    }
}

@Composable
private fun PhotoChip(onPhoto: () -> Unit) {
    val colors = XoTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(R.string.fp_title)
    Box(
        Modifier
            .size(XoSize.touchMin)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onPhoto)
            .clearAndSetSemantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = XoSize.photoChipWidth, height = XoSize.chipHeight)
                .border(XoSize.hairline, colors.ink, RoundedCornerShape(XoSize.chipRadius)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(XoIcons.Camera, null, tint = colors.ink, modifier = Modifier.size(XoSize.photoChipIcon))
        }
    }
}

@Composable
private fun CustomChip(onCustom: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .heightIn(min = XoSize.touchMin)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onCustom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .heightIn(min = XoSize.chipHeight)
                .border(XoSize.hairline, colors.ink, RoundedCornerShape(XoSize.chipRadius))
                .padding(horizontal = XoSpacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.food_custom_chip), style = type.caption, color = colors.ink, maxLines = 1)
        }
    }
}

@Composable
private fun Entries(
    state: FoodUiState,
    onDelete: (FoodEntry) -> Unit,
    photos: com.projectxo.app.core.data.photo.FoodPhotoStore?,
    onOpen: (FoodEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    Column(modifier) {
        Text(
            stringResource(R.string.food_list_title), style = type.caption, color = colors.muted,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(XoSpacing.s3))
        if (state.loaded && state.entries.isEmpty()) {
            Text(stringResource(R.string.food_empty), style = type.body, color = colors.muted)
            return@Column
        }
        // Новые записи появляются раскрытием; те, что были при открытии экрана, стоят сразу.
        val known = remember { mutableSetOf<Long>() }
        val firstFill = known.isEmpty()
        state.entries.forEachIndexed { i, entry ->
            key(entry.id) {
                val appear = remember { Animatable(if (firstFill || reduced || entry.id in known) 1f else 0f) }
                LaunchedEffect(Unit) {
                    if (appear.value < 1f) appear.animateTo(1f, tween(XoMotion.STATE_MS, easing = XoMotion.EmphasizedDecelerate))
                }
                EntryRow(
                    entry = entry,
                    isLast = i == state.entries.lastIndex,
                    onDelete = { onDelete(entry) },
                    photos = photos,
                    onOpen = { onOpen(entry) },
                    modifier = Modifier.graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * -size.height / 3f },
                )
            }
        }
        state.entries.forEach { known += it.id }
    }
}

@Composable
private fun EntryRow(
    entry: FoodEntry,
    isLast: Boolean,
    onDelete: () -> Unit,
    photos: com.projectxo.app.core.data.photo.FoodPhotoStore?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    val kcal = formatCount(entry.kcal.toLong())
    val hasPhoto = entry.photoFile != null
    val rowA11y = if (hasPhoto) stringResource(R.string.food_entry_photo_a11y, entry.name, kcal, time)
    else stringResource(R.string.food_entry_a11y, entry.name, kcal, time)
    val deleteA11y = stringResource(R.string.food_delete_a11y, entry.name, kcal)

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
            Modifier
                .weight(1f)
                .then(if (hasPhoto) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
                .padding(vertical = XoSpacing.s2)
                .clearAndSetSemantics { contentDescription = rowA11y; if (hasPhoto) role = Role.Button },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, style = type.caption, color = colors.muted)
            Spacer(Modifier.width(XoSpacing.s4))
            if (hasPhoto) {
                StoredPhoto(photos, entry.photoFile, maxSidePx = 160, modifier = Modifier.size(XoSize.entryThumb).clip(RoundedCornerShape(XoSize.entryThumbRadius)))
                Spacer(Modifier.width(XoSpacing.s3))
            }
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = type.body, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(XoSpacing.textGap))
                Text(
                    stringResource(
                        R.string.food_entry_macros,
                        formatCount(entry.proteinG.toLong()), formatCount(entry.fatG.toLong()), formatCount(entry.carbsG.toLong()),
                    ),
                    style = type.caption, color = colors.muted,
                )
            }
            Spacer(Modifier.width(XoSpacing.s3))
            Text(kcal, style = type.bodyStrong, color = colors.ink)
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
private fun FoodPreview() {
    val date = LocalDate.of(2026, 11, 9)
    val base = date.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val entries = listOf(
        FoodEntry(3, base + 5 * 3_600_000L, date, "Курица с рисом", 540, 42, 12, 64),
        FoodEntry(2, base + 2 * 3_600_000L, date, "Яблоко", 80, 0, 0, 19),
        FoodEntry(1, base, date, "Овсянка с бананом", 320, 10, 6, 58),
    )
    XoTheme {
        Box(Modifier.background(XoTheme.colors.background)) {
            FoodScreen(
                state = FoodUiState(date = date, totals = FoodTotals(940, 52, 18, 141), entries = entries, loaded = true),
                onAdd = {}, onDelete = {}, onUndoDelete = {},
            )
        }
    }
}
