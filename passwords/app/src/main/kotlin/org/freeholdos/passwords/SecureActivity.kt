// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.view.View
import android.view.WindowManager

/**
 * Base of every Activity of the app. FLAG_SECURE keeps the window out of screenshots, screen recording and the recents
 * screen. It is set before the content exists, so not even the first frame can be captured. Separate windows (dialogs,
 * sheets, menus) do not inherit it; check-static.sh makes each of them say so.
 */
abstract class SecureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        // No autofill service, ours included, is told about a screen of the app itself: the passphrase and the edit form must
        // never reach a fill or a save offer. Set here so that every Activity of the app has it (the services also refuse
        // the package). Dialog windows are separate; PassphraseDialog marks its field itself.
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }
}
