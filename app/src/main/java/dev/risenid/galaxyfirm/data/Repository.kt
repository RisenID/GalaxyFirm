package dev.risenid.galaxyfirm.data

import dev.risenid.galaxyfirm.data.db.AppDatabase
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.model.DeviceCounts
import dev.risenid.galaxyfirm.data.model.DeviceStatus
import dev.risenid.galaxyfirm.data.model.DeviceType
import dev.risenid.galaxyfirm.data.model.HistoryCounts
import dev.risenid.galaxyfirm.data.model.ScanResult
import dev.risenid.galaxyfirm.data.model.ScanSession
import dev.risenid.galaxyfirm.data.model.ScanTrigger
import dev.risenid.galaxyfirm.data.net.FirmwareClient
import dev.risenid.galaxyfirm.notify.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class Repository(
    private val db: AppDatabase,
    private val client: FirmwareClient,
    private val settings: Settings,
    private val notifier: Notifier
) {
    private val deviceDao = db.deviceDao()
    private val historyDao = db.historyDao()
    private val scanMutex = Mutex()
    private val _scanning = MutableStateFlow(false)

    val devices: Flow<List<Device>> = deviceDao.observeAll()
    val sessions: Flow<List<ScanSession>> = historyDao.observeSessions()
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    suspend fun addDevice(name: String, model: String, csc: String, type: DeviceType): Result<Long> =
        withContext(Dispatchers.IO) {
            val cleanModel = model.trim().uppercase()
            val cleanCsc = csc.trim().uppercase()
            if (cleanModel.isEmpty() || cleanCsc.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Model and CSC required"))
            }
            if (deviceDao.find(cleanModel, cleanCsc) != null) {
                return@withContext Result.failure(IllegalStateException("Device already added"))
            }
            val cleanName = name.trim().ifEmpty { cleanModel }
            runCatching {
                deviceDao.insert(
                    Device(
                        name = cleanName,
                        model = cleanModel,
                        csc = cleanCsc,
                        type = type
                    )
                )
            }.recoverCatching {
                throw IllegalStateException("Device already added")
            }
        }

    suspend fun updateDevice(device: Device) = withContext(Dispatchers.IO) {
        val cleanModel = device.model.trim().uppercase()
        val cleanCsc = device.csc.trim().uppercase()
        val cleanName = device.name.trim().ifEmpty { cleanModel }
        deviceDao.update(
            device.copy(
                name = cleanName,
                model = cleanModel,
                csc = cleanCsc
            )
        )
    }

    suspend fun deleteDevice(device: Device) = withContext(Dispatchers.IO) {
        deviceDao.delete(device)
    }

    suspend fun getDevice(id: Long): Device? = withContext(Dispatchers.IO) {
        deviceDao.getById(id)
    }

    suspend fun acknowledge(device: Device) = withContext(Dispatchers.IO) {
        deviceDao.update(
            device.copy(
                status = DeviceStatus.UP_TO_DATE,
                updateFoundAt = null
            )
        )
    }

    suspend fun scanAll(trigger: ScanTrigger): ScanSession? = withContext(Dispatchers.IO) {
        if (!scanMutex.tryLock()) return@withContext null
        try {
            val allDevices = deviceDao.getAll()
            if (allDevices.isEmpty()) return@withContext null
            _scanning.value = true
            val startedAt = System.currentTimeMillis()
            val semaphore = Semaphore(4)
            val outcomes = coroutineScope {
                allDevices.map { device ->
                    async {
                        semaphore.withPermit {
                            evaluateDevice(device)
                        }
                    }
                }.awaitAll()
            }
            outcomes.forEach { deviceDao.update(it.device) }
            val newCount = outcomes.count { it.changed }
            val upToDateCount = outcomes.count { it.sessionStatus == DeviceStatus.UP_TO_DATE }
            val failedCount = outcomes.count { it.sessionStatus == DeviceStatus.FAILED }
            val primary = (outcomes.firstOrNull { it.changed } ?: outcomes.first()).device
            val session = ScanSession(
                startedAt = startedAt,
                trigger = trigger,
                deviceCount = outcomes.size,
                newCount = newCount,
                upToDateCount = upToDateCount,
                failedCount = failedCount,
                firstModel = primary.model,
                firstCsc = primary.csc
            )
            val sessionId = historyDao.insertSession(session)
            val results = outcomes.map { outcome ->
                ScanResult(
                    sessionId = sessionId,
                    deviceName = outcome.device.name,
                    model = outcome.device.model,
                    csc = outcome.device.csc,
                    status = outcome.sessionStatus,
                    firmware = outcome.device.firmware,
                    previousFirmware = outcome.device.previousFirmware,
                    error = outcome.device.lastError
                )
            }
            historyDao.insertResults(results)
            settings.lastScanAt = System.currentTimeMillis()
            historyDao.trim(settings.historyLimit)
            if (settings.notifyUpdates) {
                outcomes.filter { it.changed }.forEach { notifier.notifyUpdate(it.device) }
            }
            if (settings.notifyFailures && failedCount > 0 && trigger == ScanTrigger.AUTO) {
                notifier.notifyFailures(failedCount)
            }
            session.copy(id = sessionId)
        } finally {
            _scanning.value = false
            scanMutex.unlock()
        }
    }

    suspend fun scanOne(id: Long): Device? = withContext(Dispatchers.IO) {
        val device = deviceDao.getById(id) ?: return@withContext null
        val outcome = evaluateDevice(device)
        deviceDao.update(outcome.device)
        if (outcome.changed && settings.notifyUpdates) {
            notifier.notifyUpdate(outcome.device)
        }
        outcome.device
    }

    suspend fun sessionResults(sessionId: Long): List<ScanResult> = withContext(Dispatchers.IO) {
        historyDao.getResults(sessionId)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyDao.clear()
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val list = deviceDao.getAll()
        val array = JSONArray()
        for (device in list) {
            val item = JSONObject()
                .put("name", device.name)
                .put("model", device.model)
                .put("csc", device.csc)
                .put("type", device.type.name)
                .put("firmware", device.firmware ?: JSONObject.NULL)
                .put("fullFirmware", device.fullFirmware ?: JSONObject.NULL)
                .put("previousFirmware", device.previousFirmware ?: JSONObject.NULL)
                .put("androidVersion", device.androidVersion ?: JSONObject.NULL)
                .put("status", device.status.name)
                .put("lastCheckedAt", device.lastCheckedAt ?: JSONObject.NULL)
                .put("updateFoundAt", device.updateFoundAt ?: JSONObject.NULL)
            array.put(item)
        }
        JSONObject()
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("devices", array)
            .toString(2)
    }

    suspend fun importJson(json: String): Int = withContext(Dispatchers.IO) {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid backup file")
        }
        if (!root.has("devices")) {
            throw IllegalArgumentException("Invalid backup file")
        }
        val array = try {
            root.getJSONArray("devices")
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid backup file")
        }
        var imported = 0
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: throw IllegalArgumentException("Invalid backup file")
            val model = obj.optNullableString("model")?.trim()?.uppercase()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Invalid backup file")
            val csc = obj.optNullableString("csc")?.trim()?.uppercase()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Invalid backup file")
            if (deviceDao.find(model, csc) != null) continue
            val name = obj.optNullableString("name")?.trim()?.ifEmpty { model } ?: model
            val type = obj.optNullableString("type")
                ?.let { runCatching { DeviceType.valueOf(it) }.getOrNull() }
                ?: DeviceType.guess(model)
            val status = obj.optNullableString("status")
                ?.let { runCatching { DeviceStatus.valueOf(it) }.getOrNull() }
                ?: DeviceStatus.UNKNOWN
            val device = Device(
                name = name,
                model = model,
                csc = csc,
                type = type,
                firmware = obj.optNullableString("firmware"),
                fullFirmware = obj.optNullableString("fullFirmware"),
                previousFirmware = obj.optNullableString("previousFirmware"),
                androidVersion = obj.optNullableString("androidVersion"),
                status = status,
                lastCheckedAt = obj.optNullableLong("lastCheckedAt"),
                updateFoundAt = obj.optNullableLong("updateFoundAt")
            )
            deviceDao.insert(device)
            imported++
        }
        imported
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        deviceDao.deleteAll()
        historyDao.clear()
        settings.lastScanAt = 0L
    }

    fun counts(devices: List<Device>): DeviceCounts = DeviceCounts(
        all = devices.size,
        new = devices.count { it.status == DeviceStatus.NEW_UPDATE },
        upToDate = devices.count { it.status == DeviceStatus.UP_TO_DATE },
        failed = devices.count { it.status == DeviceStatus.FAILED }
    )

    fun historyCounts(sessions: List<ScanSession>): HistoryCounts = HistoryCounts(
        all = sessions.size,
        new = sessions.count { it.newCount > 0 },
        failed = sessions.count { it.failedCount > 0 }
    )

    private suspend fun evaluateDevice(device: Device): ScanOutcome {
        val now = System.currentTimeMillis()
        return try {
            val info = client.fetch(device.model, device.csc)
            val stored = device.firmware
            when {
                stored == null -> {
                    val updated = device.copy(
                        firmware = info.firmware,
                        fullFirmware = info.fullFirmware,
                        androidVersion = info.androidVersion,
                        status = DeviceStatus.UP_TO_DATE,
                        lastCheckedAt = now,
                        lastError = null
                    )
                    ScanOutcome(updated, DeviceStatus.UP_TO_DATE, false)
                }
                info.firmware != stored -> {
                    val updated = device.copy(
                        previousFirmware = stored,
                        firmware = info.firmware,
                        fullFirmware = info.fullFirmware,
                        androidVersion = info.androidVersion ?: device.androidVersion,
                        status = DeviceStatus.NEW_UPDATE,
                        lastCheckedAt = now,
                        updateFoundAt = now,
                        lastError = null
                    )
                    ScanOutcome(updated, DeviceStatus.NEW_UPDATE, true)
                }
                else -> {
                    val nextStatus = if (device.status == DeviceStatus.NEW_UPDATE) {
                        DeviceStatus.NEW_UPDATE
                    } else {
                        DeviceStatus.UP_TO_DATE
                    }
                    val updated = device.copy(
                        fullFirmware = info.fullFirmware,
                        androidVersion = info.androidVersion ?: device.androidVersion,
                        status = nextStatus,
                        lastCheckedAt = now,
                        lastError = null
                    )
                    ScanOutcome(updated, DeviceStatus.UP_TO_DATE, false)
                }
            }
        } catch (e: Exception) {
            val updated = device.copy(
                status = DeviceStatus.FAILED,
                lastCheckedAt = now,
                lastError = e.message ?: "Scan failed"
            )
            ScanOutcome(updated, DeviceStatus.FAILED, false)
        }
    }

    private data class ScanOutcome(
        val device: Device,
        val sessionStatus: DeviceStatus,
        val changed: Boolean
    )

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key, 0L).takeIf { it > 0L }
}
