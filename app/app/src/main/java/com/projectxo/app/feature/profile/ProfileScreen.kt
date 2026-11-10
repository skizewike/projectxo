package com.projectxo.app.feature.profile

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.model.Feel
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.ProfilePeriod
import com.projectxo.app.core.model.ProfileStats
import com.projectxo.app.core.model.Sex
import com.projectxo.app.core.model.WeightUnit
import com.projectxo.app.core.plan.BmiCategory
import com.projectxo.app.core.plan.PlanCalculator
import com.projectxo.app.core.plan.UnitMath
import com.projectxo.app.core.ui.components.XoHairline
import com.projectxo.app.core.ui.components.XoSegmented
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.formatDecimal
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * «Мой профиль» (Р50, design/settings.html): показатели и графики за неделю, месяц или всё время.
 * Правка данных профиля — отдельная страница, вход через [onEdit].
 */
@Composable
fun ProfileGraphsPage(
    state: ProfileGraphsState,
    period: ProfilePeriod,
    onPeriod: (ProfilePeriod) -> Unit,
    onEdit: () -> Unit,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val s = state.settings
    val d = state.data
    val p = s.profile
    val locale = rememberFormatLocale()
    val unit = s.units.weight

    Text(stringResource(R.string.profile_title), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
    if (p != null) {
        val sex = when (p.sex) {
            Sex.Male -> stringResource(R.string.sex_male)
            Sex.Female -> stringResource(R.string.sex_female)
            Sex.Unspecified -> null
        }
        val line = listOfNotNull(
            sex?.lowercase(locale),
            "${p.ageYears} ${stringResource(R.string.unit_years)}",
            heightText(p.heightCm, s.units.height),
        ).joinToString(" · ")
        Text(line, style = type.caption, color = colors.muted)
    }
    Spacer(Modifier.height(XoSpacing.s3))

    // Показатели.
    val weightNow = d.latestWeightKg ?: p?.weightKg
    val bmi = if (weightNow != null && p != null) PlanCalculator.bmi(weightNow, p.heightCm) else null
    XoHairline()
    Row(Modifier.fillMaxWidth().padding(vertical = XoSpacing.s3)) {
        val delta = if (d.latestWeightKg != null && d.firstWeightKg != null) d.latestWeightKg - d.firstWeightKg else null
        Kpi(
            stringResource(R.string.profile_weight), weightNow?.let { weightText(it, unit) } ?: stringResource(R.string.pick_weight_none),
            Modifier.weight(1f),
            sub = delta?.takeIf { abs(it) >= 0.05 }?.let {
                val v = formatDecimal(if (unit == WeightUnit.Kg) abs(it) else UnitMath.kgToLb(abs(it)), 1)
                stringResource(if (it < 0) R.string.profile_weight_delta_down else R.string.profile_weight_delta_up, v)
            },
        )
        Kpi(stringResource(R.string.profile_bmi), bmi?.let { formatDecimal(it, 1) } ?: stringResource(R.string.pick_weight_none), Modifier.weight(1f), sub = bmi?.let { bmiLabel(PlanCalculator.category(it)) })
        Kpi(stringResource(R.string.profile_days), formatCount(state.daysWithXo.toLong()), Modifier.weight(1f))
    }
    XoHairline()

    // Итог недели: только числа, без оценок.
    if (state.daysWithXo >= 2) state.week?.let { WeekCard(it) }

    // Изменить данные.
    Row(
        Modifier.fillMaxWidth().heightIn(min = XoSize.touchMin).clickable(role = Role.Button, onClick = onEdit).padding(vertical = XoSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(XoIcons.User, null, tint = colors.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(XoSpacing.s3))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.profile_edit), style = type.bodyStrong, color = colors.ink)
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(stringResource(R.string.profile_edit_sub), style = type.caption, color = colors.muted)
        }
        Icon(XoIcons.Chevron, null, tint = colors.muted, modifier = Modifier.size(16.dp))
    }
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s4))

    XoSegmented(
        listOf(
            ProfilePeriod.Week to stringResource(R.string.period_week),
            ProfilePeriod.Month to stringResource(R.string.period_month),
            ProfilePeriod.All to stringResource(R.string.period_all),
        ),
        period, onPeriod,
    )
    Spacer(Modifier.height(XoSpacing.s2))

    val rangeLabel = stringResource(
        when (period) { ProfilePeriod.Week -> R.string.chart_range_week; ProfilePeriod.Month -> R.string.chart_range_month; ProfilePeriod.All -> R.string.chart_range_all },
    )
    val pattern = stringResource(R.string.calendar_day_short_pattern)
    val fmt = remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
    val startLabel = if (d.dates.size <= 1) "" else fmt.format(d.dates.first())
    val endLabel = stringResource(R.string.chart_today)
    val goalSteps = state.goals.steps

    // Вес.
    val wUnit = stringResource(if (unit == WeightUnit.Kg) R.string.unit_kg else R.string.unit_lb)
    @Composable fun wv(kg: Double) = formatDecimal(if (unit == WeightUnit.Kg) kg else UnitMath.kgToLb(kg), 1)
    var wSel by remember { mutableStateOf<Int?>(null) }
    val wLast = d.weightKg.lastOrNull { it != null }
    ChartCard(
        stringResource(R.string.chart_weight), rangeLabel,
        big = wSel?.let { i -> d.weightKg.getOrNull(i)?.let { "${wv(it)} $wUnit" } } ?: wLast?.let { "${wv(it)} $wUnit" } ?: stringResource(R.string.pick_weight_none),
        sub = wSel?.let { tip(d, it) },
        a11y = wLast?.let { "${wv(it)} $wUnit" } ?: stringResource(R.string.chart_no_data),
    ) {
        DayChart(d.weightKg, ChartKind.Line, wSel, { wSel = it }, startLabel, endLabel, stringResource(R.string.chart_no_data), trend = d.weightTrend)
        Note(stringResource(if (wLast == null) R.string.chart_weight_empty else R.string.chart_weight_note))
    }

    // Шаги.
    if (Module.Steps in s.modules) {
        var sSel by remember { mutableStateOf<Int?>(null) }
        val avg = ProfileStats.average(d.steps) { it.toDouble() }
        ChartCard(
            stringResource(R.string.chart_steps), rangeLabel,
            big = sSel?.let { formatCount(d.steps[it].toLong()) } ?: avg?.let { formatCount(Math.round(it)) } ?: "",
            sub = sSel?.let { tip(d, it) } ?: stringResource(R.string.chart_avg_day),
            a11y = avg?.let { formatCount(Math.round(it)) } ?: "",
        ) {
            DayChart(d.steps.map { it.toDouble() }, ChartKind.Bars, sSel, { sSel = it }, startLabel, endLabel, stringResource(R.string.chart_no_data), goal = goalSteps.toDouble())
            Note(if (d.steps.any { it > 0 }) stringResource(R.string.chart_goal_dashed, formatCount(goalSteps)) else stringResource(R.string.chart_steps_empty))
        }
    }

    // Вода.
    if (Module.Water in s.modules) {
        var vSel by remember { mutableStateOf<Int?>(null) }
        val avg = ProfileStats.average(d.waterRatio) { minOf(1f, it).toDouble() }
        ChartCard(
            stringResource(R.string.chart_water), rangeLabel,
            big = vSel?.let { "${Math.round(d.waterRatio[it] * 100)} %" } ?: avg?.let { "${Math.round(it * 100)} %" } ?: "",
            sub = vSel?.let { tip(d, it) } ?: stringResource(R.string.chart_goal_avg),
            a11y = avg?.let { "${Math.round(it * 100)} %" } ?: "",
        ) {
            DayChart(d.waterRatio.map { it.toDouble() }, ChartKind.Bars, vSel, { vSel = it }, startLabel, endLabel, stringResource(R.string.chart_no_data), goal = 1.0)
        }
    }

    // Калории.
    if (Module.Food in s.modules) {
        var kSel by remember { mutableStateOf<Int?>(null) }
        val avg = ProfileStats.average(d.kcal) { it.toDouble() }
        ChartCard(
            stringResource(R.string.chart_kcal), rangeLabel,
            big = kSel?.let { formatCount(d.kcal[it].toLong()) } ?: avg?.let { formatCount(Math.round(it)) } ?: "",
            sub = kSel?.let { tip(d, it) } ?: stringResource(R.string.chart_avg_day),
            a11y = avg?.let { formatCount(Math.round(it)) } ?: "",
        ) {
            DayChart(d.kcal.map { it.toDouble() }, ChartKind.Bars, kSel, { kSel = it }, startLabel, endLabel, stringResource(R.string.chart_no_data), goal = state.goals.kcal.toDouble())
            Note(stringResource(R.string.chart_goal_dashed, formatCount(state.goals.kcal)))
        }
    }

    FeelCard(d.feels, rangeLabel)
    Spacer(Modifier.height(XoSpacing.s4))
}

@Composable
private fun tip(d: com.projectxo.app.core.model.ProfileData, index: Int): String {
    val ago = d.dates.size - 1 - index
    val date = d.dates.getOrNull(index)
    val locale = rememberFormatLocale()
    val pattern = stringResource(R.string.calendar_day_short_pattern)
    val text = date?.let { DateTimeFormatter.ofPattern(pattern, locale).format(it) } ?: ""
    return if (ago == 0) stringResource(R.string.chart_tip_today, text) else stringResource(R.string.chart_tip_ago, text, ago.toString())
}

@Composable
private fun ChartCard(
    title: String,
    range: String,
    big: String,
    sub: String?,
    a11y: String,
    content: @Composable () -> Unit,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val description = stringResource(R.string.chart_a11y, title, a11y)
    Column(Modifier.fillMaxWidth()) {
        XoHairline()
        Column(Modifier.padding(top = XoSpacing.s3, bottom = XoSpacing.s2)) {
            Row(Modifier.fillMaxWidth().semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = type.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
                Text(range, style = type.caption, color = colors.muted)
            }
            if (big.isNotEmpty()) Row(
                Modifier.padding(top = XoSpacing.s1).clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(big, style = type.subtitle.copy(fontSize = type.subtitle.fontSize * 1.1f), color = colors.ink)
                if (sub != null) {
                    Spacer(Modifier.width(XoSpacing.s2))
                    Text(sub, style = type.caption, color = colors.muted, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            Spacer(Modifier.height(XoSpacing.s2))
            content()
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = XoTheme.type.caption, color = XoTheme.colors.muted, modifier = Modifier.padding(top = XoSpacing.s1))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeelCard(feels: List<Int?>, rangeLabel: String) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val counts = ProfileStats.feelCounts(feels)
    val total = counts.sum()
    ChartCard(
        stringResource(R.string.chart_feel), stringResource(R.string.chart_feel_source),
        big = "", sub = null, a11y = if (total == 0) stringResource(R.string.chart_no_data) else "$total",
    ) {
        if (total == 0) {
            Note(stringResource(R.string.chart_feel_empty))
            return@ChartCard
        }
        // Полоса: доли оценок, чем лучше самочувствие, тем плотнее цвет; «отлично» — акцент.
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            counts.forEachIndexed { i, n ->
                if (n == 0) return@forEachIndexed
                Box(
                    Modifier.weight(n.toFloat()).height(12.dp)
                        .alpha(if (i == Feel.MAX) 1f else 0.25f + i * 0.18f)
                        .background(if (i == Feel.MAX) colors.accent else colors.ink),
                )
            }
        }
        Spacer(Modifier.height(XoSpacing.s2))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(XoSpacing.s3), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            counts.forEachIndexed { i, n ->
                Text(
                    stringResource(R.string.chart_feel_share, feelLabel(i), Math.round(n * 100f / total).toString()),
                    style = type.caption, color = colors.muted,
                )
            }
        }
        val (first, second) = ProfileStats.goodShareHalves(feels)
        if (first != null && second != null) {
            Note(stringResource(R.string.chart_feel_halves, Math.round(second * 100).toString(), Math.round(first * 100).toString()))
        }
    }
}

@Composable
private fun feelLabel(i: Int) = stringResource(
    when (i) { 0 -> R.string.feel_0; 1 -> R.string.feel_1; 2 -> R.string.feel_2; 3 -> R.string.feel_3; else -> R.string.feel_4 },
)

@Composable
private fun Kpi(label: String, value: String, modifier: Modifier, sub: String? = null) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Column(modifier.padding(end = XoSpacing.s2)) {
        Text(label, style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.textGap))
        Text(value, style = type.bodyStrong, color = colors.ink)
        if (sub != null) Text(sub, style = type.caption, color = colors.muted)
    }
}

@Composable
private fun weightText(kg: Double, unit: WeightUnit): String =
    if (unit == WeightUnit.Kg) "${formatDecimal(kg, 1)} ${stringResource(R.string.unit_kg)}"
    else "${formatDecimal(UnitMath.kgToLb(kg), 1)} ${stringResource(R.string.unit_lb)}"

@Composable
private fun heightText(cm: Int, unit: com.projectxo.app.core.model.HeightUnit): String =
    if (unit == com.projectxo.app.core.model.HeightUnit.Cm) "$cm ${stringResource(R.string.unit_cm)}"
    else UnitMath.cmToFtIn(cm).let { (ft, inch) -> "$ft′ $inch″" }

@Composable
private fun bmiLabel(cat: BmiCategory) = stringResource(
    when (cat) {
        BmiCategory.Under -> R.string.bmi_under; BmiCategory.Normal -> R.string.bmi_normal; BmiCategory.Over -> R.string.bmi_over
        BmiCategory.Obese1 -> R.string.bmi_obese1; BmiCategory.Obese2 -> R.string.bmi_obese2; BmiCategory.Obese3 -> R.string.bmi_obese3
    },
)


@Composable
private fun WeekCard(w: com.projectxo.app.core.model.WeekSummary) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    Spacer(Modifier.height(XoSpacing.s4))
    Text(stringResource(R.string.week_title), style = type.bodyStrong, color = colors.ink, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(XoSpacing.s2))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(w.averagePercent.toString(), style = type.display, color = colors.ink)
        Text(stringResource(R.string.percent_sign), style = type.caption, color = colors.muted, modifier = Modifier.padding(start = XoSpacing.s1, bottom = XoSpacing.s2))
        Spacer(Modifier.width(XoSpacing.s3))
        Text(stringResource(R.string.week_avg_caption), style = type.caption, color = colors.muted, modifier = Modifier.padding(bottom = XoSpacing.s2))
    }
    Text(stringResource(R.string.week_days_full, w.daysComplete.toString(), w.days.toString()), style = type.caption, color = colors.muted)
    w.bestDay?.let { d ->
        val name = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
        Text(stringResource(R.string.week_best, name, w.bestPercent.toString()), style = type.caption, color = colors.muted)
    }
    w.previousPercent?.let { Text(stringResource(R.string.week_prev, it.toString()), style = type.caption, color = colors.muted) }
    Spacer(Modifier.height(XoSpacing.s3))
    w.perModule.forEach { m ->
        val name = stringResource(com.projectxo.app.core.ui.icons.ModuleUi.nameRes(m.module))
        val a11y = stringResource(R.string.week_module_a11y, name, m.percent.toString())
        Row(Modifier.fillMaxWidth().padding(vertical = XoSpacing.s1).clearAndSetSemantics { contentDescription = a11y }, verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = type.body, color = colors.ink, modifier = Modifier.width(112.dp))
            Box(Modifier.weight(1f).height(XoSize.bar).background(colors.track)) {
                Box(Modifier.fillMaxWidth((m.percent / 100f).coerceIn(0f, 1f)).height(XoSize.bar).background(colors.ink))
            }
            Text(m.percent.toString() + " %", style = type.caption, color = colors.muted, modifier = Modifier.width(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
    Spacer(Modifier.height(XoSpacing.s3))
    XoHairline()
}
