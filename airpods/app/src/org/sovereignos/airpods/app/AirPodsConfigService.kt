package org.sovereignos.airpods.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.android.settingslib.bluetooth.devicesettings.DeviceInfo
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingConfigOptions
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingsConfig
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingsConfigServiceStatus
import com.android.settingslib.bluetooth.devicesettings.IDeviceSettingsConfigProviderService
import com.android.settingslib.bluetooth.devicesettings.IGetDeviceSettingsConfigCallback
import org.sovereignos.airpods.ui.AirPodsUiState

/**
 * Found through metadata key 25. Answers with the page layout for a recognised AirPods, connected or not
 * (Settings reads the list of rows once, AIR-194), and with a failure for any other device, so Settings keeps
 * its stock page there. Not exported; Settings (system uid) is still allowed to bind it
 * (ActivityManager.checkComponentPermission).
 */
class AirPodsConfigService : Service() {
    private val factory by lazy { NativeSettingsFactory(this) }

    private val binder = object : IDeviceSettingsConfigProviderService.Stub() {
        override fun getDeviceSettingsConfig(
            device: DeviceInfo,
            callback: IGetDeviceSettingsConfigCallback,
        ) = respond(device, callback)

        override fun getDeviceSettingsConfigWithOptions(
            device: DeviceInfo,
            callback: IGetDeviceSettingsConfigCallback,
            options: DeviceSettingConfigOptions,
        ) = respond(device, callback)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun respond(device: DeviceInfo, callback: IGetDeviceSettingsConfigCallback) {
        Callers.enforceSettings()
        device.bluetoothAddress?.let { AirPodsSessionHost.awaitFirstControls(this, it, PAGE_WAIT_MS) }
        val state = AirPodsStateStore.state
        val address = state.address
        if (address != null && address.equals(device.bluetoothAddress, ignoreCase = true)) {
            callback.onResult(DeviceSettingsConfigServiceStatus(true), factory.config(state))
            return
        }
        // No session for this pair right now: the case is closed, or the session is still starting. Settings reads the
        // list of rows once, so a recognised pair still gets every row; AirPodsSettingsService publishes the values
        // when the session has them and until then no setting, which keeps the rows hidden (AIR-194).
        val bluetoothAddress = device.bluetoothAddress
        if (bluetoothAddress != null && AirPodsSessionHost.recognisedAddress(this, bluetoothAddress)) {
            callback.onResult(DeviceSettingsConfigServiceStatus(true), factory.config(AirPodsUiState.layoutOnly()))
            return
        }
        callback.onResult(
            DeviceSettingsConfigServiceStatus(false),
            DeviceSettingsConfig(emptyList(), emptyList()),
        )
    }

    private companion object {
        /** How long a page request may wait for a session that is being set up (see awaitFirstControls). */
        const val PAGE_WAIT_MS = 1_500L
    }
}
