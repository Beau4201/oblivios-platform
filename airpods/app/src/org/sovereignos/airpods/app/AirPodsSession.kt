package org.sovereignos.airpods.app

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.SessionReducer
import org.sovereignos.airpods.ui.SessionSeed
import org.sovereignos.airpods.validation.AapCodec
import org.sovereignos.airpods.validation.AirPodsValidationException
import org.sovereignos.airpods.validation.BatteryStatus
import org.sovereignos.airpods.validation.ConversationAwarenessLevel
import org.sovereignos.airpods.validation.EarState
import org.sovereignos.airpods.validation.ListeningModeState
import org.sovereignos.airpods.validation.UnrecognisedMessage

/**
 * One L2CAP session with one AirPods: opens the channel, sends the opening frames, decodes every
 * frame with AapCodec and feeds the result into AirPodsStateStore through SessionReducer. The state
 * becomes "connected" only when the first frame of a kind we act on has been decoded, so a peer that
 * merely accepts the channel shows nothing. A single thread reads; writes are serialised. Logs
 * carry reasons only, never an address or a frame (production builds log nothing about the peer).
 */
internal class AirPodsSession(
    private val device: BluetoothDevice,
    private val address: String,
    private val opener: AirPodsSocketOpener,
    private val seed: SessionSeed,
    private val listener: Listener,
) {
    /** NO_FRAME: the channel opened but no frame we act on arrived in time (AIR-193); the host retries like a failed connect. */
    enum class Reason { CLOSED_BY_US, UNSUPPORTED, CONNECT_FAILED, STREAM_ENDED, PEER_MISBEHAVED, NO_FRAME }

    /** Called on the session thread. */
    interface Listener {
        fun onPublished(session: AirPodsSession)
        fun onEars(session: AirPodsSession, inEar: Int)
        fun onConversation(session: AirPodsSession, level: Int)
        fun onEnded(session: AirPodsSession, reason: Reason)
    }

    @Volatile private var closed = false
    @Volatile private var batterySeen = false
    @Volatile private var modeSeen = false

    /** True once the first frame we act on was decoded and the state published. */
    @Volatile private var published = false

    /** Set by the watchdog when it closes a channel that stayed silent. */
    @Volatile private var silent = false
    @Volatile private var socket: BluetoothSocket? = null
    private var startedAt = 0L
    private var output: OutputStream? = null
    private val writeLock = Any()

    fun start() {
        Thread({ run() }, "AirPodsSession").start()
    }

    fun close() {
        closed = true
        try {
            socket?.close()
        } catch (e: IOException) {
            // already closed
        }
    }

    /** False when there is no open channel or the write failed. Never blocks on the peer. */
    fun send(frame: ByteArray): Boolean = synchronized(writeLock) {
        val out = output ?: return false
        try {
            out.write(frame)
            out.flush()
            true
        } catch (e: IOException) {
            false
        }
    }

    /**
     * The phone's own name for the pair. The pair's rename does not change the phone's cached name
     * (AIR-162), so the page sets the alias as well, through the narrow platform call of ADR-135
     * (`setAlias` needs a privilege the app does not hold, AIR-170). False when the platform refuses it.
     */
    fun setPhoneName(name: String): Boolean =
        try {
            device.setAirPodsAlias(name)
        } catch (e: SecurityException) {
            Log.w(TAG, "the phone's alias was refused: " + e.javaClass.simpleName)
            false
        }

    private fun run() {
        var reason = Reason.STREAM_ENDED
        startedAt = System.nanoTime()
        Log.i(TAG, "session started")
        try {
            val s = try {
                opener.open(device, AAP_PSM)
            } catch (e: ReflectiveOperationException) {
                Log.w(TAG, "L2CAP socket creation is not available on this image")
                reason = Reason.UNSUPPORTED
                return
            }
            socket = s
            if (closed) {
                reason = Reason.CLOSED_BY_US
                return
            }
            s.connect()
            output = s.outputStream
            sendOpeningFrames()
            Thread({ requestAgainUntilComplete() }, "AirPodsSession-refresh").apply { isDaemon = true }.start()
            Thread({ closeIfSilent() }, "AirPodsSession-watchdog").apply { isDaemon = true }.start()
            reason = readLoop(s.inputStream)
        } catch (e: IOException) {
            reason = if (closed) Reason.CLOSED_BY_US else Reason.CONNECT_FAILED
        } catch (e: RuntimeException) {
            reason = if (closed) Reason.CLOSED_BY_US else Reason.CONNECT_FAILED
        } finally {
            synchronized(writeLock) { output = null }
            try {
                socket?.close()
            } catch (e: IOException) {
                // already closed
            }
            if (silent && !closed && reason != Reason.UNSUPPORTED) reason = Reason.NO_FRAME
            if (closed && reason != Reason.UNSUPPORTED) reason = Reason.CLOSED_BY_US
            Log.i(TAG, "session closed: " + reason + " after " + elapsedMs() + " ms")
            listener.onEnded(this, reason)
        }
    }

    private fun elapsedMs(): Long = (System.nanoTime() - startedAt) / 1_000_000L

    /**
     * A channel that opens and then stays silent never ends on its own: the read blocks and the host ignores the next
     * connection while a session exists (AIR-193, a session of 5 minutes without a frame). If no frame we act on arrived
     * in [FIRST_FRAME_TIMEOUT_MS], close the socket; the read then fails and [run] reports [Reason.NO_FRAME].
     */
    private fun closeIfSilent() {
        try {
            Thread.sleep(FIRST_FRAME_TIMEOUT_MS)
        } catch (e: InterruptedException) {
            return
        }
        if (closed || published) return
        Log.w(TAG, "no frame within " + FIRST_FRAME_TIMEOUT_MS + " ms, closing")
        silent = true
        try {
            socket?.close()
        } catch (e: IOException) {
            // already closed
        }
    }

    /**
     * The order of the opening frames is one decision, taken from the capture on a real pair, and so is
     * the gap: the capture sent them 200 ms apart. Sent back to back, the AirPods answered with the
     * settings but never sent the battery (found on the device on 2026-10-02: no rings on the page).
     */
    private fun sendOpeningFrames() {
        send(AapCodec.handshake())
        pause()
        send(AapCodec.hostCapabilities())
        pause()
        send(AapCodec.requestNotifications())
    }

    /**
     * Right after the link comes up the AirPods may send only part of what they know (seen on the
     * device on 2026-10-02: once the settings without the battery, once the battery and the settings
     * three seconds later); a second notification request returns the battery (AIR-123, order hcnn).
     * Asked again every 3 s, at most five times, until a battery and a listening mode frame came.
     */
    private fun requestAgainUntilComplete() {
        repeat(REREQUESTS) {
            try {
                Thread.sleep(REREQUEST_GAP_MS)
            } catch (e: InterruptedException) {
                return
            }
            if (closed || (batterySeen && modeSeen)) return
            send(AapCodec.requestNotifications())
        }
    }

    private fun pause() {
        try {
            Thread.sleep(OPENING_GAP_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun readLoop(input: InputStream): Reason {
        val buffer = ByteArray(READ_BUFFER)
        var malformedInARow = 0
        while (!closed) {
            val n = try {
                input.read(buffer)
            } catch (e: IOException) {
                return if (closed) Reason.CLOSED_BY_US else Reason.STREAM_ENDED
            }
            if (n < 0) return Reason.STREAM_ENDED
            if (n == 0) continue
            val message = try {
                AapCodec.decode(buffer.copyOf(n))
            } catch (e: AirPodsValidationException) {
                if (isDebuggableBuild()) Log.d(TAG, "rejected: " + e.message)
                if (++malformedInARow > MAX_MALFORMED_IN_A_ROW) {
                    Log.w(TAG, "peer sends malformed frames, closing")
                    return Reason.PEER_MISBEHAVED
                }
                continue
            }
            malformedInARow = 0
            if (isDebuggableBuild()) Log.d(TAG, "frame " + message.javaClass.simpleName + (if (message is ListeningModeState) " " + message.mode() else ""))
            if (message is UnrecognisedMessage) continue
            if (message is BatteryStatus) batterySeen = true
            if (message is ListeningModeState) modeSeen = true
            if (!published) {
                published = true
                Log.i(TAG, "first frame after " + elapsedMs() + " ms")
                AirPodsStateStore.update(SessionReducer.connected(address, seed))
                listener.onPublished(this)
            }
            if (message is EarState) {
                val inEar = listOf(message.primary(), message.secondary()).count { it == EarState.Placement.IN_EAR }
                listener.onEars(this, inEar)
            }
            if (message is ConversationAwarenessLevel) listener.onConversation(this, message.level())
            AirPodsStateStore.transform { SessionReducer.apply(it, message) }
        }
        return Reason.CLOSED_BY_US
    }

    companion object {
        private const val TAG = "AirPodsSession"

        /** The accessory protocol's fixed L2CAP PSM (0x1001). */
        const val AAP_PSM = 0x1001

        /** The longest frame we act on is 22 bytes; the device information frame is about 250. */
        private const val READ_BUFFER = 2048
        private const val MAX_MALFORMED_IN_A_ROW = 8

        /** Gap between the opening frames, as in the capture (AIR-123). */
        private const val OPENING_GAP_MS = 200L
        private const val REREQUESTS = 5
        private const val REREQUEST_GAP_MS = 3_000L

        /** A healthy pair sent its first frame within 0.5 s (AIR-193); the rest of the burst follows within seconds. */
        private const val FIRST_FRAME_TIMEOUT_MS = 8_000L
    }
}
