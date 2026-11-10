package com.projectxo.app.feature.calendar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.model.Feel
import com.projectxo.app.core.model.DayWeight
import com.projectxo.app.core.model.DayNote
import com.projectxo.app.core.model.WellbeingGroup
import com.projectxo.app.core.model.WellbeingTag
import com.projectxo.app.core.model.WeightUnit
import com.projectxo.app.core.plan.UnitMath
import com.projectxo.app.core.ui.components.XoStepper
import com.projectxo.app.core.ui.format.formatDecimal
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.icons.WellbeingIcons
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
internal fun feelName(feel: Int): String = stringResource(
    when (feel) {
        0 -> R.string.feel_0
        1 -> R.string.feel_1
        2 -> R.string.feel_2
        3 -> R.string.feel_3
        else -> R.string.feel_4
    },
)

@Composable
internal fun tagName(tag: WellbeingTag): String = stringResource(
    when (tag) {
        WellbeingTag.Energetic -> R.string.tag_energy
        WellbeingTag.Tired -> R.string.tag_tired
        WellbeingTag.Sleepy -> R.string.tag_sleepy
        WellbeingTag.Head -> R.string.tag_head
        WellbeingTag.Back -> R.string.tag_back
        WellbeingTag.Muscle -> R.string.tag_muscle
        WellbeingTag.Joints -> R.string.tag_joint
        WellbeingTag.Belly -> R.string.tag_belly
        WellbeingTag.Calm -> R.string.tag_calm
        WellbeingTag.Joy -> R.string.tag_joy
        WellbeingTag.Anxiety -> R.string.tag_anx
        WellbeingTag.Irritation -> R.string.tag_angry
        WellbeingTag.Sadness -> R.string.tag_sad
        WellbeingTag.Stress -> R.string.tag_stress
        WellbeingTag.Bloating -> R.string.tag_bloat
        WellbeingTag.Heartburn -> R.string.tag_heart
        WellbeingTag.Nausea -> R.string.tag_nausea
        WellbeingTag.Cold -> R.string.tag_cold
        WellbeingTag.Allergy -> R.string.tag_allergy
    },
)

@Composable
private fun groupName(g: WellbeingGroup): String = stringResource(
    when (g) {
        WellbeingGroup.Energy -> R.string.group_energy
        WellbeingGroup.Pain -> R.string.group_pain
        WellbeingGroup.Mood -> R.string.group_mood
        WellbeingGroup.Digestion -> R.string.group_digestion
        WellbeingGroup.Other -> R.string.group_other
    },
)

/**
 * Выбор самочувствия на весь экран (Р48): лицо дня, отметки по группам, вес, заметка, поиск.
 * Всё правится в черновике; «Сохранить» записывает день.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WellbeingPicker(state: CalendarUiState, draft: DayLogDraft, actions: CalendarActions, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase(locale)
    val date = state.selected
    val title = if (date == state.today) stringResource(R.string.pick_title_today)
    else DateTimeFormatter.ofPattern(stringResource(R.string.calendar_day_pattern), locale).format(date)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = XoSpacing.s2, vertical = XoSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(XoSize.touchMin).clickable(role = Role.Button, onClick = actions.onClosePicker), contentAlignment = Alignment.Center) {
                Icon(XoIcons.Close, stringResource(R.string.pick_close), tint = colors.ink, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = type.subtitle, color = colors.ink, modifier = Modifier.semantics { heading() })
                Text(weekday, style = type.caption, color = colors.muted)
            }
            Spacer(Modifier.width(XoSize.touchMin))
        }
        SearchField(query) { query = it }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = XoSpacing.s4).padding(bottom = XoSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(XoSpacing.s3),
        ) {
            Spacer(Modifier.height(XoSpacing.headGap))
            if (q.isEmpty()) {
                Card(stringResource(R.string.pick_overall)) { Faces(draft.feel, actions.onFeel) }
                Card(stringResource(R.string.pick_frequent)) {
                    Chips(state.frequent, draft, actions.onTag)
                }
            }
            var shown = 0
            WellbeingGroup.entries.forEach { g ->
                val tags = WellbeingTag.entries.filter { it.group == g }
                    .filter { q.isEmpty() || tagNameText(it).lowercase(locale).contains(q) }
                if (tags.isEmpty()) return@forEach
                shown++
                Card(groupName(g)) { Chips(tags, draft, actions.onTag) }
            }
            if (q.isNotEmpty() && shown == 0) {
                Text(stringResource(R.string.pick_nothing_found), style = type.body, color = colors.muted, modifier = Modifier.padding(XoSpacing.s3))
            }
            if (q.isEmpty()) {
                Card(stringResource(R.string.pick_weight)) { WeightStepper(state, draft, actions) }
                Card(stringResource(R.string.pick_note)) {
                    OutlinedTextField(
                        value = draft.note,
                        onValueChange = actions.onNote,
                        placeholder = { Text(stringResource(R.string.pick_note_hint), style = type.body, color = colors.muted) },
                        textStyle = type.body,
                        minLines = 3,
                        supportingText = { Text("${draft.note.length}/${DayNote.MAX_LENGTH}", style = type.caption, color = colors.muted) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = XoSpacing.s4, vertical = XoSpacing.s3)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(XoSize.buttonHeight)
                    .clip(CircleShape)
                    .background(colors.ink)
                    .clickable(role = Role.Button, onClick = actions.onSave),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.pick_save), style = type.bodyStrong, color = colors.background)
            }
        }
    }
}

@Composable
private fun tagNameText(tag: WellbeingTag): String = tagName(tag)

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val a11y = stringResource(R.string.pick_search_a11y)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = XoSpacing.s4, vertical = XoSpacing.s2)
            .heightIn(min = XoSize.touchMin)
            .background(colors.track, CircleShape)
            .padding(horizontal = XoSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(XoIcons.Search, null, tint = colors.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(XoSpacing.s3))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(stringResource(R.string.pick_search), style = type.body, color = colors.muted)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = a11y },
            )
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Column(
        Modifier
            .fillMaxWidth()
            .border(XoSize.hairline, colors.line, RoundedCornerShape(20.dp))
            .padding(XoSpacing.s3),
    ) {
        Text(title, style = type.bodyStrong, color = colors.ink, modifier = Modifier.padding(bottom = XoSpacing.s3).semantics { heading() })
        content()
    }
}

@Composable
private fun Faces(selected: Int?, onFeel: (Int) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in Feel.MIN..Feel.MAX) {
            val on = selected == i
            val name = feelName(i)
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.RadioButton) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onFeel(i)
                    }
                    .semantics(mergeDescendants = true) { this.selected = on },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(50.dp).background(if (on) colors.accent else colors.track, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(WellbeingIcons.face(i), null, tint = if (on) colors.onAccent else colors.ink, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    name, style = if (on) type.scale.copy(fontWeight = type.bodyStrong.fontWeight) else type.scale,
                    color = if (on) colors.ink else colors.muted, textAlign = TextAlign.Center, maxLines = 2,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(tags: List<WellbeingTag>, draft: DayLogDraft, onTag: (WellbeingTag) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val haptic = LocalHapticFeedback.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(XoSpacing.s2), verticalArrangement = Arrangement.spacedBy(XoSpacing.s2)) {
        tags.forEach { t ->
            val on = t.key in draft.tags
            val name = tagName(t)
            Row(
                Modifier
                    .heightIn(min = XoSize.touchMin - 8.dp)
                    .background(if (on) colors.ink else colors.track, CircleShape)
                    .clickable(role = Role.Checkbox) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onTag(t)
                    }
                    .semantics { this.selected = on }
                    .padding(start = 6.dp, end = XoSpacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).background(if (on) colors.accent else colors.background, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(WellbeingIcons.of(t), null, tint = if (on) colors.onAccent else colors.ink, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(XoSpacing.s2))
                Text(name, style = type.body, color = if (on) colors.background else colors.ink)
            }
        }
    }
}

@Composable
private fun WeightStepper(state: CalendarUiState, draft: DayLogDraft, actions: CalendarActions) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val less = stringResource(R.string.pick_weight_less)
    val more = stringResource(R.string.pick_weight_more)
    val clear = stringResource(R.string.pick_weight_clear)
    val kg = draft.weightKg
    val lb = state.units.weight == WeightUnit.Lb
    val valueText = when {
        kg == null -> stringResource(R.string.pick_weight_none)
        lb -> formatDecimal(UnitMath.kgToLb(kg), 1)
        else -> formatDecimal(kg, 1)
    }
    val unit = if (kg == null) "" else stringResource(if (lb) R.string.unit_lb else R.string.unit_kg)
    XoStepper(
        valueText = valueText, unit = unit,
        a11y = if (kg == null) stringResource(R.string.pick_weight_none_a11y) else stringResource(R.string.stepper_a11y, stringResource(R.string.pick_weight), valueText, unit),
        minusLabel = less, plusLabel = more,
        onMinus = { actions.onWeightStep(-DayWeight.STEP_KG) }, onPlus = { actions.onWeightStep(DayWeight.STEP_KG) },
    )
    if (kg != null) {
        Box(Modifier.heightIn(min = XoSize.touchMin).clickable(role = Role.Button, onClick = actions.onWeightClear), contentAlignment = Alignment.CenterStart) {
            Text(clear, style = type.caption, color = colors.accent)
        }
    }
}

