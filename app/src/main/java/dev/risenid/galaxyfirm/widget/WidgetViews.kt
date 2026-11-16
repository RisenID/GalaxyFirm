package dev.risenid.galaxyfirm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.model.DeviceStatus
import dev.risenid.galaxyfirm.data.model.DeviceType
import dev.risenid.galaxyfirm.data.net.UpdateType
import dev.risenid.galaxyfirm.ui.MainActivity
import dev.risenid.galaxyfirm.ui.devices.DeviceDetailActivity
import dev.risenid.galaxyfirm.ui.devices.EXTRA_DEVICE_ID
import dev.risenid.galaxyfirm.ui.devices.showsComingSoon

internal fun List<Device>.widgetOrder(): List<Device> = sortedBy {
    when {
        it.status == DeviceStatus.NEW_UPDATE -> 0
        it.showsComingSoon -> 1
        else -> 2
    }
}

@get:DrawableRes
private val DeviceType.widgetIcon: Int
    get() = when (this) {
        DeviceType.PHONE -> R.drawable.ic_widget_device_phone
        DeviceType.FOLDABLE -> R.drawable.ic_widget_device_foldable
        DeviceType.TABLET -> R.drawable.ic_widget_device_tablet
        DeviceType.WATCH -> R.drawable.ic_widget_device_watch
        DeviceType.BUDS -> R.drawable.ic_widget_device_buds
    }

private val Device.badge: Triple<Int, Int, Int>
    get() = if (showsComingSoon) {
        Triple(R.drawable.bg_badge_soon, R.color.status_soon, R.string.device_status_soon)
    } else when (status) {
        DeviceStatus.NEW_UPDATE -> when (updateType) {
            UpdateType.MAJOR -> Triple(R.drawable.bg_badge_major, R.color.status_major, R.string.device_status_major)
            UpdateType.SECURITY -> Triple(R.drawable.bg_badge_new, R.color.status_new, R.string.device_status_security)
            else -> Triple(R.drawable.bg_badge_new, R.color.status_new, R.string.device_status_new)
        }
        DeviceStatus.FAILED -> Triple(R.drawable.bg_badge_failed, R.color.status_failed, R.string.device_status_failed)
        DeviceStatus.UP_TO_DATE -> Triple(R.drawable.bg_badge_neutral, R.color.status_neutral, R.string.device_status_up_to_date)
        DeviceStatus.UNKNOWN -> Triple(R.drawable.bg_badge_neutral, R.color.status_neutral, R.string.device_status_unknown)
    }

private fun RemoteViews.setTextColorRes(context: Context, viewId: Int, @ColorRes color: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setColorStateList(viewId, "setTextColor", color)
    } else {
        setTextColor(viewId, context.getColor(color))
    }
}

internal fun Context.deviceRow(device: Device): RemoteViews =
    RemoteViews(packageName, R.layout.widget_device_row).apply {
        val (background, color, label) = device.badge
        setImageViewResource(R.id.widget_row_icon, device.type.widgetIcon)
        setTextViewText(R.id.widget_row_name, device.name)
        setTextViewText(R.id.widget_row_firmware, device.firmware ?: getString(R.string.devices_not_checked_yet))
        setTextColorRes(
            this@deviceRow,
            R.id.widget_row_firmware,
            if (device.status == DeviceStatus.NEW_UPDATE) R.color.status_new else R.color.widget_text_secondary
        )
        setInt(R.id.widget_row_badge, "setBackgroundResource", background)
        setTextColorRes(this@deviceRow, R.id.widget_row_badge, color)
        setTextViewText(R.id.widget_row_badge, getString(label))
        setOnClickFillInIntent(R.id.widget_row, Intent().putExtra(EXTRA_DEVICE_ID, device.id))
    }

private fun Context.summary(devices: List<Device>): String {
    if (devices.isEmpty()) return getString(R.string.widget_summary_no_devices)
    return listOf(
        R.plurals.widget_summary_updates to devices.count { it.status == DeviceStatus.NEW_UPDATE },
        R.plurals.widget_summary_soon to devices.count { it.showsComingSoon },
        R.plurals.widget_summary_failed to devices.count { it.status == DeviceStatus.FAILED }
    )
        .filter { it.second > 0 }
        .joinToString(getString(R.string.widget_summary_separator)) { (res, count) ->
            resources.getQuantityString(res, count, count)
        }
        .ifEmpty { getString(R.string.widget_summary_up_to_date) }
}

private fun Context.openAppIntent(): PendingIntent = PendingIntent.getActivity(
    this,
    0,
    Intent(this, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)

private fun Context.openDeviceTemplate(): PendingIntent = PendingIntent.getActivities(
    this,
    1,
    arrayOf(
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        Intent(this, DeviceDetailActivity::class.java)
    ),
    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)

private fun Context.refreshIntent(): PendingIntent = PendingIntent.getBroadcast(
    this,
    2,
    Intent(this, FirmwareWidgetProvider::class.java).setAction(FirmwareWidgetProvider.ACTION_REFRESH),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)

@Suppress("DEPRECATION")
private fun Context.widgetViews(devices: List<Device>, scanning: Boolean): RemoteViews =
    RemoteViews(packageName, R.layout.widget_firmware).apply {
        setTextViewText(R.id.widget_summary, summary(devices))
        setViewVisibility(R.id.widget_refresh, if (scanning) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_progress, if (scanning) View.VISIBLE else View.GONE)
        setOnClickPendingIntent(R.id.widget_header, openAppIntent())
        setOnClickPendingIntent(R.id.widget_refresh, refreshIntent())
        setRemoteAdapter(R.id.widget_list, Intent(this@widgetViews, FirmwareWidgetService::class.java))
        setEmptyView(R.id.widget_list, R.id.widget_empty)
        setPendingIntentTemplate(R.id.widget_list, openDeviceTemplate())
    }

@Suppress("DEPRECATION")
internal fun Context.updateWidgets(devices: List<Device>, scanning: Boolean) {
    val manager = AppWidgetManager.getInstance(this)
    val ids = manager.getAppWidgetIds(ComponentName(this, FirmwareWidgetProvider::class.java))
    if (ids.isEmpty()) return
    manager.updateAppWidget(ids, widgetViews(devices, scanning))
    manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
}
