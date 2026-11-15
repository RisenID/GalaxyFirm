package dev.risenid.galaxyfirm.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.databinding.ActivityAutoScanBinding
import dev.risenid.galaxyfirm.util.formatStamp
import dev.risenid.galaxyfirm.work.ScanScheduler
import dev.oneuiproject.oneui.ktx.seslSetFillHorizontalPaddingEnabled

class AutoScanActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAutoScanBinding
    private val settings by lazy { ServiceLocator.settings(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutoScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scrollView.apply {
            seslSetFillHorizontalPaddingEnabled(true)
            seslSetGoToTopEnabled(true)
        }

        binding.toolbarLayout.switchBar.apply {
            isChecked = settings.autoScan
            addOnSwitchChangeListener { _, isChecked ->
                if (settings.autoScan != isChecked) {
                    settings.autoScan = isChecked
                    ScanScheduler.apply(this@AutoScanActivity)
                    updateUi()
                }
            }
        }

        binding.intervalGroup.check(intervalToRadioId(settings.intervalMinutes))
        binding.intervalGroup.setOnCheckedChangeListener { _, checkedId ->
            val minutes = radioIdToInterval(checkedId)
            if (settings.intervalMinutes != minutes) {
                settings.intervalMinutes = minutes
                ScanScheduler.apply(this)
                updateUi()
            }
        }

        binding.rowWifiOnly.isChecked = settings.wifiOnly
        binding.rowWifiOnly.onCheckedChangedListener = { _, checked ->
            if (settings.wifiOnly != checked) {
                settings.wifiOnly = checked
                ScanScheduler.apply(this)
                updateUi()
            }
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateUi() {
        val enabled = settings.autoScan
        if (binding.toolbarLayout.switchBar.isChecked != enabled) {
            binding.toolbarLayout.switchBar.isChecked = enabled
        }
        val targetRadioId = intervalToRadioId(settings.intervalMinutes)
        if (binding.intervalGroup.checkedRadioButtonId != targetRadioId) {
            binding.intervalGroup.check(targetRadioId)
        }
        if (binding.rowWifiOnly.isChecked != settings.wifiOnly) {
            binding.rowWifiOnly.isChecked = settings.wifiOnly
        }

        binding.intervalGroup.isEnabled = enabled
        binding.intervalGroup.children.forEach { it.isEnabled = enabled }
        binding.rowWifiOnly.isEnabled = enabled
        binding.rowNextScan.isEnabled = enabled

        val nextAt = ScanScheduler.nextRunAt(this)
        binding.rowNextScan.summary = if (nextAt != null) {
            formatStamp(this, nextAt)
        } else {
            getString(R.string.settings_auto_scan_summary_off)
        }
    }

    private fun intervalToRadioId(minutes: Int): Int = when (minutes) {
        15 -> R.id.radio_15
        60 -> R.id.radio_60
        120 -> R.id.radio_120
        180 -> R.id.radio_180
        else -> R.id.radio_30
    }

    private fun radioIdToInterval(id: Int): Int = when (id) {
        R.id.radio_15 -> 15
        R.id.radio_60 -> 60
        R.id.radio_120 -> 120
        R.id.radio_180 -> 180
        else -> 30
    }
}
