// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.data

import android.content.Context
import java.io.File
import org.freeholdos.passwords.core.Checkup
import org.freeholdos.passwords.core.Code
import org.freeholdos.passwords.core.Entry
import org.freeholdos.passwords.core.Reply
import org.freeholdos.passwords.core.Summary
import org.freeholdos.passwords.core.VaultCore
import org.freeholdos.passwords.core.Wire
import org.freeholdos.passwords.fill.Account
import org.freeholdos.passwords.keys.KeyGateway
import org.freeholdos.passwords.lock.LockState
import org.freeholdos.passwords.lock.SessionManager
import org.freeholdos.passwords.lock.VaultFiles

/** What a change of the vault came to. */
sealed interface SaveResult {
    data object Saved : SaveResult
    data object Locked : SaveResult
    data class Failed(val code: Int) : SaveResult
}

/**
 * The vault as the screens and the services use it: every call goes through the open session, every change writes the
 * new vault file atomically and rebuilds the domain index. Calls on a locked vault answer null or [SaveResult.Locked].
 */
class VaultRepository(
    private val files: VaultFiles,
    private val session: SessionManager,
    private val index: DomainIndexStore,
) {
    private val handle: Long? get() = session.handle

    fun list(): List<Summary>? = handle?.let { h -> Wire.decodeSummaries(payloadOf(VaultCore.list(h)) ?: return null) }

    fun search(query: String): List<Summary>? = handle?.let { h -> Wire.decodeSummaries(payloadOf(VaultCore.search(h, query)) ?: return null) }

    fun get(id: ByteArray): Entry? = handle?.let { h -> Wire.decode(payloadOf(VaultCore.get(h, id)) ?: return null) }

    /** The accounts of a site (a host) or, without a host, of an app (a package name). */
    fun accounts(webDomain: String?, packageName: String): List<Account>? {
        val h = handle ?: return null
        val raw = if (webDomain != null) VaultCore.matches(h, webDomain, 0) else VaultCore.matches(h, packageName, 1)
        return Wire.decodeSummaries(payloadOf(raw) ?: return null)?.map { Account(it.id, it.username) }
    }

    fun checkup(): Checkup? = handle?.let { h -> Wire.decodeCheckup(payloadOf(VaultCore.checkup(h)) ?: return null) }

    /** Adds the entry or replaces the editable fields of the entry with that id. */
    fun save(entry: Entry): SaveResult {
        val h = handle ?: return SaveResult.Locked
        val wire = Wire.encode(entry)
        val reply = Reply.of(VaultCore.addOrEdit(h, wire))
        wire.fill(0)
        return persist(h, reply)
    }

    fun delete(id: ByteArray): SaveResult {
        val h = handle ?: return SaveResult.Locked
        return persist(h, Reply.of(VaultCore.delete(h, id)))
    }

    fun markFilled(id: ByteArray, nowMs: Long): SaveResult {
        val h = handle ?: return SaveResult.Locked
        return persist(h, Reply.of(VaultCore.markFilled(h, id, nowMs)))
    }

    /** Merges an export file into the vault; returns the number of entries added, or a negative [Code]. */
    fun importFile(blob: ByteArray, exportPassphrase: ByteArray): Int {
        val h = handle ?: return -Code.DEAD_HANDLE
        val reply = Reply.of(VaultCore.importEntries(h, blob, exportPassphrase))
        if (!reply.ok) return -reply.status
        val p = reply.payload()
        if (p.size < 5) return -Code.INTERNAL
        val added = (p[0].toInt() and 0xff) or ((p[1].toInt() and 0xff) shl 8) or ((p[2].toInt() and 0xff) shl 16) or ((p[3].toInt() and 0xff) shl 24)
        val file = p.copyOfRange(4, p.size)
        if (!files.writeVault(file)) return -Code.IO
        index.rebuild(h)
        return added
    }

    /** The encrypted export file, or a negative [Code]. The caller writes it where the user chose. */
    fun export(exportPassphrase: ByteArray): ByteArray? = handle?.let { h -> payloadOf(VaultCore.export(h, exportPassphrase)) }

    /** The core's answer is the whole new vault file: write it, then rebuild the index from the open session. */
    private fun persist(h: Long, reply: Reply): SaveResult {
        if (!reply.ok) return SaveResult.Failed(reply.status)
        if (!files.writeVault(reply.payload())) return SaveResult.Failed(Code.IO)
        index.rebuild(h)
        return SaveResult.Saved
    }

    private fun payloadOf(raw: ByteArray?): ByteArray? = Reply.of(raw).let { if (it.ok) it.payload() else null }
}

/**
 * The domain index (spec 4.1) in credential-encrypted storage: keyed hashes of the registrable domains and packages that
 * have an account, readable by the autofill service while the vault is locked. It is rebuilt from the open vault after
 * every unlock and every change, so a damaged index repairs itself; a missing one simply answers "no account".
 */
class DomainIndexStore(dir: File, private val gateway: KeyGateway) {
    private val file = File(dir, "domains.idx")

    @Volatile
    private var cached: ByteArray? = null

    fun rebuild(handle: Long) {
        val reply = Reply.of(VaultCore.indexBuild(handle, indexKey()))
        if (!reply.ok) return
        val bytes = reply.payload()
        cached = bytes
        VaultCore.writeFileAtomic(file.path, bytes)
    }

    /** Does an account exist for this site or, without a host, this app? Answers without the vault key. */
    fun hasAccount(webDomain: String?, packageName: String): Boolean {
        val bytes = cached ?: Reply.of(VaultCore.readFileBounded(file.path)).let { if (it.ok) it.payload() else null }?.also { cached = it } ?: return false
        val key = indexKey()
        return if (webDomain != null) VaultCore.indexContains(bytes, key, webDomain, 0) else VaultCore.indexContains(bytes, key, packageName, 1)
    }

    /**
     * The key stays in memory for the life of the process: reading it from the secure hardware takes tens of milliseconds
     * and the autofill service asks on every focus. Code in this process can use the Keystore key anyway (residual 2 of
     * spec 4.1), so the copy adds no reach.
     */
    @Volatile
    private var key: ByteArray? = null

    private fun indexKey(): ByteArray = key ?: gateway.indexKey().also { key = it }
}

/** The lock state of the process as the services see it. */
val SessionManager.isOpen: Boolean get() = state is LockState.Unlocked

fun Context.vaultRepository(): VaultRepository = (applicationContext as org.freeholdos.passwords.PasswordsApplication).repository
