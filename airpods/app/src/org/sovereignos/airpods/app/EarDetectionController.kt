package org.sovereignos.airpods.app

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.EarDetectionPolicy
import org.sovereignos.airpods.ui.EarDetectionPolicy.Action

/**
 * Acts on the AirPods' placement report like an iPhone with Automatic Ear Detection on (ADR-125):
 * the rules are in [EarDetectionPolicy]; this class only executes them. Pausing and resuming use
 * the media key, which any app may send; moving the audio to the phone and back uses the one narrow
 * platform call [BluetoothDevice.setAirPodsAudioRoute]. Everything runs on one worker thread, in
 * order. Reports are coalesced to the latest value and batches are at least [MIN_GAP_MS] apart, so a
 * peer that floods reports cannot make the phone flap. One controller per session; [end] restores
 * the route and stops the worker.
 */
internal class EarDetectionController(
    context: Context,
    private val device: BluetoothDevice,
    enabled: Boolean,
) : AirPodsStateStore.Listener {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val policy = EarDetectionPolicy(enabled)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "AirPodsEars").apply { isDaemon = true } }
    private val latest = AtomicInteger(NONE)

    @Volatile private var ended = false

    init {
        AirPodsStateStore.addListener(this)
    }

    /** The number of pods in the ears (0 to 2), from the session thread. */
    fun onEars(inEar: Int) {
        latest.set(inEar)
        submit { drain() }
    }

    /** The user turned the switch on the page on or off. */
    override fun onStateChanged(old: AirPodsUiState, new: AirPodsUiState) {
        if (new.isConnected && new.earDetection != old.earDetection) {
            submit { execute(policy.setEnabled(new.earDetection)) }
        }
    }

    /** The session is over: restore a route we removed, then stop. */
    fun end() {
        if (ended) return
        ended = true
        AirPodsStateStore.removeListener(this)
        worker.execute { execute(policy.end()) }
        worker.shutdown()
    }

    private fun submit(task: Runnable) {
        if (ended) return
        try {
            worker.execute(task)
        } catch (e: RejectedExecutionException) {
            // ended in the meantime
        }
    }

    private fun drain() {
        val count = latest.getAndSet(NONE)
        if (count == NONE) return
        execute(policy.onEars(count))
        try {
            Thread.sleep(MIN_GAP_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun execute(actions: List<Action>) {
        if (actions.isEmpty()) return
        if (isDebuggableBuild()) Log.d(TAG, "actions $actions")
        for (action in actions) {
            when (action) {
                Action.PAUSE_RESUMABLE -> if (pauseIfPlaying()) policy.notePaused()
                Action.PAUSE_FINAL -> {
                    pauseIfPlaying()
                    waitUntilSilent()
                }
                Action.RESUME -> if (!audio.isMusicActive) sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                Action.ROUTE_AWAY -> route(true)
                Action.ROUTE_BACK -> route(false)
            }
        }
    }

    private fun pauseIfPlaying(): Boolean {
        if (!audio.isMusicActive) return false
        sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        return true
    }

    /** The pause key is asynchronous; give it up to 0.5 s so no sound slips out of the speaker. */
    private fun waitUntilSilent() {
        repeat(SILENCE_POLLS) {
            if (!audio.isMusicActive) return
            try {
                Thread.sleep(SILENCE_POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    private fun sendKey(code: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun route(away: Boolean) {
        val ok = try {
            device.setAirPodsAudioRoute(away)
        } catch (e: SecurityException) {
            false
        }
        if (!ok) Log.w(TAG, "audio route change refused, away=$away")
    }

    private companion object {
        const val TAG = "AirPodsEars"
        const val NONE = -1
        const val MIN_GAP_MS = 100L
        const val SILENCE_POLLS = 20
        const val SILENCE_POLL_MS = 25L
    }
}
