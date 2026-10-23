// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.fill

/**
 * Whose login this is: a site, an app, or nothing the vault may answer for. The autofill framework passes on whatever
 * `webDomain` an app writes into its view structure and checks nothing, so any app could ask for the passwords of any site
 * (review of 2026-10-23, P-01). The vault believes a site only from a browser that the user trusts, and answers a browser
 * only for a site (P-04: a browser is never an app of its own).
 */
sealed interface Origin {
    /** A site, reported by a trusted browser. */
    data class Site(val domain: String) : Origin

    /** An app, by its package name: what is left for every app that is not a browser. */
    data class App(val packageName: String) : Origin

    /** Nothing is offered and nothing is saved. */
    data object None : Origin
}

/** What the phone says about a package. Android: the package manager; in the host checks: a fake. */
interface PackageFacts {
    /** Part of the system image (or an update of a system app, which must carry the same signature). */
    fun isSystemApp(pkg: String): Boolean

    /** Opens `https` links from other apps, as a browser does (also an app that only registers the bare scheme). */
    fun isBrowser(pkg: String): Boolean

    /** A digest of the certificates that signed the installed package; null when it is not installed or not visible. */
    fun signerDigest(pkg: String): String?
}

/** The browsers the user chose to trust, with the signer digest of each at that moment. Kept in the private storage of the app. */
interface TrustStore {
    fun digestOf(pkg: String): String?
    fun put(pkg: String, digest: String)
    fun remove(pkg: String)
}

/**
 * The policy. Trusted are (1) Vanadium, the browser of the image, by its package name, only while it is a system app (a
 * package name is unique on the phone and an update of a system app needs the system signature, so nobody else can be it),
 * and (2) a browser the user added in the settings, only while its signer digest is the one stored then.
 */
class OriginPolicy(private val facts: PackageFacts, private val store: TrustStore) {
    fun trusted(pkg: String): Boolean {
        if (pkg in BUILT_IN && facts.isSystemApp(pkg)) return true
        val stored = store.digestOf(pkg) ?: return false
        return stored == facts.signerDigest(pkg)
    }

    /**
     * The origin of a screen of [pkg] that claims the site [claimedDomain] (null: no claim). A trusted browser is believed;
     * every other browser gets nothing; any other app is an app, and its claim is ignored.
     */
    fun resolve(pkg: String, claimedDomain: String?): Origin = when {
        trusted(pkg) -> if (claimedDomain != null) Origin.Site(claimedDomain) else Origin.None
        facts.isBrowser(pkg) -> Origin.None
        else -> Origin.App(pkg)
    }

    /** Trusts [pkg] from now on, bound to its signer digest. False when the phone gives no digest (not installed, not visible). */
    fun trust(pkg: String): Boolean {
        if (pkg in BUILT_IN) return false
        val digest = facts.signerDigest(pkg) ?: return false
        store.put(pkg, digest)
        return true
    }

    fun untrust(pkg: String) {
        if (pkg !in BUILT_IN) store.remove(pkg)
    }

    companion object {
        /** Vanadium of the image: `app.vanadium.browser` [measured: aapt2 dump badging of external/vanadium/prebuilt/arm64/TrichromeChrome.apk]. */
        const val VANADIUM = "app.vanadium.browser"
        val BUILT_IN: Set<String> = setOf(VANADIUM)
    }
}
