// SPDX-License-Identifier: Apache-2.0
package org.oblivios.airpods.app

import android.content.Context
import java.util.EnumMap
import org.oblivios.airpods.validation.AirPodsControl

/**
 * The last value written to each control of the pair, per device, in the app's private storage (no
 * backup, no network). The pair reports some controls only in the connection burst of a later session
 * and some never (AIR-179), so this is what the page shows for those until a report arrives. Removed
 * with the bond.
 */
internal object ControlStore {
    private const val FILE = "airpods_controls"
    private const val NONE = Int.MIN_VALUE

    /** Every stored value that is still inside the range of its control. */
    fun readAll(context: Context, address: String): Map<AirPodsControl, Int> {
        val prefs = prefs(context)
        val out = EnumMap<AirPodsControl, Int>(AirPodsControl::class.java)
        for (control in AirPodsControl.values()) {
            val value = prefs.getInt(key(address, control), NONE)
            if (value != NONE && control.accepts(value)) out[control] = value
        }
        return out
    }

    fun write(context: Context, address: String, control: AirPodsControl, value: Int) {
        if (!control.accepts(value)) return
        prefs(context).edit().putInt(key(address, control), value).apply()
    }

    fun forget(context: Context, address: String) {
        val editor = prefs(context).edit()
        for (control in AirPodsControl.values()) editor.remove(key(address, control))
        editor.apply()
    }

    private fun key(address: String, control: AirPodsControl) = address.uppercase() + ":" + control.name

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
