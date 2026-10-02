package org.sovereignos.airpods.app

import android.content.Context
import android.media.AudioManager
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.ConversationAwarenessPolicy
import org.sovereignos.airpods.ui.ConversationAwarenessPolicy.Action

/**
 * Conversation awareness like on an iPhone (ADR-125): when the owner speaks the music volume goes
 * down, and when the AirPods say the conversation is over it comes back. The rules are in
 * [ConversationAwarenessPolicy]; this class only executes them. It touches STREAM_MUSIC only, lowers
 * it only while music is playing, and gives it back only if it is still the value this class set, so
 * a change the owner made meanwhile wins. `AudioManager` volume calls need no permission. One worker
 * thread, in order; one controller per session.
 */
internal class ConversationDuckController(
    context: Context,
    enabled: Boolean,
) : AirPodsStateStore.Listener {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val policy = ConversationAwarenessPolicy(enabled)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "AirPodsDuck").apply { isDaemon = true } }

    @Volatile private var ended = false

    // Worker thread only.
    private var original = NONE
    private var lowered = NONE

    init {
        AirPodsStateStore.addListener(this)
    }

    /** A conversation level (0 to 255) from the session thread. */
    fun onLevel(level: Int) {
        submit { execute(policy.onLevel(level)) }
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

    private fun lower() {
        if (original != NONE || !audio.isMusicActive) return
        val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val target = maxOf(min, (now * AirPodsStateStore.state.duckPercent + 50) / 100)
        if (target >= now) return
        original = now
        lowered = target
        if (isDebuggableBuild()) Log.d(TAG, "lower $now -> $target")
        fade(now, target)
    }

    private fun giveBack() {
        val back = original
        if (back == NONE) return
        original = NONE
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (isDebuggableBuild()) Log.d(TAG, "give back, now $current, was $back")
        if (current == lowered) fade(lowered, back)
    }

    /** One index at a time, so the change is a short fade and not a jump. */
    private fun fade(from: Int, to: Int) {
        val step = if (to > from) 1 else -1
        var volume = from
        while (volume != to) {
            volume += step
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            try {
                Thread.sleep(FADE_STEP_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, to, 0)
                return
            }
        }
    }

    private companion object {
        const val TAG = "AirPodsDuck"
        const val NONE = -1
        const val FADE_STEP_MS = 40L
    }
}
