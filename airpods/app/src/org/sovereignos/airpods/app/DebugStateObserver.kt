package org.sovereignos.airpods.app

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.DuckLevels
import org.sovereignos.airpods.ui.DebugStateParser
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode

/**
 * Debuggable builds only (AirPodsRuntime checks Build.TYPE != "user"): reads the state from the
 * global setting `airpods_debug_state`, set with `adb shell settings put global ...`. There is no
 * component or IPC endpoint for it, so the production app has no debug entry. The format is in
 * DebugStateParser; invalid input is ignored and never logged (it may contain an address).
 */
internal class DebugStateObserver(private val context: Context) :
    ContentObserver(Handler(Looper.getMainLooper())) {

    fun start() {
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(KEY), false, this)
        AirPodsCommands.sink = DebugCommandSink
        apply()
    }

    override fun onChange(selfChange: Boolean) {
        apply()
    }

    private fun apply() {
        // A live session owns the state; the debug input is for a device without one.
        if (AirPodsSessionHost.live) return
        val raw = Settings.Global.getString(context.contentResolver, KEY)
        val next = try {
            DebugStateParser.parse(raw)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "ignored an invalid debug state")
            return
        }
        AirPodsStateStore.update(next)
    }

    /** Echoes accepted commands into the store, as a real session would after the device answers. */
    private object DebugCommandSink : AirPodsCommandSink {
        override fun setListeningMode(mode: ListeningMode): Boolean {
            val state = AirPodsStateStore.state
            if (!state.isConnected) return false
            return try {
                AirPodsStateStore.update(state.toBuilder().listeningMode(mode).build())
                true
            } catch (e: IllegalArgumentException) {
                false
            }
        }

        override fun setFeature(feature: AirPodsUiState.Feature, enabled: Boolean): Boolean {
            val state = AirPodsStateStore.state
            if (!state.isConnected) return false
            val builder = state.toBuilder()
            when (feature) {
                AirPodsUiState.Feature.EAR_DETECTION -> builder.earDetection(enabled)
                AirPodsUiState.Feature.CONVERSATION_AWARENESS -> builder.conversationAwareness(enabled)
                else -> return false
            }
            AirPodsStateStore.update(builder.build())
            return true
        }

        override fun setDuckPercent(percent: Int): Boolean {
            val state = AirPodsStateStore.state
            if (!state.isConnected || !DuckLevels.isValid(percent)) return false
            AirPodsStateStore.update(state.toBuilder().duckPercent(percent).build())
            return true
        }

        override fun setAdaptiveStrength(percent: Int): Boolean {
            val state = AirPodsStateStore.state
            if (!state.isConnected || percent !in 0..100) return false
            val kept = org.sovereignos.airpods.ui.AdaptiveSteps.percentOf(org.sovereignos.airpods.ui.AdaptiveSteps.stepOf(percent))
            AirPodsStateStore.update(state.toBuilder().adaptivePercent(kept).build())
            return true
        }

        override fun setPressAndHold(modes: Set<ListeningMode>): Boolean {
            val state = AirPodsStateStore.state
            if (!state.isConnected) return false
            AirPodsStateStore.update(state.toBuilder().pressHoldModes(modes).build())
            return true
        }

        override fun rename(name: String): Boolean =
            AirPodsStateStore.state.isConnected && org.sovereignos.airpods.validation.AapCodec.isValidName(name)
    }

    companion object {
        const val KEY = "airpods_debug_state"
        private const val TAG = "AirPodsDebug"
    }
}
