// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

import android.os.Bundle
import org.freeholdos.passwords.PasswordsApplication
import org.freeholdos.passwords.R
import org.freeholdos.passwords.SecureActivity
import org.freeholdos.passwords.data.SaveResult

/**
 * The step after a login was submitted while the vault was locked: the autofill framework starts it (the service handed
 * it over as the continuation of the save), it opens the vault with the same prompt as a fill and then saves the login
 * that waits in [PendingSaveSlot]. The intent carries nothing; the login never leaves the memory of the process.
 */
class SaveAuthActivity : SecureActivity() {
    private val app get() = applicationContext as PasswordsApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UnlockFlow.run(this, app.session, forFill = false, onOpened = ::save, onCancel = ::discard)
    }

    private fun save() {
        val pending = app.pendingSave.take() ?: return finish()
        val result = try {
            LoginSaver.save(app.repository, packageManager, pending.webDomain, pending.packageName, pending.candidate)
        } finally {
            pending.candidate.wipe()
        }
        if (result is SaveResult.Saved) finish() else PassphraseDialog.showMessage(this, R.string.pm_save_failed_title, R.string.pm_save_failed, ::finish)
    }

    private fun discard() {
        app.pendingSave.clear()
        finish()
    }

    override fun onDestroy() {
        if (isFinishing) app.pendingSave.clear()
        super.onDestroy()
    }
}
