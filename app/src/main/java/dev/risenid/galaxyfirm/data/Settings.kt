package dev.risenid.galaxyfirm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class Settings(context: Context) {
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    var autoScan: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCAN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SCAN, value).apply()

    var intervalMinutes: Int
        get() = prefs.getString(KEY_INTERVAL, "30")?.toIntOrNull()?.takeIf { it in ALLOWED_INTERVALS } ?: 30
        set(value) = prefs.edit().putString(KEY_INTERVAL, value.toString()).apply()

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var notifyUpdates: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_UPDATES, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_UPDATES, value).apply()

    var notifyUpcoming: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_UPCOMING, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_UPCOMING, value).apply()

    var notifyFailures: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_FAILURES, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_FAILURES, value).apply()

    var theme: String
        get() = prefs.getString(KEY_THEME, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var lastScanAt: Long
        get() = prefs.getLong(KEY_LAST_SCAN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SCAN, value).apply()

    var nextScanAt: Long
        get() = prefs.getLong(KEY_NEXT_SCAN, 0L)
        set(value) = prefs.edit().putLong(KEY_NEXT_SCAN, value).apply()

    var permissionsPrompted: Boolean
        get() = prefs.getBoolean(KEY_PERMISSIONS_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSIONS_PROMPTED, value).apply()

    var pullTipSeen: Boolean
        get() = prefs.getBoolean(KEY_PULL_TIP_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_PULL_TIP_SEEN, value).apply()

    var historyLimit: Int
        get() = prefs.getString(KEY_HISTORY_LIMIT, "500")?.toIntOrNull()?.coerceAtLeast(1) ?: 500
        set(value) = prefs.edit().putString(KEY_HISTORY_LIMIT, value.toString()).apply()

    fun nightMode(): Int = when (theme) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun observe(): Flow<Unit> = callbackFlow {
        trySend(Unit)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(Unit)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    companion object {
        const val KEY_AUTO_SCAN = "auto_scan"
        const val KEY_INTERVAL = "scan_interval"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_NOTIFY_UPDATES = "notify_updates"
        const val KEY_NOTIFY_UPCOMING = "notify_upcoming"
        const val KEY_NOTIFY_FAILURES = "notify_failures"
        const val KEY_THEME = "theme"
        const val KEY_LAST_SCAN = "last_scan_at"
        const val KEY_NEXT_SCAN = "next_scan_at"
        const val KEY_PERMISSIONS_PROMPTED = "permissions_prompted"
        const val KEY_PULL_TIP_SEEN = "pull_tip_seen"
        const val KEY_HISTORY_LIMIT = "history_limit"

        private val ALLOWED_INTERVALS = setOf(10, 15, 30, 60, 120, 180)
    }
}
