package dev.risenid.galaxyfirm.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.model.DigestItem
import dev.risenid.galaxyfirm.data.model.ScanResult
import dev.risenid.galaxyfirm.data.model.ScanSession
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY status = 'NEW_UPDATE' DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Device>>

    @Query("SELECT * FROM devices ORDER BY status = 'NEW_UPDATE' DESC, name COLLATE NOCASE ASC")
    suspend fun getAll(): List<Device>

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Device?

    @Query("SELECT * FROM devices WHERE model = :model AND csc = :csc LIMIT 1")
    suspend fun find(model: String, csc: String): Device?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(device: Device): Long

    @Update
    suspend fun update(device: Device)

    @Delete
    suspend fun delete(device: Device)

    @Query("DELETE FROM devices")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM devices")
    suspend fun count(): Int

    @Query("UPDATE devices SET alertLevel = :level WHERE id = :id")
    suspend fun setAlertLevel(id: Long, level: String)
}

@Dao
interface DigestDao {
    @Insert
    suspend fun insert(items: List<DigestItem>)

    @Query("SELECT * FROM digest_items ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<DigestItem>

    @Query("DELETE FROM digest_items")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC, id DESC")
    fun observeSessions(): Flow<List<ScanSession>>

    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC, id DESC")
    suspend fun getSessions(): List<ScanSession>

    @Query("SELECT * FROM scan_results WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getResults(sessionId: Long): List<ScanResult>

    @Query("SELECT * FROM scan_results ORDER BY id ASC")
    suspend fun getAllResults(): List<ScanResult>

    @Insert
    suspend fun insertSession(session: ScanSession): Long

    @Insert
    suspend fun insertResults(results: List<ScanResult>)

    @Query("DELETE FROM scan_sessions")
    suspend fun clear()

    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC, id DESC LIMIT 1")
    suspend fun latestSession(): ScanSession?

    @Query("DELETE FROM scan_sessions WHERE id NOT IN (SELECT id FROM scan_sessions ORDER BY startedAt DESC, id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}
