// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

/** A login the user submitted while the vault was locked, waiting in memory for the unlock that lets it be saved. */
class PendingSave(val webDomain: String?, val packageName: String, val candidate: SaveCandidate, internal val deadline: Long) {
    override fun toString() = "PendingSave(${if (webDomain != null) "site" else "app"}, $candidate)"
}

/**
 * The one place a submitted login waits for the unlock. It lives in the memory of the app process only, never in an
 * intent, a file or a saved state, and it is wiped when it is taken, replaced, cleared or older than [ttlMs]. A process
 * that dies in between loses the login; the user types it again, which is better than a password on disk.
 */
class PendingSaveSlot(private val ttlMs: Long = 120_000L, private val now: () -> Long) {
    private var held: PendingSave? = null

    /** Keeps [candidate] (and wipes a login that was waiting before it); the slot now owns the characters. */
    @Synchronized
    fun put(webDomain: String?, packageName: String, candidate: SaveCandidate) {
        held?.candidate?.wipe()
        held = PendingSave(webDomain, packageName, candidate, now() + ttlMs)
    }

    /** Hands the login over, once; null when nothing waits or it has expired (an expired one is wiped). */
    @Synchronized
    fun take(): PendingSave? {
        val h = held ?: return null
        held = null
        if (now() > h.deadline) {
            h.candidate.wipe()
            return null
        }
        return h
    }

    @Synchronized
    fun clear() {
        held?.candidate?.wipe()
        held = null
    }
}
