// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.PendingIntentHandler
import org.freeholdos.passwords.PasswordsApplication
import org.freeholdos.passwords.SecureActivity

/**
 * The step of the Credential Manager between a tap and the answer. Without an entry id it only opens the vault and
 * answers with the list of accounts; with one it opens the vault and answers with that account's password credential.
 */
class CredentialAuthActivity : SecureActivity() {
    private val app get() = applicationContext as PasswordsApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UnlockFlow.run(this, app.session, forFill = true, onOpened = ::answer, onCancel = ::cancel)
    }

    private fun answer() {
        val result = Intent()
        val entryId = intent.getStringExtra(EXTRA_ENTRY)?.let(::unhex)
        if (entryId != null) {
            val entry = app.repository.get(entryId)
            if (entry == null) {
                PendingIntentHandler.setGetCredentialException(result, GetCredentialUnknownException())
            } else {
                try {
                    PendingIntentHandler.setGetCredentialResponse(result, GetCredentialResponse(PasswordCredential(entry.username, String(entry.password))))
                    app.repository.markFilled(entryId, System.currentTimeMillis())
                } finally {
                    entry.wipe()
                }
            }
        } else {
            val request = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent) ?: return cancel()
            val option = request.beginGetCredentialOptions.filterIsInstance<BeginGetPasswordOption>().firstOrNull() ?: return cancel()
            val pkg = request.callingAppInfo?.packageName ?: return cancel()
            val response = BeginGetCredentialResponse.Builder()
            app.repository.accounts(null, pkg).orEmpty().forEach { a ->
                val pending = android.app.PendingIntent.getActivity(
                    this, a.entryId.contentHashCode(),
                    Intent(this, CredentialAuthActivity::class.java).putExtra(EXTRA_ENTRY, a.entryId.joinToString("") { "%02x".format(it) }),
                    android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
                response.addCredentialEntry(PasswordCredentialEntry.Builder(this, a.username.ifEmpty { getString(org.freeholdos.passwords.R.string.pm_chip_generic) }, pending, option).build())
            }
            PendingIntentHandler.setBeginGetCredentialResponse(result, response.build())
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun unhex(s: String): ByteArray? =
        if (s.length == 32 && s.all { it in '0'..'9' || it in 'a'..'f' }) ByteArray(16) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() } else null

    companion object {
        const val EXTRA_ENTRY = "org.freeholdos.passwords.extra.ENTRY"
    }
}
