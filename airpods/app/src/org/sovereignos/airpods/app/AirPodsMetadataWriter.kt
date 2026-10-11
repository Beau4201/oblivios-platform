// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.airpods.app

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.sovereignos.airpods.app.AirPodsContract.ICON_CASE
import org.sovereignos.airpods.app.AirPodsContract.ICON_LEFT
import org.sovereignos.airpods.app.AirPodsContract.ICON_RIGHT
import org.sovereignos.airpods.app.AirPodsContract.KEY_CASE_BATTERY
import org.sovereignos.airpods.app.AirPodsContract.KEY_CASE_CHARGING
import org.sovereignos.airpods.app.AirPodsContract.KEY_CASE_ICON
import org.sovereignos.airpods.app.AirPodsContract.KEY_FAST_PAIR_CUSTOMIZED_FIELDS
import org.sovereignos.airpods.app.AirPodsContract.KEY_IS_UNTETHERED_HEADSET
import org.sovereignos.airpods.app.AirPodsContract.KEY_LEFT_BATTERY
import org.sovereignos.airpods.app.AirPodsContract.KEY_LEFT_CHARGING
import org.sovereignos.airpods.app.AirPodsContract.KEY_LEFT_ICON
import org.sovereignos.airpods.app.AirPodsContract.KEY_RIGHT_BATTERY
import org.sovereignos.airpods.app.AirPodsContract.KEY_RIGHT_CHARGING
import org.sovereignos.airpods.app.AirPodsContract.KEY_RIGHT_ICON
import org.sovereignos.airpods.ui.AirPodsUiState
import org.sovereignos.airpods.ui.MetadataEncoder

/**
 * Makes Settings' own device page show the AirPods: registers the identity (the platform only
 * accepts the metadata of a registered, bonded device), grants Settings read access to the icon
 * URIs, then writes the keys in the order the reader code needs: icons first (icon keys do not
 * trigger a refresh), the untethered flag, battery and charging, and key 25 (the provider tags)
 * last. The only privileged call is BluetoothDevice#setAirPodsMetadata, checked by the platform
 * against our one permission; there is no BLUETOOTH_PRIVILEGED.
 *
 * The first set-up of an address is only considered done when every write succeeded. A state that
 * arrives while the bond is still forming is refused by the platform (found on the device on
 * 2026-10-01: the owner re-paired and the state arrived during BOND_BONDING); it is retried on the
 * next state change and on every connection or bond event (retry()).
 */
internal class AirPodsMetadataWriter(private val context: Context) : AirPodsStateStore.Listener {
    private var initializedFor: String? = null
    private var written: AirPodsUiState = AirPodsUiState.DISCONNECTED

    override fun onStateChanged(old: AirPodsUiState, new: AirPodsUiState) {
        // Listeners of two threads can be called out of order; the store holds the newest state, which is the one to write.
        sync(AirPodsStateStore.state)
    }

    /**
     * The bond of this address is gone and the platform dropped its metadata with it: a later pairing of the same
     * address must set everything up again (icons, untethered flag, provider tags), not only the changed battery keys.
     */
    @Synchronized
    fun forget(address: String) {
        if (initializedFor.equals(address, ignoreCase = true)) {
            initializedFor = null
            written = AirPodsUiState.DISCONNECTED
        }
    }

    /** Called on connection and bond events: repeats whatever did not succeed before. */
    fun retry() {
        sync(AirPodsStateStore.state)
    }

    @Synchronized
    private fun sync(state: AirPodsUiState) {
        val address = state.address ?: return
        try {
            write(state, address)
        } catch (e: RuntimeException) {
            Log.w(TAG, "metadata write failed: " + e.javaClass.simpleName)
        }
    }

    private fun write(state: AirPodsUiState, address: String): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        val device = adapter.getRemoteDevice(address)
        val first = initializedFor != address
        if (first && !setUp(device)) return false
        val prev = if (first) null else written
        var ok = true
        if (prev == null || prev.leftPercent != state.leftPercent) {
            ok = putPercent(device, KEY_LEFT_BATTERY, state.leftPercent) && ok
        }
        if (prev == null || prev.rightPercent != state.rightPercent) {
            ok = putPercent(device, KEY_RIGHT_BATTERY, state.rightPercent) && ok
        }
        if (prev == null || prev.casePercent != state.casePercent) {
            ok = putPercent(device, KEY_CASE_BATTERY, state.casePercent) && ok
        }
        if (prev == null || prev.leftCharging != state.leftCharging) {
            ok = putFlag(device, KEY_LEFT_CHARGING, state.leftCharging) && ok
        }
        if (prev == null || prev.rightCharging != state.rightCharging) {
            ok = putFlag(device, KEY_RIGHT_CHARGING, state.rightCharging) && ok
        }
        if (prev == null || prev.caseCharging != state.caseCharging) {
            ok = putFlag(device, KEY_CASE_CHARGING, state.caseCharging) && ok
        }
        if (first) ok = putProviderTags(device) && ok
        if (ok) {
            initializedFor = address
            written = state
        }
        return ok
    }

    /** Identity, icon access, icons and the untethered flag: everything Settings needs first. */
    private fun setUp(device: BluetoothDevice): Boolean {
        if (!device.setAirPodsIdentity(true)) {
            Log.w(TAG, "identity registration refused, will retry")
            return false
        }
        grantIconAccess()
        return put(device, KEY_LEFT_ICON, MetadataEncoder.uri(ICON_LEFT)) &&
            put(device, KEY_RIGHT_ICON, MetadataEncoder.uri(ICON_RIGHT)) &&
            put(device, KEY_CASE_ICON, MetadataEncoder.uri(ICON_CASE)) &&
            putFlag(device, KEY_IS_UNTETHERED_HEADSET, true)
    }

    /** Key 25 last: it makes Settings ask our service for the page. */
    private fun putProviderTags(device: BluetoothDevice): Boolean = put(
        device,
        KEY_FAST_PAIR_CUSTOMIZED_FIELDS,
        MetadataEncoder.configTags(
            context.packageName,
            AirPodsConfigService::class.java.name,
            AirPodsContract.ACTION_DEVICE_SETTINGS,
        ),
    )

    /** Settings takes a persistable grant before it reads an icon URI (research record section 4). */
    private fun grantIconAccess() {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        for (uri in listOf(ICON_LEFT, ICON_RIGHT, ICON_CASE)) {
            context.grantUriPermission(AirPodsContract.SETTINGS_PACKAGE, Uri.parse(uri), flags)
        }
    }

    private fun putPercent(device: BluetoothDevice, key: Int, percent: Int): Boolean =
        put(device, key, MetadataEncoder.percentOrClear(percent))

    private fun putFlag(device: BluetoothDevice, key: Int, value: Boolean): Boolean =
        put(device, key, MetadataEncoder.bool(value))

    private fun put(device: BluetoothDevice, key: Int, value: ByteArray): Boolean {
        // True also when the stored value already equals the request; false means refused.
        val accepted = device.setAirPodsMetadata(key, value)
        if (!accepted) Log.w(TAG, "metadata key $key not written")
        return accepted
    }

    private companion object {
        const val TAG = "AirPodsMetadata"
    }
}
