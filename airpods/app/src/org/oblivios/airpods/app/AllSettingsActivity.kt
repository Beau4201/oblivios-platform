// SPDX-License-Identifier: Apache-2.0
package org.oblivios.airpods.app

import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import org.oblivios.airpods.R

/**
 * Our own AirPods page, opened from the "All AirPods settings" row of Settings' device page (an explicit
 * intent from Settings, the system uid; the activity is not exported). It looks like Settings because it
 * is built from Settings' own components and theme (ADR-129).
 */
class AllSettingsActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.airpods_page_title)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, AirPodsSettingsFragment())
                .commit()
        }
    }
}
