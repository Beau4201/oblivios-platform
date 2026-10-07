package org.sovereignos.airpods.app

import java.util.concurrent.CopyOnWriteArrayList
import org.sovereignos.airpods.ui.AirPodsUiState

/** The one place the AirPods UI state lives. Plan 6D feeds it from the real session. */
object AirPodsStateStore {
    fun interface Listener {
        fun onStateChanged(old: AirPodsUiState, new: AirPodsUiState)
    }

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile
    var state: AirPodsUiState = AirPodsUiState.DISCONNECTED
        private set

    /**
     * Atomic read-modify-write: the session's reader thread and the command sink both change the
     * state, so a change that depends on the current state must be made here, not from a copy read
     * outside. Returns false when nothing changed. Listeners run on the caller's thread.
     */
    fun transform(change: (AirPodsUiState) -> AirPodsUiState): Boolean {
        val (old, next) = synchronized(lock) {
            val previous = state
            val updated = change(previous)
            if (updated == previous) return false
            state = updated
            previous to updated
        }
        // One failing listener must not keep the others from seeing the change; the first failure still propagates.
        var failure: RuntimeException? = null
        for (listener in listeners) {
            try {
                listener.onStateChanged(old, next)
            } catch (e: RuntimeException) {
                if (failure == null) failure = e
            }
        }
        failure?.let { throw it }
        return true
    }

    /** Returns false when nothing changed. Listeners run on the caller's thread. */
    fun update(next: AirPodsUiState): Boolean = transform { next }

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}
