package dev.risenid.galaxyfirm.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.databinding.ActivityNotificationSettingsBinding
import dev.oneuiproject.oneui.ktx.seslSetFillHorizontalPaddingEnabled

class NotificationSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationSettingsBinding
    private val settings by lazy { ServiceLocator.settings(this) }
    private val notifier by lazy { ServiceLocator.notifier(this) }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
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
            openSystemNotificationSettings()
        }

        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        binding.cardPermissionTip.isVisible = !notifier.canNotify()
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
        } else {
            openSystemNotificationSettings()
        }
    }

    private fun openSystemNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        runCatching { startActivity(intent) }.onFailure {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
            runCatching { startActivity(fallback) }
        }
    }
}
