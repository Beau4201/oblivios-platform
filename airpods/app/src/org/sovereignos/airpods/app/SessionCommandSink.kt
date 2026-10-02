package org.sovereignos.airpods.app

import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.ListeningModeToggles
import org.sovereignos.airpods.ui.SessionReducer
import org.sovereignos.airpods.validation.AapCodec
import org.sovereignos.airpods.validation.AirPodsMessage
import org.sovereignos.airpods.validation.ConversationAwarenessSetting
import org.sovereignos.airpods.validation.EarDetectionSetting
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode

/**
 * Sends the typed commands of Settings' page to the live session. A command is accepted only if the
 * state says the control exists. The listening mode changes the state only when the AirPods report
 * it (they answer every request, and refuse what they do not allow); the two comfort settings are
 * not reported after a write, so the written value is applied.
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
        val frame: ByteArray
        val written: AirPodsMessage
        when (feature) {
            AirPodsUiState.Feature.EAR_DETECTION -> {
                frame = AapCodec.setEarDetection(enabled)
                written = EarDetectionSetting(enabled)
            }
            AirPodsUiState.Feature.CONVERSATION_AWARENESS -> {
                frame = AapCodec.setConversationAwareness(enabled)
                written = ConversationAwarenessSetting(enabled)
            }
            else -> return false
        }
        if (!session.send(frame)) return false
        // These two settings are not reported back after a write (checked on a real pair, AIR-123;
        // the setting does change, a new connection reads the new value), so the written value is
        // shown now and the next connection reads the real one.
        AirPodsStateStore.transform { SessionReducer.apply(it, written) }
        return true
    }
}
