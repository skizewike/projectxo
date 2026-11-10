package com.projectxo.app.core.reminders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.projectxo.app.MainActivity
import com.projectxo.app.R
import com.projectxo.app.XoApplication
import com.projectxo.app.core.data.FastingSession
import com.projectxo.app.core.model.FastingMath
import com.projectxo.app.core.ui.format.timeOfDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Уведомления модулей волны 2: голодание, сон, привычки. Все тихие (IMPORTANCE_LOW), как и остальные (Р58). */
object Wave2Notifier {
    private const val CH_FASTING = "fasting"
    private const val CH_SLEEP = "sleep"
    private const val CH_HABITS = "habits"
    const val ID_FASTING = 1003
    const val ID_FASTING_GOAL = 1004
    const val ID_FASTING_WINDOW = 1005
    const val ID_SLEEP_BED = 1006
    const val ID_SLEEP_WAKE = 1007
    const val ID_HABIT_BASE = 2000

    const val ACTION_FINISH_FASTING = "com.projectxo.app.FINISH_FASTING"
    const val ACTION_BED = "com.projectxo.app.SLEEP_BED"
    const val ACTION_WAKE = "com.projectxo.app.SLEEP_WAKE"
    const val ACTION_HABIT_DONE = "com.projectxo.app.HABIT_DONE"
    const val EXTRA_HABIT_ID = "habit_id"

    private fun channels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_FASTING, context.getString(R.string.module_fasting), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_SLEEP, context.getString(R.string.module_sleep), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_HABITS, context.getString(R.string.module_habits), NotificationManager.IMPORTANCE_LOW))
    }

    private fun open(context: Context, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, code: Int, action: String, habitId: Long? = null): PendingIntent {
        val i = Intent(context, ModuleActionReceiver::class.java).setAction(action)
        if (habitId != null) i.putExtra(EXTRA_HABIT_ID, habitId)
        return PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { timeOfDay(it.hour * 60 + it.minute) }

    /** Голодание идёт: тихое уведомление с секундомером и кнопкой «Закончить». */
    @SuppressLint("MissingPermission")
    fun showFasting(context: Context, s: FastingSession) {
        if (!ReminderNotifier.canPost(context)) return
        channels(context)
        val goalMs = s.planHours * 3_600_000L
        val n = NotificationCompat.Builder(context, CH_FASTING)
            .setSmallIcon(R.drawable.ic_stat_xo)
            .setContentTitle(context.getString(R.string.fast_notif_title, context.getString(R.string.dur_h, s.planHours.toString())))
            .setContentText(context.getString(R.string.fast_notif_body, clock(FastingMath.eatingWindowStart(s.startMs, s.planHours))))
            .setUsesChronometer(true)
            .setWhen(s.startMs)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(goalMs.toInt() / 1000, ((System.currentTimeMillis() - s.startMs).coerceIn(0, goalMs) / 1000).toInt(), false)
            .setContentIntent(open(context, 3))
            .addAction(0, context.getString(R.string.fast_notif_finish), action(context, 4, ACTION_FINISH_FASTING))
            .build()
        NotificationManagerCompat.from(context).notify(ID_FASTING, n)
    }

    @SuppressLint("MissingPermission")
    fun showFastingGoal(context: Context, s: FastingSession) {
        if (!ReminderNotifier.canPost(context)) return
        channels(context)
        val n = NotificationCompat.Builder(context, CH_FASTING)
            .setSmallIcon(R.drawable.ic_stat_xo)
            .setContentTitle(context.getString(R.string.fast_notif_goal_title, s.planHours.toString()))
            .setContentText(context.getString(R.string.fast_notif_goal_body, clock(s.startMs + 24 * 3_600_000L)))
            .setSilent(true).setAutoCancel(true).setContentIntent(open(context, 3))
            .addAction(0, context.getString(R.string.fast_notif_finish), action(context, 4, ACTION_FINISH_FASTING))
            .build()
        NotificationManagerCompat.from(context).notify(ID_FASTING_GOAL, n)
    }

    @SuppressLint("MissingPermission")
    fun showFastingWindow(context: Context) {
        if (!ReminderNotifier.canPost(context)) return
        channels(context)
        val n = NotificationCompat.Builder(context, CH_FASTING)
            .setSmallIcon(R.drawable.ic_stat_xo)
            .setContentTitle(context.getString(R.string.fast_notif_window_title))
            .setContentText(context.getString(R.string.fast_notif_window_body))
            .setSilent(true).setAutoCancel(true).setContentIntent(open(context, 3))
            .build()
        NotificationManagerCompat.from(context).notify(ID_FASTING_WINDOW, n)
    }

    fun cancelFasting(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(ID_FASTING)
        nm.cancel(ID_FASTING_GOAL)
        nm.cancel(ID_FASTING_WINDOW)
    }

    @SuppressLint("MissingPermission")
    fun showBedReminder(context: Context, bedMin: Int) {
        if (!ReminderNotifier.canPost(context)) return
        channels(context)
        val n = NotificationCompat.Builder(context, CH_SLEEP)
            .setSmallIcon(R.drawable.ic_stat_xo)
            .setContentTitle(context.getString(R.string.sleep_notif_bed_title))
            .setContentText(context.getString(R.string.sleep_notif_bed_body, timeOfDay(bedMin)))
            .setSilent(true).setAutoCancel(true).setContentIntent(open(context, 5))
            .addAction(0, context.getString(R.string.sleep_bed), action(context, 6, ACTION_BED))
            .build()
        NotificationManagerCompat.from(context).notify(ID_SLEEP_BED, n)
    }

    @SuppressLint("MissingPermission")
    fun showWakePrompt(context: Context) {
        if (!ReminderNotifier.canPost(context)) return
        channels(context)
        val n = NotificationCompat.Builder(context, CH_SLEEP)
            .setSmallIcon(R.drawable.ic_stat_xo)
            .setContentTitle(context.getString(R.string.sleep_notif_wake_title))
            .setContentText(context.getString(R.string.sleep_notif_wake_body))
            .setSilent(true).setAutoCancel(true).setContentIntent(open(context, 5))
            .addAction(0, context.getString(R.string.sleep_wake), action(context, 7, ACTION_WAKE))
            .build()
        NotificationManagerCompat.from(context).notify(ID_SLEEP_WAKE, n)
    }

    @SuppressLint("MissingPermission")
    fun showHabit(context: Context, habitId: Long, name: String) {
        if (!ReminderNotifier.canPost(context)) return
        channels(context)
        val n = NotificationCompat.Builder(context, CH_HABITS)
            .setSmallIcon(R.drawable.ic_stat_xo)
            .setContentTitle(name)
            .setContentText(context.getString(R.string.habits_notif_body))
            .setSilent(true).setAutoCancel(true).setContentIntent(open(context, 8))
            .addAction(0, context.getString(R.string.habits_notif_done), action(context, 9 + habitId.toInt(), ACTION_HABIT_DONE, habitId))
            .build()
        NotificationManagerCompat.from(context).notify(ID_HABIT_BASE + habitId.toInt(), n)
    }

    // ——— Планирование ———

    private const val WORK_GOAL = "fasting-goal"
    private const val WORK_WINDOW = "fasting-window"

    /** Привести уведомления и таймеры голодания в соответствие с текущим состоянием. */
    fun syncFasting(context: Context, s: FastingSession?) {
        val wm = WorkManager.getInstance(context)
        if (s == null) {
            wm.cancelUniqueWork(WORK_GOAL)
            wm.cancelUniqueWork(WORK_WINDOW)
            cancelFasting(context)
            return
        }
        showFasting(context, s)
        val now = System.currentTimeMillis()
        val data = Data.Builder().putLong("start", s.startMs).putInt("plan", s.planHours).putLong("id", s.id).build()
        val goalAt = FastingMath.eatingWindowStart(s.startMs, s.planHours)
        wm.enqueueUniqueWork(
            WORK_GOAL, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FastingWorker>().setInitialDelay((goalAt - now).coerceAtLeast(0), TimeUnit.MILLISECONDS).setInputData(Data.Builder().putAll(data).putString("kind", "goal").build()).build(),
        )
        val windowWarnAt = s.startMs + 24 * 3_600_000L - FastingMath.WINDOW_WARN_MS
        if (windowWarnAt > now) {
            wm.enqueueUniqueWork(
                WORK_WINDOW, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FastingWorker>().setInitialDelay(windowWarnAt - now, TimeUnit.MILLISECONDS).setInputData(Data.Builder().putAll(data).putString("kind", "window").build()).build(),
            )
        } else {
            wm.cancelUniqueWork(WORK_WINDOW)
        }
    }
}

/** Срабатывает к концу цели и за 15 минут до конца окна. Проверяет, что то же голодание ещё идёт. */
class FastingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as XoApplication).container
        val cur = container.fasting.current().first() ?: return Result.success()
        if (cur.id != inputData.getLong("id", -1L) || cur.startMs != inputData.getLong("start", -1L)) return Result.success()
        when (inputData.getString("kind")) {
            "goal" -> Wave2Notifier.showFastingGoal(applicationContext, cur)
            "window" -> Wave2Notifier.showFastingWindow(applicationContext)
        }
        return Result.success()
    }
}

/** Кнопки в уведомлениях модулей: закончить голодание, лечь и встать, отметить привычку. Приложение не открывается. */
class ModuleActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as XoApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                val now = container.clock.nowMs()
                when (intent.action) {
                    Wave2Notifier.ACTION_FINISH_FASTING -> container.fasting.current().first()?.let {
                        val end = now.coerceAtLeast(it.startMs + 60_000L)
                        container.fasting.finish(it.id, end)
                    }
                    Wave2Notifier.ACTION_BED -> if (container.sleep.current().first() == null) container.sleep.start(now)
                    Wave2Notifier.ACTION_WAKE -> container.sleep.current().first()?.let {
                        if (com.projectxo.app.core.model.SleepMath.isValidNight(it.startMs, now)) container.sleep.finish(it.id, now) else container.sleep.delete(it.id)
                    }
                    Wave2Notifier.ACTION_HABIT_DONE -> {
                        val id = intent.getLongExtra(Wave2Notifier.EXTRA_HABIT_ID, -1L)
                        container.habits.habits().first().firstOrNull { it.id == id }?.let {
                            container.habits.setCheck(it.id, container.clock.today(), it.target)
                            androidx.core.app.NotificationManagerCompat.from(context).cancel(Wave2Notifier.ID_HABIT_BASE + id.toInt())
                        }
                    }
                }
                if (intent.action == Wave2Notifier.ACTION_WAKE) androidx.core.app.NotificationManagerCompat.from(context).cancel(Wave2Notifier.ID_SLEEP_WAKE)
                if (intent.action == Wave2Notifier.ACTION_BED) androidx.core.app.NotificationManagerCompat.from(context).cancel(Wave2Notifier.ID_SLEEP_BED)
            } finally {
                pending.finish()
            }
        }
    }
}
