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

/**
 * Found through metadata key 25. Answers with the page layout for the recognised, connected AirPods
 * and with a failure for any other device, so Settings keeps its stock page there. Not exported;
 * Settings (system uid) is still allowed to bind it (ActivityManager.checkComponentPermission).
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
        if (address == null || !address.equals(device.bluetoothAddress, ignoreCase = true)) {
            callback.onResult(
                DeviceSettingsConfigServiceStatus(false),
                DeviceSettingsConfig(emptyList(), emptyList()),
            )
            return
        }
        callback.onResult(DeviceSettingsConfigServiceStatus(true), factory.config(state))
    }

    private companion object {
        /** How long a page request may wait for a session that is being set up (see awaitFirstControls). */
        const val PAGE_WAIT_MS = 1_500L
    }
}
