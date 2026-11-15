package dev.risenid.galaxyfirm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.risenid.galaxyfirm.data.model.Converters
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.model.ScanResult
import dev.risenid.galaxyfirm.data.model.ScanSession

@Database(
    entities = [Device::class, ScanSession::class, ScanResult::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "galaxyfirm.db")
                .build()
    }
}
