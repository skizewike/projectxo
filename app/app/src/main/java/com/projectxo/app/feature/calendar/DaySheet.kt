package com.projectxo.app.feature.calendar

import com.projectxo.app.core.ui.format.formatVolumeNumber
import com.projectxo.app.core.ui.format.formatVolume
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.data.FoodEntry
import com.projectxo.app.core.data.WaterEntry
import com.projectxo.app.core.data.DayLog
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.WeightUnit
import com.projectxo.app.core.plan.UnitMath
import com.projectxo.app.core.ui.components.MiniRing
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.formatDecimal
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.icons.WellbeingIcons
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Лист выбранного дня: баланс, модули, самочувствие, записи. Тянется вверх за ручку. */
@Composable
internal fun DaySheet(
    state: CalendarUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val detail = state.detail
    Column(modifier.fillMaxWidth().sheetSurface()) {
        val toggleLabel = stringResource(if (expanded) R.string.calendar_sheet_collapse else R.string.calendar_sheet_expand)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = XoSize.touchMin)
                .clickable(role = Role.Button, onClick = onToggle)
                .clearAndSetSemantics { contentDescription = toggleLabel; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(36.dp).height(4.dp).background(colors.line, CircleShape))
        }
        if (detail == null) return@Column
        Box(Modifier.padding(horizontal = XoSpacing.s5)) { SheetHeader(state, detail) }
        Column(Modifier.weight(1f, fill = true).verticalScroll(rememberScrollState()).padding(horizontal = XoSpacing.s5)) {
            Modules(state, detail)
            Wellbeing(state, detail.log, onEdit)
            Records(state, detail)
            Spacer(Modifier.height(XoSpacing.s7))
        }
    }
}

@Composable
private fun SheetHeader(state: CalendarUiState, detail: DayDetail) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    val datePattern = stringResource(R.string.calendar_day_short_pattern)
    val dateText = remember(detail.date, datePattern, locale) { DateTimeFormatter.ofPattern(datePattern, locale).format(detail.date) }
    val weekday = detail.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
    val title = if (detail.date == state.today) stringResource(R.string.day_title_today, dateText)
    else stringResource(R.string.day_title, dateText, weekday)
    val dayNo = java.time.temporal.ChronoUnit.DAYS.between(state.start, detail.date).toInt() + 1
    val sub = if (dayNo <= 1) stringResource(R.string.day_first) else stringResource(R.string.day_number, formatCount(dayNo.toLong()))
    val percent = Math.round(detail.balance * 100)
    val balanceA11y = stringResource(R.string.day_balance_a11y, percent.toString())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = type.subtitle, color = colors.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(sub, style = type.caption, color = colors.muted)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clearAndSetSemantics { contentDescription = balanceA11y },
        ) {
            Text("$percent %", style = type.caption, color = colors.muted)
            Spacer(Modifier.width(XoSpacing.s2))
            MiniRing(detail.balance, 36.dp, 2.2.dp)
        }
    }
}

@Composable
private fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Row(
        Modifier.fillMaxWidth().padding(top = XoSpacing.s4, bottom = XoSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = type.caption, color = colors.muted, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null) {
            Box(Modifier.heightIn(min = XoSize.touchMin).clickable(role = Role.Button, onClick = onAction).padding(horizontal = XoSpacing.s2), contentAlignment = Alignment.Center) {
                Text(action, style = type.bodyStrong.copy(fontSize = type.caption.fontSize), color = colors.accent)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Modules(state: CalendarUiState, detail: DayDetail) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    SectionTitle(stringResource(R.string.day_modules))
    val rows = com.projectxo.app.core.model.BalanceMath.progress(detail.totals, detail.goals, state.modules, state.order)
    val hairline = with(LocalDensity.current) { XoSize.hairline.toPx() }
    rows.forEachIndexed { i, p ->
        val icon = com.projectxo.app.core.ui.icons.ModuleUi.icon(p.module)
        val name = stringResource(com.projectxo.app.core.ui.icons.ModuleUi.nameRes(p.module))
        val ofText = com.projectxo.app.core.ui.format.moduleGoalText(p)
        val valueText = com.projectxo.app.core.ui.format.moduleValueText(p)
        val a11y = stringResource(R.string.module_row_a11y, name, valueText, ofText)
        val fraction = p.fraction
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(colors.line, size = Size(size.width, hairline))
                    if (i == rows.lastIndex) drawRect(colors.line, Offset(0f, size.height - hairline), Size(size.width, hairline))
                }
                .padding(vertical = XoSpacing.s2)
                .clearAndSetSemantics { contentDescription = a11y },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = colors.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(XoSpacing.s3))
            Column(Modifier.weight(1f)) {
                // FlowRow: при крупном тексте значение переходит на следующую строку, а не наезжает на название.
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = type.body, color = colors.ink)
                    Row {
                        Text(valueText, style = type.bodyStrong, color = colors.ink)
                        Spacer(Modifier.width(XoSpacing.s1))
                        Text(ofText, style = type.caption, color = colors.muted, modifier = Modifier.align(Alignment.Bottom))
                    }
                }
                Spacer(Modifier.height(XoSpacing.headGap))
                Box(Modifier.fillMaxWidth().height(XoSize.bar).background(colors.track)) {
                    Box(Modifier.fillMaxWidth(fraction).height(XoSize.bar).background(colors.ink))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Wellbeing(state: CalendarUiState, log: DayLog?, onEdit: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val empty = log == null || log.isEmpty
    SectionTitle(stringResource(R.string.day_wellbeing), if (empty) null else stringResource(R.string.day_edit), onEdit)
    if (log == null || empty) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.track)
                .clickable(role = Role.Button, onClick = onEdit)
                .padding(start = XoSpacing.s4, top = XoSpacing.s2, bottom = XoSpacing.s2, end = XoSpacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.day_add_prompt), style = type.body, color = colors.muted, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(XoIcons.Plus, null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
            }
        }
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(XoSpacing.s2), verticalArrangement = Arrangement.spacedBy(XoSpacing.s2)) {
        log.feel?.let { f -> StateChip(WellbeingIcons.face(f), feelName(f)) }
        com.projectxo.app.core.model.WellbeingTag.entries.filter { it.key in log.tags }.forEach { t ->
            StateChip(WellbeingIcons.of(t), tagName(t))
        }
        log.weightKg?.let { StateChip(XoIcons.Scale, weightText(it, state.units.weight)) }
    }
    if (log.note.isNotBlank()) {
        Row(Modifier.padding(top = XoSpacing.s3), verticalAlignment = Alignment.Top) {
            Icon(XoIcons.Note, null, tint = colors.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(XoSpacing.s3))
            Text(log.note, style = type.body, color = colors.ink)
        }
    }
}

@Composable
internal fun StateChip(icon: ImageVector, label: String) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .background(colors.track, CircleShape)
            .padding(start = 6.dp, end = XoSpacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).background(colors.background, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = colors.ink, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(XoSpacing.s2))
        Text(label, style = type.body, color = colors.ink)
    }
}

@Composable
internal fun weightText(kg: Double, unit: WeightUnit): String =
    if (unit == WeightUnit.Kg) stringResource(R.string.day_weight_kg, formatDecimal(kg, 1))
    else stringResource(R.string.day_weight_lb, formatDecimal(UnitMath.kgToLb(kg), 1))

@Composable
private fun Records(state: CalendarUiState, detail: DayDetail) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    val pattern = stringResource(R.string.time_pattern)
    val formatter = remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
    val zone = remember { ZoneId.systemDefault() }
    data class Rec(val ts: Long, val icon: ImageVector, val name: String, val value: String)
    val water = detail.waterEntries.map { e: WaterEntry ->
        Rec(e.timestampMs, XoIcons.Water, stringResource(R.string.day_record_water), formatVolume(e.ml.toLong()))
    }
    val food = detail.foodEntries.map { e: FoodEntry ->
        Rec(e.timestampMs, XoIcons.Food, e.name, stringResource(R.string.plan_kcal_value, formatCount(e.kcal.toLong())))
    }
    val all = (water + food).sortedBy { it.ts }
    SectionTitle(stringResource(R.string.day_records))
    if (all.isEmpty()) {
        Text(
            stringResource(if (detail.date == state.today) R.string.day_records_empty_today else R.string.day_records_empty),
            style = type.caption, color = colors.muted,
        )
        return
    }
    val hairline = with(LocalDensity.current) { XoSize.hairline.toPx() }
    all.forEach { r ->
        val time = formatter.format(Instant.ofEpochMilli(r.ts).atZone(zone))
        Row(
            Modifier.fillMaxWidth().drawBehind { drawRect(colors.line, size = Size(size.width, hairline)) }.padding(vertical = XoSpacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, style = type.caption, color = colors.muted, modifier = Modifier.width(44.dp))
            Icon(r.icon, null, tint = colors.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(XoSpacing.s2))
            Text(r.name, style = type.body, color = colors.ink, modifier = Modifier.weight(1f), maxLines = 1)
            Text(r.value, style = type.bodyStrong, color = colors.ink)
        }
    }
}
