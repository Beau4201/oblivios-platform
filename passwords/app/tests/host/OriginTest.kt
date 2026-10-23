// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.hosttest

import org.freeholdos.passwords.fill.Origin
import org.freeholdos.passwords.fill.OriginPolicy
import org.freeholdos.passwords.fill.PackageFacts
import org.freeholdos.passwords.fill.TrustStore

private class FakeFacts : PackageFacts {
    val system = HashSet<String>()
    val browsers = HashSet<String>()
    val digests = HashMap<String, String>()
    override fun isSystemApp(pkg: String) = pkg in system
    override fun isBrowser(pkg: String) = pkg in browsers
    override fun signerDigest(pkg: String) = digests[pkg]
}

private class FakeStore : TrustStore {
    val map = HashMap<String, String>()
    override fun digestOf(pkg: String) = map[pkg]
    override fun put(pkg: String, digest: String) { map[pkg] = digest }
    override fun remove(pkg: String) { map.remove(pkg) }
}

private const val VANADIUM = "app.vanadium.browser"

fun main() {
    vanadiumIsTrustedAsASystemAppOnly()
    aBrowserOfTheUserIsTrustedWhileItsSignerIsTheSame()
    aSiteIsBelievedFromATrustedBrowserOnly()
    aBrowserIsNeverAnApp()
    anAppIsAnAppWhateverItClaims()
    theBuiltInBrowserCannotBeChanged()
    Check.done("OriginTest")
}

private fun vanadiumIsTrustedAsASystemAppOnly() {
    val f = FakeFacts(); val p = OriginPolicy(f, FakeStore())
    Check.equal("the name is the one of the image", "app.vanadium.browser", OriginPolicy.VANADIUM)
    Check.that("not a system app: not trusted", !p.trusted(VANADIUM))
    f.system.add(VANADIUM)
    Check.that("a system app with that name is trusted", p.trusted(VANADIUM))
    f.system.add("app.other.browser")
    Check.that("another system app is not", !p.trusted("app.other.browser"))
    Check.that("a lookalike name is not", !p.trusted("app.vanadium.browser.evil") && !p.trusted("app.vanadium.browse"))
}

private fun aBrowserOfTheUserIsTrustedWhileItsSignerIsTheSame() {
    val f = FakeFacts(); val s = FakeStore(); val p = OriginPolicy(f, s)
    f.browsers.add("org.b.browser")
    Check.that("not trusted before it is added", !p.trusted("org.b.browser"))
    Check.that("no digest, no trust", !p.trust("org.b.browser") && s.map.isEmpty() && !p.trusted("org.b.browser"))
    f.digests["org.b.browser"] = "aa:bb"
    Check.that("adding stores the digest", p.trust("org.b.browser") && s.map["org.b.browser"] == "aa:bb")
    Check.that("and the browser is trusted", p.trusted("org.b.browser"))
    f.digests["org.b.browser"] = "cc"
    Check.that("another signer: no longer trusted", !p.trusted("org.b.browser"))
    f.digests.remove("org.b.browser")
    Check.that("not visible any more: not trusted", !p.trusted("org.b.browser"))
    f.digests["org.b.browser"] = "aa:bb"
    Check.that("the old signer again: trusted again", p.trusted("org.b.browser"))
    p.untrust("org.b.browser")
    Check.that("untrusting removes it", !p.trusted("org.b.browser") && s.map.isEmpty())
    Check.that("trusting one browser does not trust another", !p.trusted("org.c.browser"))
}

private fun aSiteIsBelievedFromATrustedBrowserOnly() {
    val f = FakeFacts(); val p = OriginPolicy(f, FakeStore())
    f.system.add(VANADIUM); f.browsers.add(VANADIUM)
    Check.equal("a trusted browser's site", Origin.Site("paypal.com"), p.resolve(VANADIUM, "paypal.com"))
    Check.equal("a trusted browser with no site gets nothing", Origin.None, p.resolve(VANADIUM, null))
    f.browsers.add("org.b.browser"); f.digests["org.b.browser"] = "aa"
    Check.equal("an untrusted browser's site is not believed", Origin.None, p.resolve("org.b.browser", "paypal.com"))
    p.trust("org.b.browser")
    Check.equal("a trusted one is", Origin.Site("paypal.com"), p.resolve("org.b.browser", "paypal.com"))
}

private fun aBrowserIsNeverAnApp() {
    val f = FakeFacts(); val p = OriginPolicy(f, FakeStore())
    f.browsers.add("org.b.browser"); f.system.add(VANADIUM); f.browsers.add(VANADIUM)
    Check.equal("an untrusted browser with no site: nothing, not its package", Origin.None, p.resolve("org.b.browser", null))
    Check.equal("a trusted browser with no site: nothing, not its package", Origin.None, p.resolve(VANADIUM, null))
    Check.that("a browser is never an App", listOf("org.b.browser", VANADIUM).all { b -> listOf(null, "x.example").all { d -> p.resolve(b, d) !is Origin.App } })
    // A trusted package counts as a browser even if its intent filters were not found.
    val g = FakeFacts(); val q = OriginPolicy(g, FakeStore())
    g.digests["org.hidden.browser"] = "dd"; q.trust("org.hidden.browser")
    Check.equal("a trusted package with no browser filter is still no app", Origin.None, q.resolve("org.hidden.browser", null))
}

private fun anAppIsAnAppWhateverItClaims() {
    val f = FakeFacts(); val p = OriginPolicy(f, FakeStore())
    Check.equal("an app with no claim", Origin.App("org.bank.app"), p.resolve("org.bank.app", null))
    Check.equal("an app that claims a site is still the app", Origin.App("org.evil.app"), p.resolve("org.evil.app", "paypal.com"))
    f.system.add("org.sys.app")
    Check.equal("a system app that claims a site is still the app", Origin.App("org.sys.app"), p.resolve("org.sys.app", "paypal.com"))
}

private fun theBuiltInBrowserCannotBeChanged() {
    val f = FakeFacts(); val s = FakeStore(); val p = OriginPolicy(f, s)
    f.digests[VANADIUM] = "zz"
    Check.that("trust() of the built-in browser stores nothing", !p.trust(VANADIUM) && s.map.isEmpty())
    f.system.add(VANADIUM)
    p.untrust(VANADIUM)
    Check.that("untrust() of it changes nothing", p.trusted(VANADIUM))
    s.map["org.stale.browser"] = "x"
    p.untrust(VANADIUM)
    Check.that("and removes nothing else", s.map.containsKey("org.stale.browser"))
}
