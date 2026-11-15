package dev.risenid.galaxyfirm.ui.devices

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.model.DeviceCounts
import dev.risenid.galaxyfirm.data.model.DeviceStatus
import dev.risenid.galaxyfirm.data.model.ScanTrigger
import dev.risenid.galaxyfirm.databinding.FragmentDevicesBinding
import dev.risenid.galaxyfirm.ui.TabScreen
import dev.risenid.galaxyfirm.ui.settings.PermissionsActivity
import dev.risenid.galaxyfirm.util.Permissions
import dev.risenid.galaxyfirm.util.formatTime
import dev.risenid.galaxyfirm.util.intervalShort
import dev.risenid.galaxyfirm.work.ScanScheduler
import com.google.android.material.tabs.TabLayout
import dev.oneuiproject.oneui.ktx.semToast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import dev.oneuiproject.oneui.R as iconsR

class DevicesFragment : Fragment(R.layout.fragment_devices), TabScreen {

    private enum class Filter(val label: Int) {
        ALL(R.string.devices_filter_all),
        NEW(R.string.devices_filter_new),
        UP_TO_DATE(R.string.devices_filter_up_to_date),
        FAILED(R.string.devices_filter_failed);

        fun matches(device: Device) = when (this) {
            ALL -> true
            NEW -> device.status == DeviceStatus.NEW_UPDATE
            UP_TO_DATE -> device.status == DeviceStatus.UP_TO_DATE
            FAILED -> device.status == DeviceStatus.FAILED
        }

        fun count(counts: DeviceCounts) = when (this) {
            ALL -> counts.all
            NEW -> counts.new
            UP_TO_DATE -> counts.upToDate
            FAILED -> counts.failed
        }
    }

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!
    private val repository by lazy { ServiceLocator.repository(requireContext()) }
    private val settings by lazy { ServiceLocator.settings(requireContext()) }
    private val filter = MutableStateFlow(Filter.ALL)
    private var devices: List<Device> = emptyList()
    private var tipDismissed = false
    private val adapter = DevicesAdapter(
        onClick = { requireContext().openDeviceDetail(it.id) },
        onLongClick = ::showItemMenu
    )

    override val menuRes: Int = R.menu.menu_devices

    override fun onMenuItem(itemId: Int): Boolean = when (itemId) {
        R.id.menu_devices_check_now -> {
            scanAll()
            true
        }
        R.id.menu_devices_add -> {
            requireContext().openDeviceEditor()
            true
        }
        else -> false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDevicesBinding.bind(view)
        savedInstanceState?.let {
            filter.value = Filter.entries[it.getInt(KEY_FILTER)]
            tipDismissed = it.getBoolean(KEY_TIP_DISMISSED, false)
        }
        setupPermissionTip()
        setupTabs()
        binding.deviceList.layoutManager = LinearLayoutManager(requireContext())
        binding.deviceList.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener {
            if (!settings.pullTipSeen) {
                settings.pullTipSeen = true
                binding.pullTipRow.isVisible = false
            }
            scanAll()
        }
        binding.autoScanCard.setOnClickListener {
            runCatching {
                startActivity(Intent().setClassName(requireContext(), AUTO_SCAN_ACTIVITY))
            }
        }
        binding.emptyAdd.setOnClickListener { requireContext().openDeviceEditor() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { settings.observe().collect { bindAutoScan() } }
                launch { repository.scanning.collect { binding.swipeRefresh.isRefreshing = it } }
                launch {
                    combine(repository.devices, filter, checkingIds) { list, f, ids -> Triple(list, f, ids) }
                        .collect { (list, f, ids) -> render(list, f, ids) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            bindAutoScan()
            updatePermissionTip()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_FILTER, filter.value.ordinal)
        outState.putBoolean(KEY_TIP_DISMISSED, tipDismissed)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupPermissionTip() {
        binding.permissionTipsCard.apply {
            setTitle(getString(R.string.devices_perm_tip_title))
            setSummary(getString(R.string.devices_perm_tip_summary))
            setOnCancelClickListener {
                tipDismissed = true
                binding.cardPermissionTip.isVisible = false
            }
            addButton(getString(R.string.devices_perm_tip_fix)) {
                startActivity(Intent(requireContext(), PermissionsActivity::class.java))
            }
        }
        updatePermissionTip()
    }

    private fun updatePermissionTip() {
        val missing = Permissions.missingCount(requireContext()) > 0
        if (!missing) {
            tipDismissed = false
        }
        binding.cardPermissionTip.isVisible = missing && !tipDismissed
    }

    private fun setupTabs() {
        val tabs = binding.filterTabs
        Filter.entries.forEach { tabs.addTab(tabs.newTab().setText(getString(it.label, 0)), false) }
        tabs.getTabAt(filter.value.ordinal)?.select()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                filter.value = Filter.entries[tab.position]
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun bindAutoScan() {
        val context = requireContext()
        val last = settings.lastScanAt.takeIf { it > 0L }?.let { formatTime(context, it) }
            ?: getString(R.string.devices_auto_scan_none)
        binding.autoScanInterval.text = if (settings.autoScan) {
            getString(R.string.devices_auto_scan_every, intervalShort(settings.intervalMinutes))
        } else {
            getString(R.string.devices_auto_scan_off)
        }
        binding.autoScanSummary.text = ScanScheduler.nextRunAt(context)
            ?.let { getString(R.string.devices_auto_scan_summary, formatTime(context, it), last) }
            ?: getString(R.string.devices_auto_scan_summary_off, last)
        binding.pullTipRow.isVisible = !settings.pullTipSeen
    }

    private fun render(list: List<Device>, selected: Filter, checking: Set<Long>) {
        devices = list
        val counts = repository.counts(list)
        Filter.entries.forEach { binding.filterTabs.getTabAt(it.ordinal)?.text = getString(it.label, it.count(counts)) }
        binding.filterTabs.requestLayout()
        val shown = list.filter(selected::matches)
        adapter.submitList(shown.map { DeviceRow(it, it.id in checking) })
        val noDevices = list.isEmpty()
        binding.filterTabs.isVisible = !noDevices
        binding.listCard.isVisible = shown.isNotEmpty()
        binding.emptyView.isVisible = shown.isEmpty()
        binding.emptyIcon.setImageResource(if (noDevices) iconsR.drawable.ic_oui_devices else iconsR.drawable.ic_oui_list_search)
        binding.emptyTitle.setText(if (noDevices) R.string.devices_empty_title else R.string.devices_empty_filter_title)
        binding.emptyHint.setText(if (noDevices) R.string.devices_empty_hint else R.string.devices_empty_filter_hint)
        binding.emptyAdd.isVisible = noDevices
    }

    private fun scanAll() {
        if (devices.isEmpty()) {
            binding.swipeRefresh.isRefreshing = false
            requireContext().semToast(getString(R.string.devices_add_first))
            return
        }
        binding.swipeRefresh.isRefreshing = true
        deviceScope.launch {
            val session = repository.scanAll(ScanTrigger.MANUAL)
            _binding?.swipeRefresh?.isRefreshing = repository.scanning.value
            val context = context ?: return@launch
            session?.let { context.semToast(context.scanSummary(it)) }
        }
    }

    private fun showItemMenu(anchor: View, device: Device) {
        PopupMenu(requireContext(), anchor, Gravity.END).apply {
            inflate(R.menu.menu_devices_item)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_device_item_check -> repository.checkDevice(device.id)
                    R.id.menu_device_item_edit -> requireContext().openDeviceEditor(device.id)
                    R.id.menu_device_item_delete -> requireContext().confirmDeleteDevice(device)
                }
                true
            }
            show()
        }
    }

    private companion object {
        const val KEY_FILTER = "filter"
        const val KEY_TIP_DISMISSED = "tip_dismissed"
        const val AUTO_SCAN_ACTIVITY = "dev.risenid.galaxyfirm.ui.settings.AutoScanActivity"
    }
}
