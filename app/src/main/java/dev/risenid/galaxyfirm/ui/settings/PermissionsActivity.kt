package dev.risenid.galaxyfirm.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.databinding.ActivityPermissionsBinding
import dev.risenid.galaxyfirm.util.Permissions
import dev.risenid.galaxyfirm.work.ScanScheduler
import dev.oneuiproject.oneui.ktx.seslSetFillHorizontalPaddingEnabled
import dev.oneuiproject.oneui.widget.CardItemView

class PermissionsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPermissionsBinding

    private val requestNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            Permissions.launchNotificationSettings(this)
        }
        refreshState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scrollView.apply {
            seslSetFillHorizontalPaddingEnabled(true)
            seslSetGoToTopEnabled(true)
        }

        binding.rowAlarms.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        binding.rowNotifications.setOnClickListener {
            handleNotificationTap()
        }
        binding.rowBattery.setOnClickListener {
            Permissions.launchBatteryExemption(this)
        }
        binding.rowAlarms.setOnClickListener {
            Permissions.launchExactAlarmSettings(this)
        }

        refreshState()
    }

    override fun onResume() {
        super.onResume()
        ScanScheduler.apply(this, reschedule = false)
        refreshState()
    }

    private fun refreshState() {
        val notifOk = Permissions.notificationsAllowed(this)
        binding.rowNotifications.bindState(
            granted = notifOk,
            labelRes = if (notifOk) R.string.settings_perm_allowed else R.string.settings_perm_not_allowed
        )

        val batteryOk = Permissions.batteryUnrestricted(this)
        binding.rowBattery.bindState(
            granted = batteryOk,
            labelRes = if (batteryOk) R.string.settings_perm_unrestricted else R.string.settings_perm_restricted
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmsOk = Permissions.exactAlarmsAllowed(this)
            binding.rowAlarms.bindState(
                granted = alarmsOk,
                labelRes = if (alarmsOk) R.string.settings_perm_allowed else R.string.settings_perm_not_allowed
            )
        }
    }

    private fun CardItemView.bindState(granted: Boolean, labelRes: Int) {
        summary = getString(labelRes)
        val colorRes = if (granted) R.color.status_new else R.color.status_failed
        getSummaryView().setTextColor(ContextCompat.getColor(context, colorRes))
    }

    private fun handleNotificationTap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Permissions.updatesChannelBlocked(this)) {
            Permissions.launchChannelSettings(this)
        } else {
            Permissions.launchNotificationSettings(this)
        }
    }
}
