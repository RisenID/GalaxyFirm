package dev.risenid.galaxyfirm.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.data.model.ScanTrigger
import java.util.concurrent.TimeUnit

class ScanWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        ServiceLocator.repository(applicationContext).scanAll(ScanTrigger.AUTO)
        return Result.success()
    }
}

object ScanScheduler {
    const val WORK_NAME = "auto_scan"

    fun apply(context: Context) {
        val appContext = context.applicationContext
        val settings = ServiceLocator.settings(appContext)
        val workManager = WorkManager.getInstance(appContext)
        if (settings.autoScan) {
            val networkType = if (settings.wifiOnly) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
            val request = PeriodicWorkRequestBuilder<ScanWorker>(
                settings.intervalMinutes.toLong(),
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    fun nextRunAt(context: Context): Long? {
        val settings = ServiceLocator.settings(context.applicationContext)
        if (!settings.autoScan) return null
        val intervalMs = settings.intervalMinutes * 60_000L
        val last = settings.lastScanAt
        return if (last > 0L) last + intervalMs else System.currentTimeMillis() + intervalMs
    }
}
