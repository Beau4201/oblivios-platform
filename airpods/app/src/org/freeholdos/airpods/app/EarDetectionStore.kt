// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.app

import android.content.Context

/**
 * The AirPods never report the automatic ear detection setting and do not echo a write (AIR-123), so
 * the phone keeps the value it last wrote, per device, in the app's private storage (no backup, no
 * network). The first value is the AirPods' own default, on.
 */
internal object EarDetectionStore {
    private const val FILE = "airpods_ear_detection"

    fun read(context: Context, address: String): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(address.uppercase(), true)

    fun write(context: Context, address: String, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(address.uppercase(), enabled).apply()
    }

    /** The bond is gone: a pair that is paired again starts from its own default (on), not from an old value. */
    fun forget(context: Context, address: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(address.uppercase()).apply()
    }
}
