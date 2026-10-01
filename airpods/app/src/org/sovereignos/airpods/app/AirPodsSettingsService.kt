package org.sovereignos.airpods.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import com.android.settingslib.bluetooth.devicesettings.ActionSwitchPreferenceState
import com.android.settingslib.bluetooth.devicesettings.DeviceInfo
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingState
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingsProviderServiceStatus
import com.android.settingslib.bluetooth.devicesettings.IDeviceSettingsListener
import com.android.settingslib.bluetooth.devicesettings.IDeviceSettingsProviderService
import com.android.settingslib.bluetooth.devicesettings.MultiTogglePreferenceState
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.ListeningModeToggles
import org.sovereignos.airpods.ui.NativePageLayout

/**
 * Serves the items of the native device page and takes Settings' clicks. Not exported; every call
 * checks the caller. Settings greys the toggle after a click until the provider publishes the
 * state again, so every update ends with a push of the current state, accepted or not.
 */
class AirPodsSettingsService : Service() {
    private val factory by lazy { NativeSettingsFactory(this) }
    private val listeners = RemoteCallbackList<IDeviceSettingsListener>()
    private val storeListener = AirPodsStateStore.Listener { _, new -> push(new) }

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
            push(AirPodsStateStore.state)
        }

        override fun unregisterDeviceSettingsListener(device: DeviceInfo, callback: IDeviceSettingsListener) {
            Callers.enforceSettings()
            listeners.unregister(callback)
        }

        override fun updateDeviceSettings(device: DeviceInfo, params: DeviceSettingState) {
            Callers.enforceSettings()
            val state = AirPodsStateStore.state
            val address = state.address
            if (address == null || !address.equals(device.bluetoothAddress, ignoreCase = true)) return
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
        val settings = factory.settings(state)
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
