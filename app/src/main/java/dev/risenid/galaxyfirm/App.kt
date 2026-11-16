package dev.risenid.galaxyfirm

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dev.risenid.galaxyfirm.widget.WidgetUpdater
import dev.risenid.galaxyfirm.work.DigestScheduler
import dev.risenid.galaxyfirm.work.ScanScheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(ServiceLocator.settings(this).nightMode())
        ServiceLocator.notifier(this).ensureChannels()
        ScanScheduler.apply(this, reschedule = false)
        DigestScheduler.apply(this, reschedule = false)
        WidgetUpdater.start(this)
    }
}
