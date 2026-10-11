// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.airpods.app

import android.content.Context

/**
 * The last case battery the AirPods reported, per device, in the app's private storage (no backup, no
 * network). The case is out of reach while the pods are in the ears and the owner wants its last value
 * shown at all times, also after a reconnect or a restart of the process; the next report replaces it
 * (ADR-125, decision 13). Removed when the bond is removed. Packed as percent * 2 + charging.
 */
internal object CaseBatteryStore {
    private const val FILE = "airpods_case_battery"

    /** The stored percent (or -1 when none or unreadable) and the charging flag. */
    fun read(context: Context, address: String): Pair<Int, Boolean> {
        val packed = prefs(context).getInt(address.uppercase(), -1)
        val percent = packed shr 1
        return if (packed < 0 || percent > 100) -1 to false else percent to (packed and 1 == 1)
    }

    fun write(context: Context, address: String, percent: Int, charging: Boolean) {
        if (percent < 0 || percent > 100) return
        prefs(context).edit().putInt(address.uppercase(), percent * 2 + if (charging) 1 else 0).apply()
    }

    fun forget(context: Context, address: String) {
        prefs(context).edit().remove(address.uppercase()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
