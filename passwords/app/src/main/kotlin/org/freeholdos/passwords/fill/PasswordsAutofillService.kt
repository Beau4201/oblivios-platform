// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillEventHistory
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import org.freeholdos.passwords.PasswordsApplication
import org.freeholdos.passwords.R
import org.freeholdos.passwords.core.Entry
import org.freeholdos.passwords.data.SaveResult
import org.freeholdos.passwords.data.VaultRepository
import org.freeholdos.passwords.lock.LockState
import org.freeholdos.passwords.lock.SessionManager

/**
 * Fills login fields from the vault. Locked, it answers with one generic entry that asks for authentication (and only
 * when the domain index says there is an account); open, with one dataset per account, labelled with the username. The
 * service never takes focus, opens no window of its own and logs nothing.
 */
class PasswordsAutofillService : AutofillService() {
    private val app get() = applicationContext as PasswordsApplication
    private val session: SessionManager get() = app.session
    private val repo: VaultRepository get() = app.repository

    private val lookup = object : SiteLookup {
        override fun hasAccount(webDomain: String?, packageName: String) = app.indexStore.hasAccount(webDomain, packageName)
        override fun accounts(webDomain: String?, packageName: String) = repo.accounts(webDomain, packageName) ?: emptyList()
    }

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        recordFills()
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        val screen = StructureParser.parse(structure)
        val state = session.state
        val plan = plan(screen.fields, screen.webDomain, screen.packageName, state, lookup)
        val response = FillResponse.Builder()
        var any = false
        when (plan) {
            FillPlan.None -> Unit
            is FillPlan.Authenticate -> {
                response.addDataset(authenticationDataset(screen, plan.fieldKeys))
                any = true
            }
            is FillPlan.Datasets -> plan.items.forEach { item ->
                datasetFor(item, screen)?.let { response.addDataset(it); any = true }
            }
        }
        // A new login can be saved only while the vault is open: saving needs the vault key.
        val passwordIds = screen.idsOf(screen.fields.filter { it.role == FieldRole.Password }.map { it.key })
        if (state is LockState.Unlocked && passwordIds.isNotEmpty()) {
            val optional = screen.idsOf(screen.fields.filter { it.role == FieldRole.Username }.map { it.key })
            val save = SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME, passwordIds.toTypedArray())
            if (optional.isNotEmpty()) save.setOptionalIds(optional.toTypedArray())
            response.setSaveInfo(save.build())
            any = true
        }
        callback.onSuccess(if (any) response.build() else null)
    }

    private fun presentation(label: String?): RemoteViews =
        RemoteViews(packageName, R.layout.pm_dataset).apply {
            setTextViewText(R.id.pm_label, label ?: getString(R.string.pm_chip_generic))
        }

    /** One entry that, when tapped, runs the biometric prompt and then answers with the real datasets. */
    private fun authenticationDataset(screen: ParsedScreen, keys: List<String>): Dataset {
        val ids = screen.idsOf(keys)
        val intent = Intent(this, FillAuthActivity::class.java)
            .putExtra(FillAuthActivity.EXTRA_WEB_DOMAIN, screen.webDomain)
            .putExtra(FillAuthActivity.EXTRA_PACKAGE, screen.packageName)
            .putParcelableArrayListExtra(FillAuthActivity.EXTRA_USERNAME_IDS, ArrayList(screen.idsOf(keys.filter { k -> screen.fields.first { it.key == k }.role == FieldRole.Username })))
            .putParcelableArrayListExtra(FillAuthActivity.EXTRA_PASSWORD_IDS, ArrayList(screen.idsOf(keys.filter { k -> screen.fields.first { it.key == k }.role == FieldRole.Password })))
        // Mutable on purpose: the autofill framework adds its own extras. The intent is explicit and carries no secret.
        val sender = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT).intentSender
        val builder = Dataset.Builder(presentation(null))
        ids.forEach { builder.setValue(it, null) }
        return builder.setAuthentication(sender).build()
    }

    private fun datasetFor(item: DatasetPlan, screen: ParsedScreen): Dataset? {
        val entry = repo.get(item.entryId) ?: return null
        return try {
            DatasetFactory.build(entry, presentation(item.label), screen.idsOf(item.usernameKeys), screen.idsOf(item.passwordKeys), hex(item.entryId))
        } finally {
            entry.wipe()
        }
    }

    /** The system tells the service which dataset the user picked; the vault notes the fill. */
    private fun recordFills() {
        val events = fillEventHistory?.events ?: return
        if (!session.isOpenNow()) return
        events.filter { it.type == FillEventHistory.Event.TYPE_DATASET_SELECTED }.mapNotNull { it.datasetId }.distinct().forEach { id ->
            unhex(id)?.let { repo.markFilled(it, System.currentTimeMillis()) }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        if (!session.isOpenNow()) return callback.onFailure(getString(R.string.pm_save_locked))
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess()
        val screen = StructureParser.parse(structure)
        val candidate = SaveCandidate.of(screen.fields.mapIndexedNotNull { i, f -> screen.values[i]?.let { f.role to it } })
            ?: return callback.onSuccess()
        try {
            val existing = repo.accounts(screen.webDomain, screen.packageName)?.firstOrNull { it.username == candidate.username }
            val now = System.currentTimeMillis()
            val entry = if (existing != null) {
                repo.get(existing.entryId)?.copy(password = candidate.password.copyOf(), editedMs = now)
            } else {
                Entry(
                    id = newId(), title = titleFor(screen), domains = listOfNotNull(screen.webDomain),
                    packages = if (screen.webDomain == null) listOf(screen.packageName) else emptyList(),
                    username = candidate.username, password = candidate.password.copyOf(), notes = "",
                    createdMs = now, editedMs = now, lastFilledMs = null, history = emptyList(),
                )
            }
            val result = entry?.let { e -> repo.save(e).also { e.wipe() } } ?: SaveResult.Failed(0)
            if (result is SaveResult.Saved) callback.onSuccess() else callback.onFailure(getString(R.string.pm_save_failed))
        } finally {
            candidate.wipe()
        }
    }

    private fun titleFor(screen: ParsedScreen): String =
        screen.webDomain?.removePrefix("www.")
            ?: runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(screen.packageName, 0)).toString() }.getOrDefault(screen.packageName)

    private fun newId(): ByteArray = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }

    private fun SessionManager.isOpenNow() = state is LockState.Unlocked

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String): ByteArray? = if (s.length == 32 && s.all { it in '0'..'9' || it in 'a'..'f' }) ByteArray(16) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() } else null
}

/** Builds a dataset that fills a username and password into the given fields. */
object DatasetFactory {
    fun build(entry: Entry, presentation: RemoteViews, usernameIds: List<AutofillId>, passwordIds: List<AutofillId>, datasetId: String): Dataset {
        val b = Dataset.Builder(presentation).setId(datasetId)
        usernameIds.forEach { b.setValue(it, AutofillValue.forText(entry.username)) }
        passwordIds.forEach { b.setValue(it, AutofillValue.forText(String(entry.password))) }
        return b.build()
    }
}
