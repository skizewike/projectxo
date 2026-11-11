package com.projectxo.app.core.data.shared

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.projectxo.app.XoApplication
import java.util.concurrent.TimeUnit

/** Раз в полчаса забирает отметки друга и отправляет свои, пока есть общие привычки (Р79). Только при сети. */
class SharedSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? XoApplication)?.container ?: return Result.success()
        container.shared.syncAll(container.clock.today())
        return Result.success()
    }

    companion object {
        const val NAME = "shared_sync"
        private const val PERIOD_MIN = 30L

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<SharedSyncWorker>(PERIOD_MIN, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
