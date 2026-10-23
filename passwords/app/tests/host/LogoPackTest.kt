// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.hosttest

import org.freeholdos.passwords.ui.LogoPack

private val DOMAINS = "com\tgithub\nuk\tbbc\ngithub.com\tgithub\ncodeberg.org\tcodeberg\nbbc.co.uk\tbbc\nbroken.example\tmissing\n"
private val ICONS = "github\tM0 0h24v24H0z\tGitHub\ncodeberg\tM1 1h2v2H1z\tCodeberg\nbbc\tM2 2h2v2H2z\tBBC\n"

fun main() {
    val pack = LogoPack.parse(DOMAINS, ICONS)
    exactAndSubdomains(pack)
    lookAlikesAndOddInput(pack)
    parsing()
    Check.done("LogoPackTest")
}

private fun exactAndSubdomains(p: LogoPack) {
    Check.equal("an exact domain", "M0 0h24v24H0z", p.pathFor("github.com"))
    Check.equal("a subdomain", "M0 0h24v24H0z", p.pathFor("login.github.com"))
    Check.equal("a deep subdomain", "M0 0h24v24H0z", p.pathFor("a.b.c.github.com"))
    Check.equal("a two-label suffix", "M2 2h2v2H2z", p.pathFor("www.bbc.co.uk"))
    Check.equal("case is ignored", "M1 1h2v2H1z", p.pathFor("WWW.Codeberg.ORG"))
    Check.equal("a trailing dot and a port are dropped", "M0 0h24v24H0z", p.pathFor("github.com.:8443"))
}

private fun lookAlikesAndOddInput(p: LogoPack) {
    for (h in listOf("github.com.evil.example", "evilgithub.com", "githubb.com", "co.uk", "com", "uk", "", ".", "gіthub.com", "github.co", "192.0.2.1", "[::1]", "github.com/x", "git hub.com", "login..github.com", ".github.com", "github..com")) {
        Check.that("no logo for '$h'", p.pathFor(h) == null)
    }
    // P-10: a name no DNS name can be is refused at once (a stored domain may be 64 KiB: 32 000 labels used to mean ~10^9 copied characters).
    val longName = (1..300).joinToString(".") { "a" } + ".github.com"
    Check.that("a name longer than 253 characters finds nothing", longName.length > 253 && p.pathFor(longName) == null)
    val limit = ("a".repeat(63) + ".").repeat(3) + "a".repeat(50) + ".github.com"
    Check.that("a name of exactly 253 characters is looked up", limit.length == 253 && p.pathFor(limit) == "M0 0h24v24H0z")
    val edge = ("a".repeat(61) + ".").repeat(3) + "github.com"
    Check.that("a name of 253 characters or less is looked up", edge.length <= 253 && p.pathFor(edge) == "M0 0h24v24H0z")
    val justOver = ("a".repeat(63) + ".").repeat(3) + "a".repeat(51) + ".github.com"
    Check.that("a name of exactly 254 characters is refused", justOver.length == 254 && p.pathFor(justOver) == null)
    val tooLong = ("a".repeat(61) + ".").repeat(4) + "github.com"
    Check.that("a name of 254 characters or more is refused", tooLong.length > 253 && p.pathFor(tooLong) == null)
    val started = System.nanoTime()
    p.pathFor("a.".repeat(32_000) + "github.com")
    Check.that("32 000 labels cost almost nothing", System.nanoTime() - started < 200_000_000L)
    Check.that("a row for a bare suffix is never used", p.pathFor("evil.com") == null && p.pathFor("anything.co.uk") == null)
    Check.that("a row whose icon is missing from the pack gives nothing", p.pathFor("broken.example") == null)
}

private fun parsing() {
    val messy = LogoPack.parse("\n#comment\nnotab\nx.com\n\ngood.org\tgood\nthree.org\tgood\textra\n too\tmany\tcolumns\n", "good\tM9 9h1v1H9z\tGood\nbad line\n")
    Check.equal("good rows survive junk around them", "M9 9h1v1H9z", messy.pathFor("good.org"))
    Check.that("junk rows give nothing", messy.pathFor("x.com") == null && messy.pathFor("too") == null)
    Check.that("a domain row with a third column is junk", messy.pathFor("three.org") == null)
    Check.that("an empty pack finds nothing", LogoPack.parse("", "").pathFor("github.com") == null)
    Check.that("a pack with a thousand rows loads", LogoPack.parse((0 until 1000).joinToString("\n") { "d$it.example\ts$it" }, (0 until 1000).joinToString("\n") { "s$it\tM0 0\tT$it" }).pathFor("d999.example") == "M0 0")
}
