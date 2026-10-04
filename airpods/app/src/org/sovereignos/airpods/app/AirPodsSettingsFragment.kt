package org.sovereignos.airpods.app

import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.icu.text.ListFormatter
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.FooterPreference
import com.android.settingslib.widget.SegmentedButtonPreference
import com.android.settingslib.widget.SliderPreference
import com.android.settingslib.widget.theme.R as ThemeR
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import org.sovereignos.airpods.R
import org.sovereignos.airpods.ui.AdaptiveSteps
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.AirPodsUiState.Feature
import org.sovereignos.airpods.ui.DuckLevels
import org.sovereignos.airpods.ui.ListeningModeToggles
import org.sovereignos.airpods.ui.PressAndHoldChoices
import org.sovereignos.airpods.validation.AapCodec
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode

/**
 * The groups of our own page: Listening mode (four buttons, a description, the Adaptive strength
 * slider), Comfort (the two switches), Volume while speaking (a slider, only while conversation awareness
 * is on), Sound and controls (Press and hold) and About (Name, Model number, Firmware). It shows
 * [AirPodsStateStore] and sends commands through [AirPodsCommands.sink], on a worker thread because a
 * command can wait on the session; every command ends by showing the real state again, so a refused
 * command puts the control back. The state is the truth: switches return false from their change
 * listener and are set by [render].
 */
class AirPodsSettingsFragment : PreferenceFragmentCompat() {
    private lateinit var rings: RingsPreference
    private lateinit var notConnected: Preference
    private lateinit var listeningGroup: PreferenceCategory
    private lateinit var listening: SegmentedButtonPreference
    private lateinit var description: Preference
    private lateinit var adaptive: SliderPreference
    private lateinit var comfortGroup: PreferenceCategory
    private lateinit var ear: SwitchPreferenceCompat
    private lateinit var conversation: SwitchPreferenceCompat
    private lateinit var duckGroup: PreferenceCategory
    private lateinit var duck: SliderPreference
    private lateinit var soundGroup: PreferenceCategory
    private lateinit var pressHold: Preference
    private lateinit var pressHoldFooter: FooterPreference
    private lateinit var aboutGroup: PreferenceCategory
    private lateinit var name: Preference
    private lateinit var model: Preference
    private lateinit var firmware: Preference

    private var rendering = false
    private val io: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "AirPodsPage").apply { isDaemon = true } }
    private val storeListener = AirPodsStateStore.Listener { _, new -> activity?.runOnUiThread { render(new) } }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        val screen = preferenceManager.createPreferenceScreen(ctx)

        rings = RingsPreference(ctx)
        screen.addPreference(rings)

        notConnected = Preference(ctx).apply {
            setTitle(R.string.airpods_not_connected)
            isSelectable = false
        }
        screen.addPreference(notConnected)

        listeningGroup = category(screen, "listening", R.string.airpods_group_listening)
        listening = SegmentedButtonPreference(ctx).apply {
            key = "listening_mode"
            setLabelInsideButton(true)
            setOnButtonClickListener { group, checkedId, isChecked ->
                if (isChecked && !rendering) {
                    val modes = ListeningModeToggles.modes(AirPodsStateStore.state)
                    val index = group.indexOfChild(group.findViewById(checkedId))
                    ListeningModeToggles.modeAt(modes, index)?.let { mode -> command { AirPodsCommands.sink.setListeningMode(mode) } }
                }
            }
        }
        listeningGroup.addPreference(listening)
        description = Preference(ctx).apply {
            key = "listening_description"
            isSelectable = false
        }
        listeningGroup.addPreference(description)
        adaptive = SliderPreference(ctx).apply {
            key = "adaptive_strength"
            isPersistent = false // the state store is the only source of truth, not SharedPreferences
            setTitle(R.string.airpods_adaptive_title)
            setSummary(R.string.airpods_adaptive_summary)
            min = 0
            max = AdaptiveSteps.STEPS - 1
            sliderIncrement = 1
            setTickVisible(true)
            setTextStart(R.string.airpods_mode_transparency)
            setTextEnd(R.string.airpods_mode_noise_cancel)
            setOnPreferenceChangeListener { _, value ->
                if (!rendering) command { AirPodsCommands.sink.setAdaptiveStrength(AdaptiveSteps.percentOf(value as Int)) }
                true
            }
        }
        listeningGroup.addPreference(adaptive)

        comfortGroup = category(screen, "comfort", R.string.airpods_group_comfort)
        ear = switchRow("ear_detection", R.string.airpods_ear_detection_title, R.string.airpods_ear_detection_summary,
            R.drawable.ic_airpods_ear, Feature.EAR_DETECTION)
        comfortGroup.addPreference(ear)
        conversation = switchRow("conversation_awareness", R.string.airpods_conversation_title,
            R.string.airpods_conversation_summary, R.drawable.ic_airpods_voice, Feature.CONVERSATION_AWARENESS)
        comfortGroup.addPreference(conversation)

        duckGroup = category(screen, "duck", R.string.airpods_duck_title)
        duck = SliderPreference(ctx).apply {
            key = "duck_percent"
            isPersistent = false
            setTitle(R.string.airpods_duck_title)
            setSummary(R.string.airpods_duck_summary)
            min = DuckLevels.MIN
            max = DuckLevels.MAX
            sliderIncrement = 1
            showSliderValue = true
            setLabelFormater { value -> getString(R.string.airpods_duck_option, value.toInt()) }
            setOnPreferenceChangeListener { _, value ->
                if (!rendering) command { AirPodsCommands.sink.setDuckPercent(value as Int) }
                true
            }
        }
        duckGroup.addPreference(duck)

        soundGroup = category(screen, "sound", R.string.airpods_group_sound)
        pressHold = Preference(ctx).apply {
            key = "press_hold"
            setTitle(R.string.airpods_press_hold_title)
            icon = tinted(R.drawable.ic_airpods_squeeze)
            setOnPreferenceClickListener {
                showPressHoldDialog()
                true
            }
        }
        soundGroup.addPreference(pressHold)
        pressHoldFooter = FooterPreference(ctx).apply {
            key = "press_hold_footer"
            setSummary(R.string.airpods_press_hold_footer)
        }
        screen.addPreference(pressHoldFooter)

        aboutGroup = category(screen, "about", R.string.airpods_group_about)
        name = Preference(ctx).apply {
            key = "name"
            setTitle(R.string.airpods_name_title)
            setOnPreferenceClickListener {
                showNameDialog()
                true
            }
        }
        aboutGroup.addPreference(name)
        model = Preference(ctx).apply {
            key = "model"
            setTitle(R.string.airpods_model_title)
            isSelectable = false
        }
        aboutGroup.addPreference(model)
        firmware = Preference(ctx).apply {
            key = "firmware"
            setTitle(R.string.airpods_firmware_title)
            isSelectable = false
        }
        aboutGroup.addPreference(firmware)

        preferenceScreen = screen
        render(AirPodsStateStore.state)
    }

    override fun onStart() {
        super.onStart()
        AirPodsStateStore.addListener(storeListener)
        render(AirPodsStateStore.state)
    }

    override fun onStop() {
        AirPodsStateStore.removeListener(storeListener)
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun category(screen: androidx.preference.PreferenceScreen, key: String, @StringRes title: Int): PreferenceCategory =
        PreferenceCategory(requireContext()).apply {
            this.key = key
            setTitle(title)
            screen.addPreference(this)
        }

    private fun switchRow(key: String, @StringRes title: Int, @StringRes summary: Int, @DrawableRes icon: Int, feature: Feature) =
        SwitchPreferenceCompat(requireContext()).apply {
            this.key = key
            isPersistent = false
            setTitle(title)
            setSummary(summary)
            this.icon = tinted(icon)
            // The state is the truth: refuse the tap here, the command comes back through the store and render().
            setOnPreferenceChangeListener { _, value ->
                if (!rendering) command { AirPodsCommands.sink.setFeature(feature, value as Boolean) }
                false
            }
        }

    /** Runs a command off the main thread, then shows the real state again (a refused command puts the control back). */
    private fun command(block: () -> Unit) {
        try {
            io.execute {
                block()
                activity?.runOnUiThread { render(AirPodsStateStore.state) }
            }
        } catch (e: RejectedExecutionException) {
            // the page is gone
        }
    }

    private fun render(state: AirPodsUiState) {
        if (!isAdded || preferenceScreen == null) return
        rendering = true
        try {
            val connected = state.isConnected
            rings.isVisible = connected
            rings.show(state)
            notConnected.isVisible = !connected

            listeningGroup.isVisible = connected && Feature.LISTENING_MODE in state.supported
            val modes = ListeningModeToggles.modes(state)
            for (i in 0 until BUTTONS) {
                if (i < modes.size) {
                    listening.setUpButton(i, getString(labelOf(modes[i])), transparentIcon())
                    listening.setButtonVisibility(i, true)
                } else {
                    listening.setButtonVisibility(i, false)
                }
            }
            listening.setCheckedIndex(ListeningModeToggles.indexOf(modes, state.listeningMode))
            listening.isEnabled = state.listeningMode != null
            description.setSummary(descriptionOf(state.listeningMode))
            adaptive.isVisible = Feature.ADAPTIVE_MODE in state.supported
            val step = AdaptiveSteps.stepOf(state.adaptivePercent)
            adaptive.isEnabled = step >= 0
            if (step >= 0 && adaptive.value != step) adaptive.value = step

            comfortGroup.isVisible = connected
            ear.isVisible = Feature.EAR_DETECTION in state.supported
            ear.isChecked = state.earDetection
            conversation.isVisible = Feature.CONVERSATION_AWARENESS in state.supported
            conversation.isChecked = state.conversationAwareness

            duckGroup.isVisible = connected && Feature.CONVERSATION_AWARENESS in state.supported && state.conversationAwareness
            if (duck.value != state.duckPercent) duck.value = state.duckPercent

            soundGroup.isVisible = connected && Feature.PRESS_AND_HOLD in state.supported
            pressHoldFooter.isVisible = soundGroup.isVisible
            pressHold.setSummary(pressHoldSummary(state))

            aboutGroup.isVisible = connected
            name.isVisible = Feature.RENAME in state.supported
            name.summary = currentName(state)
            model.summary = state.modelNumber ?: getString(R.string.airpods_unknown)
            firmware.summary = state.firmware ?: getString(R.string.airpods_unknown)
        } finally {
            rendering = false
        }
    }

    private fun pressHoldSummary(state: AirPodsUiState): String {
        if (state.pressHoldModes.isEmpty()) return getString(R.string.airpods_press_hold_unset)
        val labels = PressAndHoldChoices.offered(state).filter { it in state.pressHoldModes }.map { getString(labelOf(it)) }
        return ListFormatter.getInstance().format(labels)
    }

    private fun showPressHoldDialog() {
        val state = AirPodsStateStore.state
        if (!state.isConnected) return
        val offered = PressAndHoldChoices.offered(state)
        val labels = offered.map { getString(labelOf(it)) }.toTypedArray()
        val checked = BooleanArray(offered.size) { offered[it] in state.pressHoldModes }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.airpods_press_hold_dialog_title)
            .setMultiChoiceItems(labels, checked) { dialog, which, isChecked ->
                val next = PressAndHoldChoices.next(AirPodsStateStore.state.pressHoldModes, offered[which], isChecked)
                if (next == null) {
                    // Fewer than two would be left: put the box back.
                    (dialog as AlertDialog).listView.setItemChecked(which, true)
                } else {
                    command { AirPodsCommands.sink.setPressAndHold(next) }
                }
            }
            .setPositiveButton(R.string.airpods_done, null)
            .show()
    }

    private fun showNameDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            filters = arrayOf(InputFilter.LengthFilter(NAME_INPUT_CHARS))
            setText(currentName(AirPodsStateStore.state))
            setSelection(text.length)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.airpods_name_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.airpods_name_dialog_positive, null)
            .setNegativeButton(R.string.airpods_name_dialog_negative, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString()
                if (!AapCodec.isValidName(text)) {
                    input.error = getString(R.string.airpods_name_invalid)
                } else {
                    dialog.dismiss()
                    command { AirPodsCommands.sink.rename(text) }
                }
            }
        }
        dialog.show()
    }

    /** The phone's name for the pair (the alias, else the name): the page shows what Settings shows. */
    private fun currentName(state: AirPodsUiState): String {
        val address = state.address ?: return getString(R.string.airpods_unknown)
        return try {
            val device = requireContext().getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(address)
            device.alias ?: device.name ?: getString(R.string.airpods_unknown)
        } catch (e: IllegalArgumentException) {
            getString(R.string.airpods_unknown)
        } catch (e: SecurityException) {
            getString(R.string.airpods_unknown)
        }
    }

    @StringRes
    private fun labelOf(mode: ListeningMode): Int = when (mode) {
        ListeningMode.OFF -> R.string.airpods_mode_off
        ListeningMode.TRANSPARENCY -> R.string.airpods_mode_transparency
        ListeningMode.ANC -> R.string.airpods_mode_noise_cancel
        ListeningMode.ADAPTIVE -> R.string.airpods_mode_adaptive
    }

    @StringRes
    private fun descriptionOf(mode: ListeningMode?): Int = when (mode) {
        ListeningMode.OFF -> R.string.airpods_mode_off_description
        ListeningMode.TRANSPARENCY -> R.string.airpods_mode_transparency_description
        ListeningMode.ANC -> R.string.airpods_mode_noise_cancel_description
        ListeningMode.ADAPTIVE -> R.string.airpods_mode_adaptive_description
        null -> R.string.airpods_mode_unknown_description
    }

    /** Row icons take the row-icon colour of the theme, like Settings' own rows. */
    private fun tinted(@DrawableRes id: Int): Drawable? {
        val drawable = requireContext().getDrawable(id)?.mutate() ?: return null
        drawable.setTint(requireContext().getColor(ThemeR.color.settingslib_materialColorOnSurfaceVariant))
        return drawable
    }

    /** The segmented button wants an icon; with the label inside the button it is not drawn. */
    private fun transparentIcon(): Drawable {
        val px = (ICON_DP * resources.displayMetrics.density).toInt()
        return BitmapDrawable(resources, Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888))
    }

    private companion object {
        const val BUTTONS = 4
        const val ICON_DP = 24
        const val NAME_INPUT_CHARS = 64
    }
}
