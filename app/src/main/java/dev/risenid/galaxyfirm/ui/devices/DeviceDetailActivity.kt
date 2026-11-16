package dev.risenid.galaxyfirm.ui.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.model.DeviceStatus
import dev.risenid.galaxyfirm.data.net.UpdateType
import dev.risenid.galaxyfirm.databinding.ActivityDeviceDetailBinding
import dev.risenid.galaxyfirm.data.model.DeviceType
import dev.risenid.galaxyfirm.util.formatDate
import dev.risenid.galaxyfirm.util.formatStamp
import dev.risenid.galaxyfirm.util.osVersionDetail
import dev.risenid.galaxyfirm.util.osVersionLabel
import dev.oneuiproject.oneui.ktx.semToast
import dev.oneuiproject.oneui.widget.CardItemView
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dev.oneuiproject.oneui.design.R as designR

class DeviceDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceDetailBinding
    private val repository by lazy { ServiceLocator.repository(this) }
    private var device: Device? = null
    private var checking = false
    private lateinit var secondaryColor: ColorStateList

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        secondaryColor = binding.headerFirmware.textColors
        val id = intent.getLongExtra(EXTRA_DEVICE_ID, 0L)
        if (id <= 0L) return finish()
        binding.rowCurrent.copyable { it.firmware }
        binding.rowFullBuild.copyable { it.fullFirmware }
        binding.rowUpcoming.copyable { it.upcomingFirmware }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repository.devices.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged(),
                    checkingIds.map { id in it }.distinctUntilChanged()
                ) { found, busy -> found to busy }.collect { (found, busy) ->
                    if (found == null) finish() else render(found, busy)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_device_detail, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_device_detail_seen).isVisible = device?.status == DeviceStatus.NEW_UPDATE
        menu.findItem(R.id.menu_device_detail_check).isEnabled = !checking
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val current = device ?: return super.onOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_device_detail_check -> repository.checkDevice(current.id)
            R.id.menu_device_detail_edit -> openDeviceEditor(current.id)
            R.id.menu_device_detail_seen -> deviceScope.launch { repository.acknowledge(current) }
            R.id.menu_device_detail_delete -> confirmDeleteDevice(current)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun render(device: Device, busy: Boolean) {
        this.device = device
        checking = busy
        binding.toolbarLayout.setTitle(device.name)
        binding.toolbarLayout.setSubtitle(getString(R.string.device_detail_subtitle, device.model, device.csc))
        binding.illustration.setImageResource(device.type.illustration)
        binding.headerName.text = device.name
        binding.headerFirmware.text = device.firmware ?: getString(R.string.devices_not_checked_yet)
        if (device.status == DeviceStatus.NEW_UPDATE) {
            binding.headerFirmware.setTextColor(ContextCompat.getColor(this, R.color.status_new))
        } else {
            binding.headerFirmware.setTextColor(secondaryColor)
        }
        binding.badge.bindBadge(device)
        binding.badge.isInvisible = busy
        binding.progress.isVisible = busy
        binding.rowCurrent.summary = device.firmware ?: getString(R.string.devices_not_checked_yet)
        val isNew = device.status == DeviceStatus.NEW_UPDATE
        binding.rowUpdateType.isVisible = isNew
        if (isNew) {
            binding.rowUpdateType.summary = updateTypeSummary(device.type, device.updateType, device.previousAndroidVersion, device.androidVersion)
        }
        val upcoming = device.upcomingFirmware
        binding.upcomingGroup.isVisible = upcoming != null
        if (upcoming != null) {
            binding.rowUpcoming.summary = upcoming
            binding.rowUpcomingType.summary = updateTypeSummary(device.type, device.upcomingType, device.androidVersion, device.upcomingAndroidVersion)
            binding.rowUpcomingReleased.show(device.upcomingReleasedAt?.let(::formatDate))
            binding.rowUpcomingSpotted.show(device.upcomingFoundAt?.let { formatStamp(this, it) })
        }
        binding.rowReleased.show(device.releasedAt?.let(::formatDate))
        binding.rowFullBuild.show(device.fullFirmware?.let(::formatBuild))
        binding.rowPrevious.show(device.previousFirmware)
        binding.rowAndroid.title = getString(if (device.type == DeviceType.WATCH) R.string.device_detail_wear_os else R.string.device_detail_android)
        binding.rowAndroid.show(device.androidVersion?.let { osVersionDetail(device.type, it) })
        binding.rowRegion.summary = cscRegion(device.csc)
            ?.let { getString(R.string.device_detail_subtitle, device.csc, it) } ?: device.csc
        binding.rowLastChecked.summary = device.lastCheckedAt?.let { formatStamp(this, it) }
            ?: getString(R.string.device_detail_never)
        binding.rowUpdateFound.show(device.updateFoundAt?.let { formatStamp(this, it) })
        binding.rowError.show(device.lastError?.takeIf { device.status == DeviceStatus.FAILED })
        invalidateOptionsMenu()
    }

    private fun updateTypeSummary(deviceType: DeviceType, type: UpdateType?, oldOs: String?, newOs: String?): String = when (type) {
        UpdateType.MAJOR -> {
            if (!oldOs.isNullOrBlank() && !newOs.isNullOrBlank() && oldOs != newOs) {
                getString(R.string.device_detail_update_type_major_android, osVersionLabel(deviceType, oldOs), osVersionLabel(deviceType, newOs))
            } else {
                getString(R.string.device_status_major)
            }
        }
        UpdateType.SECURITY -> getString(R.string.device_status_security)
        else -> getString(R.string.device_detail_update_type_regular)
    }

    private fun formatBuild(full: String): String {
        val parts = full.split('/').map { it.trim() }
        return if (parts.size >= 3) {
            getString(
                R.string.device_detail_full_build_value,
                parts[0],
                parts[1].ifEmpty { "-" },
                parts[2].ifEmpty { "-" }
            )
        } else {
            full
        }
    }

    private fun CardItemView.show(value: String?) {
        isVisible = value != null
        summary = value
    }

    private fun CardItemView.copyable(value: (Device) -> String?) {
        val copy = View.OnLongClickListener {
            device?.let(value)?.let(::copyToClipboard)
            true
        }
        setOnClickListener { copy.onLongClick(it) }
        findViewById<View>(designR.id.cardview_container).setOnLongClickListener(copy)
    }

    private fun copyToClipboard(text: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.device_detail_full_build), text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) semToast(getString(R.string.device_detail_copied))
    }
}
