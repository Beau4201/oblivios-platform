package org.sovereignos.airpods.app

import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.validation.AirPodsControl
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode

/** Where clicks from Settings' device page and from our own page go. */
interface AirPodsCommandSink {
    /** True if the command was accepted and will change the state through the store. */
    fun setListeningMode(mode: ListeningMode): Boolean

    fun setFeature(feature: AirPodsUiState.Feature, enabled: Boolean): Boolean

    /** The owner chose how loud the music stays while they speak: 10 to 90 percent ([org.sovereignos.airpods.ui.DuckLevels]); applies to the next conversation. */
    fun setDuckPercent(percent: Int): Boolean

    /** The adaptive strength slider, 0 to 100 (the page sends 0, 50 or 100; the pair rounds to those). */
    fun setAdaptiveStrength(percent: Int): Boolean

    /**
     * The modes the stem press and hold cycles through. A selection of fewer than two modes is only
     * remembered by the state (the owner is still choosing); two or more are sent and stored.
     */
    fun setPressAndHold(modes: Set<ListeningMode>): Boolean

    /** Renames the AirPods and the phone's own name for them. The text is validated again here. */
    fun rename(name: String): Boolean

    /** Writes one control of the pair (Plan 9). Refused when the value is outside its range or the control is not offered. */
    fun setControl(control: AirPodsControl, value: Int): Boolean

    /** The call management setting: the two data bytes of one choice of [org.sovereignos.airpods.ui.CallChoices]. */
    fun setCallManagement(first: Int, second: Int): Boolean

    /** Starts "Play sound" through the AirPods; false when it may not start. Waits for the audio route: not on the main thread. */
    fun playSound(): Boolean

    fun stopSound()

    /** True when "Play sound" may start now: no pod is in an ear. */
    val soundAvailable: Boolean

    val soundPlaying: Boolean
}

object AirPodsCommands {
    @Volatile
    var sink: AirPodsCommandSink = Rejecting

    /** Without a session nothing can be sent to the AirPods: refuse, and the page shows the truth. */
    private object Rejecting : AirPodsCommandSink {
        override fun setListeningMode(mode: ListeningMode): Boolean = false

        override fun setFeature(feature: AirPodsUiState.Feature, enabled: Boolean): Boolean = false

        override fun setDuckPercent(percent: Int): Boolean = false

        override fun setAdaptiveStrength(percent: Int): Boolean = false

        override fun setPressAndHold(modes: Set<ListeningMode>): Boolean = false

        override fun rename(name: String): Boolean = false

        override fun setControl(control: AirPodsControl, value: Int): Boolean = false

        override fun setCallManagement(first: Int, second: Int): Boolean = false

        override fun playSound(): Boolean = false

        override fun stopSound() = Unit

        override val soundAvailable: Boolean = false

        override val soundPlaying: Boolean = false
    }
}
