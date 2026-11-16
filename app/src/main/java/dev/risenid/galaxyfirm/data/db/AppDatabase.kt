package dev.risenid.galaxyfirm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.risenid.galaxyfirm.data.model.Converters
import dev.risenid.galaxyfirm.data.model.Device
import dev.risenid.galaxyfirm.data.model.ScanResult
import dev.risenid.galaxyfirm.data.model.ScanSession

@Database(
    entities = [Device::class, ScanSession::class, ScanResult::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun historyDao(): HistoryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE devices ADD COLUMN upcomingFirmware TEXT")
                db.execSQL("ALTER TABLE devices ADD COLUMN upcomingAndroidVersion TEXT")
                db.execSQL("ALTER TABLE devices ADD COLUMN upcomingType TEXT")
                db.execSQL("ALTER TABLE devices ADD COLUMN upcomingFoundAt INTEGER")
                db.execSQL("ALTER TABLE scan_sessions ADD COLUMN upcomingCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_results ADD COLUMN upcomingFirmware TEXT")
                db.execSQL("ALTER TABLE scan_results ADD COLUMN upcomingType TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE devices ADD COLUMN releasedAt INTEGER")
                db.execSQL("ALTER TABLE devices ADD COLUMN upcomingReleasedAt INTEGER")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "galaxyfirm.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
