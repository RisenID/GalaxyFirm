package dev.risenid.galaxyfirm.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.notify.Notifier

object Permissions {
    fun notificationsAllowed(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return !updatesChannelBlocked(appContext)
    }

    fun updatesChannelBlocked(context: Context): Boolean {
        val appContext = context.applicationContext
        ServiceLocator.notifier(appContext).ensureChannels()
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(Notifier.CHANNEL_UPDATES) ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    fun batteryUnrestricted(context: Context): Boolean {
        val appContext = context.applicationContext
        val pm = appContext.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    fun exactAlarmsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java)
            ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun missingCount(context: Context): Int {
        var count = 0
        if (!notificationsAllowed(context)) count++
        if (!batteryUnrestricted(context)) count++
        if (!exactAlarmsAllowed(context)) count++
        return count
    }

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    fun channelSettingsIntent(context: Context, channelId: String = Notifier.CHANNEL_UPDATES): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        }

    fun exactAlarmIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.fromParts("package", context.packageName, null)
            )
        } else {
            appDetailsIntent(context)
        }

    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )

    fun launchNotificationSettings(context: Context) {
        runCatching { context.startActivity(notificationSettingsIntent(context)) }.onFailure {
            runCatching { context.startActivity(appDetailsIntent(context)) }
        }
    }

    fun launchChannelSettings(context: Context, channelId: String = Notifier.CHANNEL_UPDATES) {
        runCatching { context.startActivity(channelSettingsIntent(context, channelId)) }.onFailure {
            launchNotificationSettings(context)
        }
    }

    fun launchBatteryExemption(context: Context) {
        val batteryPage = Intent(
            "android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL",
            Uri.fromParts("package", context.packageName, null)
        )
        runCatching { context.startActivity(batteryPage) }.onFailure {
            runCatching { context.startActivity(appDetailsIntent(context)) }
        }
    }

    fun launchExactAlarmSettings(context: Context) {
        runCatching { context.startActivity(exactAlarmIntent(context)) }.onFailure {
            runCatching { context.startActivity(appDetailsIntent(context)) }
        }
    }
}
