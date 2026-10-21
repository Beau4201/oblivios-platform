// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords

import android.os.Bundle
import android.widget.TextView

/** Placeholder of the scaffold (Task 10): Task 14 replaces the content with the screens. */
class MainActivity : SecureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { setText(R.string.pm_app_name) })
    }
}
