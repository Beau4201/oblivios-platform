// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import org.freeholdos.passwords.PasswordsApplication
import org.freeholdos.passwords.R
import org.freeholdos.passwords.lock.LockState

/**
 * The Credential Manager side of the app: password credentials only, for native apps that ask by their package name.
 * (A browser's request for a website carries an origin only for apps on a privileged allow list, which version 1 does
 * not ship; websites are served through the autofill service.) A locked vault shows one generic entry that opens it; an
 * open one lists the usernames of the accounts linked to the calling app.
 */
class PasswordsCredentialProviderService : CredentialProviderService() {
    private val app get() = applicationContext as PasswordsApplication

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        val option = request.beginGetCredentialOptions.filterIsInstance<BeginGetPasswordOption>().firstOrNull()
        val pkg = request.callingAppInfo?.packageName
        val response = BeginGetCredentialResponse.Builder()
        if (option != null && pkg != null) {
            if (app.session.state is LockState.Unlocked) {
                app.repository.accounts(null, pkg).orEmpty().forEach { response.addCredentialEntry(entryFor(it.entryId, it.username, option)) }
            } else if (app.indexStore.hasAccount(null, pkg)) {
                response.addAuthenticationAction(AuthenticationAction(getString(R.string.pm_chip_generic), pendingIntent(null)))
            }
        }
        callback.onResult(response.build())
    }

    internal fun entryFor(entryId: ByteArray, username: String, option: BeginGetPasswordOption): CredentialEntry =
        PasswordCredentialEntry.Builder(this, username.ifEmpty { getString(R.string.pm_chip_generic) }, pendingIntent(entryId), option).build()

    /** Selecting an entry (id given) or unlocking (no id) runs [CredentialAuthActivity]. Mutable: the system adds the request. */
    private fun pendingIntent(entryId: ByteArray?): PendingIntent {
        val intent = Intent(this, CredentialAuthActivity::class.java)
        entryId?.let { intent.putExtra(CredentialAuthActivity.EXTRA_ENTRY, it.joinToString("") { b -> "%02x".format(b) }) }
        val code = entryId?.contentHashCode() ?: 0
        return PendingIntent.getActivity(this, code, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Creating credentials (saving through the Credential Manager) and clearing state are out of version 1.
    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        callback.onResult(BeginCreateCredentialResponse.Builder().build())
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        callback.onResult(null)
    }
}
