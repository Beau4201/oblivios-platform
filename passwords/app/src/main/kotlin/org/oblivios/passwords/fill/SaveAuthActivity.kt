// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.fill

import android.os.Bundle
import org.oblivios.passwords.PasswordsApplication
import org.oblivios.passwords.R
import org.oblivios.passwords.SecureActivity
import org.oblivios.passwords.data.SaveResult

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

    /** The login taken from the slot while the question is on the screen; this activity wipes it. */
    private var asking: PendingSave? = null

    /**
     * The user sees which site or app, which user name and which app the login came from, and whether it replaces a
     * password, before anything is written (review of 2026-10-23, P-07): the site is what the other app says it is, the app
     * is what the system says.
     */
    private fun save() {
        val pending = app.pendingSave.take() ?: return finish()
        asking = pending
        val source = appLabel(pending.packageName)
        val replaces = LoginSaver.replaces(app.repository, pending.webDomain, pending.packageName, pending.candidate)
        val message = getString(
            if (replaces) R.string.pm_save_confirm_replace else R.string.pm_save_confirm_new,
            pending.webDomain ?: source, pending.candidate.username.ifEmpty { "-" }, source,
        )
        PassphraseDialog.confirmText(
            this, R.string.pm_save_confirm_title, message, R.string.pm_save_confirm_yes,
            onNo = { pending.candidate.wipe(); asking = null; finish() },
        ) { write(pending) }
    }

    private fun write(pending: PendingSave) {
        val result = try {
            LoginSaver.save(app.repository, packageManager, pending.webDomain, pending.packageName, pending.candidate)
        } finally {
            pending.candidate.wipe()
            asking = null
        }
        if (result is SaveResult.Saved) finish() else PassphraseDialog.showMessage(this, R.string.pm_save_failed_title, R.string.pm_save_failed, ::finish)
    }

    private fun appLabel(pkg: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    private fun discard() {
        app.pendingSave.clear()
        finish()
    }

    override fun onDestroy() {
        asking?.candidate?.wipe()
        asking = null
        if (isFinishing) app.pendingSave.clear()
        super.onDestroy()
    }
}
