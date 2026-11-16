package dev.risenid.galaxyfirm.ui.devices

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.data.RegionBuild
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.net.BuildVersion
import dev.risenid.galaxyfirm.databinding.ItemRegionBinding
import dev.risenid.galaxyfirm.databinding.SheetRegionsBinding
import dev.oneuiproject.oneui.app.SemBottomSheetDialogFragment
import dev.oneuiproject.oneui.ktx.semToast
import kotlinx.coroutines.launch

class RegionsSheet : SemBottomSheetDialogFragment(R.layout.sheet_regions) {

    private val repository by lazy { ServiceLocator.repository(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = SheetRegionsBinding.bind(view)
        val id = requireArguments().getLong(EXTRA_DEVICE_ID)
        viewLifecycleOwner.lifecycleScope.launch {
            val device = repository.getDevice(id) ?: return@launch dismiss()
            binding.subtitle.text = device.model
            val codes = resources.getStringArray(R.array.device_csc_codes).filter { it != device.csc }
            val builds = repository.otherRegions(device.model, codes)
            binding.loading.isVisible = false
            binding.empty.isVisible = builds.isEmpty()
            binding.scroll.isVisible = builds.isNotEmpty()
            builds.forEachIndexed { index, build -> addRow(binding.list, device, build, index == 0) }
        }
    }

    private fun addRow(list: LinearLayout, device: Device, build: RegionBuild, first: Boolean) {
        val context = requireContext()
        val row = ItemRegionBinding.inflate(layoutInflater, list, true)
        row.divider.isVisible = !first
        row.region.text = context.regionLabel(build.csc)
        row.firmware.text = build.firmware
        val diff = device.firmware?.let { BuildVersion.compare(build.firmware, it) }
        row.tag.isVisible = diff != null
        if (diff != null) {
            row.tag.setText(
                when {
                    diff > 0 -> R.string.device_regions_newer
                    diff == 0 -> R.string.device_regions_same
                    else -> R.string.device_regions_older
                }
            )
            if (diff > 0) {
                row.tag.setBackgroundResource(R.drawable.bg_badge_new)
                row.tag.setTextColor(ContextCompat.getColor(context, R.color.status_new))
            }
        }
        row.row.setOnClickListener { confirmTrack(context, device, build.csc) }
    }

    private fun confirmTrack(context: Context, device: Device, csc: String) {
        AlertDialog.Builder(context)
            .setTitle(context.regionLabel(csc))
            .setMessage(context.getString(R.string.device_regions_track_message, device.model, context.cscRegion(csc) ?: csc))
            .setNegativeButton(R.string.devices_cancel, null)
            .setPositiveButton(R.string.device_regions_track) { _, _ -> track(context, device, csc) }
            .show()
    }

    private fun track(context: Context, device: Device, csc: String) {
        deviceScope.launch {
            val name = context.getString(R.string.device_regions_name, device.name, csc)
            repository.addDevice(name, device.model, csc, device.type)
                .onSuccess { id ->
                    repository.checkDevice(id)
                    context.semToast(context.getString(R.string.device_regions_added))
                }
                .onFailure { error ->
                    val message = if (error is IllegalStateException) R.string.device_edit_error_duplicate else R.string.device_edit_error_generic
                    context.semToast(context.getString(message))
                }
        }
    }

    private fun Context.regionLabel(csc: String): String =
        cscRegion(csc)?.let { getString(R.string.device_edit_csc_item, it, csc) } ?: csc

    companion object {
        private const val TAG = "regions"

        fun show(manager: FragmentManager, deviceId: Long) {
            if (manager.findFragmentByTag(TAG) != null) return
            RegionsSheet().apply { arguments = Bundle().apply { putLong(EXTRA_DEVICE_ID, deviceId) } }.show(manager, TAG)
        }
    }
}
