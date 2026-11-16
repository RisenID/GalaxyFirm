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
import dev.risenid.galaxyfirm.data.model.DigestItem
import dev.risenid.galaxyfirm.data.model.ScanResult
import dev.risenid.galaxyfirm.data.model.ScanSession

@Database(
    entities = [Device::class, ScanSession::class, ScanResult::class, DigestItem::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun historyDao(): HistoryDao
    abstract fun digestDao(): DigestDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE devices ADD COLUMN installedFirmware TEXT")
                db.execSQL("ALTER TABLE devices ADD COLUMN isThisDevice INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE devices ADD COLUMN alertLevel TEXT NOT NULL DEFAULT 'ALL'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `digest_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`deviceId` INTEGER NOT NULL, `deviceName` TEXT NOT NULL, `model` TEXT NOT NULL, `csc` TEXT NOT NULL, " +
                        "`firmware` TEXT NOT NULL, `kind` TEXT NOT NULL, `updateType` TEXT, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "galaxyfirm.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
