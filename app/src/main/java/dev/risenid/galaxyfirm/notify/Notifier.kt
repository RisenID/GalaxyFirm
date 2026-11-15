package dev.risenid.galaxyfirm.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.net.UpdateType

class Notifier(private val context: Context) {
    private val appContext = context.applicationContext

    fun ensureChannels() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val updatesChannel = NotificationChannel(
            CHANNEL_UPDATES,
            "Firmware updates",
            NotificationManager.IMPORTANCE_HIGH
        )
        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            "Scan status",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannels(listOf(updatesChannel, statusChannel))
    }

    @SuppressLint("MissingPermission")
    fun notifyUpdate(device: Device) {
        if (!canNotify()) return
        ensureChannels()
        val title = when (device.updateType) {
            UpdateType.MAJOR -> "Major update available"
            UpdateType.SECURITY -> "Security update available"
            else -> "New update available"
        }
        val androidSuffix = if (device.updateType == UpdateType.MAJOR && !device.androidVersion.isNullOrBlank()) {
            " · Android ${device.androidVersion}"
        } else {
            ""
        }
        val body = "${device.name} · ${device.model} (${device.csc})\nVersion: ${device.firmware}$androidSuffix"
        val notification = NotificationCompat.Builder(appContext, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launchPendingIntent(device.id.toInt()))
            .build()
        NotificationManagerCompat.from(appContext).notify(device.id.toInt(), notification)
    }

    @SuppressLint("MissingPermission")
    fun notifyFailures(count: Int) {
        if (!canNotify() || count <= 0) return
        ensureChannels()
        val text = if (count == 1) {
            "1 device failed during automatic check"
        } else {
            "$count devices failed during automatic check"
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("Firmware check failed")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(launchPendingIntent(FAILURE_NOTIFICATION_ID))
            .build()
        NotificationManagerCompat.from(appContext).notify(FAILURE_NOTIFICATION_ID, notification)
    }

    fun isChannelBlocked(channelId: String = CHANNEL_UPDATES): Boolean {
        ensureChannels()
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(channelId) ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun launchPendingIntent(requestCode: Int): PendingIntent? {
        val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            ?: return null
        return PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_UPDATES = "firmware_updates"
        const val CHANNEL_STATUS = "scan_status"
        private const val FAILURE_NOTIFICATION_ID = -1001
    }
}
