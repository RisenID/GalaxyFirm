package dev.risenid.galaxyfirm.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import dev.risenid.galaxyfirm.data.net.BuildVersion

data class ThisDevice(
    val model: String,
    val csc: String,
    val installedPda: String?,
    val androidVersion: String
) {
    fun matches(model: String, csc: String): Boolean =
        this.model.equals(model.trim(), ignoreCase = true) && this.csc.equals(csc.trim(), ignoreCase = true)

    companion object {
        private val CSC_KEYS = listOf("ro.csc.sales_code", "ril.matchedcsc", "persist.omc.sales_code")
        private val CSC_PATTERN = Regex("^[A-Z0-9]{3}$")

        val current: ThisDevice? by lazy { detect() }

        private fun detect(): ThisDevice? {
            if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return null
            val model = Build.MODEL.orEmpty().trim().uppercase()
            if (!model.startsWith("SM-")) return null
            val csc = CSC_KEYS.firstNotNullOfOrNull { key -> prop(key)?.uppercase()?.takeIf(CSC_PATTERN::matches) }
                ?: return null
            val pda = prop("ro.build.PDA")?.uppercase()?.takeIf { BuildVersion.isPlausiblePda(it) }
                ?: Build.DISPLAY.orEmpty().uppercase().split('.', ' ', '_', '-')
                    .firstOrNull { !it.contains('/') && BuildVersion.isPlausiblePda(it, model) }
            return ThisDevice(model, csc, pda, Build.VERSION.RELEASE.orEmpty())
        }

        private fun prop(key: String): String? = runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            try {
                process.inputStream.bufferedReader().use { it.readLine() }?.trim()
            } finally {
                process.destroy()
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}

fun Context.userDeviceName(): String? =
    runCatching { Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) }
        .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
