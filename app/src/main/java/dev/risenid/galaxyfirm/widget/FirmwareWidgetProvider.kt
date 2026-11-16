package dev.risenid.galaxyfirm.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class FirmwareWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) WidgetScanWorker.enqueue(context) else super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.start(context)
        val pending = goAsync()
        WidgetUpdater.refresh(context) { pending.finish() }
    }

    companion object {
        const val ACTION_REFRESH = "dev.risenid.galaxyfirm.widget.REFRESH"
    }
}
