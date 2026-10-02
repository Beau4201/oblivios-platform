package org.sovereignos.airpods.app

import android.content.Context
import org.sovereignos.airpods.ui.DuckLevels

/** The owner's choice of how loud the music stays while they speak: one of [DuckLevels], else the default. */
internal object DuckLevelStore {
    private const val FILE = "airpods_duck_level"
    private const val KEY = "percent"

    fun read(context: Context): Int {
        val percent = prefs(context).getInt(KEY, DuckLevels.DEFAULT)
        return if (DuckLevels.isValid(percent)) percent else DuckLevels.DEFAULT
    }

    fun write(context: Context, percent: Int) {
        if (DuckLevels.isValid(percent)) prefs(context).edit().putInt(KEY, percent).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
