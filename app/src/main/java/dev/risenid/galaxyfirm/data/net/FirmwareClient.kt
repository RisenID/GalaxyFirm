package dev.risenid.galaxyfirm.data.net

import android.util.Xml
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

data class FirmwareInfo(
    val firmware: String,
    val fullFirmware: String,
    val androidVersion: String?
)

class FirmwareException(message: String) : Exception(message)

class FirmwareClient {
    suspend fun fetch(model: String, csc: String): FirmwareInfo = withContext(Dispatchers.IO) {
        val cleanModel = model.trim().uppercase()
        val cleanCsc = csc.trim().uppercase()
        if (cleanModel.isEmpty() || cleanCsc.isEmpty()) {
            throw FirmwareException("Model or region not found")
        }
        val endpoint = "https://fota-cloud-dn.ospserver.net/firmware/$cleanCsc/$cleanModel/version.xml"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GalaxyFirm/1.0 (Android)")
        }
        try {
            val code = conn.responseCode
            if (code == 403 || code == 404) {
                throw FirmwareException("Model or region not found")
            }
            if (code !in 200..299) {
                throw FirmwareException("Server error $code")
            }
            var latestText: String? = null
            var osVersion: String? = null
            conn.inputStream.use { stream ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, "UTF-8")
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "latest") {
                        osVersion = parser.getAttributeValue(null, "o")?.trim()?.takeIf { it.isNotEmpty() }
                        latestText = parser.nextText().trim()
                        break
                    }
                    event = parser.next()
                }
            }
            val full = latestText?.takeIf { it.isNotEmpty() }
                ?: throw FirmwareException("No firmware published")
            val pda = full.substringBefore('/').trim().takeIf { it.isNotEmpty() }
                ?: throw FirmwareException("No firmware published")
            FirmwareInfo(
                firmware = pda,
                fullFirmware = full,
                androidVersion = osVersion
            )
        } catch (e: FirmwareException) {
            throw e
        } catch (e: IOException) {
            throw FirmwareException("Network error")
        } catch (e: Exception) {
            throw FirmwareException("No firmware published")
        } finally {
            conn.disconnect()
        }
    }
}
