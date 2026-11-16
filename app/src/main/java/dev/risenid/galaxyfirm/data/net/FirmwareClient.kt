package dev.risenid.galaxyfirm.data.net

import android.util.Xml
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

data class FirmwareInfo(
    val firmware: String,
    val fullFirmware: String,
    val androidVersion: String?,
    val openDate: String? = null,
    val source: String = "fota"
)

class FirmwareException(message: String) : Exception(message)

data class FirmwareLookup(val public: FirmwareInfo?, val upcoming: FirmwareInfo?, val fusAvailable: Boolean)

class FirmwareClient(private val fusClient: FusClient = FusClient()) {
    suspend fun lookup(model: String, csc: String): FirmwareLookup = withContext(Dispatchers.IO) {
        val cleanModel = model.trim().uppercase()
        val cleanCsc = csc.trim().uppercase()
        if (cleanModel.isEmpty() || cleanCsc.isEmpty()) {
            throw FirmwareException("Model or region not found")
        }
        val (fusInfo, fota) = coroutineScope {
            val fota = async { runCatching { fetchFota(cleanModel, cleanCsc) } }
            fetchFus(cleanModel, cleanCsc) to fota.await()
        }
        val publicInfo = fota.getOrElse { error ->
            return@withContext FirmwareLookup(public = fusInfo ?: throw error, upcoming = null, fusAvailable = true)
        }
        val merged = if (fusInfo != null && fusInfo.firmware == publicInfo.firmware) {
            publicInfo.copy(
                androidVersion = publicInfo.androidVersion ?: fusInfo.androidVersion,
                openDate = publicInfo.openDate ?: fusInfo.openDate
            )
        } else {
            publicInfo
        }
        FirmwareLookup(
            public = merged,
            upcoming = fusInfo?.takeIf { BuildVersion.isNewer(it.firmware, publicInfo.firmware) },
            fusAvailable = fusInfo != null
        )
    }

    private suspend fun fetchFus(model: String, csc: String): FirmwareInfo? = try {
        fusClient.history(model, csc).lastOrNull()?.let { latest ->
            val full = normalizeVersion(latest.swVersion)
            val pda = full.substringBefore('/').trim()
            if (pda.isEmpty()) {
                null
            } else {
                FirmwareInfo(
                    firmware = pda,
                    fullFirmware = full,
                    androidVersion = parseAndroidVersion(latest.osName),
                    openDate = latest.openDate?.trim()?.takeIf { it.isNotEmpty() },
                    source = "fus"
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

    private fun fetchFota(model: String, csc: String): FirmwareInfo {
        val endpoint = "https://fota-cloud-dn.ospserver.net/firmware/$csc/$model/version.xml"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", FOTA_USER_AGENT)
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()

            var latestText: String? = null
            var osVersion: String? = null
            var errorCode: String? = null
            var hasErrorTag = false

            if (body.isNotBlank()) {
                try {
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(StringReader(body))
                    var inFirmware = false
                    var inVersion = false
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> {
                                val name = parser.name
                                when {
                                    name.equals("Error", ignoreCase = true) -> hasErrorTag = true
                                    name.equals("Code", ignoreCase = true) -> {
                                        val text = parser.nextText().trim()
                                        if (errorCode == null) {
                                            errorCode = text
                                        }
                                    }
                                    name.equals("firmware", ignoreCase = true) -> inFirmware = true
                                    inFirmware && name.equals("version", ignoreCase = true) -> inVersion = true
                                    inFirmware && inVersion && name.equals("latest", ignoreCase = true) -> {
                                        osVersion = parser.getAttributeValue(null, "o")
                                            ?.trim()
                                            ?.takeIf { it.isNotEmpty() }
                                        latestText = parser.nextText().trim()
                                    }
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                val name = parser.name
                                when {
                                    name.equals("version", ignoreCase = true) -> inVersion = false
                                    name.equals("firmware", ignoreCase = true) -> inFirmware = false
                                }
                            }
                        }
                        event = parser.next()
                    }
                } catch (_: Exception) {
                }
            }

            if (errorCode.equals("AccessDenied", ignoreCase = true) || code == 403 || code == 404) {
                throw FirmwareException("Model or region not found")
            }
            if (code !in 200..299) {
                throw FirmwareException("Server error $code")
            }
            if (hasErrorTag) {
                throw FirmwareException("No firmware published")
            }
            val rawLatest = latestText?.takeIf { it.isNotEmpty() }
                ?: throw FirmwareException("No firmware published")
            val full = normalizeVersion(rawLatest)
            val pda = full.substringBefore('/').trim().takeIf { it.isNotEmpty() }
                ?: throw FirmwareException("No firmware published")
            return FirmwareInfo(
                firmware = pda,
                fullFirmware = full,
                androidVersion = osVersion,
                openDate = null,
                source = "fota"
            )
        } catch (e: FirmwareException) {
            throw e
        } catch (e: IOException) {
            throw FirmwareException("Network error")
        } catch (_: Exception) {
            throw FirmwareException("No firmware published")
        } finally {
            conn.disconnect()
        }
    }

    private fun normalizeVersion(raw: String): String {
        val vc = raw.trim().split("/").map { it.trim() }.toMutableList()
        if (vc.size == 3) {
            vc.add(vc[0])
        }
        if (vc.size >= 3 && vc[2].isEmpty()) {
            vc[2] = vc[0]
        }
        return vc.joinToString("/")
    }

    private fun parseAndroidVersion(osName: String?): String? {
        if (osName.isNullOrBlank()) return null
        val trimmed = osName.trim()
        ANDROID_VERSION_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let {
            return it
        }
        if (NUMERIC_VERSION_REGEX.matches(trimmed)) {
            return trimmed
        }
        return null
    }

    private companion object {
        const val FOTA_USER_AGENT = "Kiss2.0_FUS"
        const val TIMEOUT_MS = 15_000
        val ANDROID_VERSION_REGEX = Regex("""Android\s+(\d+(?:\.\d+)*)""", RegexOption.IGNORE_CASE)
        val NUMERIC_VERSION_REGEX = Regex("""^\d+(?:\.\d+)*$""")
    }
}
