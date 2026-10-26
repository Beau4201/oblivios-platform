// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import org.freeholdos.passwords.ui.AppController
import org.freeholdos.passwords.ui.PasswordsRoot

/**
 * The one screen of the app. It handles its own configuration changes (rotation, theme, font size), so it is not
 * recreated and nothing needs to be saved: the instance state is deliberately left empty, so that no secret can be in it.
 */
class MainActivity : SecureActivity() {
    private lateinit var controller: AppController

    private val exportTarget = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { controller.exportTargetChosen(it) }
    private val importSource = registerForActivityResult(ActivityResultContracts.OpenDocument()) { controller.importSourceChosen(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        enableEdgeToEdge()
        controller = AppController(this)
        controller.pickExportTarget = { name -> exportTarget.launch(name) }
        controller.pickImportSource = { importSource.launch(arrayOf("*/*")) }
        setContent { PasswordsRoot(controller) }
    }

    override fun onStart() {
        super.onStart()
        controller.start()
    }

    override fun onStop() {
        controller.stop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Nothing is saved: a secret must never reach the instance state (plan, Review Focus).
    }
}
