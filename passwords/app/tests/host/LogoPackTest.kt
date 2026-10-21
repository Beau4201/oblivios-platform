// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.hosttest

import org.freeholdos.passwords.ui.LogoPack

private val DOMAINS = "github.com\tgithub\ncodeberg.org\tcodeberg\nbbc.co.uk\tbbc\nbroken.example\tmissing\n"
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
    for (h in listOf("github.com.evil.example", "evilgithub.com", "githubb.com", "co.uk", "com", "uk", "", ".", "gіthub.com", "github.co", "192.0.2.1", "[::1]", "github.com/x", "git hub.com")) {
        Check.that("no logo for '$h'", p.pathFor(h) == null)
    }
    Check.that("a row whose icon is missing from the pack gives nothing", p.pathFor("broken.example") == null)
}

private fun parsing() {
    val messy = LogoPack.parse("\n#comment\nnotab\nx.com\n\ngood.org\tgood\n too\tmany\tcolumns\n", "good\tM9 9h1v1H9z\tGood\nbad line\n")
    Check.equal("good rows survive junk around them", "M9 9h1v1H9z", messy.pathFor("good.org"))
    Check.that("junk rows give nothing", messy.pathFor("x.com") == null && messy.pathFor("too") == null)
    Check.that("an empty pack finds nothing", LogoPack.parse("", "").pathFor("github.com") == null)
    Check.that("a pack with a thousand rows loads", LogoPack.parse((0 until 1000).joinToString("\n") { "d$it.example\ts$it" }, (0 until 1000).joinToString("\n") { "s$it\tM0 0\tT$it" }).pathFor("d999.example") == "M0 0")
}
