// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.widget.RemoteViews
import org.freeholdos.passwords.PasswordsApplication
import org.freeholdos.passwords.R
import org.freeholdos.passwords.SecureActivity
import org.freeholdos.passwords.lock.BiometricOutcome
import org.freeholdos.passwords.lock.LockState
import org.freeholdos.passwords.lock.PassphraseOutcome

/**
 * The step between a tap on the locked entry and the fill: it opens the vault (the system biometric prompt, with the
 * passphrase as the fallback inside it) and answers the autofill framework with a Dataset for one account or with a
 * FillResponse for several. It has no content of its own and nothing in it is shown except the prompt.
 */
class FillAuthActivity : SecureActivity() {
    private val app get() = applicationContext as PasswordsApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (val state = app.session.state) {
            is LockState.Unlocked -> answer()
            LockState.SecretLost -> PassphraseDialog.showSecretLost(this) { cancel() }
            is LockState.NeedsPassphrase -> askPassphrase(null)
            LockState.Locked -> if (app.session.biometricsEnabled()) {
                app.session.biometricUnlock(this, forFill = true, done = ::afterBiometric)
            } else {
                askPassphrase(null)
            }
        }
    }

    private fun afterBiometric(outcome: BiometricOutcome) {
        when (outcome) {
            BiometricOutcome.Opened -> answer()
            BiometricOutcome.Cancelled -> cancel()
            else -> askPassphrase(null)
        }
    }

    private fun askPassphrase(error: CharSequence?) {
        PassphraseDialog.show(this, forFill = true, error = error, onCancel = ::cancel) { chars ->
            val outcome = app.session.unlockWithPassphrase(chars)
            chars.fill('\u0000')
            when (outcome) {
                PassphraseOutcome.Opened -> answer()
                is PassphraseOutcome.Wrong -> askPassphrase(getString(R.string.pm_passphrase_wrong))
                is PassphraseOutcome.Throttled -> askPassphrase(resources.getQuantityString(R.plurals.pm_passphrase_wait, secondsOf(outcome.waitMs), secondsOf(outcome.waitMs)))
                PassphraseOutcome.SecretLost -> PassphraseDialog.showSecretLost(this) { cancel() }
                is PassphraseOutcome.Failed -> cancel()
            }
        }
    }

    private fun secondsOf(ms: Long): Int = ((ms + 999) / 1000).toInt().coerceAtLeast(1)

    @Suppress("DEPRECATION")
    private fun answer() {
        val webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return cancel()
        val users = intent.getParcelableArrayListExtra<AutofillId>(EXTRA_USERNAME_IDS) ?: arrayListOf()
        val passwords = intent.getParcelableArrayListExtra<AutofillId>(EXTRA_PASSWORD_IDS) ?: arrayListOf()
        val accounts = app.repository.accounts(webDomain, pkg).orEmpty()
        val datasets = accounts.mapNotNull { a ->
            val entry = app.repository.get(a.entryId) ?: return@mapNotNull null
            try {
                DatasetFactory.build(entry, label(a.username), users, passwords, a.entryId.joinToString("") { "%02x".format(it) }) to a
            } finally {
                entry.wipe()
            }
        }
        if (datasets.isEmpty()) return cancel()
        val result: android.os.Parcelable = if (datasets.size == 1) {
            app.repository.markFilled(datasets[0].second.entryId, System.currentTimeMillis())
            datasets[0].first
        } else {
            FillResponse.Builder().apply { datasets.forEach { addDataset(it.first) } }.build()
        }
        setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, result))
        finish()
    }

    private fun label(username: String): RemoteViews =
        RemoteViews(packageName, R.layout.pm_dataset).apply {
            setTextViewText(R.id.pm_label, username.ifEmpty { getString(R.string.pm_chip_generic) })
        }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_WEB_DOMAIN = "org.freeholdos.passwords.extra.WEB_DOMAIN"
        const val EXTRA_PACKAGE = "org.freeholdos.passwords.extra.PACKAGE"
        const val EXTRA_USERNAME_IDS = "org.freeholdos.passwords.extra.USERNAME_IDS"
        const val EXTRA_PASSWORD_IDS = "org.freeholdos.passwords.extra.PASSWORD_IDS"
    }
}
