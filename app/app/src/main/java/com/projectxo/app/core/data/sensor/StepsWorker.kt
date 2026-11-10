package com.projectxo.app.core.data.sensor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.projectxo.app.XoApplication
import java.util.concurrent.TimeUnit

/**
 * Фоновая проверка датчика раз в 15 минут (минимум WorkManager): чтобы шаги раскладывались по часам
 * точнее, даже когда приложение закрыто. Без постоянной службы в фоне (Р25).
 */
class StepsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as XoApplication).container
        val counter = container.stepSensor.readOnce() ?: return Result.success()
        container.stepsRecorder.onReading(counter, container.clock.nowMs())
        return Result.success()
    }

    companion object {
        private const val NAME = "steps-sensor"
        private const val PERIOD_MIN = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepsWorker>(PERIOD_MIN, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
