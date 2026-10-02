package org.sovereignos.airpods.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.sovereignos.airpods.ui.AirPodsRecognition
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.Backoff

/**
 * Owns the one AirPods session of the process. Every field is touched on the main thread only: the
 * receiver, the service and the session's callbacks (posted) all arrive there. The foreground
 * service exists to keep the process out of the frozen cached state (a cached app does not run);
 * it lives exactly as long as the Bluetooth link to the recognised AirPods.
 */
internal object AirPodsSessionHost {
    private const val TAG = "AirPodsHost"
    private const val SETTLE_MS = 300L

    private val main = Handler(Looper.getMainLooper())
    private val opener: AirPodsSocketOpener = HiddenL2capOpener

    private var service: AirPodsSessionService? = null
    private var target: BluetoothDevice? = null
    private var session: AirPodsSession? = null
    private var attempt = 0
    private var sinkBeforeSession: AirPodsCommandSink? = null
    private var ears: EarDetectionController? = null
    private var duck: ConversationDuckController? = null
    private val retry = Runnable { connect() }

    /** True while a published session exists. The debug input stays quiet then. */
    @Volatile
    var live = false
        private set

    /** A bonded device that lists the accessory-protocol service. Only a candidate test. */
    fun recognised(device: BluetoothDevice): Boolean {
        if (device.bondState != BluetoothDevice.BOND_BONDED) return false
        val uuids = device.uuids?.map { it.toString() }
        return AirPodsRecognition.matches(uuids)
    }

    /**
     * For the binder thread that answers Settings' request for the page: Settings reads the list of
     * items once, so if the AirPods are connected but the session has not reported the listening mode
     * yet (the process was just started, or the link just came up), wait for it, at most
     * [timeoutMs]. Based on the real link state, not on a flag that could race with the process start.
     */
    fun awaitFirstControls(context: Context, address: String, timeoutMs: Long) {
        if (AirPodsUiState.Feature.LISTENING_MODE in AirPodsStateStore.state.supported) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return
        }
        if (!device.isConnected || !recognised(device)) return
        val latch = java.util.concurrent.CountDownLatch(1)
        val listener = AirPodsStateStore.Listener { _, new ->
            if (AirPodsUiState.Feature.LISTENING_MODE in new.supported) latch.countDown()
        }
        AirPodsStateStore.addListener(listener)
        try {
            if (AirPodsUiState.Feature.LISTENING_MODE !in AirPodsStateStore.state.supported &&
                latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            ) {
                // The settings arrive as one burst a few milliseconds apart; let the rest of it land
                // (a page laid out between the mode and the conversation awareness frame lacks a row).
                Thread.sleep(SETTLE_MS)
            }
        } finally {
            AirPodsStateStore.removeListener(listener)
        }
    }

    /** The BR/EDR link of a bonded device came up (receiver). */
    fun onLinkUp(context: Context, device: BluetoothDevice) {
        if (!recognised(device)) return
        target = device
        attempt = 0
        try {
            context.startForegroundService(Intent(context, AirPodsSessionService::class.java))
        } catch (e: RuntimeException) {
            // For example the foreground-service start rule or a missing BLUETOOTH_CONNECT grant.
            Log.w(TAG, "foreground service not started: " + e.javaClass.simpleName)
        }
    }

    /** The link or the bond of this address is gone (receiver). */
    fun onLinkDown(address: String) {
        val t = target ?: return
        if (!t.address.equals(address, ignoreCase = true)) return
        target = null
        main.removeCallbacks(retry)
        val s = session
        if (s != null) s.close() else stopService()
    }

    /** A process start finds the AirPods already connected (for example after the process was killed). */
    fun resume(context: Context) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val bonded = try {
            adapter.bondedDevices
        } catch (e: SecurityException) {
            return
        }
        for (device in bonded) if (device.isConnected) onLinkUp(context, device)
    }

    fun onServiceStarted(svc: AirPodsSessionService) {
        service = svc
        connect()
    }

    fun onServiceStopped(svc: AirPodsSessionService) {
        if (service === svc) service = null
    }

    private fun connect() {
        val device = target
        if (device == null) {
            stopService()
            return
        }
        if (session != null) return
        // The platform serves the L2CAP mode and the Device-ID answer of this peer only once it is
        // registered, so the registration comes before the first connection attempt.
        if (!device.setAirPodsIdentity(true)) {
            retryOrStop(device)
            return
        }
        val address = device.address.uppercase()
        val earDetection = service?.let { EarDetectionStore.read(it.applicationContext, address) } ?: true
        val s = AirPodsSession(device, address, opener, earDetection, listener)
        session = s
        service?.let { ears = EarDetectionController(it.applicationContext, device, earDetection) }
        // The conversation awareness switch value arrives with the AirPods' first reports; the controller
        // follows the state store from then on, so it starts with the switch off.
        service?.let { duck = ConversationDuckController(it.applicationContext, false) }
        s.start()
    }

    private val listener = object : AirPodsSession.Listener {
        override fun onPublished(session: AirPodsSession) {
            main.post {
                if (this@AirPodsSessionHost.session !== session) return@post
                attempt = 0
                live = true
                sinkBeforeSession = AirPodsCommands.sink
                AirPodsCommands.sink = SessionCommandSink(session, service?.applicationContext)
            }
        }

        override fun onEars(session: AirPodsSession, inEar: Int) {
            main.post {
                if (this@AirPodsSessionHost.session === session) {
                    ears?.onEars(inEar)
                    if (inEar == 0) duck?.onPodsOut()
                }
            }
        }

        override fun onConversation(session: AirPodsSession, level: Int) {
            main.post {
                if (this@AirPodsSessionHost.session === session) duck?.onLevel(level)
            }
        }

        override fun onEnded(session: AirPodsSession, reason: AirPodsSession.Reason) {
            main.post { ended(session, reason) }
        }
    }

    private fun ended(ended: AirPodsSession, reason: AirPodsSession.Reason) {
        if (session !== ended) return
        ears?.end()
        ears = null
        duck?.end()
        duck = null
        session = null
        if (live) {
            live = false
            AirPodsCommands.sink = sinkBeforeSession ?: AirPodsCommands.sink
        }
        AirPodsStateStore.update(AirPodsUiState.DISCONNECTED)
        Log.i(TAG, "session ended: $reason")
        val device = target
        when (reason) {
            // We closed it because the link went down; if the link came up again meanwhile, start over.
            AirPodsSession.Reason.CLOSED_BY_US -> if (device == null) stopService() else connect()
            AirPodsSession.Reason.UNSUPPORTED,
            AirPodsSession.Reason.PEER_MISBEHAVED -> stopService()
            AirPodsSession.Reason.CONNECT_FAILED,
            AirPodsSession.Reason.STREAM_ENDED -> if (device == null) stopService() else retryOrStop(device)
        }
    }

    /** Retries with a growing delay while the link is still up; otherwise the service stops. */
    private fun retryOrStop(device: BluetoothDevice) {
        if (!device.isConnected) {
            target = null
            stopService()
            return
        }
        main.postDelayed(retry, Backoff.delayMs(attempt++))
    }

    private fun stopService() {
        val svc = service ?: return
        service = null
        svc.stopSelf()
    }
}
