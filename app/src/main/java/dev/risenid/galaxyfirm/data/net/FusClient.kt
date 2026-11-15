package dev.risenid.galaxyfirm.data.net

import android.util.Xml
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

data class FusBinary(
    val sequence: Int,
    val swVersion: String,
    val osName: String?,
    val openDate: String?,
    val nature: Int?,
    val status: Int?
)

class FusClient {
    private enum class Request(val value: String, val cloud: Boolean) {
        GENERATE_NONCE("NF_SmartDownloadGenerateNonce.do", false),
        HISTORY("SmartHistory.do", false)
    }

    private val mutex = Mutex()
    private var nonce: String = ""
    private var auth: String = ""
    private var sessionId: String = ""

    suspend fun history(model: String, csc: String): List<FusBinary> = withContext(Dispatchers.IO) {
        val cleanModel = model.trim().uppercase()
        val cleanCsc = csc.trim().uppercase()
        if (cleanModel.isEmpty() || cleanCsc.isEmpty()) {
            return@withContext emptyList()
        }
        val requestBody = createHistoryRequest(cleanModel, cleanCsc)
        val responseXml = mutex.withLock {
            makeReq(
                request = Request.HISTORY,
                data = requestBody,
                signature = cleanModel,
                includeNonce = true
            )
        }
        parseHistoryInfos(responseXml)
    }

    private fun generateNonce() {
        nonce = ""
        auth = ""
        makeReq(
            request = Request.GENERATE_NONCE,
            data = "",
            signature = null,
            includeNonce = true,
            allowRetry = false
        )
    }

    private fun makeReq(
        request: Request,
        data: String = "",
        signature: String? = null,
        includeNonce: Boolean = true,
        allowRetry: Boolean = true
    ): String {
        if (nonce.isBlank() && request != Request.GENERATE_NONCE) {
            generateNonce()
        }
        val authV = getAuthV(
            includeNonce = includeNonce,
            signature = signature,
            cloud = request.cloud
        )
        val bodyBytes = data.toByteArray(Charsets.UTF_8)
        val conn = (URL("$BASE_URL/${request.value}").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            doOutput = true
            setFixedLengthStreamingMode(bodyBytes.size)
            setRequestProperty("Authorization", authV)
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Cookie", "JSESSIONID=$sessionId;SESSION=$sessionId")
            setRequestProperty("Set-Cookie", "JSESSIONID=$sessionId;SESSION=$sessionId")
            setRequestProperty("Content-Length", bodyBytes.size.toString())
        }
        val code: Int
        val body: String
        val headers: Map<String?, List<String>>
        try {
            conn.outputStream.use { out ->
                if (bodyBytes.isNotEmpty()) {
                    out.write(bodyBytes)
                }
            }
            code = conn.responseCode
            headers = conn.headerFields
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            body = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        } finally {
            conn.disconnect()
        }

        if (request != Request.GENERATE_NONCE && allowRetry && is401(code, body)) {
            generateNonce()
            return makeReq(
                request = request,
                data = data,
                signature = signature,
                includeNonce = includeNonce,
                allowRetry = false
            )
        }

        val nonceHeader = headers.entries
            .firstOrNull { it.key != null && it.key.equals("NONCE", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
        if (nonceHeader != null) {
            nonce = nonceHeader
            try {
                auth = legacyGetAuth(legacyDecryptNonce(nonceHeader))
            } catch (_: Exception) {
            }
        }

        val cookieValues = headers.entries
            .filter { it.key != null && it.key.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value.orEmpty() }
        val matchedCookie = cookieValues.firstOrNull {
            it.contains("JSESSIONID=") || it.contains("SESSION=")
        }
        if (matchedCookie != null) {
            sessionId = matchedCookie
                .replace("JSESSIONID=", "")
                .replace("SESSION=", "")
                .replace(Regex(";.*$"), "")
        }

        if (code !in 200..299 && request == Request.GENERATE_NONCE && nonce.isBlank()) {
            throw IOException("FUS nonce HTTP $code")
        }
        return body
    }

    private fun getAuthV(
        includeNonce: Boolean = true,
        signature: String? = null,
        cloud: Boolean = false
    ): String {
        val cleanSig = signature?.takeIf { it.isNotBlank() }
        val hasSignature = cleanSig != null
        val localNonce = when {
            includeNonce && hasSignature -> {
                val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
                CharArray(16) { chars.random() }.concatToString()
            }
            includeNonce -> nonce
            else -> ""
        }
        val headerNonce = if (cloud) localNonce else nonce
        val sig = makeSignatureHash(cleanSig) ?: auth
        val nc = if (hasSignature) "00000001" else ""
        val type = if (hasSignature) "auth" else ""
        val realm = if (hasSignature) "interface" else ""
        return "FUS nonce=\"$headerNonce\", signature=\"$sig\", nc=\"$nc\", type=\"$type\", realm=\"$realm\""
    }

    private fun makeSignatureHash(signature: String?): String? {
        if (signature == null) return null
        val a = md5Hex("auth:$nonce:00000001")
        val b = md5Hex("interface:$signature")
        return md5Hex("$a:FUS:$b")
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun is401(code: Int, body: String): Boolean {
        if (code == 401) return true
        if (body.isBlank()) return false
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(body))
            var inFusBody = false
            var inResults = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        when {
                            name.equals("FUSBody", ignoreCase = true) -> inFusBody = true
                            inFusBody && name.equals("Results", ignoreCase = true) -> inResults = true
                            inFusBody && inResults && name.equals("Status", ignoreCase = true) -> {
                                if (parser.nextText().trim() == "401") return true
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name
                        when {
                            name.equals("Results", ignoreCase = true) -> inResults = false
                            name.equals("FUSBody", ignoreCase = true) -> inFusBody = false
                        }
                    }
                }
                event = parser.next()
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun createHistoryRequest(model: String, region: String): String =
        "<FUSMsg>\n" +
            "\t<FUSHdr>\n" +
            "\t\t<ProtoVer>1</ProtoVer>\n" +
            "\t\t<SessionID>0</SessionID>\n" +
            "\t\t<MsgID>1</MsgID>\n" +
            "\t</FUSHdr>\n" +
            "\t<FUSBody>\n" +
            "\t\t<Put>\n" +
            "\t\t\t<ACCESS_MODE>\n" +
            "\t\t\t\t<Data>1</Data>\n" +
            "\t\t\t</ACCESS_MODE>\n" +
            "\t\t\t<BINARY_MODEL_NAME>\n" +
            "\t\t\t\t<Data>${escapeXml(model)}</Data>\n" +
            "\t\t\t</BINARY_MODEL_NAME>\n" +
            "\t\t\t<BINARY_LOCAL_CODE>\n" +
            "\t\t\t\t<Data>${escapeXml(region)}</Data>\n" +
            "\t\t\t</BINARY_LOCAL_CODE>\n" +
            "\t\t</Put>\n" +
            "\t</FUSBody>\n" +
            "</FUSMsg>"

    private fun escapeXml(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    private fun parseHistoryInfos(xml: String): List<FusBinary> {
        if (xml.isBlank()) return emptyList()
        val list = mutableListOf<FusBinary>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))
            var inBinaryInfo = false
            var currentField: String? = null
            val fields = mutableMapOf<String, String>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (name.equals("BINARY_INFO", ignoreCase = true)) {
                            inBinaryInfo = true
                            currentField = null
                            fields.clear()
                        } else if (inBinaryInfo) {
                            if (currentField != null && name.equals("Data", ignoreCase = true)) {
                                val text = parser.nextText().trim()
                                fields.putIfAbsent(currentField!!, text)
                            } else if (currentField == null) {
                                currentField = name.uppercase()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name
                        if (inBinaryInfo && name.equals("BINARY_INFO", ignoreCase = true)) {
                            val seq = fields["BINARY_SEQUENCE"]?.toIntOrNull()
                            val sw = fields["BINARY_SW_VERSION"].orEmpty()
                            if (seq != null && sw.isNotEmpty()) {
                                list.add(
                                    FusBinary(
                                        sequence = seq,
                                        swVersion = sw,
                                        osName = fields["BINARY_OS_NAME"]?.takeIf { it.isNotEmpty() },
                                        openDate = fields["BINARY_OPEN_DATE"]?.takeIf { it.isNotEmpty() },
                                        nature = fields["BINARY_NATURE"]?.toIntOrNull(),
                                        status = fields["BINARY_STATUS"]?.toIntOrNull()
                                    )
                                )
                            }
                            inBinaryInfo = false
                            currentField = null
                            fields.clear()
                        } else if (inBinaryInfo && currentField != null && name.equals(currentField, ignoreCase = true)) {
                            currentField = null
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return list
            .filterNot { it.osName == BETA_OS_NAME }
            .sortedBy { it.sequence }
    }

    private fun legacyDecryptNonce(input: String): String {
        val decoded = Base64.getDecoder().decode(input)
        val keyBytes = KEY_1.toByteArray(Charsets.UTF_8)
        val iv = keyBytes.copyOfRange(0, 16)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return unpad(cipher.doFinal(decoded)).toString(Charsets.UTF_8)
    }

    private fun legacyGetAuth(decryptedNonce: String): String {
        val keyData = ByteArray(decryptedNonce.length) { (decryptedNonce[it].code % 16).toByte() }
        val fKey = getFKey(keyData)
        val iv = fKey.copyOfRange(0, 16)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(fKey, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(pad(decryptedNonce.toByteArray(Charsets.UTF_8)))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun getFKey(input: ByteArray): ByteArray {
        val sb = StringBuilder(32)
        for (i in 0 until 16) {
            sb.append(KEY_1[input[i].toInt() % KEY_1.length])
        }
        sb.append(KEY_2)
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun pad(data: ByteArray): ByteArray {
        val size = 16 - (data.size % 16)
        return data + ByteArray(size) { size.toByte() }
    }

    private fun unpad(data: ByteArray): ByteArray {
        val last = data.last()
        return if (last >= 0) {
            data.dropLast(last.toInt()).toByteArray()
        } else {
            data.take(-last).toByteArray()
        }
    }

    private companion object {
        const val BASE_URL = "https://neofussvr.sslcs.cdngc.net"
        const val USER_AGENT = "SMART 2.0"
        const val BETA_OS_NAME = "Z(Android 99)"
        const val TIMEOUT_MS = 15_000
        const val KEY_1 = "vicopx7dqu06emacgpnpy8j8zwhduwlh"
        const val KEY_2 = "9u7qab84rpc16gvk"
    }
}
