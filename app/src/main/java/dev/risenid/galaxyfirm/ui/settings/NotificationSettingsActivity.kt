package dev.risenid.galaxyfirm.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.data.Settings
import dev.risenid.galaxyfirm.databinding.ActivityNotificationSettingsBinding
import dev.risenid.galaxyfirm.notify.Notifier
import dev.risenid.galaxyfirm.util.Permissions
import dev.risenid.galaxyfirm.util.formatTime
import dev.risenid.galaxyfirm.work.DigestScheduler
import java.util.Calendar
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

        binding.rowNotifyUpcoming.isChecked = settings.notifyUpcoming
        binding.rowNotifyUpcoming.onCheckedChangedListener = { _, checked ->
            if (settings.notifyUpcoming != checked) {
                settings.notifyUpcoming = checked
            }
        }

        binding.rowNotifyFailures.isChecked = settings.notifyFailures
        binding.rowNotifyFailures.onCheckedChangedListener = { _, checked ->
            if (settings.notifyFailures != checked) {
                settings.notifyFailures = checked
            }
        }

        binding.rowAlertStyle.setOnClickListener { pickAlertStyle() }
        binding.rowDigestTime.setOnClickListener { pickDigestTime() }

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
        if (binding.rowNotifyUpcoming.isChecked != settings.notifyUpcoming) {
            binding.rowNotifyUpcoming.isChecked = settings.notifyUpcoming
        }
        if (binding.rowNotifyFailures.isChecked != settings.notifyFailures) {
            binding.rowNotifyFailures.isChecked = settings.notifyFailures
        }
        bindAlertStyle()
    }

    private fun bindAlertStyle() {
        binding.rowAlertStyle.summary = getString(
            if (settings.digest) R.string.settings_alert_style_digest else R.string.settings_alert_style_instant
        )
        binding.rowDigestTime.isVisible = settings.digest
        binding.rowDigestTime.summary = formatTime(this, hourMillis(settings.digestHour))
    }

    private fun pickAlertStyle() {
        val modes = arrayOf(Settings.MODE_INSTANT, Settings.MODE_DIGEST)
        val labels = arrayOf(getString(R.string.settings_alert_style_instant), getString(R.string.settings_alert_style_digest))
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_alert_style_title)
            .setSingleChoiceItems(labels, modes.indexOf(settings.notifyMode)) { dialog, which ->
                if (settings.notifyMode != modes[which]) {
                    settings.notifyMode = modes[which]
                    DigestScheduler.apply(this)
                    bindAlertStyle()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    private fun pickDigestTime() {
        TimePickerDialog(this, { _, hour, _ ->
            if (settings.digestHour != hour) {
                settings.digestHour = hour
                DigestScheduler.apply(this)
                bindAlertStyle()
            }
        }, settings.digestHour, 0, DateFormat.is24HourFormat(this)).show()
    }

    private fun hourMillis(hour: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
    }.timeInMillis

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
