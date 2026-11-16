package dev.risenid.galaxyfirm.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import dev.risenid.galaxyfirm.data.net.BuildVersion
import dev.risenid.galaxyfirm.data.net.UpdateType

enum class DeviceType {
    PHONE,
    FOLDABLE,
    TABLET,
    WATCH,
    BUDS;

    companion object {
        fun guess(model: String): DeviceType {
            val m = model.trim().uppercase()
            return when {
                m.startsWith("SM-F") -> FOLDABLE
                m.startsWith("SM-X") || m.startsWith("SM-T") || m.startsWith("SM-P") -> TABLET
                m.startsWith("SM-L") || m.startsWith("SM-R8") || m.startsWith("SM-R9") -> WATCH
                m.startsWith("SM-R") -> BUDS
                else -> PHONE
            }
        }
    }
}

enum class DeviceStatus {
    UNKNOWN,
    UP_TO_DATE,
    NEW_UPDATE,
    FAILED
}

enum class AlertLevel {
    ALL,
    MAJOR_SECURITY,
    MAJOR,
    NONE;

    fun allows(type: UpdateType?): Boolean = when (this) {
        ALL -> true
        MAJOR_SECURITY -> type == UpdateType.MAJOR || type == UpdateType.SECURITY
        MAJOR -> type == UpdateType.MAJOR
        NONE -> false
    }

    companion object {
        fun of(value: String?): AlertLevel = entries.firstOrNull { it.name == value } ?: ALL
    }
}

enum class ScanTrigger {
    MANUAL,
    AUTO
}

@Entity(
    tableName = "devices",
    indices = [Index(value = ["model", "csc"], unique = true)]
)
data class Device(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val model: String,
    val csc: String,
    val type: DeviceType,
    val firmware: String? = null,
    val fullFirmware: String? = null,
    val previousFirmware: String? = null,
    val previousAndroidVersion: String? = null,
    val androidVersion: String? = null,
    val status: DeviceStatus = DeviceStatus.UNKNOWN,
    val updateType: UpdateType? = null,
    val lastCheckedAt: Long? = null,
    val updateFoundAt: Long? = null,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val upcomingFirmware: String? = null,
    val upcomingAndroidVersion: String? = null,
    val upcomingType: UpdateType? = null,
    val upcomingFoundAt: Long? = null,
    val releasedAt: Long? = null,
    val upcomingReleasedAt: Long? = null,
    val installedFirmware: String? = null,
    @ColumnInfo(defaultValue = "0") val isThisDevice: Boolean = false,
    @ColumnInfo(defaultValue = "ALL") val alertLevel: String = AlertLevel.ALL.name
)

val Device.alerts: AlertLevel
    get() = AlertLevel.of(alertLevel)

fun Device.updateReadyToInstall(): Boolean {
    val available = firmware ?: return false
    val installed = installedFirmware ?: return false
    return BuildVersion.isNewer(available, installed)
}

fun Device.installUpdateType(): UpdateType? {
    if (!updateReadyToInstall()) return null
    return BuildVersion.classify(firmware.orEmpty(), installedFirmware, androidVersion, null)
}

@Entity(tableName = "digest_items")
data class DigestItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: Long,
    val deviceName: String,
    val model: String,
    val csc: String,
    val firmware: String,
    val kind: String,
    val updateType: UpdateType?,
    val createdAt: Long
) {
    companion object {
        const val AVAILABLE = "AVAILABLE"
        const val UPCOMING = "UPCOMING"
    }
}

@Entity(tableName = "scan_sessions")
data class ScanSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val trigger: ScanTrigger,
    val deviceCount: Int,
    val newCount: Int,
    val majorCount: Int = 0,
    val upToDateCount: Int,
    val failedCount: Int,
    val firstModel: String?,
    val firstCsc: String?,
    val upcomingCount: Int = 0
)

@Entity(
    tableName = "scan_results",
    foreignKeys = [
        ForeignKey(
            entity = ScanSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ScanResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val deviceName: String,
    val model: String,
    val csc: String,
    val status: DeviceStatus,
    val updateType: UpdateType? = null,
    val firmware: String?,
    val previousFirmware: String?,
    val error: String?,
    val upcomingFirmware: String? = null,
    val upcomingType: UpdateType? = null
)

data class DeviceCounts(
    val all: Int,
    val new: Int,
    val upcoming: Int,
    val upToDate: Int,
    val failed: Int
)

data class HistoryCounts(
    val all: Int,
    val new: Int,
    val failed: Int
)

enum class HistoryFilter {
    ALL,
    NEW,
    FAILED
}

class Converters {
    @TypeConverter
    fun fromDeviceType(value: DeviceType): String = value.name

    @TypeConverter
    fun toDeviceType(value: String): DeviceType =
        runCatching { DeviceType.valueOf(value) }.getOrDefault(DeviceType.PHONE)

    @TypeConverter
    fun fromDeviceStatus(value: DeviceStatus): String = value.name

    @TypeConverter
    fun toDeviceStatus(value: String): DeviceStatus =
        runCatching { DeviceStatus.valueOf(value) }.getOrDefault(DeviceStatus.UNKNOWN)

    @TypeConverter
    fun fromScanTrigger(value: ScanTrigger): String = value.name

    @TypeConverter
    fun toScanTrigger(value: String): ScanTrigger =
        runCatching { ScanTrigger.valueOf(value) }.getOrDefault(ScanTrigger.MANUAL)

    @TypeConverter
    fun fromUpdateType(value: UpdateType?): String? = value?.name

    @TypeConverter
    fun toUpdateType(value: String?): UpdateType? =
        value?.let { runCatching { UpdateType.valueOf(it) }.getOrNull() }
}
