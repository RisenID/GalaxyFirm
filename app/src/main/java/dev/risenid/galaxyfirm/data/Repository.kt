package dev.risenid.galaxyfirm.data

import dev.risenid.galaxyfirm.data.db.AppDatabase
import dev.risenid.galaxyfirm.data.model.AlertLevel
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.model.DeviceCounts
import dev.risenid.galaxyfirm.data.model.DeviceStatus
import dev.risenid.galaxyfirm.data.model.DeviceType
import dev.risenid.galaxyfirm.data.model.DigestItem
import dev.risenid.galaxyfirm.data.model.HistoryCounts
import dev.risenid.galaxyfirm.data.model.ScanResult
import dev.risenid.galaxyfirm.data.model.ScanSession
import dev.risenid.galaxyfirm.data.model.ScanTrigger
import dev.risenid.galaxyfirm.data.model.alerts
import dev.risenid.galaxyfirm.data.net.BuildVersion
import dev.risenid.galaxyfirm.data.net.FirmwareClient
import dev.risenid.galaxyfirm.data.net.FirmwareException
import dev.risenid.galaxyfirm.data.net.FirmwareLookup
import dev.risenid.galaxyfirm.data.net.FusBinary
import dev.risenid.galaxyfirm.data.net.UpdateType
import dev.risenid.galaxyfirm.data.net.androidVersion
import dev.risenid.galaxyfirm.data.net.pda
import dev.risenid.galaxyfirm.data.net.releasedAt
import dev.risenid.galaxyfirm.notify.Notifier
import dev.risenid.galaxyfirm.util.ThisDevice
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

data class RegionBuild(val csc: String, val firmware: String)

class Repository(
    private val db: AppDatabase,
    private val client: FirmwareClient,
    private val settings: Settings,
    private val notifier: Notifier
) {
    private val deviceDao = db.deviceDao()
    private val historyDao = db.historyDao()
    private val digestDao = db.digestDao()
    private val scanMutex = Mutex()
    private val _scanning = MutableStateFlow(false)

    val devices: Flow<List<Device>> = deviceDao.observeAll()
    val sessions: Flow<List<ScanSession>> = historyDao.observeSessions()
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    suspend fun addDevice(
        name: String,
        model: String,
        csc: String,
        type: DeviceType,
        installedFirmware: String? = null,
        isThisDevice: Boolean = false,
        alertLevel: AlertLevel = AlertLevel.ALL
    ): Result<Long> =
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
                        type = type,
                        installedFirmware = installedFirmware?.trim()?.uppercase()?.ifEmpty { null },
                        isThisDevice = isThisDevice,
                        alertLevel = alertLevel.name
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

    suspend fun setAlertLevel(id: Long, level: AlertLevel) = withContext(Dispatchers.IO) {
        deviceDao.setAlertLevel(id, level.name)
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
                updateType = null,
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
            val upcomingCount = outcomes.count { it.upcomingChanged }
            val majorCount = outcomes.count { it.changed && it.device.updateType == UpdateType.MAJOR }
            val upToDateCount = outcomes.count { it.sessionStatus == DeviceStatus.UP_TO_DATE }
            val failedCount = outcomes.count { it.sessionStatus == DeviceStatus.FAILED }
            val primary = (outcomes.firstOrNull { it.changed } ?: outcomes.firstOrNull { it.upcomingChanged } ?: outcomes.first()).device
            val session = ScanSession(
                startedAt = startedAt,
                trigger = trigger,
                deviceCount = outcomes.size,
                newCount = newCount,
                majorCount = majorCount,
                upToDateCount = upToDateCount,
                failedCount = failedCount,
                firstModel = primary.model,
                firstCsc = primary.csc,
                upcomingCount = upcomingCount
            )
            val sessionId = historyDao.insertSession(session)
            val results = outcomes.map { outcome ->
                ScanResult(
                    sessionId = sessionId,
                    deviceName = outcome.device.name,
                    model = outcome.device.model,
                    csc = outcome.device.csc,
                    status = outcome.sessionStatus,
                    updateType = if (outcome.changed) outcome.device.updateType else null,
                    firmware = outcome.device.firmware,
                    previousFirmware = outcome.device.previousFirmware,
                    error = outcome.device.lastError,
                    upcomingFirmware = outcome.device.upcomingFirmware.takeIf { outcome.upcomingChanged },
                    upcomingType = outcome.device.upcomingType.takeIf { outcome.upcomingChanged }
                )
            }
            historyDao.insertResults(results)
            settings.lastScanAt = System.currentTimeMillis()
            historyDao.trim(settings.historyLimit)
            alert(outcomes)
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
        alert(listOf(outcome))
        outcome.device
    }

    suspend fun otherRegions(model: String, codes: List<String>): List<RegionBuild> = coroutineScope {
        val semaphore = Semaphore(8)
        codes.map { code ->
            async { semaphore.withPermit { client.publicFirmware(model, code)?.let { RegionBuild(code, it.firmware) } } }
        }.awaitAll().filterNotNull().sortedWith { a, b -> BuildVersion.compare(b.firmware, a.firmware) ?: 0 }
    }

    suspend fun postDigest() = withContext(Dispatchers.IO) {
        val items = digestDao.getAll()
            .groupBy { it.deviceId to it.kind }
            .map { (_, group) -> group.last() }
        if (items.isNotEmpty()) notifier.notifyDigest(items)
        digestDao.clear()
    }

    suspend fun clearDigest() = withContext(Dispatchers.IO) {
        digestDao.clear()
    }

    private suspend fun alert(outcomes: List<ScanOutcome>) {
        val available = outcomes.filter { settings.notifyUpdates && it.changed && it.device.alerts.allows(it.device.updateType) }
            .map { it.device }
        val upcoming = outcomes.filter { settings.notifyUpcoming && it.upcomingChanged && it.device.alerts.allows(it.device.upcomingType) }
            .map { it.device }
        if (!settings.digest) {
            available.forEach(notifier::notifyUpdate)
            upcoming.forEach(notifier::notifyUpcoming)
            return
        }
        val now = System.currentTimeMillis()
        val items = available.map { it.digestItem(DigestItem.AVAILABLE, it.firmware, it.updateType, now) } +
            upcoming.map { it.digestItem(DigestItem.UPCOMING, it.upcomingFirmware, it.upcomingType, now) }
        if (items.isNotEmpty()) digestDao.insert(items)
    }

    private fun Device.digestItem(kind: String, build: String?, type: UpdateType?, now: Long) = DigestItem(
        deviceId = id,
        deviceName = name,
        model = model,
        csc = csc,
        firmware = build.orEmpty(),
        kind = kind,
        updateType = type,
        createdAt = now
    )

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
                .put("previousAndroidVersion", device.previousAndroidVersion ?: JSONObject.NULL)
                .put("androidVersion", device.androidVersion ?: JSONObject.NULL)
                .put("status", device.status.name)
                .put("updateType", device.updateType?.name ?: JSONObject.NULL)
                .put("lastCheckedAt", device.lastCheckedAt ?: JSONObject.NULL)
                .put("updateFoundAt", device.updateFoundAt ?: JSONObject.NULL)
                .put("upcomingFirmware", device.upcomingFirmware ?: JSONObject.NULL)
                .put("upcomingAndroidVersion", device.upcomingAndroidVersion ?: JSONObject.NULL)
                .put("upcomingType", device.upcomingType?.name ?: JSONObject.NULL)
                .put("upcomingFoundAt", device.upcomingFoundAt ?: JSONObject.NULL)
                .put("releasedAt", device.releasedAt ?: JSONObject.NULL)
                .put("upcomingReleasedAt", device.upcomingReleasedAt ?: JSONObject.NULL)
                .put("installedFirmware", device.installedFirmware ?: JSONObject.NULL)
                .put("isThisDevice", device.isThisDevice)
                .put("alertLevel", device.alertLevel)
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
            val updateType = obj.optNullableUpdateType("updateType")
            val parsed = Device(
                name = name,
                model = model,
                csc = csc,
                type = type,
                firmware = obj.optNullableString("firmware"),
                fullFirmware = obj.optNullableString("fullFirmware"),
                previousFirmware = obj.optNullableString("previousFirmware"),
                previousAndroidVersion = obj.optNullableString("previousAndroidVersion"),
                androidVersion = obj.optNullableString("androidVersion"),
                status = status,
                updateType = updateType,
                lastCheckedAt = obj.optNullableLong("lastCheckedAt"),
                updateFoundAt = obj.optNullableLong("updateFoundAt"),
                upcomingFirmware = obj.optNullableString("upcomingFirmware"),
                upcomingAndroidVersion = obj.optNullableString("upcomingAndroidVersion"),
                upcomingType = obj.optNullableUpdateType("upcomingType"),
                upcomingFoundAt = obj.optNullableLong("upcomingFoundAt"),
                releasedAt = obj.optNullableLong("releasedAt"),
                upcomingReleasedAt = obj.optNullableLong("upcomingReleasedAt"),
                installedFirmware = obj.optNullableString("installedFirmware")?.trim()?.uppercase(),
                isThisDevice = obj.optBoolean("isThisDevice") && ThisDevice.current?.matches(model, csc) == true,
                alertLevel = AlertLevel.of(obj.optNullableString("alertLevel")).name
            )
            deviceDao.insert(parsed.copy(updateType = parsed.updateType ?: inferUpdateType(parsed)))
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
        upcoming = devices.count { it.upcomingFirmware != null },
        upToDate = devices.count { it.status == DeviceStatus.UP_TO_DATE },
        failed = devices.count { it.status == DeviceStatus.FAILED }
    )

    fun historyCounts(sessions: List<ScanSession>): HistoryCounts = HistoryCounts(
        all = sessions.size,
        new = sessions.count { it.newCount > 0 },
        failed = sessions.count { it.failedCount > 0 }
    )

    private fun inferUpdateType(device: Device): UpdateType? {
        val firmware = device.firmware ?: return null
        if (device.status != DeviceStatus.NEW_UPDATE) return null
        return BuildVersion.classify(firmware, device.previousFirmware, device.androidVersion, device.previousAndroidVersion)
    }

    private fun Device.withInstalledBuild(): Device {
        val here = ThisDevice.current?.takeIf { it.matches(model, csc) } ?: return this
        return copy(isThisDevice = true, installedFirmware = here.installedPda ?: installedFirmware)
    }

    private suspend fun evaluateDevice(original: Device): ScanOutcome {
        val device = original.withInstalledBuild()
        val now = System.currentTimeMillis()
        return try {
            val lookup = client.lookup(device.model, device.csc)
            val pub = lookup.public ?: throw FirmwareException("No firmware published")
            val stored = device.firmware
            val outcome = when {
                stored == null -> {
                    val up = lookup.upcoming
                    val updated = device.copy(
                        firmware = pub.firmware,
                        fullFirmware = pub.fullFirmware,
                        androidVersion = pub.androidVersion,
                        status = DeviceStatus.UP_TO_DATE,
                        updateType = null,
                        lastCheckedAt = now,
                        lastError = null,
                        upcomingFirmware = up?.firmware,
                        upcomingAndroidVersion = up?.androidVersion,
                        upcomingType = up?.let { BuildVersion.classify(it.firmware, pub.firmware, it.androidVersion, pub.androidVersion) },
                        upcomingFoundAt = up?.let { now }
                    )
                    ScanOutcome(updated, DeviceStatus.UP_TO_DATE, changed = false, upcomingChanged = false)
                }
                BuildVersion.isNewer(pub.firmware, stored) -> {
                    val updated = device.copy(
                        previousFirmware = stored,
                        previousAndroidVersion = device.androidVersion,
                        firmware = pub.firmware,
                        fullFirmware = pub.fullFirmware,
                        androidVersion = pub.androidVersion ?: device.androidVersion,
                        status = DeviceStatus.NEW_UPDATE,
                        updateType = BuildVersion.classify(pub.firmware, stored, pub.androidVersion, device.androidVersion),
                        lastCheckedAt = now,
                        updateFoundAt = now,
                        lastError = null
                    )
                    withUpcoming(updated, device, lookup, now, changed = true)
                }
                BuildVersion.isNewer(stored, pub.firmware) -> {
                    val wasNew = device.status == DeviceStatus.NEW_UPDATE
                    val updated = device.copy(
                        firmware = pub.firmware,
                        fullFirmware = pub.fullFirmware,
                        androidVersion = pub.androidVersion,
                        previousFirmware = device.previousFirmware?.takeUnless { it == pub.firmware },
                        previousAndroidVersion = if (wasNew) null else device.previousAndroidVersion,
                        status = DeviceStatus.UP_TO_DATE,
                        updateType = null,
                        updateFoundAt = if (wasNew) null else device.updateFoundAt,
                        lastCheckedAt = now,
                        lastError = null
                    )
                    withUpcoming(updated, device, lookup, now, changed = false)
                }
                else -> {
                    val nextStatus = if (device.updateFoundAt != null) {
                        DeviceStatus.NEW_UPDATE
                    } else {
                        DeviceStatus.UP_TO_DATE
                    }
                    val same = pub.firmware == stored
                    val updated = device.copy(
                        fullFirmware = if (same) pub.fullFirmware else device.fullFirmware,
                        androidVersion = if (same) pub.androidVersion ?: device.androidVersion else device.androidVersion,
                        status = nextStatus,
                        updateType = if (nextStatus == DeviceStatus.NEW_UPDATE) device.updateType ?: inferUpdateType(device) else null,
                        lastCheckedAt = now,
                        lastError = null
                    )
                    withUpcoming(updated, device, lookup, now, changed = false)
                }
            }
            outcome.copy(device = outcome.device.enrich(device, lookup.history))
        } catch (e: Exception) {
            val nextStatus = if (device.updateFoundAt != null) DeviceStatus.NEW_UPDATE else DeviceStatus.FAILED
            val updated = device.copy(
                status = nextStatus,
                updateType = if (nextStatus == DeviceStatus.NEW_UPDATE) device.updateType else null,
                lastCheckedAt = now,
                lastError = e.message ?: "Scan failed"
            )
            ScanOutcome(updated, DeviceStatus.FAILED, changed = false, upcomingChanged = false)
        }
    }

    private fun withUpcoming(updated: Device, previous: Device, lookup: FirmwareLookup, now: Long, changed: Boolean): ScanOutcome {
        val current = updated.firmware.orEmpty()
        val status = if (changed) DeviceStatus.NEW_UPDATE else DeviceStatus.UP_TO_DATE
        val up = lookup.upcoming?.takeIf { BuildVersion.isNewer(it.firmware, current) }
        if (up != null) {
            val isNew = up.firmware != previous.upcomingFirmware
            val device = updated.copy(
                upcomingFirmware = up.firmware,
                upcomingAndroidVersion = up.androidVersion,
                upcomingType = BuildVersion.classify(up.firmware, current, up.androidVersion, updated.androidVersion),
                upcomingFoundAt = if (isNew) now else previous.upcomingFoundAt
            )
            return ScanOutcome(device, status, changed, upcomingChanged = isNew && up.firmware != previous.firmware)
        }
        val keep = !lookup.fusAvailable && previous.upcomingFirmware?.let { BuildVersion.isNewer(it, current) } == true
        val device = if (keep) {
            updated
        } else {
            updated.copy(upcomingFirmware = null, upcomingAndroidVersion = null, upcomingType = null, upcomingFoundAt = null)
        }
        return ScanOutcome(device, status, changed, upcomingChanged = false)
    }

    private fun Device.enrich(previous: Device, history: List<FusBinary>): Device {
        val index = history.indexOfFirst { it.pda == firmware }
        val match = history.getOrNull(index)
        val previousPda = previousFirmware ?: history.getOrNull(index - 1)?.pda
        val upcoming = history.firstOrNull { it.pda == upcomingFirmware }
        return copy(
            androidVersion = androidVersion ?: match?.androidVersion,
            previousFirmware = previousPda,
            previousAndroidVersion = previousAndroidVersion ?: history.firstOrNull { it.pda == previousPda }?.androidVersion,
            releasedAt = match?.releasedAt ?: releasedAt.takeIf { firmware == previous.firmware },
            upcomingReleasedAt = upcoming?.releasedAt ?: upcomingReleasedAt.takeIf { upcomingFirmware == previous.upcomingFirmware }
        )
    }

    private data class ScanOutcome(
        val device: Device,
        val sessionStatus: DeviceStatus,
        val changed: Boolean,
        val upcomingChanged: Boolean
    )

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

    private fun JSONObject.optNullableUpdateType(key: String): UpdateType? =
        optNullableString(key)?.let { runCatching { UpdateType.valueOf(it) }.getOrNull() }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key, 0L).takeIf { it > 0L }
}
