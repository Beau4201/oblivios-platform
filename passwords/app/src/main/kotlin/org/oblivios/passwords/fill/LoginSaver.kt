// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.fill

import android.content.pm.PackageManager
import java.security.SecureRandom
import org.oblivios.passwords.core.Entry
import org.oblivios.passwords.data.SaveResult
import org.oblivios.passwords.data.VaultRepository

/**
 * Saves a login the user submitted on another app's form: a new entry for the site or app, or a new password for the
 * account with the same username. Needs the open vault. The caller wipes [candidate]; the copies made here are wiped here.
 */
object LoginSaver {
    /** Whether saving [candidate] would replace the password of an account that exists (the user is told before it happens). */
    fun replaces(repo: VaultRepository, webDomain: String?, packageName: String, candidate: SaveCandidate): Boolean =
        repo.accounts(webDomain, packageName)?.any { it.username == candidate.username } == true

    fun save(repo: VaultRepository, pm: PackageManager, webDomain: String?, packageName: String, candidate: SaveCandidate): SaveResult {
        val existing = repo.accounts(webDomain, packageName)?.firstOrNull { it.username == candidate.username }
        val now = System.currentTimeMillis()
        val entry = if (existing != null) {
            repo.get(existing.entryId)?.copy(password = candidate.password.copyOf(), editedMs = now)
        } else {
            Entry(
                id = ByteArray(16).also { SecureRandom().nextBytes(it) }, title = titleFor(pm, webDomain, packageName),
                domains = listOfNotNull(webDomain), packages = if (webDomain == null) listOf(packageName) else emptyList(),
                username = candidate.username, password = candidate.password.copyOf(), notes = "",
                createdMs = now, editedMs = now, lastFilledMs = null, history = emptyList(),
            )
        }
        return entry?.let { e -> repo.save(e).also { e.wipe() } } ?: SaveResult.Failed(0)
    }

    private fun titleFor(pm: PackageManager, webDomain: String?, packageName: String): String =
        webDomain?.removePrefix("www.")
            ?: runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
}
