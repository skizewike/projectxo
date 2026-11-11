package com.projectxo.app.feature.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.projectxo.app.R
import com.projectxo.app.core.model.Exercise
import com.projectxo.app.core.model.ExerciseGroup
import com.projectxo.app.core.ui.figure.ExerciseFigure
import com.projectxo.app.core.ui.figure.FigureMode
import com.projectxo.app.core.model.PushupVariant
import com.projectxo.app.core.model.WorkoutCounting
import com.projectxo.app.core.model.WorkoutMath
import com.projectxo.app.core.model.WorkoutRating
import com.projectxo.app.core.ui.components.RollingNumber
import com.projectxo.app.core.ui.components.XoHairline
import com.projectxo.app.core.ui.components.XoPrimaryButton
import com.projectxo.app.core.ui.components.XoSegmented
import com.projectxo.app.core.ui.components.XoTextButton
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.motion.pressScale
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

class WorkoutActions(
    val onOpen: (Exercise) -> Unit,
    val onCheck: (Int) -> Unit,
    val onConfirmLevel: () -> Unit,
    val onTap: () -> Unit,
    val onAdjust: (Int) -> Unit,
    val onFinishSet: () -> Unit,
    val onAddRest: () -> Unit,
    val onEndRest: () -> Unit,
    val onAbort: () -> Unit,
    val onRate: (Int) -> Unit,
    val onBack: () -> Unit,
    val onUntap: () -> Unit = {},
    val onStartHold: () -> Unit = {},
    val onStopHold: () -> Unit = {},
    val onCounting: (WorkoutCounting) -> Unit = {},
    val onTimer: (com.projectxo.app.core.model.WorkoutTimerStyle) -> Unit = {},
    val onDays: (Int) -> Unit = {},
)

@Composable
private fun exerciseName(e: Exercise): String = stringResource(ExerciseText.nameRes(e))

/** Уровень в плитке: «20 раз» или «40 с». */
@Composable
private fun unitShort(e: Exercise, n: Int): String =
    if (e.seconds) stringResource(R.string.ex_unit_sec, formatCount(n.toLong())) else stringResource(R.string.workout_tile_reps, formatCount(n.toLong()))

@Composable
private fun unitText(e: Exercise, n: Int): String =
    if (e.seconds) stringResource(R.string.ex_unit_sec, formatCount(n.toLong())) else formatCount(n.toLong())

/** Текущее время телефона: каждый кадр, а при «меньше анимаций» четыре раза в секунду. Только пока [active]. */
@Composable
private fun rememberNowMs(active: Boolean, key: Any? = null): State<Long> {
    val reduced = LocalReducedMotion.current
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active, key, reduced) {
        if (!active) return@LaunchedEffect
        while (true) {
            now.longValue = System.currentTimeMillis()
            if (reduced) delay(250) else androidx.compose.runtime.withFrameNanos { }
        }
    }
    return now
}

/** Домашняя тренировка, экран подходов «Лесенка» (Р47): цель подхода крупно, «−» и «+», если вышло иначе. */
@Composable
fun WorkoutScreen(state: WorkoutUiState, actions: WorkoutActions, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    // Экран не гаснет во время подходов и отдыха.
    val view = LocalView.current
    val active = state.step is WorkoutStep.Sets
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
    var confirmAbort by remember { mutableStateOf(false) }
    val started = when (val st = state.step) {
        is WorkoutStep.Sets -> st.actual.isNotEmpty() || st.current > 0
        is WorkoutStep.Rate -> true
        else -> false
    }

    // Новый шаг открывается сверху, а не с места, где остановилась прокрутка списка.
    val scroll = rememberScrollState()
    LaunchedEffect(state.step::class) { scroll.scrollTo(0) }
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(scroll)
            .padding(horizontal = XoSpacing.screenGutter),
    ) {
        if (state.step is WorkoutStep.Overview) {
            Spacer(Modifier.height(XoSpacing.s5))
        } else {
            Row(Modifier.padding(top = XoSpacing.s2), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.heightIn(min = XoSize.touchMin).clickable(role = Role.Button) {
                        when {
                            state.step is WorkoutStep.Overview || state.step is WorkoutStep.Done -> actions.onBack()
                            started -> confirmAbort = true
                            else -> actions.onAbort()
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(XoIcons.Back, null, tint = colors.muted, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(
                            when (state.step) {
                                is WorkoutStep.Overview, is WorkoutStep.Done -> R.string.module_workouts
                                is WorkoutStep.CheckLevel -> R.string.back
                                else -> R.string.workout_stop
                            },
                        ),
                        style = type.caption, color = colors.muted,
                    )
                }
            }
        }
        if (state.step !is WorkoutStep.Overview) Spacer(Modifier.height(XoSpacing.s2))
        // Смена шагов: короткое появление с лёгким подъёмом; при «меньше анимаций» шаги сменяются сразу.
        val rise = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.roundToPx() }
        androidx.compose.runtime.CompositionLocalProvider(LocalTimerStyle provides state.timer) {
        AnimatedContent(
            targetState = state.step,
            contentKey = { it::class },
            transitionSpec = {
                if (reduced) EnterTransition.None togetherWith ExitTransition.None
                else (fadeIn(tween(XoMotion.STATE_MS, easing = XoMotion.Standard)) + slideInVertically(tween(XoMotion.STATE_MS, easing = XoMotion.Standard)) { rise }) togetherWith
                    fadeOut(tween(XoMotion.MICRO_MS))
            },
            label = "workoutStep",
        ) { step ->
            Column {
                when (step) {
                    WorkoutStep.Overview -> Overview(state, actions)
                    is WorkoutStep.CheckLevel -> CheckLevel(step, actions)
                    is WorkoutStep.Sets -> Sets(step, state.counting, actions)
                    is WorkoutStep.Rate -> RateStep(step, actions)
                    is WorkoutStep.Done -> DoneStep(step, actions)
                }
            }
        }
        }
        Spacer(Modifier.height(XoSpacing.s7))
    }

    if (confirmAbort) {
        ConfirmStop(onContinue = { confirmAbort = false }, onStop = { confirmAbort = false; actions.onAbort() })
    }
}

@Composable
private fun ConfirmStop(onContinue: () -> Unit, onStop: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Dialog(onDismissRequest = onContinue) {
        Column(Modifier.background(colors.background, RoundedCornerShape(24.dp)).border(XoSize.hairline, colors.line, RoundedCornerShape(24.dp)).padding(XoSpacing.s5)) {
            Text(stringResource(R.string.workout_abort_title), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(XoSpacing.s2))
            Text(stringResource(R.string.workout_abort_body), style = type.body, color = colors.muted)
            Spacer(Modifier.height(XoSpacing.s4))
            XoPrimaryButton(stringResource(R.string.workout_abort_continue), onContinue)
            XoTextButton(stringResource(R.string.workout_abort_confirm), onStop)
        }
    }
}

@Composable
private fun Overview(state: WorkoutUiState, actions: WorkoutActions) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    val datePattern = stringResource(R.string.calendar_day_short_pattern)
    val dateFormat = remember(datePattern, locale) { DateTimeFormatter.ofPattern(datePattern, locale) }
    Text(stringResource(R.string.workout_title), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(XoSpacing.textGap))
    Text(stringResource(R.string.workout_lead), style = type.caption, color = colors.muted)
    if (state.doneToday > 0) {
        Spacer(Modifier.height(XoSpacing.headGap))
        Text(stringResource(R.string.workout_today_done, state.doneToday.toString()), style = type.caption, color = colors.ink)
    }
    Spacer(Modifier.height(XoSpacing.s4))
    XoHairline()
    val suggested = state.suggested
    val mine = Exercise.entries.filter { state.level(it) != null }
    if (mine.isNotEmpty()) {
        GroupHeader(stringResource(R.string.workout_my_program), mine.size, topGap = XoSpacing.s5)
        TileGrid(mine, state, suggested, mine = true, actions)
    }
    ExerciseGroup.entries.forEach { g ->
        val items = Exercise.of(g)
        GroupHeader(stringResource(ExerciseText.groupRes(g)), items.size, topGap = XoSpacing.s6)
        TileGrid(items, state, if (mine.isEmpty()) suggested else null, mine = false, actions)
    }
    val recent = state.sessions.take(RECENT_SHOWN)
    if (recent.isNotEmpty()) {
        Spacer(Modifier.height(XoSpacing.s5))
        Text(stringResource(R.string.workout_history), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.headGap))
        recent.forEach { r ->
            val ex = Exercise.fromId(r.exercise)
            Row(Modifier.fillMaxWidth().padding(vertical = XoSpacing.s1)) {
                Text(
                    dateFormat.format(r.date) + " · " + (ex?.let { exerciseName(it) } ?: ""),
                    style = type.body, color = colors.ink, modifier = Modifier.weight(1f),
                )
                Text(r.actual.joinToString(" · "), style = type.caption, color = colors.muted)
            }
        }
    }
    Spacer(Modifier.height(XoSpacing.s5))
    WorkoutSettings(state, actions)
    Spacer(Modifier.height(XoSpacing.s4))
    Text(stringResource(R.string.workout_safety), style = type.caption, color = colors.muted)
}

/** Дни плана, способ счёта подходов и вид таймера (раньше жили в настройках «Привычек»). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WorkoutSettings(state: WorkoutUiState, actions: WorkoutActions) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    Text(stringResource(R.string.workout_settings), style = type.caption, color = colors.muted, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(XoSpacing.s2))
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s3))
    Text(stringResource(R.string.workout_days), style = type.bodyStrong, color = colors.ink)
    Spacer(Modifier.height(XoSpacing.s2))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(XoSpacing.s1), verticalArrangement = Arrangement.spacedBy(XoSpacing.s1),
    ) {
        java.time.DayOfWeek.entries.forEach { d ->
            val bit = com.projectxo.app.core.model.HabitMath.dayBit(d)
            val on = state.days and bit != 0
            Box(
                Modifier
                    .size(44.dp)
                    .border(if (on) 2.dp else XoSize.hairline, if (on) colors.ink else colors.line, CircleShape)
                    .clickable(role = Role.Checkbox) { actions.onDays(state.days xor bit) }
                    .semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) { Text(d.getDisplayName(java.time.format.TextStyle.SHORT, locale), style = type.scale, color = colors.ink) }
        }
    }
    Spacer(Modifier.height(XoSpacing.s4))
    Text(stringResource(R.string.habits_counting), style = type.bodyStrong, color = colors.ink)
    Spacer(Modifier.height(XoSpacing.s2))
    XoSegmented(
        listOf(WorkoutCounting.Ladder to stringResource(R.string.habits_counting_ladder), WorkoutCounting.Tap to stringResource(R.string.habits_counting_tap)),
        state.counting, actions.onCounting,
    )
    Spacer(Modifier.height(XoSpacing.s4))
    Text(stringResource(R.string.habits_timer), style = type.bodyStrong, color = colors.ink)
    Spacer(Modifier.height(XoSpacing.s2))
    XoSegmented(
        listOf(
            com.projectxo.app.core.model.WorkoutTimerStyle.Ring to stringResource(R.string.habits_timer_ring),
            com.projectxo.app.core.model.WorkoutTimerStyle.Bar to stringResource(R.string.habits_timer_bar),
            com.projectxo.app.core.model.WorkoutTimerStyle.Ticks to stringResource(R.string.habits_timer_ticks),
        ),
        state.timer, actions.onTimer,
    )
}


/** Заголовок группы: название слева, число упражнений справа. */
@Composable
private fun GroupHeader(title: String, count: Int, topGap: androidx.compose.ui.unit.Dp) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Spacer(Modifier.height(topGap))
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.Bottom) {
        Text(title, style = type.caption, color = colors.muted, modifier = Modifier.weight(1f))
        Text(formatCount(count.toLong()), style = type.caption, color = colors.muted)
    }
    Spacer(Modifier.height(XoSpacing.s2))
}

/** Плитки по две в ряд; в каждом ряду плитки одной высоты. */
@Composable
private fun TileGrid(items: List<Exercise>, state: WorkoutUiState, suggested: Exercise?, mine: Boolean, actions: WorkoutActions) {
    // Крупный системный текст: по одной плитке в ряд, иначе названия рвутся посреди слова.
    val perRow = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > LARGE_TEXT_SCALE) 1 else TILES_PER_ROW
    Column(verticalArrangement = Arrangement.spacedBy(XoSpacing.s2)) {
        items.chunked(perRow).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(XoSpacing.s2)) {
                pair.forEach { e ->
                    ExerciseTile(e, state, e == suggested, mine, actions, Modifier.weight(1f).fillMaxHeight())
                }
                if (pair.size < perRow) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Плитка упражнения: фигура, название (до двух строк) и одна строка пояснения.
 * Начатое упражнение: уровень («20 раз», «40 с»), у отжиманий ещё вариант. Новое: «новое · повторы».
 */
@Composable
private fun ExerciseTile(e: Exercise, state: WorkoutUiState, suggested: Boolean, mine: Boolean, actions: WorkoutActions, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val level = state.level(e)
    val name = exerciseName(e)
    val meta = when {
        level == null -> stringResource(if (e.seconds) R.string.workout_ex_new_seconds else R.string.workout_ex_new_reps)
        e == Exercise.Pushups -> unitShort(e, level) + " · " + pushupVariant(WorkoutMath.pushupVariant(level))
        else -> unitShort(e, level)
    }
    val tag = stringResource(R.string.workout_next_tag)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .border(if (mine) 1.5.dp else XoSize.hairline, if (mine) colors.ink else colors.line, shape)
            .clip(shape)
            .clickable(role = Role.Button) { actions.onOpen(e) }
            .semantics(mergeDescendants = true) { contentDescription = name + ", " + meta + if (suggested) ", $tag" else "" }
            .padding(start = XoSpacing.s3, end = XoSpacing.s3, top = XoSpacing.s2, bottom = XoSpacing.s3),
    ) {
        Box(Modifier.fillMaxWidth()) {
            ExerciseFigure(e.figure(level), Modifier.fillMaxWidth(), mode = FigureMode.Start)
            if (suggested) {
                Text(
                    tag, style = type.scale, color = colors.ink,
                    modifier = Modifier.align(Alignment.TopEnd).border(XoSize.hairline, colors.line, CircleShape).padding(horizontal = XoSpacing.s2),
                )
            }
        }
        // Текст прижат к низу плитки: пояснения в ряду стоят на одной линии, а пустое место остаётся над названием.
        Spacer(Modifier.weight(1f).heightIn(min = XoSpacing.s1))
        Text(name, style = type.bodyStrong, color = colors.ink, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Spacer(Modifier.height(XoSpacing.textGap))
        Text(meta, style = type.caption, color = colors.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

private const val TILES_PER_ROW = 2
private const val LARGE_TEXT_SCALE = 1.25f

private const val RECENT_SHOWN = 5
private val FIGURE_MAX_HEIGHT = 190.dp

@Composable
private fun pushupVariant(v: PushupVariant) = stringResource(
    when (v) { PushupVariant.Wall -> R.string.pushup_wall; PushupVariant.Knees -> R.string.pushup_knees; PushupVariant.Classic -> R.string.pushup_classic },
)

@Composable
private fun CheckLevel(st: WorkoutStep.CheckLevel, actions: WorkoutActions) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Text(exerciseName(st.exercise), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(XoSpacing.s2))
    if (st.retest) {
        Text(stringResource(R.string.workout_check_again), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.headGap))
    }
    ExerciseFigure(st.exercise.figure(st.value), Modifier.fillMaxWidth().heightIn(max = FIGURE_MAX_HEIGHT), description = exerciseName(st.exercise))
    Spacer(Modifier.height(XoSpacing.s2))
    Text(stringResource(ExerciseText.hintRes(st.exercise)), style = type.caption, color = colors.muted)
    Spacer(Modifier.height(XoSpacing.s3))
    Text(
        stringResource(if (st.exercise.seconds) R.string.workout_check_seconds else R.string.workout_check_reps),
        style = type.body, color = colors.muted,
    )
    Spacer(Modifier.height(XoSpacing.s5))
    Stepper(unitText(st.exercise, st.value), { actions.onCheck(-1) }, { actions.onCheck(1) })
    Spacer(Modifier.height(XoSpacing.s3))
    // План на тренировку виден сразу и меняется вместе с числом.
    val plan = WorkoutMath.plan(st.value, st.exercise)
    Text(
        stringResource(R.string.workout_plan_line, plan.joinToString(" · ")) + if (st.exercise.seconds) " " + stringResource(R.string.workout_plan_sec) else "",
        style = type.caption, color = colors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(XoSpacing.s4))
    Text(stringResource(R.string.workout_safety), style = type.caption, color = colors.muted)
    Spacer(Modifier.height(XoSpacing.s5))
    XoPrimaryButton(stringResource(R.string.workout_begin), actions.onConfirmLevel)
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        RoundBtn("−", stringResource(R.string.less), onMinus)
        Text(value, style = type.display, color = colors.ink)
        RoundBtn("+", stringResource(R.string.more), onPlus)
    }
}

@Composable
private fun RoundBtn(symbol: String, label: String, onClick: () -> Unit) {
    val colors = XoTheme.colors
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(source)
            .size(56.dp)
            .border(XoSize.hairline, colors.line, CircleShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = XoTheme.type.title, color = colors.ink) }
}

@Composable
private fun Sets(st: WorkoutStep.Sets, counting: WorkoutCounting, actions: WorkoutActions) {
    val colors = XoTheme.colors
    val type = XoTheme.type

    Text(
        exerciseName(st.exercise) + if (st.exercise == Exercise.Pushups) " · " + pushupVariant(WorkoutMath.pushupVariant(st.level)) else "",
        style = type.caption, color = colors.muted,
    )
    Spacer(Modifier.height(XoSpacing.textGap))
    Text(
        stringResource(R.string.workout_set_of, (minOf(st.index + 1, st.plan.size)).toString(), st.plan.size.toString()),
        style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(XoSpacing.s3))
    ExerciseFigure(st.exercise.figure(st.level), Modifier.fillMaxWidth().heightIn(max = FIGURE_MAX_HEIGHT), description = exerciseName(st.exercise))
    Spacer(Modifier.height(XoSpacing.s3))
    Ladder(st)
    Spacer(Modifier.height(XoSpacing.s5))

    when {
        st.restEndsAtMs != null -> RestPanel(st, st.restEndsAtMs, actions)
        st.exercise.seconds -> HoldPanel(st, actions)
        else -> RepsPanel(st, counting, actions)
    }
}

/** «Лесенка»: подходы в ряд, текущий обведён, сделанные заливаются мягко. */
@Composable
private fun Ladder(st: WorkoutStep.Sets) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XoSpacing.s1)) {
        st.plan.forEachIndexed { i, target ->
            val done = i < st.actual.size
            val now = i == st.index
            val ms = if (reduced) 0 else XoMotion.STATE_MS
            val border by animateColorAsState(if (now) colors.ink else colors.line, tween(ms, easing = XoMotion.Standard), label = "rungBorder")
            val width by animateFloatAsState(if (now) 2f else 0.75f, tween(ms, easing = XoMotion.Standard), label = "rungWidth")
            val fill by animateColorAsState(if (done) colors.track else colors.background, tween(ms, easing = XoMotion.Standard), label = "rungFill")
            val text by animateColorAsState(if (done) colors.muted else colors.ink, tween(ms, easing = XoMotion.Standard), label = "rungText")
            Column(
                Modifier
                    .weight(1f)
                    .background(fill, RoundedCornerShape(12.dp))
                    .border(width.dp, border, RoundedCornerShape(12.dp))
                    .padding(vertical = XoSpacing.s2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (done) st.actual[i].toString() else target.toString(), style = type.bodyStrong, color = text)
                Text(if (i == st.plan.lastIndex) stringResource(R.string.workout_last_short) else (i + 1).toString(), style = type.scale, color = colors.muted)
            }
        }
    }
}

/** Отжимания, приседания, пресс: цель подхода и «−», «+» или счёт касанием. */
@Composable
private fun RepsPanel(st: WorkoutStep.Sets, counting: WorkoutCounting, actions: WorkoutActions) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val haptic = LocalHapticFeedback.current
    val target = st.plan[st.index]
    val shown = if (st.current > 0) st.current else target
    if (counting == WorkoutCounting.Tap) {
        // «Счёт касанием»: каждое касание — один повтор; «Убрать повтор» исправляет ошибку.
        Text(stringResource(R.string.workout_tap_hint, unitText(st.exercise, target)), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s2))
        val source = remember { MutableInteractionSource() }
        Box(
            Modifier
                .pressScale(source)
                .fillMaxWidth().height(200.dp)
                .border(XoSize.hairline, colors.line, RoundedCornerShape(20.dp))
                .clickable(interactionSource = source, indication = null, role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    actions.onTap()
                },
            contentAlignment = Alignment.Center,
        ) {
            RollingNumber(st.current.toLong(), st.current.toString(), type.display.copy(fontSize = type.display.fontSize * 2f), colors.ink)
        }
        if (st.current > 0) XoTextButton(stringResource(R.string.workout_untap), actions.onUntap)
    } else {
        Text(stringResource(R.string.workout_goal_reps), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s2))
        Stepper(unitText(st.exercise, shown), { actions.onAdjust(-1) }, { actions.onAdjust(1) })
    }
    Spacer(Modifier.height(XoSpacing.s4))
    XoPrimaryButton(
        stringResource(if (st.index == st.plan.lastIndex) R.string.workout_set_last else R.string.workout_set_done),
        { haptic.performHapticFeedback(HapticFeedbackType.Confirm); actions.onFinishSet() },
    )
}

/** Планка: цель в секундах, обратный отсчёт 3-2-1, удержание с кольцом, сигнал в конце. */
@Composable
private fun HoldPanel(st: WorkoutStep.Sets, actions: WorkoutActions) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val haptic = LocalHapticFeedback.current
    val target = st.plan[st.index]
    val holdStart = st.holdStartMs
    val now by rememberNowMs(active = holdStart != null, key = holdStart)

    if (holdStart == null) {
        Text(stringResource(R.string.workout_goal_seconds), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s2))
        val shown = if (st.current > 0) st.current else target
        Stepper(unitText(st.exercise, shown), { actions.onAdjust(-1) }, { actions.onAdjust(1) })
        Spacer(Modifier.height(XoSpacing.s4))
        XoPrimaryButton(stringResource(R.string.workout_hold_start), actions.onStartHold)
        XoTextButton(stringResource(R.string.workout_set_done), { haptic.performHapticFeedback(HapticFeedbackType.Confirm); actions.onFinishSet() })
        return
    }

    val goal = if (st.current > 0) st.current else target
    val elapsedMs = now - holdStart
    val prep = elapsedMs < 0
    // Конец удержания: сигнал и запись подхода.
    LaunchedEffect(holdStart, goal) {
        val untilEnd = holdStart + goal * 1000L - System.currentTimeMillis()
        if (untilEnd > 0) delay(untilEnd)
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        actions.onStopHold()
    }
    // Сигнал на каждую секунду обратного отсчёта.
    val prepSecond = if (prep) ((-elapsedMs) / 1000L + 1).toInt() else 0
    LaunchedEffect(prepSecond) { if (prepSecond > 0) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }

    val fraction = if (prep) 0f else (elapsedMs / (goal * 1000f)).coerceIn(0f, 1f)
    val leftSec = if (prep) goal else ((goal * 1000L - elapsedMs + 999) / 1000).toInt().coerceAtLeast(0)
    val label = if (prep) prepSecond.toString() else "%d:%02d".format(leftSec / 60, leftSec % 60)
    val a11y = if (prep) stringResource(R.string.workout_prep) + ", " + prepSecond else stringResource(R.string.workout_hold_a11y, leftSec.toString())
    TimerRing(fraction, label, if (prep) stringResource(R.string.workout_prep) else stringResource(R.string.workout_hold_now), a11y, filling = true)
    Spacer(Modifier.height(XoSpacing.s2))
    Text(stringResource(R.string.workout_hold_hint), style = type.caption, color = colors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(XoSpacing.s4))
    XoPrimaryButton(stringResource(if (prep) R.string.cancel else R.string.workout_hold_stop), actions.onStopHold)
}

/** Отдых: кольцо убывает, цифры крупно, в последние три секунды короткая вибрация, в конце сигнал. */
@Composable
private fun RestPanel(st: WorkoutStep.Sets, endMs: Long, actions: WorkoutActions) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val haptic = LocalHapticFeedback.current
    val now by rememberNowMs(active = true, key = endMs)
    val leftMs = (endMs - now).coerceAtLeast(0L)
    val leftSec = ((leftMs + 999) / 1000).toInt()
    LaunchedEffect(endMs) {
        // Окончание считаем по часам, а не по кадрам: так сигнал придёт и при остановке анимаций.
        val until = endMs - System.currentTimeMillis()
        if (until > 0) delay(until)
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        actions.onEndRest()
    }
    LaunchedEffect(leftSec) { if (leftSec in 1..3) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    val total = (st.restTotalSec.coerceAtLeast(1)) * 1000f
    val a11y = stringResource(R.string.workout_rest_a11y, leftSec.toString())
    TimerRing(leftMs / total, "%d:%02d".format(leftSec / 60, leftSec % 60), stringResource(R.string.workout_rest), a11y, filling = false, urgent = leftSec in 1..3)
    Spacer(Modifier.height(XoSpacing.s2))
    Text(
        stringResource(R.string.workout_next_up, unitText(st.exercise, st.plan[st.index])),
        style = type.caption, color = colors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(XoSpacing.s4))
    XoPrimaryButton(stringResource(R.string.workout_skip_rest), actions.onEndRest)
    XoTextButton(stringResource(R.string.workout_add_rest), actions.onAddRest)
}

/** Тонкое кольцо таймера. [filling] — дуга растёт (удержание), иначе убывает (отдых). */
@Composable
private fun TimerRing(fraction: Float, time: String, caption: String, a11y: String, filling: Boolean, urgent: Boolean = false) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val style = LocalTimerStyle.current
    val f = fraction.coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().height(RING_SIZE).clearAndSetSemantics { contentDescription = a11y },
        contentAlignment = Alignment.Center,
    ) {
        when (style) {
            com.projectxo.app.core.model.WorkoutTimerStyle.Ring -> Canvas(Modifier.size(RING_SIZE)) {
                val w = 4.dp.toPx()
                val inset = w / 2f
                val box = Size(size.width - w, size.height - w)
                drawArc(colors.track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(w))
                if (f > 0f) drawArc(colors.ink, -90f, 360f * f, false, Offset(inset, inset), box, style = Stroke(w, cap = StrokeCap.Round))
            }
            com.projectxo.app.core.model.WorkoutTimerStyle.Ticks -> Canvas(Modifier.size(RING_SIZE)) {
                // 60 штрихов: на отдыхе гаснут по одному, на удержании зажигаются. В последние три секунды отдыха штрихи длиннее.
                val n = 60
                val r2 = size.minDimension / 2f - 2.dp.toPx()
                val c = Offset(size.width / 2f, size.height / 2f)
                for (i in 0 until n) {
                    val a = Math.toRadians(-90.0 + i * 360.0 / n)
                    val lit = (i + 0.5f) / n <= f
                    val long = urgent && lit
                    val r1 = r2 - (if (long) 14.dp.toPx() else 9.dp.toPx())
                    drawLine(
                        if (lit) colors.ink else colors.track,
                        Offset(c.x + (Math.cos(a) * r1).toFloat(), c.y + (Math.sin(a) * r1).toFloat()),
                        Offset(c.x + (Math.cos(a) * r2).toFloat(), c.y + (Math.sin(a) * r2).toFloat()),
                        strokeWidth = (if (long) 2.5.dp else 2.dp).toPx(), cap = StrokeCap.Round,
                    )
                }
            }
            com.projectxo.app.core.model.WorkoutTimerStyle.Bar -> Unit
        }
        if (style == com.projectxo.app.core.model.WorkoutTimerStyle.Bar) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(time, style = type.display, color = colors.ink)
                Spacer(Modifier.height(XoSpacing.textGap))
                Text(caption, style = type.caption, color = colors.muted)
                Spacer(Modifier.height(XoSpacing.s4))
                Box(Modifier.fillMaxWidth().height(XoSize.bar).background(colors.track)) {
                    Box(Modifier.fillMaxWidth(f).height(XoSize.bar).background(colors.ink))
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, style = type.display, color = colors.ink)
                Spacer(Modifier.height(XoSpacing.textGap))
                Text(caption, style = type.caption, color = colors.muted)
            }
        }
    }
}

private val RING_SIZE = 200.dp

/** Вид таймера из настроек привычек (Р64). */
private val LocalTimerStyle = androidx.compose.runtime.compositionLocalOf { com.projectxo.app.core.model.WorkoutTimerStyle.Ring }

@Composable
private fun RateStep(st: WorkoutStep.Rate, actions: WorkoutActions) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val haptic = LocalHapticFeedback.current
    Text(stringResource(R.string.workout_rate_title), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(XoSpacing.textGap))
    Text(
        stringResource(if (st.allDone) R.string.workout_rate_all else R.string.workout_rate_partial),
        style = type.body, color = colors.muted,
    )
    Spacer(Modifier.height(XoSpacing.s4))
    st.actual.forEachIndexed { i, v ->
        Row(Modifier.fillMaxWidth().padding(vertical = XoSpacing.s1)) {
            Text(stringResource(R.string.workout_set_n, (i + 1).toString()), style = type.body, color = colors.muted, modifier = Modifier.weight(1f))
            Text(unitText(st.exercise, v), style = type.bodyStrong, color = colors.ink)
        }
    }
    Spacer(Modifier.height(XoSpacing.s5))
    listOf(
        WorkoutRating.EASY to R.string.workout_rate_easy,
        WorkoutRating.NORMAL to R.string.workout_rate_normal,
        WorkoutRating.HARD to R.string.workout_rate_hard,
    ).forEach { (r, label) ->
        val source = remember { MutableInteractionSource() }
        Box(
            Modifier
                .pressScale(source)
                .fillMaxWidth().padding(bottom = XoSpacing.s2).heightIn(min = XoSize.buttonHeight)
                .border(XoSize.hairline, colors.line, CircleShape)
                .clickable(interactionSource = source, indication = null, role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    actions.onRate(r)
                },
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(label), style = type.bodyStrong, color = colors.ink) }
    }
}

@Composable
private fun DoneStep(st: WorkoutStep.Done, actions: WorkoutActions) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    // Галочка прописывается двумя штрихами, число уровня перекатывается со старого на новое.
    val draw by animateFloatAsState(1f, tween(if (reduced) 0 else 600, easing = XoMotion.Standard), label = "doneMark")
    var shownLevel by remember { mutableLongStateOf(st.oldLevel.toLong()) }
    LaunchedEffect(st) {
        if (!reduced) delay(450)
        shownLevel = st.newLevel.toLong()
    }
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(72.dp)) {
            val w = 3.dp.toPx()
            val a = Offset(size.width * 0.22f, size.height * 0.52f)
            val b = Offset(size.width * 0.43f, size.height * 0.72f)
            val c = Offset(size.width * 0.80f, size.height * 0.30f)
            val t1 = (draw * 2f).coerceIn(0f, 1f)
            val t2 = (draw * 2f - 1f).coerceIn(0f, 1f)
            drawCircle(colors.line, size.minDimension / 2f - w, style = Stroke(XoSize.hairline.toPx()))
            if (t1 > 0f) drawLine(colors.ink, a, Offset(a.x + (b.x - a.x) * t1, a.y + (b.y - a.y) * t1), w, StrokeCap.Round)
            if (t2 > 0f) drawLine(colors.ink, b, Offset(b.x + (c.x - b.x) * t2, b.y + (c.y - b.y) * t2), w, StrokeCap.Round)
        }
    }
    Text(stringResource(R.string.workout_done_title), style = type.title, color = colors.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
    Spacer(Modifier.height(XoSpacing.headGap))
    Text(
        stringResource(R.string.workout_done_total, unitText(st.exercise, st.total)),
        style = type.caption, color = colors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(XoSpacing.s4))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        RollingNumber(shownLevel, shownLevel.toString(), type.display, colors.ink)
        if (st.exercise.seconds) Text(" " + stringResource(R.string.workout_plan_sec), style = type.caption, color = colors.muted, modifier = Modifier.padding(bottom = XoSpacing.s2))
    }
    Spacer(Modifier.height(XoSpacing.s2))
    Text(
        if (st.newLevel > st.oldLevel) stringResource(R.string.workout_done_up, exerciseName(st.exercise), unitText(st.exercise, st.oldLevel), unitText(st.exercise, st.newLevel))
        else stringResource(R.string.workout_done_same, exerciseName(st.exercise), unitText(st.exercise, st.newLevel)),
        style = type.body, color = colors.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(XoSpacing.s5))
    XoPrimaryButton(stringResource(R.string.got_it), actions.onBack)
}
