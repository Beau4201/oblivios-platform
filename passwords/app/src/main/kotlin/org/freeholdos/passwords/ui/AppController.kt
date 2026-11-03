// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.freeholdos.passwords.PasswordsApplication
import org.freeholdos.passwords.R
import org.freeholdos.passwords.SecureActivity
import org.freeholdos.passwords.core.Checkup
import org.freeholdos.passwords.core.ClipGuard
import org.freeholdos.passwords.core.Code
import org.freeholdos.passwords.core.Entry
import org.freeholdos.passwords.core.SiteText
import org.freeholdos.passwords.core.Summary
import org.freeholdos.passwords.core.VaultCore
import org.freeholdos.passwords.core.Reply
import org.freeholdos.passwords.data.SaveResult
import org.freeholdos.passwords.fill.OriginPolicy
import org.freeholdos.passwords.fill.ChipSettings
import org.freeholdos.passwords.fill.PassphraseDialog
import org.freeholdos.passwords.lock.BiometricOutcome
import org.freeholdos.passwords.lock.LockState
import org.freeholdos.passwords.lock.PassphraseOutcome
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

sealed interface Route {
    data object Passwords : Route
    data object Search : Route
    data object Checkup : Route
    data object Settings : Route
    data object Export : Route
    data object Create : Route
    data class Detail(val id: String) : Route
    data class Edit(val id: String?) : Route
}

fun hexOf(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
fun unhexId(s: String): ByteArray = ByteArray(16) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

/**
 * All state of the screens and the actions they trigger. No secret is held here beyond the entry being shown, which
 * is wiped when the screen goes; nothing is saved to the instance state (the activity handles its own configuration
 * changes and the screens use no rememberSaveable).
 */
/** A browser in the list of the settings. */
class BrowserRow(val pkg: String, val label: String, val trusted: Boolean)

class AppController(val activity: SecureActivity) {
    private val app = activity.applicationContext as PasswordsApplication
    private val session get() = app.session
    private val repo get() = app.repository
    private val main = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences(ChipSettings.PREFS, Context.MODE_PRIVATE)
    private val chips = ChipSettings(prefs)

    var lock by mutableStateOf<LockState>(session.state)
        private set
    var hasVault by mutableStateOf(session.hasVault())
        private set
    var route by mutableStateOf<Route>(Route.Passwords)
        private set
    private val stack = ArrayList<Route>()
    var entries by mutableStateOf<List<Summary>>(emptyList())
        private set
    var checkup by mutableStateOf<Checkup?>(null)
        private set
    var toast by mutableStateOf<String?>(null)
        private set
    var lockedError by mutableStateOf<String?>(null)

    /** First start with "Import from a file": a vault is made first, then the file is picked. */
    var importAfterCreate = false
    private var autoPrompted = false

    /** The entry on the detail or edit screen, with its password; wiped when the screen goes. */
    var current by mutableStateOf<Entry?>(null)
        private set
    var revealed by mutableStateOf(false)
        private set

    /** Set by the activity: the system file pickers. */
    var pickExportTarget: ((String) -> Unit)? = null
    var pickImportSource: (() -> Unit)? = null
    private var pendingExport: ByteArray? = null

    private val listener: (LockState) -> Unit = { s ->
        lock = s
        hasVault = session.hasVault()
        if (s is LockState.Unlocked) refresh() else clearSecrets()
        if (s !is LockState.Unlocked) autoPrompted = false
    }

    fun start() {
        session.observe(listener)
        lock = session.state
        hasVault = session.hasVault()
        if (lock is LockState.Unlocked) refresh()
    }

    fun stop() {
        session.stopObserving(listener)
        clearSecrets()
    }

    // --- data ---

    fun refresh() {
        entries = repo.list().orEmpty().sortedBy { it.title.lowercase() }
        checkup = repo.checkup()
    }

    fun attentionCount(): Int = checkup?.let { c -> (c.reused.flatten().map { hexOf(it) } + c.weak.map { hexOf(it.first) }).toSet().size } ?: 0

    fun needsAttention(id: ByteArray): Boolean = checkup?.let { c -> c.reused.any { g -> g.any { it.contentEquals(id) } } || c.weak.any { it.first.contentEquals(id) } } ?: false

    fun search(query: String): List<Summary>? = repo.search(query)?.sortedBy { it.title.lowercase() }

    fun lastFilledLabel(ms: Long?): String? = ms?.let { android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString() }

    // --- navigation ---

    fun go(r: Route) {
        if (r != route) stack.add(route)
        route = r
        if (r !is Route.Detail && r !is Route.Edit) wipeCurrent()
    }

    /** A top-level destination from the navigation bar: the stack starts over. */
    fun top(r: Route) {
        stack.clear()
        route = r
        wipeCurrent()
        if (r == Route.Passwords || r == Route.Checkup) refresh()
    }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        route = stack.removeAt(stack.size - 1)
        if (route !is Route.Detail && route !is Route.Edit) wipeCurrent()
        return true
    }

    fun openEntry(id: String) {
        wipeCurrent()
        current = repo.get(unhexId(id))
        revealed = false
        go(Route.Detail(id))
    }

    fun newEntry() {
        wipeCurrent()
        current = null
        go(Route.Edit(null))
    }

    fun editEntry() {
        (route as? Route.Detail)?.let { go(Route.Edit(it.id)) }
    }

    private fun wipeCurrent() {
        current?.wipe()
        current = null
        revealed = false
    }

    private fun clearSecrets() {
        wipeCurrent()
        entries = emptyList()
        checkup = null
        route = Route.Passwords
        stack.clear()
    }

    // --- toast ---

    fun say(text: String) {
        toast = text
        main.postDelayed({ if (toast == text) toast = null }, 2500)
    }

    // --- unlocking ---

    fun hasBiometrics(): Boolean = session.biometricsEnabled()

    /** True when a strong biometric is enrolled, which the biometric Keystore key needs before it can be made. */
    fun biometricsAvailable(): Boolean =
        activity.getSystemService(BiometricManager::class.java)?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun unlockWithBiometrics() {
        session.biometricUnlock(activity, forFill = false) { outcome ->
            when (outcome) {
                BiometricOutcome.Opened -> lockedError = null
                BiometricOutcome.Invalidated, BiometricOutcome.UsePassphrase, BiometricOutcome.NoWrapper -> lock = session.state
                BiometricOutcome.Cancelled -> Unit
                is BiometricOutcome.Failed -> lockedError = activity.getString(R.string.pm_save_failed)
            }
        }
    }

    /** Called when the locked screen shows: the biometric prompt opens by itself once per lock. */
    fun autoPromptIfNeeded() {
        if (autoPrompted || lock != LockState.Locked || !hasBiometrics()) return
        autoPrompted = true
        unlockWithBiometrics()
    }

    fun unlockWithPassphrase(passphrase: CharArray): Boolean {
        val r = session.unlockWithPassphrase(passphrase)
        passphrase.fill('\u0000')
        lockedError = when (r) {
            PassphraseOutcome.Opened -> null
            is PassphraseOutcome.Wrong -> activity.getString(R.string.pm_passphrase_wrong)
            is PassphraseOutcome.Throttled -> activity.resources.getQuantityString(R.plurals.pm_passphrase_wait, secs(r.waitMs), secs(r.waitMs))
            PassphraseOutcome.SecretLost -> null
            is PassphraseOutcome.Failed -> activity.getString(R.string.pm_save_failed)
        }
        return r == PassphraseOutcome.Opened
    }

    private fun secs(ms: Long) = ((ms + 999) / 1000).toInt().coerceAtLeast(1)

    // --- creating the vault ---

    fun createVault(passphrase: CharArray, withBiometrics: Boolean): Boolean {
        val code = session.createVault(passphrase)
        passphrase.fill('\u0000')
        hasVault = session.hasVault()
        if (code != Code.OK) {
            say(activity.getString(R.string.pm_save_failed))
            return false
        }
        if (withBiometrics) session.enableBiometrics(activity) { }
        refresh()
        return true
    }

    fun deleteUnopenableVault() {
        session.deleteUnopenableVault()
        hasVault = session.hasVault()
    }

    // --- one password ---

    /** The eye: a fresh biometric (or the passphrase) before the password is shown. */
    fun reveal() {
        if (revealed) {
            revealed = false
            return
        }
        confirmUser { ok -> if (ok) revealed = true }
    }

    /** Runs [then] after a fresh strong biometric (or the passphrase): for anything that would show a stored password. */
    fun confirmThen(then: () -> Unit) = confirmUser { ok -> if (ok) then() }

    /**
     * A fresh strong biometric, or the passphrase when biometrics are off. [messageRes] is what the passphrase prompt says;
     * the default is about a password, the actions that take everything out or remove something say [R.string.pm_confirm_action].
     */
    private fun confirmUser(messageRes: Int = R.string.pm_confirm_reveal, done: (Boolean) -> Unit) {
        if (hasBiometrics()) {
            session.confirmWithBiometrics(activity, done)
        } else {
            PassphraseDialog.show(activity, forFill = false, error = null, onCancel = { done(false) }, titleRes = R.string.pm_confirm_passphrase_title, messageRes = messageRes) { chars ->
                val r = session.verifyPassphrase(chars)
                chars.fill('\u0000')
                done(r == PassphraseOutcome.Opened)
            }
        }
    }

    /**
     * Copies with the sensitive flag (the system hides it from previews) and clears it after 30 seconds. The usual use is
     * copy, switch to the other app, paste: 30 seconds later this app is in the background and may not read the clipboard any
     * more (Android 10 and later), so the clip is not compared with the text. A change of the clipboard after our own is
     * somebody else's copy and is left alone (P-06); the platform reports our own copy twice, so [ClipGuard] counts only a
     * change a second or more after it. Whether a clear from the background works on this platform is a device check
     * (PWM-014).
     */
    fun copy(label: String, text: CharSequence, message: String) {
        val cm = activity.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        val guard = ClipGuard(SystemClock.elapsedRealtime())
        val listener = ClipboardManager.OnPrimaryClipChangedListener { guard.onChanged(SystemClock.elapsedRealtime()) }
        cm.addPrimaryClipChangedListener(listener)
        cm.setPrimaryClip(clip)
        say(message)
        main.postDelayed({
            cm.removePrimaryClipChangedListener(listener)
            if (!guard.changedByOthers) cm.clearPrimaryClip()
        }, 30_000)
    }

    fun copyPassword(e: Entry) {
        confirmUserIfHidden { copy(activity.getString(R.string.pm_field_password), String(e.password), activity.getString(R.string.pm_copied_password)) }
    }

    private fun confirmUserIfHidden(then: () -> Unit) {
        if (revealed) then() else confirmUser { ok -> if (ok) then() }
    }

    fun openSite(domain: String) {
        // A stored name may be anything the page or an import gave: only a plain host is opened (`me@evil.example` is not one).
        val host = SiteText.hostOf(domain)?.takeIf { it.isNotEmpty() && it == domain.trim().lowercase() } ?: return
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://$host"))) }
    }

    fun save(entry: Entry): Boolean {
        val id = entry.id.copyOf()
        val r = repo.save(entry)
        entry.wipe()
        return when (r) {
            SaveResult.Saved -> {
                refresh()
                // The detail screen under the edit screen must show what was saved, not what was there before.
                if (current?.id?.contentEquals(id) == true) {
                    current?.wipe()
                    current = repo.get(id)
                    revealed = false
                }
                true
            }
            SaveResult.Locked -> false
            is SaveResult.Failed -> {
                say(activity.getString(if (r.code == Code.TOO_LARGE) R.string.pm_edit_too_long else R.string.pm_save_failed))
                false
            }
        }
    }

    /** Deleting asks for a fresh biometric (or the passphrase), like showing the password does: an unlocked phone is not enough (P-05). */
    fun delete(id: String) = confirmUser(R.string.pm_confirm_action) { ok ->
        if (ok) {
            repo.delete(unhexId(id))
            refresh()
            wipeCurrent()
            top(Route.Passwords)
        }
    }

    fun generatePassword(): CharArray {
        val bytes = Reply.of(VaultCore.generate(20, lower = true, upper = true, digits = true, symbols = true))
        val raw = if (bytes.ok) bytes.payload() else ByteArray(0)
        bytes.wipe()
        val chars = StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(raw)).let { cb -> CharArray(cb.remaining()).also { cb.get(it) } }
        raw.fill(0)
        return chars
    }

    fun strengthOf(password: CharArray): Int {
        val bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password)).let { b -> ByteArray(b.remaining()).also { b.get(it) } }
        val level = VaultCore.strength(bytes)
        bytes.fill(0)
        return level
    }

    fun newId(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    // --- per-login keyboard chip switch (kept in the app's private preferences, keyed by entry id) ---

    fun chipOffered(id: String): Boolean = chips.isOffered(id)

    fun setChipOffered(id: String, offered: Boolean) = chips.setOffered(id, offered)

    var suggestionsOn: Boolean
        get() = chips.suggestionsOn
        set(v) { chips.suggestionsOn = v }

    // --- settings, export and import ---

    // --- trusted browsers (P-01): which package may say which website a page is ---

    /** The browsers of the phone that can be added to the trusted ones (Vanadium is trusted without being asked). */
    fun browserRows(): List<BrowserRow> = app.packageFacts.browsers()
        .filter { it !in OriginPolicy.BUILT_IN && it != activity.packageName }
        .map { BrowserRow(it, app.packageFacts.label(it), app.originPolicy.trusted(it)) }
        .sortedWith(compareBy({ it.label.lowercase() }, { it.pkg }))

    /** Trusting a browser asks for a fresh biometric (or the passphrase); not trusting it does not. [done] runs either way. */
    fun setBrowserTrusted(pkg: String, on: Boolean, done: () -> Unit) {
        if (!on) {
            app.originPolicy.untrust(pkg)
            done()
            return
        }
        confirmUser(R.string.pm_confirm_action) { ok ->
            if (ok && !app.originPolicy.trust(pkg)) say(activity.getString(R.string.pm_settings_browsers_failed))
            done()
        }
    }

    /**
     * Switches biometric unlock on or off; [done] gets what the switch is now, which is not what was asked when the prompt
     * is cancelled or fails (the switch used to show the asked state anyway, P-17). Turning it off asks too (P-05).
     */
    fun toggleBiometrics(on: Boolean, done: (Boolean) -> Unit) {
        if (on) {
            // The Keystore refuses a key that needs a biometric when none is enrolled; ask first instead of crashing on it.
            if (!biometricsAvailable()) {
                say(activity.getString(R.string.pm_settings_biometrics_none))
                return done(false)
            }
            session.enableBiometrics(activity) { ok -> lock = session.state; done(ok) }
        } else {
            confirmUser(R.string.pm_confirm_action) { ok ->
                if (ok) session.disableBiometrics()
                done(!ok)
            }
        }
    }

    fun autofillServiceActive(): Boolean {
        val s = android.provider.Settings.Secure.getString(activity.contentResolver, "autofill_service")
        return s != null && s.startsWith(activity.packageName)
    }

    fun requestAutofillService() {
        runCatching {
            activity.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${activity.packageName}")))
        }
    }

    var lastExportMs: Long
        get() = prefs.getLong("last_export_ms", 0L)
        private set(v) { prefs.edit().putLong("last_export_ms", v).apply() }

    /** Entries made or changed since the last export. */
    fun changesSinceExport(): Int = entries.count { it.editedMs > lastExportMs }

    /**
     * Everything leaves the phone with this: a fresh biometric (or the passphrase) first, as for showing one password
     * (P-05). The characters of [passphrase] are wiped on every way out.
     */
    fun startExport(passphrase: CharArray) = confirmUser(R.string.pm_confirm_action) { ok ->
        if (!ok) {
            passphrase.fill('\u0000')
            return@confirmUser
        }
        val bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(passphrase)).let { b -> ByteArray(b.remaining()).also { b.get(it) } }
        passphrase.fill('\u0000')
        val file = repo.export(bytes)
        bytes.fill(0)
        if (file == null) {
            say(activity.getString(R.string.pm_export_failed))
            return@confirmUser
        }
        pendingExport = file
        pickExportTarget?.invoke("freeholdos-passwords.fhpx")
    }

    /** The picker answered: write the file to the chosen place. */
    fun exportTargetChosen(uri: Uri?) {
        val data = pendingExport
        pendingExport = null
        if (uri == null || data == null) return
        val ok = runCatching { activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(data) } != null }.getOrDefault(false)
        if (ok) {
            lastExportMs = System.currentTimeMillis()
            say(activity.getString(R.string.pm_export_done))
            back()
        } else {
            say(activity.getString(R.string.pm_export_failed))
        }
    }

    private var pendingImport: ByteArray? = null

    fun importSourceChosen(uri: Uri?) {
        if (uri == null) return
        val data = runCatching { activity.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_IMPORT) } }.getOrNull()
        if (data == null) {
            say(activity.getString(R.string.pm_import_failed))
            return
        }
        if (!isExportFile(data)) {
            importCsv(data)
            return
        }
        pendingImport = data
        PassphraseDialog.show(activity, forFill = false, error = null, onCancel = { pendingImport = null }, titleRes = R.string.pm_settings_import, messageRes = R.string.pm_import_passphrase) { chars ->
            val bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars)).let { b -> ByteArray(b.remaining()).also { b.get(it) } }
            chars.fill('\u0000')
            val added = repo.importFile(pendingImport ?: ByteArray(0), bytes)
            bytes.fill(0)
            pendingImport = null
            if (added >= 0) {
                refresh()
                say(activity.resources.getQuantityString(R.plurals.pm_import_done, added, added))
            } else {
                say(activity.getString(R.string.pm_import_failed))
            }
        }
    }

    /** The plain CSV of another manager: asks once (the file holds every password in the clear), merges, wipes the file. */
    private fun importCsv(data: ByteArray) = confirmUser(R.string.pm_confirm_action) { who ->
        if (!who) {
            data.fill(0)
            return@confirmUser
        }
        PassphraseDialog.confirm(
            activity, R.string.pm_import_csv_title, R.string.pm_import_csv_body, R.string.pm_import_csv_yes,
            onNo = { data.fill(0) },
        ) {
            val result = repo.importCsv(data, System.currentTimeMillis())
            data.fill(0)
            if (result == null) {
                say(activity.getString(R.string.pm_import_csv_failed))
            } else {
                refresh()
                say(activity.getString(R.string.pm_import_csv_done, result.added, result.duplicates, result.skipped))
            }
        }
    }

    private companion object {
        const val MAX_IMPORT = 64 * 1024 * 1024 + 64

        /** The export file of this app starts with the magic `FHPX`; anything else is taken for a CSV. */
        fun isExportFile(data: ByteArray) = data.size >= 4 && data[0] == 'F'.code.toByte() && data[1] == 'H'.code.toByte() && data[2] == 'P'.code.toByte() && data[3] == 'X'.code.toByte()
    }
}
