package org.sovereignos.airpods.app

import android.content.Context
import android.util.Log
import org.sovereignos.airpods.ui.AdaptiveSteps
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.ControlRows
import org.sovereignos.airpods.ui.DuckLevels
import org.sovereignos.airpods.ui.ListeningModeToggles
import org.sovereignos.airpods.ui.PressAndHoldChoices
import org.sovereignos.airpods.ui.SessionReducer
import org.sovereignos.airpods.validation.AapCodec
import org.sovereignos.airpods.validation.AirPodsControl
import org.sovereignos.airpods.validation.AirPodsMessage
import org.sovereignos.airpods.validation.CallManagementReport
import org.sovereignos.airpods.validation.ControlReport
import org.sovereignos.airpods.validation.ConversationAwarenessSetting
import org.sovereignos.airpods.validation.EarDetectionSetting
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode

/**
 * Sends the typed commands of Settings' page and of our own page to the live session. A command is
 * accepted only if the state says the control exists. The listening mode changes the state only when
 * the AirPods report it (they answer every request, and refuse what they do not allow); the comfort
 * settings, press and hold and the adaptive strength are not reported after a write, so the written
 * value is applied (the next session reads the real adaptive strength, AIR-164).
 */
internal class SessionCommandSink(
    private val session: AirPodsSession,
    private val context: Context?,
    private val find: FindSoundController?,
) : AirPodsCommandSink {
    override fun setListeningMode(mode: ListeningMode): Boolean {
        val state = AirPodsStateStore.state
        if (isDebuggableBuild()) Log.d(TAG, "setListeningMode " + mode + " connected=" + state.isConnected + " now=" + state.listeningMode)
        if (!state.isConnected || mode !in ListeningModeToggles.modes(state)) return false
        if (mode == ListeningMode.OFF) {
            // A pair with the off option disabled refuses off and answers with another mode (AIR-123);
            // asking for off is the owner's intent, so allow it first. Idempotent when already allowed.
            if (!session.send(AapCodec.setAllowOffOption(true))) return false
            try {
                Thread.sleep(ALLOW_OFF_GAP_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
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
        val address = state.address
        if (feature == AirPodsUiState.Feature.EAR_DETECTION && context != null && address != null) {
            EarDetectionStore.write(context, address, enabled)
        }
        return true
    }

    override fun setDuckPercent(percent: Int): Boolean {
        val state = AirPodsStateStore.state
        if (!state.isConnected || AirPodsUiState.Feature.CONVERSATION_AWARENESS !in state.supported ||
            !DuckLevels.isValid(percent)
        ) {
            return false
        }
        if (context != null) DuckLevelStore.write(context, percent)
        AirPodsStateStore.transform { it.toBuilder().duckPercent(percent).build() }
        return true
    }

    override fun setAdaptiveStrength(percent: Int): Boolean {
        val state = AirPodsStateStore.state
        if (!state.isConnected || AirPodsUiState.Feature.ADAPTIVE_MODE !in state.supported || percent !in 0..100) {
            return false
        }
        if (!session.send(AapCodec.setAdaptiveLevel(percent))) return false
        // The pair rounds to 0, 50 or 100 and reports it only in the next session's burst (AIR-164):
        // show the position the pair will keep.
        val kept = AdaptiveSteps.percentOf(AdaptiveSteps.stepOf(percent))
        AirPodsStateStore.transform { it.toBuilder().adaptivePercent(kept).build() }
        return true
    }

    override fun setPressAndHold(modes: Set<ListeningMode>): Boolean {
        val state = AirPodsStateStore.state
        if (!state.isConnected || AirPodsUiState.Feature.PRESS_AND_HOLD !in state.supported) return false
        if (!PressAndHoldChoices.offered(state).containsAll(modes)) return false
        if (PressAndHoldChoices.complete(modes)) {
            if (!session.send(AapCodec.setPressAndHoldModes(modes))) return false
            val address = state.address
            if (context != null && address != null) {
                PressAndHoldStore.write(context, address, PressAndHoldChoices.mask(modes))
            }
        } else if (PressAndHoldChoices.complete(state.pressHoldModes)) {
            // Going from a complete selection to fewer than two is refused (the page checks first).
            return false
        }
        AirPodsStateStore.transform { it.toBuilder().pressHoldModes(modes).build() }
        return true
    }

    override fun rename(name: String): Boolean {
        val state = AirPodsStateStore.state
        if (!state.isConnected || AirPodsUiState.Feature.RENAME !in state.supported || !AapCodec.isValidName(name)) {
            return false
        }
        if (!session.send(AapCodec.rename(name))) return false
        // The pair does not change the phone's cached name (AIR-162): set the phone's alias as well.
        session.setPhoneName(name)
        return true
    }

    override fun setControl(control: AirPodsControl, value: Int): Boolean {
        val state = AirPodsStateStore.state
        if (!state.isConnected || !ControlRows.allowed(control, value) || !ControlRows.offered(state, control)) return false
        if (isDebuggableBuild()) Log.d(TAG, "setControl " + control + " " + value)
        if (!session.send(AapCodec.setControl(control, value))) return false
        // The pair reports most of these only in the burst of the next session and some never (AIR-179):
        // show the written value now and keep it for the next session.
        val address = state.address
        if (context != null && address != null) ControlStore.write(context, address, control, value)
        AirPodsStateStore.transform { SessionReducer.apply(it, ControlReport(control, value)) }
        return true
    }

    override fun setCallManagement(first: Int, second: Int): Boolean {
        val state = AirPodsStateStore.state
        // Offered only once the pair has reported the setting, like the controls it reports.
        if (!state.isConnected || state.callFirst < 0 || first !in 0..MAX_BYTE || second !in 0..MAX_BYTE) return false
        if (!session.send(AapCodec.setCallManagement(first, second))) return false
        AirPodsStateStore.transform { SessionReducer.apply(it, CallManagementReport(first, second)) }
        return true
    }

    override fun playSound(): Boolean = find?.start() ?: false

    override fun stopSound() {
        find?.stop()
    }

    override val soundAvailable: Boolean
        get() = find?.available() ?: false

    override val soundPlaying: Boolean
        get() = find?.playing ?: false

    private companion object {
        const val TAG = "AirPodsCommands"
        const val MAX_BYTE = 255

        /** Pause between "allow off" and "off", as between the opening frames (AIR-123). */
        const val ALLOW_OFF_GAP_MS = 200L
    }
}
