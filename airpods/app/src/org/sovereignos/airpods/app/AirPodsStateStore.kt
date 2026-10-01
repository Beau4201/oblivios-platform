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

    /** Returns false when nothing changed. Listeners run on the caller's thread. */
    fun update(next: AirPodsUiState): Boolean {
        val old = synchronized(lock) {
            if (next == state) return false
            val previous = state
            state = next
            previous
        }
        for (listener in listeners) listener.onStateChanged(old, next)
        return true
    }

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}
