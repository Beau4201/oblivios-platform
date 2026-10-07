package org.sovereignos.airpods.app

import android.app.Application
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process

/**
 * Build.IS_DEBUGGABLE is @hide (@UnsupportedAppUsage) and not in the system API stubs, which the
 * first compile of this code showed. Build.TYPE is public and is "user" on a release build.
 */
internal fun isDebuggableBuild(): Boolean = Build.TYPE != "user"

/**
 * The app works only for the system user (the owner). The default grant of BLUETOOTH_CONNECT reaches every user, and
 * the Duress decoy user must not show or drive the owner's AirPods.
 */
internal fun runsInThisUser(): Boolean = Process.myUserHandle().isSystem

/** Wires the process once: the metadata writer, and the debug input in debuggable builds only. */
object AirPodsRuntime {
    private var writer: AirPodsMetadataWriter? = null

    @Synchronized
    fun start(context: Context) {
        if (!runsInThisUser() || writer != null) return
        val app = context.applicationContext
        writer = AirPodsMetadataWriter(app).also { AirPodsStateStore.addListener(it) }
        AirPodsStateStore.addListener(CaseBatteryRecorder(app))
        AirPodsStateStore.addListener(ConnectionSheetTrigger(app))
        if (isDebuggableBuild()) DebugStateObserver(app).start()
        // A process that starts while the AirPods are connected (it was killed, or Settings bound us)
        // has to find the link itself: no connection event will come for a link that is already up.
        Handler(Looper.getMainLooper()).post { AirPodsSessionHost.resume(app) }
    }

    /** A connection or bond event: repeat any metadata write that did not succeed before. */
    @Synchronized
    fun onConnectionEvent() {
        writer?.retry()
    }

    /** The bond of this address is gone: the metadata the platform held for it is gone too. */
    @Synchronized
    fun onUnbond(address: String) {
        writer?.forget(address)
    }
}

class AirPodsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AirPodsRuntime.start(this)
    }
}

/**
 * Starts our process and the session when a bonded AirPods' Bluetooth link comes up or goes away.
 * All three are protected broadcasts that only the system can send, and the Bluetooth process
 * sends them as its own uid, so the receiver has to be exported. Only the BR/EDR link counts: the
 * AirPods also have an LE link that comes and goes on its own.
 */
class ConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!runsInThisUser()) return
        // The receiver is exported (the Bluetooth process sends as its own uid); an explicit broadcast of any other
        // action from another app must not run binder calls on the main thread.
        if (intent.action !in HANDLED_ACTIONS) return
        AirPodsRuntime.start(context)
        AirPodsRuntime.onConnectionEvent()
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED ->
                if (isBredr(intent)) AirPodsSessionHost.onLinkUp(context.applicationContext, device)
            BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                if (isBredr(intent)) AirPodsSessionHost.onLinkDown(device.address)
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED ->
                if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED) == BluetoothProfile.STATE_CONNECTED) {
                    AirPodsSessionHost.onAudioConnected(context.applicationContext, device)
                }
            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_NONE -> {
                        AirPodsSessionHost.onLinkDown(device.address)
                        // The stored values never outlive the bond, and the platform drops the metadata with it.
                        AirPodsRuntime.onUnbond(device.address)
                        CaseBatteryStore.forget(context.applicationContext, device.address)
                        PressAndHoldStore.forget(context.applicationContext, device.address)
                        ControlStore.forget(context.applicationContext, device.address)
                        EarDetectionStore.forget(context.applicationContext, device.address)
                    }
                    // A first pairing: the link came up while the bond was still forming, so no later ACL event comes.
                    BluetoothDevice.BOND_BONDED ->
                        if (device.isConnected) AirPodsSessionHost.onLinkUp(context.applicationContext, device)
                }
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            BluetoothDevice.ACTION_BOND_STATE_CHANGED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
        )
    }

    private fun isBredr(intent: Intent): Boolean =
        intent.getIntExtra(BluetoothDevice.EXTRA_TRANSPORT, BluetoothDevice.TRANSPORT_AUTO) == BluetoothDevice.TRANSPORT_BREDR
}
