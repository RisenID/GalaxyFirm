package dev.risenid.galaxyfirm

import android.content.Context
import dev.risenid.galaxyfirm.data.Repository
import dev.risenid.galaxyfirm.data.Settings
import dev.risenid.galaxyfirm.data.db.AppDatabase
import dev.risenid.galaxyfirm.data.net.FirmwareClient
import dev.risenid.galaxyfirm.notify.Notifier

object ServiceLocator {
    @Volatile
    private var databaseInstance: AppDatabase? = null

    @Volatile
    private var settingsInstance: Settings? = null

    @Volatile
    private var notifierInstance: Notifier? = null

    @Volatile
    private var repositoryInstance: Repository? = null

    fun database(context: Context): AppDatabase =
        databaseInstance ?: synchronized(this) {
            databaseInstance ?: AppDatabase.create(context.applicationContext).also {
                databaseInstance = it
            }
        }

    fun settings(context: Context): Settings =
        settingsInstance ?: synchronized(this) {
            settingsInstance ?: Settings(context.applicationContext).also {
                settingsInstance = it
            }
        }

    fun notifier(context: Context): Notifier =
        notifierInstance ?: synchronized(this) {
            notifierInstance ?: Notifier(context.applicationContext).also {
                it.ensureChannels()
                notifierInstance = it
            }
        }

    fun repository(context: Context): Repository =
        repositoryInstance ?: synchronized(this) {
            repositoryInstance ?: Repository(
                db = database(context),
                client = FirmwareClient(),
                settings = settings(context),
                notifier = notifier(context)
            ).also {
                repositoryInstance = it
            }
        }
}
