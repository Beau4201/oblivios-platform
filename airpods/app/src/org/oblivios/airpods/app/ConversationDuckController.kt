// SPDX-License-Identifier: Apache-2.0
package org.oblivios.airpods.app

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.AudioManager
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import org.oblivios.airpods.ui.AirPodsUiState
import org.oblivios.airpods.ui.ConversationAwarenessPolicy
import org.oblivios.airpods.ui.ConversationAwarenessPolicy.Action

/**
 * Conversation awareness like on an iPhone (ADR-125): when the owner speaks the music volume goes
 * down, and when the AirPods say the conversation is over it comes back. The rules are in
 * [ConversationAwarenessPolicy]; this class only executes them. The audio hardening of this OS
 * ignores an app's own `AudioManager` volume calls (AIR-153), so the volume change is made by the
 * one narrow platform call [BluetoothDevice.setAirPodsMusicDuck] (ADR-127), which lowers the music
 * to the share the owner chose, only while these AirPods are the active output, and gives it back
 * only if the volume is still the value it set. This class lowers only while music is playing.
 * One worker thread, in order; one controller per session.
 */
internal class ConversationDuckController(
    context: Context,
    private val device: BluetoothDevice,
    enabled: Boolean,
) : AirPodsStateStore.Listener {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val policy = ConversationAwarenessPolicy(enabled)
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "AirPodsDuck").apply { isDaemon = true } }

    @Volatile private var ended = false

    // Worker thread only.
    private var ducked = false
    private var silence: ScheduledFuture<*>? = null

    init {
        AirPodsStateStore.addListener(this)
    }

    /** A conversation level (0 to 255) from the session thread. */
    fun onLevel(level: Int) {
        submit {
            execute(policy.onLevel(level))
            rearmSilence(level)
        }
    }

    /** Both pods are out of the ears. */
    fun onPodsOut() {
        submit { execute(policy.onPodsOut()) }
    }

    /** The owner turned the conversation awareness switch on the page on or off. */
    override fun onStateChanged(old: AirPodsUiState, new: AirPodsUiState) {
        if (new.isConnected && new.conversationAwareness != old.conversationAwareness) {
            submit { execute(policy.setEnabled(new.conversationAwareness)) }
        }
    }

    /** The session is over: give the volume back, then stop. */
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

    private fun execute(actions: List<Action>) {
        for (action in actions) {
            when (action) {
                Action.DUCK -> lower()
                Action.RESTORE -> giveBack()
            }
        }
    }

    /**
     * A very short utterance makes the pods send no end level (capture 2026-10-04): while the volume
     * is lowered, a level that does not follow ends the conversation: after 1 s when the last level was
     * 0B (04 always follows it within 0.22 s), else after 8 s (the longest gap seen inside a
     * conversation is 5.4 s, so a restore never cuts into one that is going on).
     */
    private fun rearmSilence(level: Int) {
        silence?.cancel(false)
        silence = if (ducked) {
            try {
                worker.schedule(
                    { execute(policy.onSilence()) },
                    ConversationAwarenessPolicy.silenceMillis(level),
                    TimeUnit.MILLISECONDS,
                )
            } catch (e: RejectedExecutionException) {
                null
            }
        } else {
            null
        }
    }

    private fun lower() {
        if (ducked || !audio.isMusicActive) return
        val percent = AirPodsStateStore.state.duckPercent
        ducked = call(true, percent)
        if (isDebuggableBuild()) Log.d(TAG, "lower to $percent percent: $ducked")
    }

    private fun giveBack() {
        if (!ducked) return
        ducked = false
        val ok = call(false, 0)
        if (isDebuggableBuild()) Log.d(TAG, "give back: $ok")
    }

    private fun call(duck: Boolean, percent: Int): Boolean =
        try {
            device.setAirPodsMusicDuck(duck, percent)
        } catch (e: SecurityException) {
            false
        }

    private companion object {
        const val TAG = "AirPodsDuck"

    }
}
