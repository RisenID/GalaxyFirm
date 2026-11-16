package dev.risenid.galaxyfirm.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.risenid.galaxyfirm.ServiceLocator
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DigestWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repository = ServiceLocator.repository(applicationContext)
        if (ServiceLocator.settings(applicationContext).digest) repository.postDigest() else repository.clearDigest()
        return Result.success()
    }
}

object DigestScheduler {
    const val WORK_NAME = "daily_digest"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun apply(context: Context, reschedule: Boolean = true) {
        val appContext = context.applicationContext
        val settings = ServiceLocator.settings(appContext)
        val workManager = WorkManager.getInstance(appContext)
        if (!settings.digest) {
            workManager.cancelUniqueWork(WORK_NAME)
            if (reschedule) scope.launch { ServiceLocator.repository(appContext).clearDigest() }
            return
        }
        val request = PeriodicWorkRequestBuilder<DigestWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(settings.digestHour), TimeUnit.MILLISECONDS)
            .build()
        val policy = if (reschedule) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    private fun delayUntil(hour: Int): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
