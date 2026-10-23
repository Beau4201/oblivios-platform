// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.core

/**
 * What the user types into the website field of an entry, and what it becomes (review of 2026-10-23, P-09): a host the
 * index can match, or nothing. The core keeps whatever it is given, and a name like `bank.example?x=1` or `me@bank.example`
 * would be stored and then never match a page, with no word to the user.
 */
object SiteText {
    /** The longest name of DNS. */
    const val MAX_HOST = 253

    /**
     * The host of [text]: a leading `https://` or `http://`, a path, a query and a fragment are cut away and the rest is
     * lower-cased. Empty text gives "" (no site). Text that is not a host gives null: a space or other white space or
     * control character, a user name (`@`), a port or any other colon, a backslash, a dot at either end, an empty label, or
     * more than [MAX_HOST] characters.
     */
    fun hostOf(text: String): String? {
        var t = text.trim()
        for (scheme in listOf("https://", "http://")) {
            if (t.startsWith(scheme, ignoreCase = true)) {
                t = t.substring(scheme.length)
                break
            }
        }
        val cut = t.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (cut >= 0) t = t.substring(0, cut)
        t = t.lowercase()
        if (t.isEmpty()) return ""
        if (t.length > MAX_HOST) return null
        if (t.any { it.isWhitespace() || it.isISOControl() || it == '@' || it == ':' || it == '\\' }) return null
        if (t.startsWith(".") || t.endsWith(".") || t.contains("..")) return null
        return t
    }

    /**
     * The domains of an entry after an edit of the first one. The editor shows only the first site, so the others must
     * survive an edit that does not touch them: an entry imported with three addresses keeps three. [host] is the result of
     * [hostOf] (never null here).
     */
    fun domainsAfterEdit(existing: List<String>, host: String): List<String> {
        val rest = existing.drop(1)
        return when {
            host.isEmpty() -> rest
            existing.firstOrNull() == host -> existing
            else -> listOf(host) + rest.filter { it != host }
        }
    }
}
