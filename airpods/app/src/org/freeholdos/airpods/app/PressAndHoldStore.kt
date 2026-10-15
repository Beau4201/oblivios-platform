// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.app

import android.content.Context
import org.freeholdos.airpods.ui.PressAndHoldChoices

/**
 * The last complete press and hold selection written to the AirPods, per device, in the app's private
 * storage (no backup, no network). The pair does not report this setting (AIR-163), so this is the only
 * thing the page can show; 0 means nothing was written yet. Removed with the bond.
 */
internal object PressAndHoldStore {
    private const val FILE = "airpods_press_hold"

    /** The stored mask, or 0 when none or not a complete selection. */
    fun read(context: Context, address: String): Int {
        val mask = prefs(context).getInt(address.uppercase(), 0)
        return if (mask != 0 && PressAndHoldChoices.fromMask(mask).isEmpty()) 0 else mask
    }

    fun write(context: Context, address: String, mask: Int) {
        if (PressAndHoldChoices.fromMask(mask).isEmpty()) return
        prefs(context).edit().putInt(address.uppercase(), mask).apply()
    }

    fun forget(context: Context, address: String) {
        prefs(context).edit().remove(address.uppercase()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
