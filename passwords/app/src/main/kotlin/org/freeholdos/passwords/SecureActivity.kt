// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

/**
 * Base of every Activity of the app. FLAG_SECURE keeps the window out of screenshots, screen recording and the recents
 * screen. It is set before the content exists, so not even the first frame can be captured. Separate windows (dialogs,
 * sheets, menus) do not inherit it; check-static.sh makes each of them say so.
 */
abstract class SecureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
    }
}
