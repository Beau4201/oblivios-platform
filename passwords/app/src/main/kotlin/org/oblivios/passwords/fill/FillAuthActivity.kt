// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.fill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import org.oblivios.passwords.PasswordsApplication
import org.oblivios.passwords.R
import org.oblivios.passwords.SecureActivity

/**
 * The step between a tap on the locked entry and the fill: it opens the vault (the system biometric prompt, with the
 * passphrase as the fallback inside it) and answers the autofill framework with a Dataset for one account or with a
 * FillResponse for several. It has no content of its own and nothing in it is shown except the prompt.
 */
class FillAuthActivity : SecureActivity() {
    private val app get() = applicationContext as PasswordsApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UnlockFlow.run(this, app.session, forFill = true, onOpened = ::answer, onCancel = ::cancel)
    }

    @Suppress("DEPRECATION")
    private fun answer() {
        val webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return cancel()
        val users = intent.getParcelableArrayListExtra<AutofillId>(EXTRA_USERNAME_IDS) ?: arrayListOf()
        val passwords = intent.getParcelableArrayListExtra<AutofillId>(EXTRA_PASSWORD_IDS) ?: arrayListOf()
        val accounts = app.repository.accounts(webDomain, pkg).orEmpty()
        // The system hands over the request of the keyboard again, so several accounts can be chips (ADR-191). The user
        // asked for the login by tapping, so the switch of a login does not apply here.
        val chips = InlineChips.of(this, intent.getParcelableExtra<InlineSuggestionsRequest>(AutofillManager.EXTRA_INLINE_SUGGESTIONS_REQUEST))
        val withChip = chips?.let { ChipPolicy.chipCount(accounts.size, it.maxCount) } ?: 0
        val datasets = accounts.mapIndexedNotNull { i, a ->
            val entry = app.repository.get(a.entryId) ?: return@mapIndexedNotNull null
            try {
                DatasetFactory.build(entry, label(a.username), users, passwords, ChipPolicy.entryKey(a.entryId), if (i < withChip) chips?.presentation(a.username.ifEmpty { null }, i) else null) to a
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
        const val EXTRA_WEB_DOMAIN = "org.oblivios.passwords.extra.WEB_DOMAIN"
        const val EXTRA_PACKAGE = "org.oblivios.passwords.extra.PACKAGE"
        const val EXTRA_USERNAME_IDS = "org.oblivios.passwords.extra.USERNAME_IDS"
        const val EXTRA_PASSWORD_IDS = "org.oblivios.passwords.extra.PASSWORD_IDS"
    }
}
