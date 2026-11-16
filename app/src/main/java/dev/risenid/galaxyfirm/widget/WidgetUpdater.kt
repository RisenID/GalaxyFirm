package dev.risenid.galaxyfirm.widget

import android.content.Context
import dev.risenid.galaxyfirm.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        val repository = ServiceLocator.repository(app)
        scope.launch {
            combine(repository.devices.distinctUntilChanged(), repository.scanning, ::Pair)
                .distinctUntilChanged()
                .collect { (devices, scanning) -> app.updateWidgets(devices, scanning) }
        }
    }

    internal fun refresh(context: Context, done: () -> Unit) {
        val app = context.applicationContext
        val repository = ServiceLocator.repository(app)
        scope.launch {
            try {
                app.updateWidgets(repository.devices.first(), repository.scanning.value)
            } finally {
                done()
            }
        }
    }
}
