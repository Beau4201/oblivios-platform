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
    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        AirPodsStateStore.addListener(AirPodsMetadataWriter(app))
        if (isDebuggableBuild()) DebugStateObserver(app).start()
    }
}

class AirPodsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AirPodsRuntime.start(this)
    }
}

/**
 * Starts our process when an AirPods connects (Plan 6D starts the session here). ACL_CONNECTED is
 * a protected broadcast that only the system can send, and the Bluetooth process sends it as its
 * own uid, so the receiver has to be exported.
 */
class ConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AirPodsRuntime.start(context)
    }
}
