package dev.risenid.galaxyfirm.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.databinding.FragmentSettingsBinding
import dev.risenid.galaxyfirm.ui.TabScreen
import dev.risenid.galaxyfirm.util.Permissions
import dev.risenid.galaxyfirm.util.intervalLabel
import dev.oneuiproject.oneui.ktx.defaultSummaryColor
import dev.oneuiproject.oneui.ktx.seslSetFillHorizontalPaddingEnabled
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings), TabScreen {
    override val menuRes: Int = 0
    override fun onMenuItem(itemId: Int): Boolean = false

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        binding.scrollView.apply {
            seslSetFillHorizontalPaddingEnabled(true)
            seslSetGoToTopEnabled(true)
        }

        binding.rowAutoScan.setOnClickListener {
            startActivity(Intent(requireContext(), AutoScanActivity::class.java))
        }
        binding.rowNotifications.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationSettingsActivity::class.java))
        }
        binding.rowPermissions.setOnClickListener {
            startActivity(Intent(requireContext(), PermissionsActivity::class.java))
        }
        binding.rowAppearance.setOnClickListener {
            startActivity(Intent(requireContext(), AppearanceActivity::class.java))
        }
        binding.rowAppData.setOnClickListener {
            startActivity(Intent(requireContext(), AppDataActivity::class.java))
        }
        binding.rowAbout.setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
        binding.rowFeedback.setOnClickListener {
            openFeedback()
        }

        val settings = ServiceLocator.settings(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settings.observe().collect {
                    refreshSummaries()
                }
            }
        }
        refreshSummaries()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            refreshSummaries()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun refreshSummaries() {
        val context = requireContext()
        val settings = ServiceLocator.settings(context)

        binding.rowAutoScan.summary = if (settings.autoScan) {
            getString(R.string.settings_auto_scan_summary_on, intervalLabel(settings.intervalMinutes))
        } else {
            getString(R.string.settings_auto_scan_summary_off)
        }

        binding.rowNotifications.summary = if (Permissions.notificationsAllowed(context)) {
            getString(R.string.settings_notifications_summary)
        } else {
            getString(R.string.settings_notifications_summary_blocked)
        }

        val missing = Permissions.missingCount(context)
        if (missing == 0) {
            binding.rowPermissions.summary = getString(R.string.settings_permissions_all_set)
            binding.rowPermissions.getSummaryView().setTextColor(context.defaultSummaryColor)
        } else {
            binding.rowPermissions.summary = resources.getQuantityString(
                R.plurals.settings_permissions_need_attention,
                missing,
                missing
            )
            binding.rowPermissions.getSummaryView().setTextColor(
                ContextCompat.getColor(context, R.color.status_failed)
            )
        }

        binding.rowAppearance.summary = when (settings.theme) {
            "light" -> getString(R.string.settings_theme_light)
            "dark" -> getString(R.string.settings_theme_dark)
            else -> getString(R.string.settings_theme_system)
        }

        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifEmpty { "1.0.0" }
        binding.rowAbout.summary = getString(R.string.settings_version_summary, versionName)
    }

    private fun openFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_feedback_subject))
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.settings_no_email_app, Toast.LENGTH_SHORT).show()
        }
    }
}
