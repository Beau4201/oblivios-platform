package org.sovereignos.airpods.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.android.settingslib.bluetooth.devicesettings.ActionSwitchPreference
import com.android.settingslib.bluetooth.devicesettings.DeviceSetting
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingContract
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingGroup
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingIntentAction
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingItem
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingPreference
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingsConfig
import com.android.settingslib.bluetooth.devicesettings.MultiTogglePreference
import com.android.settingslib.bluetooth.devicesettings.ToggleInfo
import org.sovereignos.airpods.R
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.ListeningModeToggles
import org.sovereignos.airpods.ui.NativePageLayout
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode

/** Builds the config (what Settings lays out) and the per-item settings (what it shows). */
internal class NativeSettingsFactory(private val context: Context) {
    private val iconPx = (ICON_DP * context.resources.displayMetrics.density).toInt()

    fun config(state: AirPodsUiState): DeviceSettingsConfig {
        val items = NativePageLayout.items(state).map { item ->
            if (item.kind == NativePageLayout.Kind.BUILTIN) {
                DeviceSettingItem(
                    settingId = item.settingId,
                    preferenceKey = item.builtinKey,
                    groupIndex = item.groupIndex,
                )
            } else {
                DeviceSettingItem(
                    settingId = item.settingId,
                    packageName = context.packageName,
                    className = AirPodsSettingsService::class.java.name,
                    intentAction = AirPodsContract.ACTION_DEVICE_SETTINGS,
                    groupIndex = item.groupIndex,
                )
            }
        }
        return DeviceSettingsConfig(
            mainContentItems = items,
            moreSettingsItems = emptyList(),
            // Indexes of NativePageLayout.GROUP_LISTENING, GROUP_COMFORT and GROUP_AUDIO.
            settingGroups = listOf(
                DeviceSettingGroup(context.getString(R.string.airpods_group_listening)),
                DeviceSettingGroup(context.getString(R.string.airpods_group_comfort)),
                DeviceSettingGroup(context.getString(R.string.airpods_group_audio)),
            ),
        )
    }

    fun settings(state: AirPodsUiState): List<DeviceSetting> =
        NativePageLayout.items(state)
            .filter { it.kind != NativePageLayout.Kind.BUILTIN }
            .map { toSetting(it, state) }

    private fun toSetting(item: NativePageLayout.Item, state: AirPodsUiState): DeviceSetting {
        val preference: DeviceSettingPreference = when (item.kind) {
            NativePageLayout.Kind.LISTENING_TOGGLE -> listeningToggle(state)
            NativePageLayout.Kind.EAR_DETECTION_SWITCH -> switchRow(
                R.string.airpods_ear_detection_title,
                R.string.airpods_ear_detection_summary,
                R.drawable.ic_airpods_ear,
                state.earDetection,
            )
            NativePageLayout.Kind.CONVERSATION_SWITCH -> switchRow(
                R.string.airpods_conversation_title,
                R.string.airpods_conversation_summary,
                R.drawable.ic_airpods_voice,
                state.conversationAwareness,
            )
            NativePageLayout.Kind.ALL_SETTINGS_LINK -> linkRow()
            NativePageLayout.Kind.BUILTIN -> error("built-in items have no provider setting")
        }
        return DeviceSetting.Builder().setSettingId(item.settingId).setPreference(preference).build()
    }

    private fun listeningToggle(state: AirPodsUiState): MultiTogglePreference {
        val modes = ListeningModeToggles.modes(state)
        val builder = MultiTogglePreference.Builder()
            .setTitle(context.getString(R.string.airpods_group_listening))
            // Unknown mode: the buttons are disabled and Settings shows no selection.
            .setAllowChangingState(state.listeningMode != null)
            .setState(maxOf(0, ListeningModeToggles.indexOf(modes, state.listeningMode)))
        for (mode in modes) {
            builder.addToggleInfo(
                ToggleInfo.Builder()
                    .setLabel(context.getString(labelOf(mode)))
                    // Settings requires an icon; with the opt-in below it is not drawn.
                    .setIcon(Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888))
                    .setExtras(Bundle().apply {
                        putBoolean(DeviceSettingContract.TOGGLE_LABEL_IN_BUTTON, true)
                    })
                    .build()
            )
        }
        return builder.build()
    }

    @StringRes
    private fun labelOf(mode: ListeningMode): Int = when (mode) {
        ListeningMode.OFF -> R.string.airpods_mode_off
        ListeningMode.TRANSPARENCY -> R.string.airpods_mode_transparency
        ListeningMode.ANC -> R.string.airpods_mode_noise_cancel
        ListeningMode.ADAPTIVE -> R.string.airpods_mode_adaptive
    }

    private fun switchRow(
        @StringRes title: Int,
        @StringRes summary: Int,
        @DrawableRes icon: Int,
        checked: Boolean,
    ): ActionSwitchPreference =
        ActionSwitchPreference.Builder()
            .setTitle(context.getString(title))
            .setSummary(context.getString(summary))
            .setIcon(drawableBitmap(icon))
            .setHasSwitch(true)
            .setChecked(checked)
            .setAllowedChangingState(true)
            .build()

    private fun linkRow(): ActionSwitchPreference =
        ActionSwitchPreference.Builder()
            .setTitle(context.getString(R.string.airpods_all_settings_title))
            .setSummary(context.getString(R.string.airpods_all_settings_summary))
            .setIcon(drawableBitmap(R.drawable.ic_airpods_tune))
            .setHasSwitch(false)
            .setAction(
                DeviceSettingIntentAction.Builder()
                    .setIntent(
                        Intent().setClassName(
                            context.packageName,
                            AirPodsContract.ALL_SETTINGS_ACTIVITY,
                        )
                    )
                    .build()
            )
            .build()

    /** Settings tints the bitmap with its row-icon colour, so the stroke colour does not matter. */
    private fun drawableBitmap(@DrawableRes id: Int): Bitmap {
        val drawable = context.getDrawable(id) ?: error("missing drawable")
        val bitmap = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, iconPx, iconPx)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private companion object {
        const val ICON_DP = 24
    }
}
