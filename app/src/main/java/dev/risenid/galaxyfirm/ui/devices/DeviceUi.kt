package dev.risenid.galaxyfirm.ui.devices

import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.data.Repository
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.model.DeviceStatus
import dev.risenid.galaxyfirm.data.model.DeviceType
import dev.risenid.galaxyfirm.data.model.ScanSession
import dev.risenid.galaxyfirm.data.net.UpdateType
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val EXTRA_DEVICE_ID = "device_id"

internal val deviceScope = MainScope()

private val checking = MutableStateFlow(emptySet<Long>())

internal val checkingIds: StateFlow<Set<Long>> = checking.asStateFlow()

internal fun Repository.checkDevice(id: Long) {
    if (id in checking.value) return
    checking.update { it + id }
    deviceScope.launch {
        try {
            scanOne(id)
        } finally {
            checking.update { it - id }
        }
    }
}

@get:DrawableRes
internal val DeviceType.illustration: Int
    get() = when (this) {
        DeviceType.PHONE -> R.drawable.ic_device_phone
        DeviceType.FOLDABLE -> R.drawable.ic_device_foldable
        DeviceType.TABLET -> R.drawable.ic_device_tablet
        DeviceType.WATCH -> R.drawable.ic_device_watch
        DeviceType.BUDS -> R.drawable.ic_device_buds
    }

@get:StringRes
internal val DeviceType.label: Int
    get() = when (this) {
        DeviceType.PHONE -> R.string.device_type_phone
        DeviceType.FOLDABLE -> R.string.device_type_foldable
        DeviceType.TABLET -> R.string.device_type_tablet
        DeviceType.WATCH -> R.string.device_type_watch
        DeviceType.BUDS -> R.string.device_type_buds
    }

internal fun TextView.bindBadge(status: DeviceStatus, updateType: UpdateType? = null) {
    val (background, color, label) = when (status) {
        DeviceStatus.NEW_UPDATE -> when (updateType) {
            UpdateType.MAJOR -> Triple(R.drawable.bg_badge_major, R.color.status_major, R.string.device_status_major)
            UpdateType.SECURITY -> Triple(R.drawable.bg_badge_new, R.color.status_new, R.string.device_status_security)
            else -> Triple(R.drawable.bg_badge_new, R.color.status_new, R.string.device_status_new)
        }
        DeviceStatus.FAILED -> Triple(R.drawable.bg_badge_failed, R.color.status_failed, R.string.device_status_failed)
        DeviceStatus.UP_TO_DATE -> Triple(R.drawable.bg_badge_neutral, R.color.status_neutral, R.string.device_status_up_to_date)
        DeviceStatus.UNKNOWN -> Triple(R.drawable.bg_badge_neutral, R.color.status_neutral, R.string.device_status_unknown)
    }
    setBackgroundResource(background)
    setTextColor(ContextCompat.getColor(context, color))
    setText(label)
}

internal fun Context.cscRegion(code: String): String? {
    val index = resources.getStringArray(R.array.device_csc_codes).indexOf(code.uppercase())
    return if (index >= 0) resources.getStringArray(R.array.device_csc_regions)[index] else null
}

internal fun Context.scanSummary(session: ScanSession): String = when {
    session.newCount == 1 -> getString(R.string.devices_scan_new_one)
    session.newCount > 1 -> getString(R.string.devices_scan_new, session.newCount)
    session.failedCount > 0 -> getString(R.string.devices_scan_failed, session.failedCount)
    else -> getString(R.string.devices_scan_done)
}

internal fun Context.openDeviceEditor(id: Long? = null) {
    startActivity(Intent(this, DeviceEditActivity::class.java).apply {
        id?.let { putExtra(EXTRA_DEVICE_ID, it) }
    })
}

internal fun Context.openDeviceDetail(id: Long) {
    startActivity(Intent(this, DeviceDetailActivity::class.java).putExtra(EXTRA_DEVICE_ID, id))
}

internal fun Context.confirmDeleteDevice(device: Device) {
    val repository = ServiceLocator.repository(this)
    AlertDialog.Builder(this)
        .setTitle(getString(R.string.device_delete_title, device.name))
        .setMessage(R.string.device_delete_message)
        .setNegativeButton(R.string.devices_cancel, null)
        .setPositiveButton(R.string.devices_delete) { _, _ ->
            deviceScope.launch { repository.deleteDevice(device) }
        }
        .show()
}
