package dev.risenid.galaxyfirm.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.databinding.ActivityNotificationSettingsBinding
import dev.risenid.galaxyfirm.notify.Notifier
import dev.risenid.galaxyfirm.util.Permissions
import dev.oneuiproject.oneui.ktx.seslSetFillHorizontalPaddingEnabled

class NotificationSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationSettingsBinding
    private val settings by lazy { ServiceLocator.settings(this) }

    private val requestPermissionLauncher = registerForActivityResult(
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
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scrollView.apply {
            seslSetFillHorizontalPaddingEnabled(true)
            seslSetGoToTopEnabled(true)
        }

        binding.tipsCard.apply {
            setTitle(getString(R.string.settings_notifications_off_title))
            setSummary(getString(R.string.settings_notifications_off_summary))
            addButton(getString(R.string.settings_notifications_allow)) {
                handleAllowNotifications()
            }
        }

        binding.rowNotifyUpdates.isChecked = settings.notifyUpdates
        binding.rowNotifyUpdates.onCheckedChangedListener = { _, checked ->
            if (settings.notifyUpdates != checked) {
                settings.notifyUpdates = checked
            }
        }

        binding.rowNotifyFailures.isChecked = settings.notifyFailures
        binding.rowNotifyFailures.onCheckedChangedListener = { _, checked ->
            if (settings.notifyFailures != checked) {
                settings.notifyFailures = checked
            }
        }

        binding.rowSystemNotificationSettings.setOnClickListener {
            Permissions.launchNotificationSettings(this)
        }

        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        binding.cardPermissionTip.isVisible = !Permissions.notificationsAllowed(this)
        if (binding.rowNotifyUpdates.isChecked != settings.notifyUpdates) {
            binding.rowNotifyUpdates.isChecked = settings.notifyUpdates
        }
        if (binding.rowNotifyFailures.isChecked != settings.notifyFailures) {
            binding.rowNotifyFailures.isChecked = settings.notifyFailures
        }
    }

    private fun handleAllowNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (
            NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            Permissions.updatesChannelBlocked(this)
        ) {
            Permissions.launchChannelSettings(this, Notifier.CHANNEL_UPDATES)
        } else {
            Permissions.launchNotificationSettings(this)
        }
    }
}
