package org.sovereignos.airpods.app

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.MetadataEncoder

/**
 * Makes Settings' own device page show the AirPods: registers the identity (the platform only
 * accepts the metadata of a registered, bonded device), grants Settings read access to the icon
 * URIs, then writes the keys in the order the reader code needs: icons first (icon keys do not
 * trigger a refresh), the untethered flag, battery and charging, and key 25 (the provider tags)
 * last. The only privileged call is BluetoothDevice#setAirPodsMetadata, checked by the platform
 * against our one permission; there is no BLUETOOTH_PRIVILEGED.
 */
internal class AirPodsMetadataWriter(private val context: Context) : AirPodsStateStore.Listener {

    override fun onStateChanged(old: AirPodsUiState, new: AirPodsUiState) {
        val address = new.address ?: return
        try {
            write(old, new, address)
        } catch (e: RuntimeException) {
            Log.w(TAG, "metadata write failed: " + e.javaClass.simpleName)
        }
    }

    private fun write(old: AirPodsUiState, new: AirPodsUiState, address: String) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val device = adapter.getRemoteDevice(address)
        val first = old.address != address
        if (first) {
            if (!device.setAirPodsIdentity(true)) {
                Log.w(TAG, "identity registration refused")
                return
            }
            grantIconAccess()
            put(device, AirPodsContract.KEY_LEFT_ICON, MetadataEncoder.uri(AirPodsContract.ICON_LEFT))
            put(device, AirPodsContract.KEY_RIGHT_ICON, MetadataEncoder.uri(AirPodsContract.ICON_RIGHT))
            put(device, AirPodsContract.KEY_CASE_ICON, MetadataEncoder.uri(AirPodsContract.ICON_CASE))
            put(device, AirPodsContract.KEY_IS_UNTETHERED_HEADSET, MetadataEncoder.bool(true))
        }
        if (first || old.leftPercent != new.leftPercent) {
            put(device, AirPodsContract.KEY_LEFT_BATTERY, MetadataEncoder.percentOrClear(new.leftPercent))
        }
        if (first || old.rightPercent != new.rightPercent) {
            put(device, AirPodsContract.KEY_RIGHT_BATTERY, MetadataEncoder.percentOrClear(new.rightPercent))
        }
        if (first || old.casePercent != new.casePercent) {
            put(device, AirPodsContract.KEY_CASE_BATTERY, MetadataEncoder.percentOrClear(new.casePercent))
        }
        if (first || old.leftCharging != new.leftCharging) {
            put(device, AirPodsContract.KEY_LEFT_CHARGING, MetadataEncoder.bool(new.leftCharging))
        }
        if (first || old.rightCharging != new.rightCharging) {
            put(device, AirPodsContract.KEY_RIGHT_CHARGING, MetadataEncoder.bool(new.rightCharging))
        }
        if (first || old.caseCharging != new.caseCharging) {
            put(device, AirPodsContract.KEY_CASE_CHARGING, MetadataEncoder.bool(new.caseCharging))
        }
        if (first) {
            put(
                device,
                AirPodsContract.KEY_FAST_PAIR_CUSTOMIZED_FIELDS,
                MetadataEncoder.configTags(
                    context.packageName,
                    AirPodsConfigService::class.java.name,
                    AirPodsContract.ACTION_DEVICE_SETTINGS,
                ),
            )
        }
    }

    /** Settings takes a persistable grant before it reads an icon URI (research record section 4). */
    private fun grantIconAccess() {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        val icons = listOf(AirPodsContract.ICON_LEFT, AirPodsContract.ICON_RIGHT, AirPodsContract.ICON_CASE)
        for (uri in icons) {
            context.grantUriPermission(AirPodsContract.SETTINGS_PACKAGE, Uri.parse(uri), flags)
        }
    }

    private fun put(device: BluetoothDevice, key: Int, value: ByteArray) {
        // True also when the stored value already equals the request; false means refused.
        if (!device.setAirPodsMetadata(key, value)) {
            Log.w(TAG, "metadata key $key not written")
        }
    }

    private companion object {
        const val TAG = "AirPodsMetadata"
    }
}
