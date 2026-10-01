package org.sovereignos.airpods.app

import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode

/** Where clicks from Settings' device page go. Plan 6D installs the real gateway. */
interface AirPodsCommandSink {
    /** True if the command was accepted and will change the state through the store. */
    fun setListeningMode(mode: ListeningMode): Boolean

    fun setFeature(feature: AirPodsUiState.Feature, enabled: Boolean): Boolean
}

object AirPodsCommands {
    @Volatile
    var sink: AirPodsCommandSink = Rejecting

    /** Without a session nothing can be sent to the AirPods: refuse, and the page shows the truth. */
    private object Rejecting : AirPodsCommandSink {
        override fun setListeningMode(mode: ListeningMode): Boolean = false

        override fun setFeature(feature: AirPodsUiState.Feature, enabled: Boolean): Boolean = false
    }
}
