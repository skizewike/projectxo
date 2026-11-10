package com.projectxo.app.core.data.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Датчик шагов телефона (`TYPE_STEP_COUNTER`): общий счётчик с момента включения. */
class StepSensor(private val context: Context) {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val isAvailable: Boolean get() = sensor != null

    /** На Android 10+ нужно разрешение «Физическая активность»; раньше оно не требовалось. */
    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Показания, пока кто-то слушает. Частые события сливаются: важно только последнее. */
    fun readings(): Flow<Long> = callbackFlow {
        val s = sensor
        if (s == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0].toLong())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()

    /** Одно показание (для фоновой проверки) или null, если датчик не ответил. */
    suspend fun readOnce(timeoutMs: Long = READ_TIMEOUT_MS): Long? =
        if (!isAvailable || !hasPermission()) null else withTimeoutOrNull(timeoutMs) { readings().first() }

    private companion object {
        const val READ_TIMEOUT_MS = 10_000L
    }
}
