package dev.risenid.galaxyfirm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import dev.risenid.galaxyfirm.R
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.databinding.ActivityMainBinding
import dev.risenid.galaxyfirm.ui.devices.DevicesFragment
import dev.risenid.galaxyfirm.ui.history.HistoryFragment
import dev.risenid.galaxyfirm.ui.settings.SettingsFragment
import dev.risenid.galaxyfirm.util.Permissions
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var current = 0

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        promptBatteryIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        current = savedInstanceState?.getInt(KEY_TAB) ?: 0
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                TAGS.forEachIndexed { index, tag ->
                    val fragment = create(index)
                    add(R.id.main_container, fragment, tag)
                    if (index != current) hide(fragment)
                }
            }
        }
        binding.bottomTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = show(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        binding.bottomTab.post { binding.bottomTab.getTabAt(current)?.select() }
        supportFragmentManager.executePendingTransactions()
        applyTitle()
        maybePromptFirstLaunchPermissions()
    }

    private fun maybePromptFirstLaunchPermissions() {
        val settings = ServiceLocator.settings(this)
        if (settings.permissionsPrompted) return
        settings.permissionsPrompted = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            promptBatteryIfNeeded()
        }
    }

    private fun promptBatteryIfNeeded() {
        if (Permissions.batteryUnrestricted(this)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_battery_dialog_title)
            .setMessage(R.string.settings_battery_dialog_message)
            .setPositiveButton(R.string.settings_battery_dialog_allow) { _, _ ->
                Permissions.launchBatteryExemption(this)
            }
            .setNegativeButton(R.string.settings_battery_dialog_not_now, null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, current)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val res = currentScreen()?.menuRes ?: 0
        if (res != 0) menuInflater.inflate(res, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        currentScreen()?.onMenuItem(item.itemId) == true || super.onOptionsItemSelected(item)

    private fun currentScreen() = supportFragmentManager.findFragmentByTag(TAGS[current]) as? TabScreen

    private fun create(index: Int): Fragment = when (index) {
        0 -> DevicesFragment()
        1 -> HistoryFragment()
        else -> SettingsFragment()
    }

    private fun show(index: Int) {
        if (index !in TAGS.indices || index == current) return
        current = index
        supportFragmentManager.commit {
            TAGS.forEachIndexed { i, tag ->
                val fragment = supportFragmentManager.findFragmentByTag(tag) ?: return@forEachIndexed
                if (i == index) show(fragment) else hide(fragment)
            }
        }
        applyTitle()
        invalidateOptionsMenu()
        binding.toolbarLayout.setExpanded(true, false)
    }

    private fun applyTitle() {
        val title = getString(TITLES[current])
        binding.toolbarLayout.setTitle(title)
        binding.toolbarLayout.setSubtitle(getString(SUBTITLES[current]))
    }

    companion object {
        private const val KEY_TAB = "tab"
        private val TAGS = listOf("devices", "history", "settings")
        private val TITLES = listOf(R.string.app_name, R.string.tab_history, R.string.tab_settings)
        private val SUBTITLES = listOf(R.string.subtitle_devices, R.string.subtitle_history, R.string.subtitle_settings)
    }
}
