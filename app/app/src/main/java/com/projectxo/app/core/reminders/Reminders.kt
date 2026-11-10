package com.projectxo.app.core.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.projectxo.app.MainActivity
import com.projectxo.app.R
import com.projectxo.app.XoApplication
import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.model.BalanceMath
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.ReminderPolicy
import com.projectxo.app.core.model.WaterPortions
import com.projectxo.app.core.model.WaterReminderInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.projectxo.app.core.ui.format.formatVolume
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Мягкие напоминания (Р58): без звука и вибрации, не чаще раза в 2 часа, не в тихие часы. */
object ReminderNotifier {
    private const val CHANNEL_WATER = "water"
    private const val CHANNEL_SUMMARY = "summary"
    const val ID_WATER = 1001
    const val ID_SUMMARY = 1002
    const val ID_WEEKLY = 1003

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        // IMPORTANCE_LOW: без звука и вибрации, значок в шторке.
        nm.createNotificationChannel(NotificationChannel(CHANNEL_WATER, context.getString(R.string.notif_channel_water), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SUMMARY, context.getString(R.string.notif_channel_summary), NotificationManager.IMPORTANCE_LOW))
    }

    private fun openApp(context: Context, code: Int): PendingIntent =
        PendingIntent.getActivity(
            context, code, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @SuppressLint("MissingPermission")
    fun showWater(context: Context, leftMl: Long) {
        if (!canPost(context)) return
        ensureChannels(context)
        val unit = (context.applicationContext as XoApplication).container.settings.settings.value.units.volume
        val add = PendingIntent.getBroadcast(
            context, 1, Intent(context, WaterActionReceiver::class.java).setAction(WaterActionReceiver.ACTION_ADD_WATER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_WATER)
            .setSmallIcon(R.drawable.ic_stat_xo)
            .setContentTitle(context.getString(R.string.notif_water_title))
            .setContentText(context.getString(R.string.notif_water_body, formatVolume(context, leftMl, unit)))
            .setContentIntent(openApp(context, 0))
            .addAction(0, context.getString(R.string.notif_water_action, formatVolume(context, WaterPortions.primaryMl(unit).toLong(), unit)), add)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(ID_WATER, n)
    }

    @SuppressLint("MissingPermission")
    fun showSummary(context: Context, percent: Int) {
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_stat_xo)
            .setContentTitle(context.getString(R.string.notif_summary_title))
            .setContentText(context.getString(R.string.notif_summary_body, percent.toString()))
            .setContentIntent(openApp(context, 2))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(ID_SUMMARY, n)
    }

    @SuppressLint("MissingPermission")
    fun showWeekly(context: Context, percent: Int) {
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_stat_xo)
            .setContentTitle(context.getString(R.string.notif_weekly_title))
            .setContentText(context.getString(R.string.notif_weekly_body, percent.toString()))
            .setContentIntent(openApp(context, 3))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(ID_WEEKLY, n)
    }

    fun cancelWater(context: Context) = NotificationManagerCompat.from(context).cancel(ID_WATER)
}

/** Кнопка «+250 мл» в уведомлении и на виджете: записывает стакан, приложение не открывается. */
class WaterActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ADD_WATER = "com.projectxo.app.ADD_WATER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as XoApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.water.add(WaterPortions.primaryMl(container.settings.settings.value.units.volume), container.clock.nowMs(), container.clock.today())
            } finally {
                ReminderNotifier.cancelWater(context)
                runCatching { com.projectxo.app.core.widget.DayRingWidget.refresh(context) }
                pending.finish()
            }
        }
    }
}

/** Что уже показывали: чтобы не повторяться раньше срока. */
class ReminderLog(context: Context) {
    private val prefs = context.getSharedPreferences("xo_reminders", Context.MODE_PRIVATE)
    var lastWaterReminderMs: Long?
        get() = if (prefs.contains("last_water")) prefs.getLong("last_water", 0) else null
        set(v) = prefs.edit().apply { if (v == null) remove("last_water") else putLong("last_water", v) }.apply()
    /** День (epoch day), когда уже показывали напоминание [key]: «bed», «wake», «h_<id>». */
    fun dayOf(key: String): Long? = if (prefs.contains("d_$key")) prefs.getLong("d_$key", 0) else null
    fun setDay(key: String, day: Long) = prefs.edit().putLong("d_$key", day).apply()
    var lastWeeklyDay: Long?
        get() = if (prefs.contains("last_weekly")) prefs.getLong("last_weekly", 0) else null
        set(v) = prefs.edit().apply { if (v == null) remove("last_weekly") else putLong("last_weekly", v) }.apply()
    var lastSummaryDay: Long?
        get() = if (prefs.contains("last_summary")) prefs.getLong("last_summary", 0) else null
        set(v) = prefs.edit().apply { if (v == null) remove("last_summary") else putLong("last_summary", v) }.apply()
}

/** Проверка раз в 30 минут: решает по [ReminderPolicy], что показать. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as XoApplication).container
        val settings = container.settings.settings.value
        val r = settings.reminders
        if (!ReminderPolicy.anyEnabled(r, settings.modules)) return Result.success()
        if (!ReminderNotifier.canPost(applicationContext)) return Result.success()

        val clock = container.clock
        val today = clock.today()
        val now = clock.nowMs()
        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val log = ReminderLog(applicationContext)

        val total = container.water.totalFor(today).first()
        val goal = container.goals.goal(GoalType.WaterMl).first()
        val last = container.water.entriesFor(today).first().firstOrNull()?.timestampMs
        val input = WaterReminderInput(nowMin, now, r, Module.Water in settings.modules, total, goal, last, log.lastWaterReminderMs)
        if (ReminderPolicy.shouldRemindWater(input)) {
            ReminderNotifier.showWater(applicationContext, goal - total)
            log.lastWaterReminderMs = now
        }

        if (ReminderPolicy.shouldSendSummary(nowMin, r, log.lastSummaryDay, today.toEpochDay())) {
            val totals = container.history.totalsBetween(today, today).first()[today]
            val goals = container.history.goals().first()
            val percent = Math.round(BalanceMath.balance(totals, goals, settings.modules) * 100)
            ReminderNotifier.showSummary(applicationContext, percent)
            log.lastSummaryDay = today.toEpochDay()
        }
        if (ReminderPolicy.shouldSendWeekly(nowMin, today.dayOfWeek, r, log.lastWeeklyDay, today.toEpochDay())) {
            val start = settings.startEpochDay?.let { java.time.LocalDate.ofEpochDay(it) }?.takeIf { !it.isAfter(today) } ?: today
            val totals = container.history.totalsBetween(start, today).first()
            val goals = container.history.goals().first()
            val week = com.projectxo.app.core.model.WeekSummaryBuilder.build(today, start, totals, goals, settings.modules, settings.moduleOrder)
            // Первые дни с XO итога ещё нет: молчим, но день отмечаем, чтобы не проверять каждые полчаса.
            if (week != null && week.days >= 2) ReminderNotifier.showWeekly(applicationContext, week.averagePercent)
            log.lastWeeklyDay = today.toEpochDay()
        }
        runWave2(container, settings, log, nowMin, today)
        return Result.success()
    }

    /** Сон, привычки и обновление уведомления голодания. */
    private suspend fun runWave2(
        container: com.projectxo.app.AppContainer,
        settings: com.projectxo.app.core.model.AppSettings,
        log: ReminderLog,
        nowMin: Int,
        today: java.time.LocalDate,
    ) {
        val ctx = applicationContext
        val day = today.toEpochDay()
        if (Module.Fasting in settings.modules) container.fasting.current().first()?.let { Wave2Notifier.showFasting(ctx, it) }
        if (Module.Sleep in settings.modules) {
            val w = settings.wave2
            val cur = container.sleep.current().first()
            if (cur == null && w.sleepRemind) {
                val untilBed = ((w.sleepBedMin - nowMin) % 1440 + 1440) % 1440
                if (untilBed in 0..SLEEP_REMIND_MIN && log.dayOf("bed") != day) {
                    Wave2Notifier.showBedReminder(ctx, w.sleepBedMin)
                    log.setDay("bed", day)
                }
            }
            if (cur != null) {
                val goalMs = com.projectxo.app.core.model.SleepMath.goalMinutes(w.sleepBedMin, w.sleepWakeMin) * 60_000L
                if (container.clock.nowMs() - cur.startMs > goalMs + 60 * 60_000L && log.dayOf("wake") != day) {
                    Wave2Notifier.showWakePrompt(ctx)
                    log.setDay("wake", day)
                }
            }
        }
        if (Module.Habits in settings.modules) {
            val habits = container.habits.habits().first().filter { it.reminderMin != null }
            if (habits.isNotEmpty()) {
                val checks = container.habits.checks(today.with(java.time.DayOfWeek.MONDAY), today).first()
                habits.forEach { h ->
                    val r = h.reminderMin ?: return@forEach
                    val since = ((nowMin - r) % 1440 + 1440) % 1440
                    val m = com.projectxo.app.core.model.HabitMath.Marks(h, checks)
                    if (since < HABIT_WINDOW_MIN && com.projectxo.app.core.model.HabitMath.dueOn(h, m, today) && !m.done(today) && log.dayOf("h_${h.id}") != day) {
                        Wave2Notifier.showHabit(ctx, h.id, h.name)
                        log.setDay("h_${h.id}", day)
                    }
                }
            }
        }
    }

    companion object {
        private const val SLEEP_REMIND_MIN = 30
        private const val HABIT_WINDOW_MIN = 45
        private const val NAME = "reminders"
        private const val PERIOD_MIN = 30L

        /** Включить или выключить фоновую проверку по настройкам. */
        fun sync(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ReminderWorker>(PERIOD_MIN, TimeUnit.MINUTES).build())
            } else {
                wm.cancelUniqueWork(NAME)
            }
        }
    }
}
