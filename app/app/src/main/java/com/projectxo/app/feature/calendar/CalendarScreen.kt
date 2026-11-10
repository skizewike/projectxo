package com.projectxo.app.feature.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.ui.components.MiniRing
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import com.projectxo.app.core.model.WellbeingTag
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Что экран календаря может попросить у модели. */
class CalendarActions(
    val onClose: () -> Unit,
    val onSelect: (LocalDate) -> Unit,
    val onMode: (CalendarMode) -> Unit,
    val onOpenPicker: () -> Unit,
    val onClosePicker: () -> Unit,
    val onFeel: (Int) -> Unit,
    val onTag: (WellbeingTag) -> Unit,
    val onWeightStep: (Double) -> Unit,
    val onWeightClear: () -> Unit,
    val onNote: (String) -> Unit,
    val onSave: () -> Unit,
)

/**
 * Календарь (Р31, Р48; design/calendar.html). Сверху месяц или год с кольцами «Баланса дня»,
 * внизу лист выбранного дня: модули, самочувствие, записи. «+» открывает выбор самочувствия.
 */
@Composable
fun CalendarScreen(state: CalendarUiState, actions: CalendarActions, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current

    BoxWithConstraints(modifier.fillMaxSize().background(colors.background)) {
        val collapsedH = maxHeight * SHEET_COLLAPSED
        val fullH = maxHeight * SHEET_FULL
        val target = if (expanded) fullH else collapsedH
        val sheetH by animateDpAsState(
            target, if (reduced) tween(0) else tween(XoMotion.SCREEN_MS, easing = XoMotion.Standard), label = "sheet",
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            TopBar(state.mode, actions.onClose, actions.onMode)
            when (state.mode) {
                CalendarMode.Month -> MonthList(state, collapsedH, actions.onSelect)
                CalendarMode.Year -> YearGrid(state, collapsedH) { ym ->
                    val last = ym.atEndOfMonth()
                    val pick = if (state.today.year == ym.year && state.today.month == ym.month) state.today else last
                    actions.onMode(CalendarMode.Month)
                    actions.onSelect(pick)
                }
            }
        }

        DaySheet(
            state = state,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            onEdit = actions.onOpenPicker,
            modifier = Modifier.align(Alignment.BottomCenter).height(sheetH),
        )

        AnimatedVisibility(
            visible = state.draft != null,
            enter = if (reduced) fadeIn(tween(XoMotion.MICRO_MS)) else slideInVertically(tween(XoMotion.SCREEN_MS, easing = XoMotion.Standard)) { it },
            exit = if (reduced) fadeOut(tween(XoMotion.MICRO_MS)) else slideOutVertically(tween(XoMotion.SCREEN_MS, easing = XoMotion.Standard)) { it },
        ) {
            val draft = state.draft
            if (draft != null) WellbeingPicker(state, draft, actions)
        }
    }
}

@Composable
private fun TopBar(mode: CalendarMode, onClose: () -> Unit, onMode: (CalendarMode) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XoSpacing.s2, vertical = XoSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(XoSize.touchMin).clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(XoIcons.Close, stringResource(R.string.calendar_close), tint = colors.ink, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(CircleShape).background(colors.track).padding(3.dp),
        ) {
            listOf(CalendarMode.Month to R.string.calendar_mode_month, CalendarMode.Year to R.string.calendar_mode_year).forEach { (m, label) ->
                val on = m == mode
                Box(
                    Modifier
                        .heightIn(min = XoSize.touchMin - 6.dp)
                        .clip(CircleShape)
                        .background(if (on) colors.background else Color.Transparent)
                        .clickable(role = Role.Tab) { onMode(m) }
                        .semantics { selected = on }
                        .padding(horizontal = XoSpacing.s5),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(label), style = if (on) type.bodyStrong else type.body, color = if (on) colors.ink else colors.muted)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(XoSize.touchMin))
    }
}

@Composable
private fun WeekdayHeader() {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    Row(Modifier.fillMaxWidth().padding(horizontal = XoSpacing.s3).padding(bottom = XoSpacing.s1)) {
        DayOfWeek.entries.forEach { d ->
            Text(
                d.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                style = type.scale, color = colors.muted, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(XoSize.hairline).background(colors.line))
}

@Composable
private fun MonthList(state: CalendarUiState, bottomInset: androidx.compose.ui.unit.Dp, onSelect: (LocalDate) -> Unit) {
    val locale = rememberFormatLocale()
    val months = remember(state.start, state.today) {
        generateSequence(YearMonth.from(state.start)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(state.today) }.toList()
    }
    val startIndex = remember { months.indexOf(YearMonth.from(state.selected)).coerceAtLeast(0) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    WeekdayHeader()
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().padding(horizontal = XoSpacing.s3),
        contentPadding = PaddingValues(bottom = bottomInset),
    ) {
        itemsIndexed(months, key = { _, ym -> ym.toString() }) { _, ym ->
            Month(ym, state, locale, onSelect)
        }
    }
}

@Composable
private fun Month(ym: YearMonth, state: CalendarUiState, locale: Locale, onSelect: (LocalDate) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val title = ym.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } +
        if (ym.year != state.today.year) " ${ym.year}" else ""
    Column(Modifier.fillMaxWidth()) {
        Text(
            title, style = type.subtitle.copy(fontSize = type.body.fontSize * 1.15f), color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = XoSpacing.s4 + XoSpacing.s1, bottom = XoSpacing.s2).semantics { heading() },
        )
        val firstOffset = ym.atDay(1).dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(firstOffset) { null } + (1..ym.lengthOfMonth()).map { ym.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                week.forEach { d -> DayCell(d, state, onSelect, Modifier.weight(1f)) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate?, state: CalendarUiState, onSelect: (LocalDate) -> Unit, modifier: Modifier) {
    if (date == null) {
        Spacer(modifier)
        return
    }
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    val ok = state.isSelectable(date)
    val sel = date == state.selected
    val isToday = date == state.today
    val balance = state.balances[date] ?: 0f
    val pattern = stringResource(R.string.calendar_day_pattern)
    val label = remember(date, pattern, locale) { java.time.format.DateTimeFormatter.ofPattern(pattern, locale).format(date) }
    val percent = Math.round(balance * 100)
    val logged = date in state.logged
    val a11y = when {
        !ok -> stringResource(R.string.calendar_day_off_a11y, label)
        logged -> stringResource(R.string.calendar_day_logged_a11y, label, percent.toString())
        else -> stringResource(R.string.calendar_day_a11y, label, percent.toString())
    }
    Box(
        modifier
            .height(DAY_CELL_HEIGHT)
            .then(if (ok) Modifier.clickable(role = Role.Button) { onSelect(date) } else Modifier)
            .clearAndSetSemantics { contentDescription = a11y; role = Role.Button; this.selected = sel },
        contentAlignment = Alignment.Center,
    ) {
        if (sel) Box(Modifier.size(DAY_SELECT).background(colors.ink, CircleShape))
        if (ok) MiniRing(balance, DAY_RING, 2.2.dp, onInk = sel)
        Text(
            date.dayOfMonth.toString(),
            style = if (isToday || sel) type.bodyStrong else type.body,
            color = when {
                sel -> colors.background
                ok -> colors.ink
                else -> colors.muted
            },
            modifier = Modifier.alpha(if (ok) 1f else 0.5f),
        )
        if (isToday && !sel) {
            // Метка «СЕГОДНЯ» не растёт с системным текстом: она над числом и иначе наезжает на него.
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1f),
            ) {
                Text(
                    stringResource(R.string.calendar_today_tag), style = type.scale.copy(fontSize = type.scale.fontSize * 0.82f),
                    color = colors.ink, maxLines = 1, softWrap = false,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
        if (logged && !sel) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(4.dp).background(colors.accent, CircleShape))
    }
}

@Composable
private fun YearGrid(state: CalendarUiState, bottomInset: androidx.compose.ui.unit.Dp, onMonth: (YearMonth) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    val year = state.today.year
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = XoSpacing.s4, vertical = XoSpacing.s3),
        verticalArrangement = Arrangement.spacedBy(XoSpacing.s4),
    ) {
        (1..12).toList().chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XoSpacing.s3)) {
                row.forEach { m ->
                    val ym = YearMonth.of(year, m)
                    val name = ym.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
                    val enabled = !ym.isAfter(YearMonth.from(state.today)) && !ym.atEndOfMonth().isBefore(state.start)
                    Column(
                        Modifier
                            .weight(1f)
                            .then(if (enabled) Modifier.clickable(role = Role.Button) { onMonth(ym) } else Modifier)
                            .semantics(mergeDescendants = true) { contentDescription = name },
                    ) {
                        Text(
                            name, style = type.caption.copy(fontWeight = type.bodyStrong.fontWeight),
                            color = if (ym == YearMonth.from(state.today)) colors.accent else colors.ink,
                            modifier = Modifier.padding(bottom = XoSpacing.s1),
                        )
                        val offset = ym.atDay(1).dayOfWeek.value - 1
                        val cells: List<LocalDate?> = List(offset) { null } + (1..ym.lengthOfMonth()).map { ym.atDay(it) }
                        cells.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                week.forEach { d ->
                                    val dot = Modifier.weight(1f).aspectRatio(1f)
                                    when {
                                        d == null -> Spacer(dot)
                                        d.isAfter(state.today) -> Box(dot.alpha(0.35f).background(colors.track, CircleShape))
                                        !state.isSelectable(d) -> Box(dot.background(colors.track, CircleShape))
                                        else -> {
                                            val p = state.balances[d] ?: 0f
                                            Box(
                                                dot.background(colors.track, CircleShape)
                                                    .background((if (p >= 1f) colors.accent else colors.ink).copy(alpha = 0.15f + p * 0.85f).takeIf { p > 0f } ?: Color.Transparent, CircleShape),
                                            )
                                        }
                                    }
                                }
                                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(bottomInset))
    }
}

private const val SHEET_COLLAPSED = 0.46f
private const val SHEET_FULL = 0.82f
private val DAY_CELL_HEIGHT = 50.dp
private val DAY_RING = 40.dp
private val DAY_SELECT = 44.dp

/** Тонкая рамка листа дня: скруглённый верх. */
internal fun sheetShape() = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

@Composable
internal fun Modifier.sheetSurface(): Modifier {
    val colors = XoTheme.colors
    return this
        .clip(sheetShape())
        .background(colors.background)
        .border(XoSize.hairline, colors.line, sheetShape())
}
