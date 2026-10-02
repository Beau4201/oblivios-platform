package org.sovereignos.airpods.app

import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.ListeningModeToggles
import org.sovereignos.airpods.validation.AapCodec
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode

/**
 * Sends the typed commands of Settings' page to the live session. A command is accepted only if the
 * state says the control exists; the state itself changes only when the AirPods report the new
 * value, so the page shows what the device did and never what we hoped it did.
 */
internal class SessionCommandSink(private val session: AirPodsSession) : AirPodsCommandSink {
    override fun setListeningMode(mode: ListeningMode): Boolean {
        val state = AirPodsStateStore.state
        if (!state.isConnected || mode !in ListeningModeToggles.modes(state)) return false
        return session.send(AapCodec.setListeningMode(mode))
    }

    override fun setFeature(feature: AirPodsUiState.Feature, enabled: Boolean): Boolean {
        val state = AirPodsStateStore.state
        if (!state.isConnected || feature !in state.supported) return false
        return when (feature) {
            AirPodsUiState.Feature.EAR_DETECTION -> session.send(AapCodec.setEarDetection(enabled))
            AirPodsUiState.Feature.CONVERSATION_AWARENESS -> session.send(AapCodec.setConversationAwareness(enabled))
            else -> false
        }
    }
}
