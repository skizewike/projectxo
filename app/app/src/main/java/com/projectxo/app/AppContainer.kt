package com.projectxo.app

import android.app.Application
import android.content.Context
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.GoalsRepository
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.data.prefs.PrefsSettingsRepository
import com.projectxo.app.core.data.SystemClock
import com.projectxo.app.core.data.WaterRepository
import com.projectxo.app.core.data.BreathingRepository
import com.projectxo.app.core.data.DayLogRepository
import com.projectxo.app.core.data.FastingRepository
import com.projectxo.app.core.data.HabitRepository
import com.projectxo.app.core.data.SleepRepository
import com.projectxo.app.core.data.WorkoutRepository
import com.projectxo.app.core.data.db.RoomBreathingRepository
import com.projectxo.app.core.data.db.RoomFastingRepository
import com.projectxo.app.core.data.db.RoomHabitRepository
import com.projectxo.app.core.data.db.RoomSleepRepository
import com.projectxo.app.core.data.db.RoomWorkoutRepository
import com.projectxo.app.core.data.FoodRepository
import com.projectxo.app.core.data.db.RoomDayLogRepository
import com.projectxo.app.core.model.BalanceHistory
import com.projectxo.app.core.model.HistoryExtras
import com.projectxo.app.core.data.db.RoomFoodRepository
import com.projectxo.app.core.data.db.RoomGoalsRepository
import com.projectxo.app.core.data.db.RoomWaterRepository
import com.projectxo.app.core.data.db.XoDatabase
import com.projectxo.app.core.data.StepsRepository
import com.projectxo.app.core.data.db.RoomStepsRepository
import com.projectxo.app.core.data.sensor.StepSensor
import com.projectxo.app.core.data.sensor.StepsWorker
import com.projectxo.app.core.steps.StepsRecorder
import com.projectxo.app.core.model.ReminderPolicy
import com.projectxo.app.core.reminders.ReminderWorker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.model.WaterPortions
import com.projectxo.app.core.reminders.ReminderNotifier
import kotlinx.coroutines.withContext
import com.projectxo.app.core.model.AppSettings

/** Ручная сборка зависимостей. Один экземпляр на приложение. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val db by lazy { XoDatabase.build(context) }

    /** Для записей, которые должны дойти до базы, даже если экран закрыт. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: AppClock = SystemClock
    val water: WaterRepository by lazy { RoomWaterRepository(db.waterDao()) }
    val food: FoodRepository by lazy { RoomFoodRepository(db.foodDao()) }
    /** Снимки записей еды по фото (Р71): только в закрытой папке приложения. */
    val foodPhotos by lazy { com.projectxo.app.core.data.photo.FoodPhotoStore(java.io.File(appContext.filesDir, "food_photos")) }
    val sleep: SleepRepository by lazy { RoomSleepRepository(db.sleepDao()) }
    val fasting: FastingRepository by lazy { RoomFastingRepository(db.fastingDao()) }
    val breathing: BreathingRepository by lazy { RoomBreathingRepository(db.breathingDao()) }
    val habits: HabitRepository by lazy { RoomHabitRepository(db.habitDao()) }
    val workouts: WorkoutRepository by lazy { RoomWorkoutRepository(db.workoutDao()) }
    val mood: com.projectxo.app.core.data.MoodRepository by lazy { com.projectxo.app.core.data.db.RoomMoodRepository(db.moodDao()) }
    /** Привычки вместе (Р79): сервер комнат из `server/habit-rooms`; адрес задаётся при сборке (`sharedRoomsUrl`). */
    val shared: com.projectxo.app.core.data.shared.SharedHabitRepository by lazy {
        com.projectxo.app.core.data.db.DbSharedHabitRepository(
            db.sharedDao(), com.projectxo.app.core.data.shared.HttpRoomsApi(BuildConfig.SHARED_ROOMS_URL), habits, clock,
        )
    }
    val dayLog: DayLogRepository by lazy { RoomDayLogRepository(db.dayLogDao()) }
    val goals: GoalsRepository by lazy { RoomGoalsRepository(db.goalDao()) }
    val steps: StepsRepository by lazy { RoomStepsRepository(db.stepsDao()) }
    /** Читается сразу при запуске: тема и «онбординг пройден» нужны до первого кадра. */
    val settings: SettingsRepository = PrefsSettingsRepository(context)
    /** Еда по фото (Р69, Р70): свой ключ из настроек или общий посредник из сборки. */
    val foodAiShared: Boolean = BuildConfig.FOOD_AI_PROXY_URL.isNotBlank()
    val foodPhoto: com.projectxo.app.core.data.foodai.FoodPhotoAnalyzer by lazy {
        com.projectxo.app.core.data.foodai.GeminiFoodAnalyzer(
            apiKey = { settings.settings.value.foodAiKey },
            proxyUrl = BuildConfig.FOOD_AI_PROXY_URL,
            installId = {
                settings.settings.value.installId.ifEmpty {
                    java.util.UUID.randomUUID().toString().also { id -> settings.update { it.copy(installId = id) } }
                }
            },
        )
    }
    val history by lazy {
        BalanceHistory(water, food, steps, goals, HistoryExtras(sleep, fasting, breathing, habits, workouts, settings) { clock.nowMs() })
    }

    /** Пример напоминания сразу: чтобы увидеть, как оно выглядит (Настройки, «Уведомления»). */
    fun showExampleReminder() {
        appScope.launch {
            val today = clock.today()
            val left = goals.goal(GoalType.WaterMl).first() - water.totalFor(today).first()
            ReminderNotifier.showWater(appContext, left.coerceAtLeast(WaterPortions.primaryMl(settings.settings.value.units.volume).toLong()))
        }
    }

    /** CSV со всеми днями с первого дня в XO по сегодня (волна 2). */
    suspend fun exportCsv(): String {
        val today = clock.today()
        val start = settings.settings.value.startEpochDay?.let { java.time.LocalDate.ofEpochDay(it) }?.takeIf { !it.isAfter(today) } ?: today
        val totals = history.totalsBetween(start, today).first()
        val logs = dayLog.observeBetween(start, today).first()
        return com.projectxo.app.core.model.CsvExport.build(start, today, totals, logs)
    }

    /** «Удалить все данные»: записи, цели и настройки. После этого приложение стартует как новое. */
    fun deleteAllData() {
        appScope.launch {
            // Общие привычки (Р79): сначала выйти из комнат, чтобы отметки не остались на сервере. Без сети ждём недолго.
            runCatching {
                kotlinx.coroutines.withTimeoutOrNull(DELETE_LEAVE_TIMEOUT_MS) { shared.rooms().first().forEach { shared.leave(it.roomId) } }
            }
            withContext(Dispatchers.IO) {
                db.clearAllTables()
                foodPhotos.deleteAll()
            }
            settings.update { AppSettings() }
        }
    }
    val stepSensor by lazy { StepSensor(context) }
    val stepsRecorder by lazy { StepsRecorder(steps) }
    val stepsLive by lazy { StepsLive(stepSensor, stepsRecorder, clock) }
    val motionSteps by lazy { com.projectxo.app.core.data.sensor.MotionSteps(appContext, stepSensor) }

    init {
        // Р73: привычка «Тренировка» превращается в модуль «Тренировки».
        appScope.launch { com.projectxo.app.core.data.WorkoutMigration.run(habits, settings) }
        // Фоновая синхронизация общих привычек идёт, только пока у человека есть хотя бы одна комната.
        appScope.launch {
            if (!shared.available) return@launch
            shared.rooms().map { it.isNotEmpty() }.distinctUntilChanged().collect { has ->
                runCatching { com.projectxo.app.core.data.shared.SharedSyncWorker.schedule(appContext, has) }
            }
        }
    }
}

/** Виджет перерисовывается при каждом изменении дня, пока процесс жив. */
fun AppContainer.keepWidgetFresh(context: Context) {
    appScope.launch {
        val today = clock.today()
        kotlinx.coroutines.flow.combine(history.totalsBetween(today, today), history.goals(), settings.settings) { t, g, s ->
            Triple(t[today], g, s.modules)
        }.distinctUntilChanged().collect { runCatching { com.projectxo.app.core.widget.DayRingWidget.refresh(context) } }
    }
}

/**
 * Живые шаги, пока приложение на экране (Р25): слушаем датчик, только если есть разрешение.
 * [refreshPermission] вызывается при возвращении в приложение и после ответа на запрос разрешения.
 */
class StepsLive(private val sensor: StepSensor, private val recorder: StepsRecorder, private val clock: AppClock) {
    private val _permission = MutableStateFlow(sensor.hasPermission())
    val permission: StateFlow<Boolean> = _permission.asStateFlow()
    val hasSensor: Boolean get() = sensor.isAvailable

    fun refreshPermission() {
        _permission.value = sensor.hasPermission()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run() {
        permission
            .flatMapLatest { granted -> if (granted && sensor.isAvailable) sensor.readings() else emptyFlow() }
            .collect { counter -> recorder.onReading(counter, clock.nowMs()) }
    }
}

/** Сколько ждать выхода из общих привычек при «Удалить все данные», мс. */
private const val DELETE_LEAVE_TIMEOUT_MS = 8_000L

class XoApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** Ресурсы на языке, выбранном в приложении: уведомления, виджет и фоновые задачи говорят на нём же. */
    @Volatile private var localized: android.content.res.Resources? = null

    override fun getResources(): android.content.res.Resources = localized ?: super.getResources()

    private fun applyLanguage(l: com.projectxo.app.core.model.AppLanguage) {
        localized = null
        localized = com.projectxo.app.core.data.prefs.AppLocale.resources(baseContext, l)
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applyLanguage(com.projectxo.app.core.data.prefs.AppLocale.saved(this))
        container.appScope.launch {
            container.settings.settings.map { it.language }.distinctUntilChanged().collect { applyLanguage(it) }
        }
        // В тестах (Robolectric) WorkManager не инициализируется сам — там фоновая проверка не нужна.
        runCatching { StepsWorker.schedule(this) }
        container.keepWidgetFresh(this)
        // Голодание: уведомление с секундомером и таймеры цели следуют за состоянием в базе.
        container.appScope.launch {
            container.fasting.current().distinctUntilChanged().collect { s -> runCatching { com.projectxo.app.core.reminders.Wave2Notifier.syncFasting(this@XoApplication, s) } }
        }
        // Фоновая проверка напоминаний включена, только пока что-то из них включено.
        container.appScope.launch {
            container.settings.settings
                .map { ReminderPolicy.anyEnabled(it.reminders, it.modules) && it.onboarded }
                .distinctUntilChanged()
                .collect { on -> runCatching { ReminderWorker.sync(this@XoApplication, on) } }
        }
    }
}
