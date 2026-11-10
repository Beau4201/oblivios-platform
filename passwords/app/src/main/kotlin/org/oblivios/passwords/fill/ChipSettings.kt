// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.fill

import android.content.Context
import android.content.SharedPreferences

/**
 * The two switches of the chips, in the private preferences of the app: the screens write them and the autofill service
 * reads them. Both run in the one process of the app, so they share one SharedPreferences instance.
 */
class ChipSettings(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    /** "Suggestions above the keyboard": the automatic offer as a whole. */
    var suggestionsOn: Boolean
        get() = prefs.getBoolean(KEY_SUGGESTIONS, true)
        set(v) { prefs.edit().putBoolean(KEY_SUGGESTIONS, v).apply() }

    /** The keys ([ChipPolicy.entryKey]) of the logins that are not offered automatically. */
    fun chipOff(): Set<String> = prefs.getStringSet(KEY_CHIP_OFF, emptySet()).orEmpty()

    fun isOffered(key: String): Boolean = key !in chipOff()

    fun setOffered(key: String, offered: Boolean) {
        val set = chipOff().toMutableSet()
        if (offered) set.remove(key) else set.add(key)
        prefs.edit().putStringSet(KEY_CHIP_OFF, set).apply()
    }

    companion object {
        const val PREFS = "ui"
        private const val KEY_SUGGESTIONS = "suggestions"
        private const val KEY_CHIP_OFF = "chip_off"
    }
}
