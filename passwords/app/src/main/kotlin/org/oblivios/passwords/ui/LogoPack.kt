// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.ui

/**
 * The bundled logo pack (spec 4.2, passwords/logos/build-pack.py): registrable domain to icon, icon to path data in a 24 by
 * 24 box. A host matches the longest suffix that is in the pack, label by label, so `login.github.com` finds `github.com`
 * and `github.com.evil.example` finds nothing. A bare suffix (`co.uk`, `com`) is never looked up, a name with a character
 * outside `a-z`, `0-9`, `-` and `.` is refused (a look-alike in another script has none), and so is an IP literal.
 */
class LogoPack private constructor(private val domains: Map<String, String>, private val icons: Map<String, String>) {
    fun pathFor(host: String): String? {
        var h = host.lowercase()
        if (h.startsWith("[")) return null
        when (h.count { it == ':' }) {
            0 -> Unit
            1 -> h = h.substringBefore(':')
            else -> return null
        }
        h = h.removeSuffix(".")
        // A stored domain may be up to 64 KiB and comes from a page or an import; the walk below copies the labels once per
        // label, so a name that no DNS name can be is refused before it (review of 2026-10-23, P-10).
        if (h.length > MAX_HOST) return null
        if (h.isEmpty() || !h.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '.' }) return null
        val labels = h.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() }) return null
        for (i in 0..labels.size - 2) {
            val slug = domains[labels.drop(i).joinToString(".")] ?: continue
            return icons[slug]
        }
        return null
    }

    companion object {
        /** The longest name of DNS, without the dot at the end. */
        const val MAX_HOST = 253

        val EMPTY = LogoPack(emptyMap(), emptyMap())

        /** `domains.tsv` (domain, slug) and `icons.tsv` (slug, path, title); rows with another shape are ignored. */
        fun parse(domainsTsv: String, iconsTsv: String): LogoPack {
            val domains = HashMap<String, String>()
            for (line in domainsTsv.lineSequence()) {
                val c = line.split('\t')
                if (c.size == 2 && c[0].isNotEmpty() && c[1].isNotEmpty() && !line.startsWith("#")) domains[c[0].lowercase()] = c[1]
            }
            val icons = HashMap<String, String>()
            for (line in iconsTsv.lineSequence()) {
                val c = line.split('\t')
                if ((c.size == 2 || c.size == 3) && c[0].isNotEmpty() && c[1].isNotEmpty()) icons[c[0]] = c[1]
            }
            return LogoPack(domains, icons)
        }
    }
}
