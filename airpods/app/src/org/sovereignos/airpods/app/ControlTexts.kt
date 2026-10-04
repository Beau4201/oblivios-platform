package org.sovereignos.airpods.app

import androidx.annotation.StringRes
import org.sovereignos.airpods.R
import org.sovereignos.airpods.validation.AirPodsControl

/** The words of the control rows of [org.sovereignos.airpods.ui.ControlRows]: titles, summaries and the label of each choice. */
internal object ControlTexts {
    @StringRes
    fun title(control: AirPodsControl): Int = when (control) {
        AirPodsControl.MICROPHONE -> R.string.airpods_mic_title
        AirPodsControl.PRESS_SPEED -> R.string.airpods_press_speed_title
        AirPodsControl.PRESS_HOLD_DURATION -> R.string.airpods_hold_duration_title
        AirPodsControl.CHIME_VOLUME -> R.string.airpods_chime_title
        AirPodsControl.CONNECT_AUTOMATICALLY -> R.string.airpods_auto_connect_title
        AirPodsControl.SWIPE_INTERVAL -> R.string.airpods_swipe_interval_title
        AirPodsControl.SWIPE_MODE -> R.string.airpods_swipe_title
        AirPodsControl.PERSONALIZED_VOLUME -> R.string.airpods_personalized_title
        AirPodsControl.GAIN_SWIPE -> R.string.airpods_gain_swipe_title
        AirPodsControl.CASE_TONE -> R.string.airpods_case_tone_title
        AirPodsControl.SLEEP_DETECTION -> R.string.airpods_sleep_title
        AirPodsControl.CASE_TONE_VOLUME -> R.string.airpods_case_tone_volume_title
    }

    @StringRes
    fun summary(control: AirPodsControl): Int = when (control) {
        AirPodsControl.MICROPHONE -> R.string.airpods_mic_summary
        AirPodsControl.PRESS_SPEED -> R.string.airpods_press_speed_summary
        AirPodsControl.PRESS_HOLD_DURATION -> R.string.airpods_hold_duration_summary
        AirPodsControl.CHIME_VOLUME -> R.string.airpods_chime_summary
        AirPodsControl.CONNECT_AUTOMATICALLY -> R.string.airpods_auto_connect_summary
        AirPodsControl.SWIPE_INTERVAL -> R.string.airpods_swipe_interval_summary
        AirPodsControl.SWIPE_MODE -> R.string.airpods_swipe_summary
        AirPodsControl.PERSONALIZED_VOLUME -> R.string.airpods_personalized_summary
        AirPodsControl.GAIN_SWIPE -> R.string.airpods_gain_swipe_summary
        AirPodsControl.CASE_TONE -> R.string.airpods_case_tone_summary
        AirPodsControl.SLEEP_DETECTION -> R.string.airpods_sleep_summary
        AirPodsControl.CASE_TONE_VOLUME -> R.string.airpods_case_tone_volume_summary
    }

    /** The label of each value of a choice row, in the order of [org.sovereignos.airpods.ui.ControlRows.Row.choices]. */
    @StringRes
    fun choiceLabels(control: AirPodsControl): IntArray = when (control) {
        AirPodsControl.MICROPHONE -> intArrayOf(R.string.airpods_choice_automatic, R.string.airpods_choice_right, R.string.airpods_choice_left)
        AirPodsControl.PRESS_SPEED,
        AirPodsControl.PRESS_HOLD_DURATION -> intArrayOf(R.string.airpods_choice_default, R.string.airpods_choice_slower, R.string.airpods_choice_slowest)
        AirPodsControl.SWIPE_INTERVAL -> intArrayOf(R.string.airpods_choice_default, R.string.airpods_choice_longer, R.string.airpods_choice_longest)
        else -> intArrayOf()
    }
}
