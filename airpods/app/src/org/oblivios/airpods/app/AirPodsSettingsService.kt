// SPDX-License-Identifier: Apache-2.0
package org.oblivios.airpods.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import com.android.settingslib.bluetooth.devicesettings.ActionSwitchPreferenceState
import com.android.settingslib.bluetooth.devicesettings.DeviceInfo
import com.android.settingslib.bluetooth.devicesettings.DeviceSetting
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingState
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingsProviderServiceStatus
import com.android.settingslib.bluetooth.devicesettings.IDeviceSettingsListener
import com.android.settingslib.bluetooth.devicesettings.IDeviceSettingsProviderService
import com.android.settingslib.bluetooth.devicesettings.MultiTogglePreferenceState
import org.oblivios.airpods.ui.AirPodsUiState
import org.oblivios.airpods.ui.ListeningModeToggles
import org.oblivios.airpods.ui.NativePageLayout

/**
 * Serves the items of the native device page and takes Settings' clicks. Not exported; every call
 * checks the caller. Settings greys the toggle after a click until the provider publishes the
 * state again, so every update ends with a push of the current state, accepted or not.
 */
class AirPodsSettingsService : Service() {
    private val factory by lazy { NativeSettingsFactory(this) }
    private val listeners = RemoteCallbackList<IDeviceSettingsListener>()
    // While the session is up the settings carry its values; when it ends an empty list hides the rows again, so the
    // page never shows a value that is no longer true (AIR-194, AIR-156).
    private val storeListener = AirPodsStateStore.Listener { old, new ->
        if (new.isConnected) push(new) else old.address?.let { pushTo(it, emptyList()) }
    }

    override fun onCreate() {
        super.onCreate()
        AirPodsStateStore.addListener(storeListener)
    }

    override fun onDestroy() {
        AirPodsStateStore.removeListener(storeListener)
        listeners.kill()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : IDeviceSettingsProviderService.Stub() {
        override fun getServiceStatus(): DeviceSettingsProviderServiceStatus {
            Callers.enforceSettings()
            return DeviceSettingsProviderServiceStatus(true)
        }

        override fun registerDeviceSettingsListener(device: DeviceInfo, callback: IDeviceSettingsListener) {
            Callers.enforceSettings()
            val address = device.bluetoothAddress ?: return
            listeners.register(callback, address.uppercase())
            val state = AirPodsStateStore.state
            if (state.isConnected && state.address.equals(address, ignoreCase = true)) {
                push(state)
            } else {
                pushTo(address.uppercase(), emptyList())
            }
        }

        override fun unregisterDeviceSettingsListener(device: DeviceInfo, callback: IDeviceSettingsListener) {
            Callers.enforceSettings()
            listeners.unregister(callback)
        }

        override fun updateDeviceSettings(device: DeviceInfo, params: DeviceSettingState) {
            Callers.enforceSettings()
            val state = AirPodsStateStore.state
            val address = state.address
            if (address == null || !address.equals(device.bluetoothAddress, ignoreCase = true)) {
                // No session: nothing was applied. Settings greys the row after a click until the provider publishes
                // again, so publish the empty list, which removes the row instead of leaving a selection that was
                // never set (AIR-156 d).
                device.bluetoothAddress?.let { pushTo(it.uppercase(), emptyList()) }
                return
            }
            val sink = AirPodsCommands.sink
            val pref = params.preferenceState
            when (params.settingId) {
                NativePageLayout.ID_LISTENING -> if (pref is MultiTogglePreferenceState) {
                    ListeningModeToggles.modeAt(ListeningModeToggles.modes(state), pref.state)
                        ?.let { sink.setListeningMode(it) }
                }
                NativePageLayout.ID_EAR -> if (pref is ActionSwitchPreferenceState) {
                    sink.setFeature(AirPodsUiState.Feature.EAR_DETECTION, pref.checked)
                }
                NativePageLayout.ID_CONVERSATION -> if (pref is ActionSwitchPreferenceState) {
                    sink.setFeature(AirPodsUiState.Feature.CONVERSATION_AWARENESS, pref.checked)
                }
            }
            push(AirPodsStateStore.state)
        }
    }

    private fun push(state: AirPodsUiState) {
        val address = state.address ?: return
        pushTo(address, factory.settings(state))
    }

    // RemoteCallbackList allows one broadcast at a time: the session thread (state changes) and Settings' binder
    // threads (register, update) both push, and a second beginBroadcast throws IllegalStateException.
    private fun pushTo(address: String, settings: List<DeviceSetting>) {
        synchronized(listeners) {
            val count = listeners.beginBroadcast()
            try {
                for (i in 0 until count) {
                    if (listeners.getBroadcastCookie(i) != address) continue
                    try {
                        listeners.getBroadcastItem(i).onDeviceSettingsChanged(settings)
                    } catch (e: RemoteException) {
                        // The listener died; RemoteCallbackList drops it.
                    }
                }
            } finally {
                listeners.finishBroadcast()
            }
        }
    }
}
