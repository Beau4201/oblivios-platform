// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.fill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillEventHistory
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import java.util.regex.Pattern
import org.oblivios.passwords.PasswordsApplication
import org.oblivios.passwords.R
import org.oblivios.passwords.core.Entry
import org.oblivios.passwords.data.SaveResult
import org.oblivios.passwords.data.VaultRepository
import org.oblivios.passwords.lock.LockState
import org.oblivios.passwords.lock.SessionManager

/**
 * Fills login fields from the vault. Locked, it answers with one generic entry that asks for authentication (and only
 * when the domain index says there is an account); open, with one dataset per account, labelled with the username. The
 * service never takes focus, opens no window of its own and logs nothing.
 */
class PasswordsAutofillService : AutofillService() {
    private val app get() = applicationContext as PasswordsApplication
    private val session: SessionManager get() = app.session
    private val repo: VaultRepository get() = app.repository
    private val chipSettings by lazy { ChipSettings(this) }

    private val lookup = object : SiteLookup {
        override fun hasAccount(webDomain: String?, packageName: String) = app.indexStore.hasAccount(webDomain, packageName)
        override fun accounts(webDomain: String?, packageName: String) = repo.accounts(webDomain, packageName) ?: emptyList()
    }

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        recordFills()
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        // The app's own screens (the passphrase, the edit form) are never filled and never offered for saving: on the first
        // device round the passphrase of a new vault was offered as a login of this app and saved into the vault.
        if (structure.activityComponent?.packageName == packageName) return callback.onSuccess(null)
        val screen = StructureParser.parse(structure, app.originPolicy)
        val state = session.state
        val manual = request.flags and FillRequest.FLAG_MANUAL_REQUEST != 0
        val plan = ChipPolicy.offer(plan(screen.fields, screen.webDomain, screen.packageName, state, lookup), manual, chipSettings.suggestionsOn, chipSettings.chipOff())
        // Chips only for a keyboard that ChipPolicy trusts (ADR-191); every other keyboard gets the system list.
        val chips = InlineChips.of(this, request.inlineSuggestionsRequest)
        val response = FillResponse.Builder()
        var any = false
        when (plan) {
            FillPlan.None -> Unit
            is FillPlan.Authenticate -> {
                response.addDataset(authenticationDataset(screen, plan.fieldKeys, chips?.presentation(null, 0)))
                any = true
            }
            is FillPlan.Datasets -> {
                val withChip = chips?.let { ChipPolicy.chipCount(plan.items.size, it.maxCount) } ?: 0
                plan.items.forEachIndexed { i, item ->
                    datasetFor(item, screen, if (i < withChip) chips?.presentation(item.label, i) else null)?.let { response.addDataset(it); any = true }
                }
            }
        }
        // A submitted login is offered for saving on a login form, locked or open, when the vault has no account for the
        // site or app yet (the domain index answers that while locked too). Locked, the save waits for the unlock
        // (onSaveRequest). Never for an app of the system image (the origin policy gives such a screen no fields) and, in an
        // app, only for a form with a username too ([offersSave]).
        val passwordIds = screen.idsOf(screen.fields.filter { it.role == FieldRole.Password }.map { it.key })
        if (offersSave(screen.fields, screen.webDomain, lookup.hasAccount(screen.webDomain, screen.packageName)) && passwordIds.isNotEmpty()) {
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
    private fun authenticationDataset(screen: ParsedScreen, keys: List<String>, inline: InlinePresentation?): Dataset {
        val ids = screen.idsOf(keys)
        val intent = Intent(this, FillAuthActivity::class.java)
            .putExtra(FillAuthActivity.EXTRA_WEB_DOMAIN, screen.webDomain)
            .putExtra(FillAuthActivity.EXTRA_PACKAGE, screen.packageName)
            .putParcelableArrayListExtra(FillAuthActivity.EXTRA_USERNAME_IDS, ArrayList(screen.idsOf(keys.filter { k -> screen.fields.first { it.key == k }.role == FieldRole.Username })))
            .putParcelableArrayListExtra(FillAuthActivity.EXTRA_PASSWORD_IDS, ArrayList(screen.idsOf(keys.filter { k -> screen.fields.first { it.key == k }.role == FieldRole.Password })))
        // Mutable on purpose: the autofill framework adds its own extras. The intent is explicit and carries no secret.
        val sender = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT).intentSender
        val builder = Dataset.Builder()
        ids.forEach { DatasetFactory.setField(builder, it, null, presentation(null), inline) }
        return builder.setAuthentication(sender).build()
    }

    private fun datasetFor(item: DatasetPlan, screen: ParsedScreen, inline: InlinePresentation?): Dataset? {
        val entry = repo.get(item.entryId) ?: return null
        return try {
            DatasetFactory.build(entry, presentation(item.label), screen.idsOf(item.usernameKeys), screen.idsOf(item.passwordKeys), hex(item.entryId), inline)
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
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess()
        // Never a login of the app itself (its passphrase screens), whatever the fill step offered.
        if (structure.activityComponent?.packageName == packageName) return callback.onSuccess()
        val screen = StructureParser.parse(structure, app.originPolicy)
        // The account test is left out here: the offer was made for a login without an account, and the user chose to save it.
        if (!offersSave(screen.fields, screen.webDomain, hasAccount = false)) return callback.onSuccess()
        val candidate = SaveCandidate.of(screen.fields.mapIndexedNotNull { i, f -> screen.values[i]?.let { f.role to it } })
            ?: return callback.onSuccess()
        if (!session.isOpenNow()) {
            // Locked: the login waits in memory and the framework starts the unlock as the continuation of the save.
            // The pending intent is explicit and immutable and carries nothing.
            app.pendingSave.put(screen.webDomain, screen.packageName, candidate)
            val sender = PendingIntent.getActivity(this, 1, Intent(this, SaveAuthActivity::class.java), PendingIntent.FLAG_IMMUTABLE).intentSender
            return callback.onSuccess(sender)
        }
        try {
            val result = LoginSaver.save(repo, packageManager, screen.webDomain, screen.packageName, candidate)
            if (result is SaveResult.Saved) callback.onSuccess() else callback.onFailure(getString(R.string.pm_save_failed))
        } finally {
            candidate.wipe()
        }
    }

    private fun SessionManager.isOpenNow() = state is LockState.Unlocked

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String): ByteArray? = if (s.length == 32 && s.all { it in '0'..'9' || it in 'a'..'f' }) ByteArray(16) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() } else null
}

/** Builds a dataset that fills a username and password into the given fields. */
object DatasetFactory {
    /** The explicit "no filter": the system then hides the entry or chip as soon as the user types, and never matches what was typed against the value. */
    private val NO_FILTER: Pattern? = null

    fun build(entry: Entry, presentation: RemoteViews, usernameIds: List<AutofillId>, passwordIds: List<AutofillId>, datasetId: String, inline: InlinePresentation? = null): Dataset {
        val b = Dataset.Builder().setId(datasetId)
        usernameIds.forEach { setField(b, it, AutofillValue.forText(entry.username), presentation, inline) }
        passwordIds.forEach { setField(b, it, AutofillValue.forText(String(entry.password)), presentation, inline) }
        return b.build()
    }

    /** Every field of every dataset is added here: the check-static.sh guard fails the build on a value added without NO_FILTER. */
    fun setField(b: Dataset.Builder, id: AutofillId, value: AutofillValue?, presentation: RemoteViews, inline: InlinePresentation?) {
        if (inline == null) b.setValue(id, value, NO_FILTER, presentation) else b.setValue(id, value, NO_FILTER, presentation, inline)
    }
}
