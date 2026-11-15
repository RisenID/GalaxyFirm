package dev.risenid.galaxyfirm.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.data.Settings
import dev.risenid.galaxyfirm.data.model.ScanTrigger
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ScanWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!hasNetwork(applicationContext)) {
            return Result.retry()
        }
        val session = ServiceLocator.repository(applicationContext).scanAll(ScanTrigger.AUTO)
        if (session != null &&
            session.deviceCount > 0 &&
            session.failedCount == session.deviceCount &&
            !hasNetwork(applicationContext)
        ) {
            return Result.retry()
        }
        ScanScheduler.apply(applicationContext, reschedule = false)
        return Result.success()
    }

    private fun hasNetwork(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

object ScanScheduler {
    const val WORK_NAME = "auto_scan"
    const val WORK_ONCE_NAME = "auto_scan_once"
    private const val ALARM_REQUEST_CODE = 2001

    fun exactAlarmsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java)
            ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun apply(context: Context, reschedule: Boolean = true) {
        val appContext = context.applicationContext
        val settings = ServiceLocator.settings(appContext)
        val workManager = WorkManager.getInstance(appContext)
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val pendingIntent = alarmPendingIntent(appContext)

        if (!settings.autoScan) {
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelUniqueWork(WORK_ONCE_NAME)
            alarmManager?.cancel(pendingIntent)
            settings.nextScanAt = 0L
            return
        }

        val now = System.currentTimeMillis()
        val intervalMs = settings.intervalMinutes * 60_000L
        val existingNext = settings.nextScanAt
        val triggerAt = if (!reschedule && existingNext > now && existingNext - now <= intervalMs) {
            existingNext
        } else {
            now + intervalMs
        }
        settings.nextScanAt = triggerAt

        if (exactAlarmsAllowed(appContext) && alarmManager != null) {
            workManager.cancelUniqueWork(WORK_NAME)
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } catch (_: SecurityException) {
                enqueuePeriodic(workManager, settings, reschedule)
            }
        } else {
            alarmManager?.cancel(pendingIntent)
            enqueuePeriodic(workManager, settings, reschedule)
        }
    }

    fun nextRunAt(context: Context): Long? {
        val settings = ServiceLocator.settings(context.applicationContext)
        if (!settings.autoScan) return null
        val scheduled = settings.nextScanAt
        val now = System.currentTimeMillis()
        if (scheduled > now) return scheduled
        val intervalMs = settings.intervalMinutes * 60_000L
        val last = settings.lastScanAt
        return if (last > 0L) last + intervalMs else now + intervalMs
    }

    private fun enqueuePeriodic(
        workManager: WorkManager,
        settings: Settings,
        reschedule: Boolean
    ) {
        val networkType = if (settings.wifiOnly) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()
        val request = PeriodicWorkRequestBuilder<ScanWorker>(
            max(15, settings.intervalMinutes).toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        val policy = if (reschedule) {
            ExistingPeriodicWorkPolicy.UPDATE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            policy,
            request
        )
    }

    private fun alarmPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, ScanAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
