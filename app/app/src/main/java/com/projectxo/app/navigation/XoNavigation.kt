package com.projectxo.app.navigation

import com.projectxo.app.feature.workout.WorkoutViewModel
import com.projectxo.app.feature.workout.WorkoutScreen
import com.projectxo.app.feature.workout.WorkoutActions
import com.projectxo.app.feature.habits.HabitsViewModel
import com.projectxo.app.feature.habits.HabitsScreen
import com.projectxo.app.feature.habits.HabitsActions
import com.projectxo.app.feature.habits.HabitSeed
import com.projectxo.app.feature.fasting.FastingViewModel
import com.projectxo.app.feature.fasting.FastingScreen
import com.projectxo.app.feature.fasting.FastingActions
import com.projectxo.app.feature.sleep.SleepViewModel
import com.projectxo.app.feature.sleep.SleepScreen
import com.projectxo.app.feature.sleep.SleepActions
import com.projectxo.app.feature.breathing.BreathingViewModel
import com.projectxo.app.feature.breathing.BreathingScreen
import com.projectxo.app.core.ui.icons.ModuleUi
import com.projectxo.app.core.model.HabitFreq
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import android.provider.AlarmClock
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.ui.icons.XoIcons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.projectxo.app.R
import com.projectxo.app.core.ui.splash.EntranceSlot
import com.projectxo.app.core.ui.splash.entrance
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.projectxo.app.AppContainer
import com.projectxo.app.feature.home.TodayScreen
import com.projectxo.app.feature.home.TodayState
import com.projectxo.app.feature.home.TodayViewModel
import androidx.activity.compose.BackHandler
import com.projectxo.app.feature.calendar.CalendarActions
import com.projectxo.app.feature.calendar.CalendarScreen
import com.projectxo.app.feature.calendar.CalendarViewModel
import com.projectxo.app.feature.home.WeekViewModel
import com.projectxo.app.feature.settings.SettingsEntry
import java.time.LocalDate
import com.projectxo.app.feature.food.FoodPhotoActions
import com.projectxo.app.feature.food.FoodPhotoScreen
import com.projectxo.app.feature.food.FoodPhotoViewModel
import com.projectxo.app.feature.food.FoodScreen
import com.projectxo.app.feature.food.FoodViewModel
import com.projectxo.app.feature.water.WaterScreen
import com.projectxo.app.feature.water.WaterViewModel
import com.projectxo.app.feature.steps.StepsEntry
import kotlinx.serialization.Serializable

@Serializable data object TodayKey : NavKey
@Serializable data object WaterKey : NavKey
@Serializable data object FoodKey : NavKey
@Serializable data object StepsKey : NavKey

/** Экраны поверх вкладок: без нижней панели, «назад» возвращает на вкладку. */
@Serializable data class CalendarKey(val epochDay: Long) : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data object FoodPhotoKey : NavKey

@Serializable data object FastingKey : NavKey
@Serializable data object SleepKey : NavKey
@Serializable data object HabitsKey : NavKey
@Serializable data object BreathingKey : NavKey
@Serializable data object MoreKey : NavKey
@Serializable data object WorkoutKey : NavKey

/** Вкладка нижней панели. */
data class NavTab(val key: NavKey, @StringRes val label: Int, val icon: ImageVector)

/**
 * Вкладки (Р38): «Сегодня», включённые модули в порядке из настроек и, если модулей больше четырёх, «Ещё».
 * В «Ещё» уходят модули после третьего.
 */
object XoTabs {
    val Today = NavTab(TodayKey, R.string.tab_today, XoIcons.Today)
    val More = NavTab(MoreKey, R.string.tab_more, XoIcons.More)
    private const val MAX_MODULE_TABS = 4

    fun keyOf(m: Module): NavKey = when (m) {
        Module.Water -> WaterKey
        Module.Food -> FoodKey
        Module.Steps -> StepsKey
        Module.Fasting -> FastingKey
        Module.Sleep -> SleepKey
        Module.Habits -> HabitsKey
        Module.Breathing -> BreathingKey
    }

    fun moduleOf(key: NavKey?): Module? = Module.entries.firstOrNull { keyOf(it) == key }

    fun tabOf(m: Module) = NavTab(keyOf(m), ModuleUi.nameRes(m), ModuleUi.icon(m))

    private fun enabled(modules: Set<Module>, order: List<Module>) = order.filter { it in modules }

    fun visible(modules: Set<Module>, order: List<Module>): List<NavTab> {
        val on = enabled(modules, order)
        return if (on.size <= MAX_MODULE_TABS) listOf(Today) + on.map(::tabOf)
        else listOf(Today) + on.take(MAX_MODULE_TABS - 1).map(::tabOf) + More
    }

    /** Модули, которые не поместились во вкладки и открываются из «Ещё». */
    fun overflow(modules: Set<Module>, order: List<Module>): List<Module> {
        val on = enabled(modules, order)
        return if (on.size <= MAX_MODULE_TABS) emptyList() else on.drop(MAX_MODULE_TABS - 1)
    }
}

@Composable
fun XoNavHost(
    container: AppContainer,
    today: TodayState?,
    todayVm: TodayViewModel,
    modules: Set<Module>,
    order: List<Module>,
    /** Текущий день: ключ моделей экранов, чтобы после полуночи они считали новый день. */
    date: LocalDate,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(TodayKey)
    val weekVm: WeekViewModel = viewModel(
        key = "week-$date",
        factory = viewModelFactory { initializer { WeekViewModel(container.history, container.settings, container.clock) } },
    )
    val week by weekVm.week.collectAsStateWithLifecycle()
    fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    // «Сегодня» всегда в основании стека: «назад» с любой вкладки возвращает на неё.
    fun select(key: NavKey) {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (key != TodayKey) backStack.add(key)
    }

    val fade = tween<Float>(XoMotion.STATE_MS, easing = XoMotion.Standard)
    Column(modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.weight(1f),
            onBack = ::pop,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            popTransitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            entryProvider = entryProvider {
                entry<TodayKey> {
                    val undo by todayVm.undo.collectAsStateWithLifecycle()
                    // До первых данных показываем пустой день: кольцо уже стоит на месте, и заставке есть куда лететь.
                    TodayScreen(
                        state = today ?: TodayState.Empty,
                        date = todayVm.date,
                        onQuickAdd = todayVm::quickAddWater,
                        undo = undo,
                        onUndo = todayVm::undoQuickAdd,
                        week = week,
                        onOpenCalendar = { d -> backStack.add(CalendarKey(d.toEpochDay())) },
                        onOpenSettings = { backStack.add(SettingsKey) },
                        onOpenModule = { m -> select(XoTabs.keyOf(m)) },
                    )
                }
                entry<WaterKey> {
                    val vm: WaterViewModel = viewModel(
                        key = "water-$date",
                        factory = viewModelFactory {
                            initializer { WaterViewModel(container.water, container.goals, container.clock, container.appScope) }
                        },
                    )
                    val state by vm.state.collectAsStateWithLifecycle()
                    WaterScreen(state, onAdd = vm::add, onDelete = vm::delete, onUndoDelete = vm::undoDelete)
                }
                entry<FoodKey> {
                    val vm: FoodViewModel = viewModel(
                        key = "food-$date",
                        factory = viewModelFactory {
                            initializer { FoodViewModel(container.food, container.goals, container.clock, container.appScope, container.foodPhotos) }
                        },
                    )
                    val state by vm.state.collectAsStateWithLifecycle()
                    FoodScreen(
                        state, onAdd = vm::add, onDelete = vm::delete, onUndoDelete = vm::undoDelete,
                        onOpenPhoto = { backStack.add(FoodPhotoKey) }, photos = container.foodPhotos,
                    )
                }
                entry<FoodPhotoKey> {
                    val vm: FoodPhotoViewModel = viewModel(
                        key = "foodphoto-$date",
                        factory = viewModelFactory {
                            initializer { FoodPhotoViewModel(container.foodPhoto, container.settings, container.food, container.clock, container.appScope, container.foodAiShared, container.foodPhotos) }
                        },
                    )
                    val st by vm.state.collectAsStateWithLifecycle()
                    FoodPhotoScreen(
                        st,
                        FoodPhotoActions(
                            onHint = vm::setHint, onSaveKey = vm::saveKey, onAnalyze = vm::analyze, onRetake = vm::retake,
                            onOil = vm::chooseOil, onPortion = vm::choosePortion, onCustomGrams = vm::setCustomGrams,
                            onEditing = vm::setEditing, onStep = vm::step, onSave = vm::save, setFatName = vm::setFatName, onReset = vm::reset, onRetry = vm::retry,
                        ),
                        onBack = ::pop, onSaved = ::pop,
                    )
                }
                entry<StepsKey> { StepsEntry(container, date) }
                entry<BreathingKey> {
                    val vm: BreathingViewModel = viewModel(
                        key = "breath-$date",
                        factory = viewModelFactory { initializer { BreathingViewModel(container.breathing, container.settings, container.clock, container.appScope) } },
                    )
                    val st by vm.state.collectAsStateWithLifecycle()
                    BreathingScreen(st, vm::setRhythm, vm::setMinutes, vm::start, vm::stop, vm::complete)
                }
                entry<SleepKey> {
                    val context = LocalContext.current
                    val vm: SleepViewModel = viewModel(
                        key = "sleep-$date",
                        factory = viewModelFactory { initializer { SleepViewModel(container.sleep, container.settings, container.clock, container.appScope) } },
                    )
                    val st by vm.state.collectAsStateWithLifecycle()
                    SleepScreen(
                        st,
                        SleepActions(
                            onBed = { vm.goToBed() }, onWake = { vm.wake() }, onEditStart = vm::editStart,
                            onWakeEarlier = { vm.wake(it) }, onBedEarlier = { vm.goToBed(it) },
                            onSchedule = vm::setSchedule, onRemind = vm::setRemind, onRate = vm::rate,
                            onSkipRating = vm::skipRating, onRateLast = vm::rateLast,
                            onAlarm = { h, m ->
                                // Будильник ставится в системных «Часах»: приложению не нужно разрешение на точный будильник.
                                runCatching {
                                    context.startActivity(
                                        Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
                                            .putExtra(AlarmClock.EXTRA_SKIP_UI, false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                        ),
                    )
                }
                entry<FastingKey> {
                    val vm: FastingViewModel = viewModel(
                        key = "fasting-$date",
                        factory = viewModelFactory { initializer { FastingViewModel(container.fasting, container.settings, container.clock, container.appScope) } },
                    )
                    val st by vm.state.collectAsStateWithLifecycle()
                    FastingScreen(
                        st,
                        FastingActions(
                            onStart = { ms -> if (ms == null) vm.start() else vm.start(ms) },
                            onFinish = { ms -> if (ms == null) vm.finish() else vm.finish(ms) },
                            onEditStart = vm::editStart, onPlan = vm::setPlan, onAcceptSafety = vm::acceptSafety,
                        ),
                    )
                }
                entry<HabitsKey> {
                    val seedNames = listOf(
                        stringResource(R.string.habits_seed_1), stringResource(R.string.habits_seed_2),
                        stringResource(R.string.habits_seed_3), stringResource(R.string.habits_seed_4),
                    )
                    val seeds = remember {
                        listOf(
                            HabitSeed(seedNames[0], HabitFreq.Daily, 8 * 60),
                            HabitSeed(seedNames[1], HabitFreq.Daily, 21 * 60),
                            HabitSeed(seedNames[2], HabitFreq.Daily, 22 * 60),
                            HabitSeed(seedNames[3], HabitFreq.PerWeek(3), 19 * 60 + 30),
                        )
                    }
                    val workoutName = stringResource(R.string.habits_workout_name)
                    val vm: HabitsViewModel = viewModel(
                        key = "habits-$date",
                        factory = viewModelFactory { initializer { HabitsViewModel(container.habits, container.settings, container.clock, container.appScope, seeds, workoutName) } },
                    )
                    val st by vm.state.collectAsStateWithLifecycle()
                    HabitsScreen(
                        st,
                        HabitsActions(
                            onToggle = vm::toggle, onCount = vm::count, onSave = vm::save, onView = vm::setView,
                            onEditMode = vm::setEditMode, onDelete = vm::delete, onUndo = vm::undoDelete,
                            onWorkoutEnabled = { vm.setWorkoutEnabled(it, workoutName) },
                            onOpenWorkout = { backStack.add(WorkoutKey) }, onCounting = vm::setCounting, onTimer = vm::setTimer,
                        ),
                    )
                }
                entry<WorkoutKey> {
                    val vm: WorkoutViewModel = viewModel(
                        key = "workout-$date",
                        factory = viewModelFactory { initializer { WorkoutViewModel(container.workouts, container.habits, container.settings, container.clock, container.appScope) } },
                    )
                    val st by vm.state.collectAsStateWithLifecycle()
                    WorkoutScreen(
                        st,
                        WorkoutActions(
                            onOpen = vm::open, onCheck = vm::setCheck, onConfirmLevel = vm::confirmLevel, onTap = vm::tap, onAdjust = vm::adjust,
                            onFinishSet = { vm.finishSet() }, onAddRest = vm::addRest, onEndRest = vm::endRest, onAbort = vm::abort, onRate = vm::rate,
                            onBack = ::pop, onUntap = vm::untap, onStartHold = vm::startHold, onStopHold = vm::stopHold,
                        ),
                    )
                }
                entry<MoreKey> {
                    MoreScreen(
                        modules = XoTabs.overflow(modules, order),
                        onOpen = { m -> backStack.add(XoTabs.keyOf(m)) },
                        onSettings = { backStack.add(SettingsKey) },
                    )
                }
                entry<CalendarKey> { key ->
                    val vm: CalendarViewModel = viewModel(
                        key = "calendar-$date-${key.epochDay}",
                        factory = viewModelFactory {
                            initializer {
                                CalendarViewModel(
                                    container.history, container.water, container.food, container.dayLog,
                                    container.settings, container.clock, container.appScope, LocalDate.ofEpochDay(key.epochDay),
                                )
                            }
                        },
                    )
                    val state by vm.state.collectAsStateWithLifecycle()
                    // Пока окно самочувствия открыто, «назад» закрывает его, а не календарь.
                    BackHandler(enabled = state.draft != null) { vm.closePicker() }
                    CalendarScreen(
                        state,
                        CalendarActions(
                            onClose = ::pop, onSelect = vm::select, onMode = vm::setMode,
                            onOpenPicker = vm::openPicker, onClosePicker = vm::closePicker,
                            onFeel = vm::setFeel, onTag = vm::toggleTag, onWeightStep = vm::stepWeight,
                            onWeightClear = vm::clearWeight, onNote = vm::setNote, onSave = vm::save,
                        ),
                    )
                }
                entry<SettingsKey> { SettingsEntry(container, onBack = ::pop) }
            },
        )
        // Один включённый модуль — панели нет (docs/PRODUCT.md): «Сегодня» и экран модуля.
        val tabs = XoTabs.visible(modules, order)
        val current = backStack.lastOrNull() ?: TodayKey
        val onBarScreen = current == TodayKey || current == MoreKey || XoTabs.moduleOf(current) != null
        // Модуль из «Ещё» не стоит во вкладках: подсвечиваем «Ещё».
        val selected = if (tabs.any { it.key == current }) current else MoreKey
        if (tabs.size > 2 && onBarScreen) XoBottomBar(
            tabs = tabs,
            selected = selected,
            onSelect = ::select,
            modifier = Modifier.entrance(EntranceSlot.NAV),
        )
    }
}

/** Нижняя панель: иконка «Предметы» и подпись; активная — цветом ink и весом 500, без цветной заливки (Р40). */
@Composable
private fun XoBottomBar(tabs: List<NavTab>, selected: NavKey, onSelect: (NavKey) -> Unit, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val hairline = with(LocalDensity.current) { XoSize.hairline.toPx() }
    Row(
        modifier
            .fillMaxWidth()
            // Свой фон до самого края экрана, включая зону жестов: под панелью ничего не просвечивает.
            .background(colors.background)
            .drawBehind { drawRect(colors.line, size = Size(size.width, hairline)) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(XoSize.navHeight)
            .selectableGroup(),
    ) {
        tabs.forEach { tab ->
            val isOn = tab.key == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = isOn, role = Role.Tab, onClick = { onSelect(tab.key) }),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = if (isOn) colors.ink else colors.muted,
                        modifier = Modifier.size(XoSize.navIcon),
                    )
                    Spacer(Modifier.height(XoSize.navDot))
                    // Подписи панели растут вместе с системным текстом только до 1,3: иначе «Сегодня» не помещается.
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, XoSize.NAV_MAX_FONT_SCALE))) {
                        Text(
                            stringResource(tab.label),
                            style = if (isOn) type.tabActive else type.tab,
                            color = if (isOn) colors.ink else colors.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
