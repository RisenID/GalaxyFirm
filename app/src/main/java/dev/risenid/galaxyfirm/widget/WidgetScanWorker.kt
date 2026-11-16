package dev.risenid.galaxyfirm.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.data.model.ScanTrigger

class WidgetScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ServiceLocator.repository(applicationContext).scanAll(ScanTrigger.MANUAL)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "widget_scan"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetScanWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
