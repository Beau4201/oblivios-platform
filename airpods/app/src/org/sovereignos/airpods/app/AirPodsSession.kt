package org.sovereignos.airpods.app

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.SessionReducer
import org.sovereignos.airpods.validation.AapCodec
import org.sovereignos.airpods.validation.AirPodsValidationException
import org.sovereignos.airpods.validation.BatteryStatus
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
    private val listener: Listener,
) {
    enum class Reason { CLOSED_BY_US, UNSUPPORTED, CONNECT_FAILED, STREAM_ENDED, PEER_MISBEHAVED }

    /** Called on the session thread. */
    interface Listener {
        fun onPublished(session: AirPodsSession)
        fun onEnded(session: AirPodsSession, reason: Reason)
    }

    @Volatile private var closed = false
    @Volatile private var batterySeen = false
    @Volatile private var socket: BluetoothSocket? = null
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

    private fun run() {
        var reason = Reason.STREAM_ENDED
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
            Thread({ requestAgainUntilBattery() }, "AirPodsSession-refresh").apply { isDaemon = true }.start()
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
            if (closed && reason != Reason.UNSUPPORTED) reason = Reason.CLOSED_BY_US
            listener.onEnded(this, reason)
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
     * Right after the link comes up the AirPods may not send the battery with the settings (seen on
     * the device on 2026-10-02: no battery for a minute); a second notification request returns it
     * (AIR-123, order hcnn). Asked again every 3 s, at most five times, until a battery frame came.
     */
    private fun requestAgainUntilBattery() {
        repeat(REREQUESTS) {
            try {
                Thread.sleep(REREQUEST_GAP_MS)
            } catch (e: InterruptedException) {
                return
            }
            if (closed || batterySeen) return
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
        var published = false
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
            if (isDebuggableBuild()) Log.d(TAG, "frame " + message.javaClass.simpleName)
            if (message is UnrecognisedMessage) continue
            if (message is BatteryStatus) batterySeen = true
            if (!published) {
                published = true
                AirPodsStateStore.update(SessionReducer.connected(address))
                listener.onPublished(this)
            }
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
    }
}
