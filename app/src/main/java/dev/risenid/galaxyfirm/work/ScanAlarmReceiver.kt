package dev.risenid.galaxyfirm.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dev.risenid.galaxyfirm.ServiceLocator

class ScanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val settings = ServiceLocator.settings(appContext)
        if (!settings.autoScan) {
            ScanScheduler.apply(appContext)
            return
        }
        val networkType = if (settings.wifiOnly) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            ScanScheduler.WORK_ONCE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        ScanScheduler.apply(appContext)
    }
}
