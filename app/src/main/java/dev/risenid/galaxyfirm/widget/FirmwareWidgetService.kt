package dev.risenid.galaxyfirm.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.risenid.galaxyfirm.ServiceLocator
import dev.risenid.galaxyfirm.data.model.Device
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class FirmwareWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = DeviceFactory(applicationContext)
}

private class DeviceFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var devices = emptyList<Device>()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        devices = runBlocking { ServiceLocator.repository(context).devices.first() }.widgetOrder()
    }

    override fun onDestroy() {
        devices = emptyList()
    }

    override fun getCount(): Int = devices.size

    override fun getViewAt(position: Int): RemoteViews = context.deviceRow(devices[position])

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = devices[position].id

    override fun hasStableIds(): Boolean = true
}
