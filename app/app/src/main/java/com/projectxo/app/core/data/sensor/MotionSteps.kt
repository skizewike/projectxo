package com.projectxo.app.core.data.sensor

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.projectxo.app.MainActivity
import com.projectxo.app.R
import com.projectxo.app.XoApplication
import com.projectxo.app.core.data.HourSteps
import com.projectxo.app.core.steps.MotionStepDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/**
 * Счёт шагов по движению телефона (Р65): только для телефонов без датчика шагов.
 * Включается пользователем на экране «Ходьба», выключается там же или кнопкой в уведомлении.
 */
class MotionSteps(private val context: Context, private val stepSensor: StepSensor) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Есть акселерометр, но нет датчика шагов: тогда и только тогда нужен свой счёт. */
    val available: Boolean
        get() = !stepSensor.isAvailable &&
            (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    /** Можно запускать: включено, доступно и есть разрешение «Физическая активность» (Android 10+). */
    fun shouldRun(): Boolean = _enabled.value && available && stepSensor.hasPermission()

    fun enable() {
        setEnabled(true)
        ensureRunning()
    }

    fun disable() {
        setEnabled(false)
        context.stopService(Intent(context, MotionStepsService::class.java))
    }

    /** Запуск, если нужно. Вызывается, когда приложение на экране, и после перезагрузки. */
    fun ensureRunning() {
        if (!shouldRun()) return
        runCatching { ContextCompat.startForegroundService(context, Intent(context, MotionStepsService::class.java)) }
    }

    private fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
    }

    private companion object {
        const val PREFS = "motion_steps"
        const val KEY_ENABLED = "enabled"
    }
}

/** Служба на переднем плане: слушает акселерометр и пишет шаги в базу каждые 15 секунд. */
class MotionStepsService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val detector = MotionStepDetector()
    private val pending = AtomicInteger(0)
    private val flushLock = Mutex()
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as XoApplication).container.motionSteps.disable()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            ensureChannel()
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(null), type)
            begin()
        }
        return START_STICKY
    }

    @SuppressLint("WakelockTimeout")
    private fun begin() {
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel == null) {
            stopSelf()
            return
        }
        // Без частичной блокировки сна телефон с выключенным экраном перестаёт присылать показания.
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xo:motion-steps").apply { acquire() }
        // ~50 показаний в секунду, система может присылать их пачками раз в несколько секунд.
        sm.registerListener(this, accel, SAMPLING_US, BATCH_US)
        scope.launch {
            refreshNotification()
            while (true) {
                delay(FLUSH_MS)
                flush()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val n = detector.onSample(event.timestamp, event.values[0], event.values[1], event.values[2])
        if (n > 0) pending.addAndGet(n)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private suspend fun flush() = flushLock.withLock {
        val n = pending.getAndSet(0)
        if (n <= 0) return@withLock
        val container = (application as XoApplication).container
        val now = Instant.ofEpochMilli(container.clock.nowMs()).atZone(ZoneId.systemDefault())
        container.steps.addSteps(listOf(HourSteps(now.toLocalDate(), now.hour, n)))
        refreshNotification()
    }

    private suspend fun refreshNotification() {
        val container = (application as XoApplication).container
        val total = container.steps.totalFor(container.clock.today()).first()
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(total))
    }

    private fun notification(total: Int?): android.app.Notification {
        val open = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 11, Intent(this, MotionStepsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Ресурсы приложения: на языке, выбранном в настройках.
        val res = applicationContext.resources
        val text = if (total == null) {
            res.getString(R.string.motion_notif_text_start)
        } else {
            val locale = java.util.Locale.forLanguageTag(res.getString(R.string.format_locale))
            res.getString(
                R.string.motion_notif_text,
                java.text.NumberFormat.getIntegerInstance(locale).format(total.toLong()),
                res.getQuantityString(R.plurals.steps_unit, total),
            )
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_xo)
            .setContentTitle(res.getString(R.string.motion_notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, res.getString(R.string.motion_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, applicationContext.getString(R.string.motion_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onDestroy() {
        (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(this)
        // Последние шаги — в базу, пока служба не ушла.
        runBlocking { runCatching { flush() } }
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "motion_steps"
        private const val NOTIFICATION_ID = 1101
        private const val ACTION_STOP = "com.projectxo.app.MOTION_STEPS_STOP"
        private const val FLUSH_MS = 15_000L
        private const val SAMPLING_US = 20_000
        private const val BATCH_US = 5_000_000
    }
}

/** После перезагрузки телефона счёт продолжается, если был включён. */
class MotionStepsBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        (context.applicationContext as XoApplication).container.motionSteps.ensureRunning()
    }
}
