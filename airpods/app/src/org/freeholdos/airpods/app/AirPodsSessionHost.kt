// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.freeholdos.airpods.ui.AirPodsRecognition
import org.freeholdos.airpods.ui.AirPodsUiState
import org.freeholdos.airpods.ui.Backoff
import org.freeholdos.airpods.ui.SessionSeed

/**
 * Owns the one AirPods session of the process. Every field is touched on the main thread only: the
 * receiver, the service and the session's callbacks (posted) all arrive there. The foreground
 * service exists to keep the process out of the frozen cached state (a cached app does not run);
 * it lives exactly as long as the Bluetooth link to the recognised AirPods.
 */
internal object AirPodsSessionHost {
    private const val TAG = "AirPodsHost"
    private const val SETTLE_MS = 300L

    /** A session that ran this long counts as a working link: the next retry starts at the shortest delay again. */
    private const val STABLE_SESSION_MS = 30_000L
    private const val THIN_CHECK_MS = 10_000L

    private val main = Handler(Looper.getMainLooper())
    private val opener: AirPodsSocketOpener = HiddenL2capOpener

    private var service: AirPodsSessionService? = null
    private var target: BluetoothDevice? = null
    private var session: AirPodsSession? = null
    private var attempt = 0
    private var publishedAt = 0L
    private var audioRestartDone = false
    private var sinkBeforeSession: AirPodsCommandSink? = null
    private var ears: EarDetectionController? = null
    private var duck: ConversationDuckController? = null
    private var find: FindSoundController? = null
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

    /** True when the address is a bonded AirPods (the candidate test of [recognised]); false for anything else or without the grant. */
    fun recognisedAddress(context: Context, address: String): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        return try {
            recognised(adapter.getRemoteDevice(address))
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: SecurityException) {
            false
        }
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
        // A revoked BLUETOOTH_CONNECT makes both calls throw. The exception would travel through Binder to Settings
        // and crash it (found on the device 2026-10-05, AIR-156 d): no grant, nothing to wait for.
        val ready = try {
            device.isConnected && recognised(device)
        } catch (e: SecurityException) {
            false
        }
        if (!ready) return
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
    fun onLinkUp(context: Context, device: BluetoothDevice, resetRestart: Boolean = true) {
        if (!recognised(device)) return
        Log.i(TAG, "link up, session " + (if (session != null) "exists" else "none"))
        target = device
        attempt = 0
        if (resetRestart) audioRestartDone = false
        try {
            context.startForegroundService(Intent(context, AirPodsSessionService::class.java))
        } catch (e: RuntimeException) {
            // For example the foreground-service start rule or a missing BLUETOOTH_CONNECT grant.
            Log.w(TAG, "foreground service not started: " + e.javaClass.simpleName)
        }
    }

    /**
     * An audio profile (A2DP or hands-free) of this address connected (receiver). The pair sends its full settings
     * burst only while the phone holds it as an audio device (protocol facts, "When the pair reports its settings"),
     * and re-requesting notifications resends none of it. A session that started before the audio connected got
     * the thin burst (no listening mode, no conversation awareness) and would show a page without them until the
     * link drops: restart it once, now that the audio is connected. The burst of a session that started just before
     * the audio connected can still be full, so the check waits [THIN_CHECK_MS] (the settings arrived 4 to 9 s
     * after the start on `lynx`) and restarts only when the listening mode is still missing.
     */
    fun onAudioConnected(context: Context, device: BluetoothDevice) {
        if (session == null) {
            // A first pairing: the bond completes before the service discovery has delivered the UUIDs, so the link-up
            // of that moment did not recognise the pair. The audio profiles connect after the discovery.
            if (target == null) onLinkUp(context, device, resetRestart = false)
            return
        }
        val t = target ?: return
        if (audioRestartDone || !t.address.equals(device.address, ignoreCase = true)) return
        main.removeCallbacks(thinCheck)
        main.postDelayed(thinCheck, THIN_CHECK_MS)
    }

    private val thinCheck = Runnable {
        val s = session ?: return@Runnable
        val state = AirPodsStateStore.state
        if (audioRestartDone || !state.isConnected || AirPodsUiState.Feature.LISTENING_MODE in state.supported) {
            return@Runnable
        }
        audioRestartDone = true
        Log.i(TAG, "audio connected, the session still has no settings: restarting it")
        s.close()
    }

    /** The link or the bond of this address is gone (receiver). */
    fun onLinkDown(address: String) {
        val t = target ?: return
        if (!t.address.equals(address, ignoreCase = true)) return
        Log.i(TAG, "link down, session " + (if (session != null) "open, closing" else "none"))
        target = null
        main.removeCallbacks(retry)
        main.removeCallbacks(thinCheck)
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
        val registered = try {
            device.setAirPodsIdentity(true)
        } catch (e: SecurityException) {
            // A revoked BLUETOOTH_CONNECT: no grant, no session (the exception would reach the main looper).
            Log.w(TAG, "identity registration not allowed")
            target = null
            stopService()
            return
        }
        if (!registered) {
            retryOrStop(device)
            return
        }
        val address = device.address.uppercase()
        val earDetection = service?.let { EarDetectionStore.read(it.applicationContext, address) } ?: true
        val ctx = service?.applicationContext
        val seed = if (ctx == null) {
            SessionSeed.fresh(earDetection)
        } else {
            val case = CaseBatteryStore.read(ctx, address)
            SessionSeed(
                earDetection, DuckLevelStore.read(ctx), case.first, case.second, PressAndHoldStore.read(ctx, address),
                ControlStore.readAll(ctx, address),
            )
        }
        if (isDebuggableBuild()) Log.d(TAG, "seeded controls " + seed.controls())
        val s = AirPodsSession(device, address, opener, seed, listener)
        session = s
        service?.let { ears = EarDetectionController(it.applicationContext, device, earDetection) }
        // The conversation awareness switch value arrives with the AirPods' first reports; the controller
        // follows the state store from then on, so it starts with the switch off.
        service?.let { duck = ConversationDuckController(it.applicationContext, device, false) }
        service?.let { find = FindSoundController(it.applicationContext, ears) }
        s.start()
    }

    private val listener = object : AirPodsSession.Listener {
        override fun onPublished(session: AirPodsSession) {
            main.post {
                if (this@AirPodsSessionHost.session !== session) return@post
                // The backoff is not reset here: a peer that sends one frame and closes would be retried every 2 s
                // for as long as the link is up. ended() resets it after a session that lasted (STABLE_SESSION_MS).
                publishedAt = SystemClock.elapsedRealtime()
                live = true
                sinkBeforeSession = AirPodsCommands.sink
                AirPodsCommands.sink = SessionCommandSink(session, service?.applicationContext, find)
            }
        }

        override fun onEars(session: AirPodsSession, inEar: Int) {
            main.post {
                if (this@AirPodsSessionHost.session === session) {
                    ears?.onEars(inEar)
                    find?.onEars(inEar)
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
        if (live && SystemClock.elapsedRealtime() - publishedAt >= STABLE_SESSION_MS) attempt = 0
        ears?.end()
        ears = null
        duck?.end()
        duck = null
        find?.end()
        find = null
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
            AirPodsSession.Reason.NO_FRAME,
            AirPodsSession.Reason.STREAM_ENDED -> if (device == null) stopService() else retryOrStop(device)
        }
    }

    /** Retries with a growing delay while the link is still up; otherwise the service stops. */
    private fun retryOrStop(device: BluetoothDevice) {
        val connected = try {
            device.isConnected
        } catch (e: SecurityException) {
            false
        }
        if (!connected) {
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
