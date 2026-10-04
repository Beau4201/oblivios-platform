package org.sovereignos.airpods.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import org.sovereignos.airpods.ui.FindSoundPolicy

/**
 * "Play sound" (ADR-134): a rising tone through the connected AirPods, so that a pod that lies somewhere
 * can be heard. The rules are in [FindSoundPolicy]; this class only plays. It starts only while the pair
 * reports that no pod is in an ear, puts the audio route back to the AirPods for the tone through
 * [EarDetectionController.beginFind] and restores it with [EarDetectionController.endFind], rises in
 * steps to a fixed cap, and ends by itself after a minute, when a pod goes into an ear, or when the link
 * is gone. It is not Find My: it needs the connection to this phone. One controller per session.
 */
internal class FindSoundController(
    @Suppress("unused") private val context: Context,
    private val ears: EarDetectionController?,
) {
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()

    @Volatile private var inEar = NO_REPORT
    @Volatile private var starting = false
    private var track: AudioTrack? = null
    private var startedAt = 0L

    @Volatile
    var playing = false
        private set

    /** The number of pods in the ears (0 to 2), from the session. A pod in an ear ends a running sound at once. */
    fun onEars(count: Int) {
        inEar = count
        if (playing && count != 0) stop()
    }

    /** True when a sound may start now (the page shows it). */
    fun available(): Boolean = FindSoundPolicy.allowed(AirPodsStateStore.state.isConnected, inEar)

    /** Starts the sound; false when it may not. Waits for the route, so call it off the main thread. */
    fun start(): Boolean {
        synchronized(lock) {
            if (playing || starting || !available()) return false
            starting = true
        }
        var routed = false
        try {
            if (ears != null) {
                routed = true
                if (!ears.beginFind()) return false
            }
            SystemClock.sleep(ROUTE_SETTLE_MS)
            // A pod may have gone into an ear while the route was changing.
            if (!available()) return false
            val t = buildTrack()
            synchronized(lock) {
                track = t
                startedAt = SystemClock.elapsedRealtime()
                playing = true
            }
            t.setVolume(FindSoundPolicy.gainAt(0))
            t.play()
            main.postDelayed(tick, TICK_MS)
            routed = false
            return true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "the sound could not start: " + e.javaClass.simpleName)
            return false
        } finally {
            if (routed) ears?.endFind()
            synchronized(lock) { starting = false }
        }
    }

    fun stop() {
        val t: AudioTrack?
        synchronized(lock) {
            if (!playing) return
            playing = false
            t = track
            track = null
        }
        main.removeCallbacks(tick)
        try {
            t?.stop()
        } catch (e: IllegalStateException) {
            // already stopped
        }
        t?.release()
        ears?.endFind()
    }

    /** The session is over. */
    fun end() = stop()

    private val tick = object : Runnable {
        override fun run() {
            val t: AudioTrack?
            val elapsed: Long
            synchronized(lock) {
                if (!playing) return
                t = track
                elapsed = SystemClock.elapsedRealtime() - startedAt
            }
            if (FindSoundPolicy.mustStop(AirPodsStateStore.state.isConnected, inEar, elapsed)) {
                stop()
                return
            }
            t?.setVolume(FindSoundPolicy.gainAt(elapsed))
            main.postDelayed(this, TICK_MS)
        }
    }

    private fun buildTrack(): AudioTrack {
        val pcm = tone()
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setLoopPoints(0, pcm.size, -1)
        return t
    }

    /** One second of two short beeps (2093 Hz at 0 ms, 2637 Hz at 250 ms) and silence; looped. */
    private fun tone(): ShortArray {
        val out = ShortArray(RATE)
        fun beep(startMs: Int, hz: Double) {
            val from = RATE * startMs / 1000
            val len = RATE * BEEP_MS / 1000
            val fade = RATE * FADE_MS / 1000.0
            for (i in 0 until len) {
                val env = min(1.0, min(i, len - 1 - i) / fade)
                out[from + i] = (sin(2 * PI * hz * i / RATE) * env * Short.MAX_VALUE).toInt().toShort()
            }
        }
        beep(0, 2093.0)
        beep(250, 2637.0)
        return out
    }

    private companion object {
        const val TAG = "AirPodsFind"
        const val NO_REPORT = -1
        const val RATE = 44_100
        const val BEEP_MS = 180
        const val FADE_MS = 10
        const val TICK_MS = 250L

        /** The A2DP link needs a moment after the route is put back before the first beep is heard. */
        const val ROUTE_SETTLE_MS = 800L
    }
}
