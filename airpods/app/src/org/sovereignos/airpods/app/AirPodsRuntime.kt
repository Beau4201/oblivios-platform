package org.sovereignos.airpods.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Build.IS_DEBUGGABLE is @hide (@UnsupportedAppUsage) and not in the system API stubs, which the
 * first compile of this code showed. Build.TYPE is public and is "user" on a release build.
 */
internal fun isDebuggableBuild(): Boolean = Build.TYPE != "user"

/** Wires the process once: the metadata writer, and the debug input in debuggable builds only. */
object AirPodsRuntime {
    private var writer: AirPodsMetadataWriter? = null

    @Synchronized
    fun start(context: Context) {
        if (writer != null) return
        val app = context.applicationContext
        writer = AirPodsMetadataWriter(app).also { AirPodsStateStore.addListener(it) }
        if (isDebuggableBuild()) DebugStateObserver(app).start()
    }

    /** A connection or bond event: repeat any metadata write that did not succeed before. */
    @Synchronized
    fun onConnectionEvent() {
        writer?.retry()
    }
}

class AirPodsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AirPodsRuntime.start(this)
    }
}

/**
 * Starts our process when an AirPods connects or its bond changes (Plan 6D starts the session
 * here). Both are protected broadcasts that only the system can send, and the Bluetooth process
 * sends them as its own uid, so the receiver has to be exported.
 */
class ConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AirPodsRuntime.start(context)
        AirPodsRuntime.onConnectionEvent()
    }
}
