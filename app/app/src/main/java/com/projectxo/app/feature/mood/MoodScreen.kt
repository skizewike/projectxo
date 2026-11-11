package com.projectxo.app.feature.mood

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.model.Feel
import com.projectxo.app.core.model.MoodEntry
import com.projectxo.app.core.model.MoodTag
import com.projectxo.app.core.ui.components.UndoBar
import com.projectxo.app.core.ui.components.XoHairline
import com.projectxo.app.core.ui.components.XoPrimaryButton
import com.projectxo.app.core.ui.format.timeOfDay
import com.projectxo.app.core.ui.icons.WellbeingIcons
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import java.time.Instant
import java.time.ZoneId

class MoodActions(
    val onMood: (Int) -> Unit,
    val onEnergy: (Int) -> Unit,
    val onTag: (MoodTag) -> Unit,
    val onSave: () -> Unit,
    val onDelete: (MoodEntry) -> Unit,
    val onUndo: () -> Unit,
)

private val MOOD_LABELS = listOf(R.string.mood_face_0, R.string.mood_face_1, R.string.mood_face_2, R.string.mood_face_3, R.string.mood_face_4)
private val ENERGY_LABELS = listOf(R.string.energy_0, R.string.energy_1, R.string.energy_2, R.string.energy_3, R.string.energy_4)

private fun tagRes(t: MoodTag): Int = when (t) {
    MoodTag.Sleep -> R.string.mood_tag_sleep
    MoodTag.Food -> R.string.mood_tag_food
    MoodTag.Work -> R.string.mood_tag_work
    MoodTag.Workout -> R.string.mood_tag_workout
    MoodTag.Walk -> R.string.mood_tag_walk
    MoodTag.Social -> R.string.mood_tag_social
    MoodTag.Weather -> R.string.mood_tag_weather
    MoodTag.Stress -> R.string.mood_tag_stress
}

/** «Самочувствие», вариант «Две шкалы» (Р74, design/mood-variants.html): лица, пять делений энергии, пометки, список за день. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodScreen(state: MoodUiState, actions: MoodActions, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = XoSpacing.screenGutter),
        ) {
            Spacer(Modifier.height(XoSpacing.s5))
            Text(stringResource(R.string.module_mood), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(XoSpacing.headGap))
            Text(stringResource(R.string.mood_count_today, state.entries.size.toString()), style = type.caption, color = colors.muted)

            Spacer(Modifier.height(XoSpacing.s5))
            Text(stringResource(R.string.mood_scale_mood), style = type.caption, color = colors.muted)
            Spacer(Modifier.height(XoSpacing.s2))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XoSpacing.s1)) {
                for (i in Feel.MIN..Feel.MAX) {
                    val on = state.mood == i
                    val label = stringResource(MOOD_LABELS[i])
                    Column(
                        Modifier
                            .weight(1f)
                            .border(if (on) 2.dp else XoSize.hairline, if (on) colors.ink else colors.line, RoundedCornerShape(14.dp))
                            .clickable(role = Role.RadioButton) { actions.onMood(i) }
                            .semantics { selected = on; contentDescription = label }
                            .padding(vertical = XoSpacing.s2),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(WellbeingIcons.face(i), null, tint = if (on) colors.ink else colors.muted, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(XoSpacing.textGap))
                        Text(label, style = type.scale, color = if (on) colors.ink else colors.muted, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(Modifier.height(XoSpacing.s5))
            Text(stringResource(R.string.mood_scale_energy), style = type.caption, color = colors.muted)
            Spacer(Modifier.height(XoSpacing.s2))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XoSpacing.s1)) {
                for (i in Feel.MIN..Feel.MAX) {
                    val on = state.energy == i
                    val label = stringResource(ENERGY_LABELS[i])
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = XoSize.touchMin)
                            .border(if (on) 2.dp else XoSize.hairline, if (on) colors.ink else colors.line, RoundedCornerShape(12.dp))
                            .clickable(role = Role.RadioButton) { actions.onEnergy(i) }
                            .semantics { selected = on; contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) { Text((i + 1).toString(), style = type.bodyStrong, color = if (on) colors.ink else colors.muted) }
                }
            }
            Spacer(Modifier.height(XoSpacing.textGap))
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(ENERGY_LABELS[Feel.MIN]), style = type.scale, color = colors.muted, modifier = Modifier.weight(1f))
                Text(stringResource(ENERGY_LABELS[Feel.MAX]), style = type.scale, color = colors.muted)
            }

            Spacer(Modifier.height(XoSpacing.s5))
            Text(stringResource(R.string.mood_tags_title), style = type.caption, color = colors.muted)
            Spacer(Modifier.height(XoSpacing.s2))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(XoSpacing.s2), verticalArrangement = Arrangement.spacedBy(XoSpacing.s2)) {
                MoodTag.entries.forEach { t ->
                    val on = t in state.tags
                    Box(
                        Modifier
                            .heightIn(min = XoSize.touchMin)
                            .border(if (on) 2.dp else XoSize.hairline, if (on) colors.ink else colors.line, CircleShape)
                            .clickable(role = Role.Checkbox) { actions.onTag(t) }
                            .semantics { selected = on }
                            .padding(horizontal = XoSpacing.s4),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(tagRes(t)), style = type.body, color = if (on) colors.ink else colors.muted) }
                }
            }

            Spacer(Modifier.height(XoSpacing.s5))
            XoPrimaryButton(stringResource(R.string.mood_save), actions.onSave, enabled = state.canSave)

            if (state.entries.isNotEmpty()) {
                Spacer(Modifier.height(XoSpacing.s5))
                Text(stringResource(R.string.mood_today_list), style = type.caption, color = colors.muted, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(XoSpacing.s2))
                XoHairline()
                state.entries.forEach { e -> EntryRow(e, actions) }
            }
            Spacer(Modifier.height(XoSpacing.s8))
        }
        UndoBar(
            visible = state.pendingDelete != null,
            message = stringResource(R.string.undo_deleted),
            action = stringResource(R.string.undo_restore),
            onAction = actions.onUndo,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun EntryRow(e: MoodEntry, actions: MoodActions) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val time = Instant.ofEpochMilli(e.timestampMs).atZone(ZoneId.systemDefault()).toLocalTime().let { timeOfDay(it.hour * 60 + it.minute) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = XoSize.rowHeight).padding(vertical = XoSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = type.caption, color = colors.muted, modifier = Modifier.width(52.dp))
        Icon(WellbeingIcons.face(e.mood), null, tint = colors.ink, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(XoSpacing.s3))
        Column(Modifier.weight(1f)) {
            Text(stringResource(MOOD_LABELS[e.mood]), style = type.bodyStrong, color = colors.ink)
            val energy = stringResource(R.string.mood_entry_energy, stringResource(ENERGY_LABELS[e.energy]).lowercase())
            val tags = e.tags.sortedBy { it.ordinal }.map { stringResource(tagRes(it)) }
            Text((listOf(energy) + tags).joinToString(" · "), style = type.caption, color = colors.muted)
        }
        Box(
            Modifier.heightIn(min = XoSize.touchMin).clickable(role = Role.Button) { actions.onDelete(e) }.padding(horizontal = XoSpacing.s2),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.mood_delete), style = type.caption, color = colors.muted) }
    }
    XoHairline()
}
