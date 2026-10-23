// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.hosttest

import org.freeholdos.passwords.core.SiteText

fun main() {
    hostsAreCutAndLowered()
    textThatIsNoHostIsRefused()
    anEditKeepsTheOtherSites()
    Check.done("SiteTextTest")
}

private fun hostsAreCutAndLowered() {
    Check.equal("a plain host", "bank.example", SiteText.hostOf("bank.example"))
    Check.equal("a scheme, a path and the case", "bank.example", SiteText.hostOf("  HTTPS://Bank.Example/login?x=1#top "))
    Check.equal("http too", "bank.example", SiteText.hostOf("http://bank.example"))
    Check.equal("a query without a path", "bank.example", SiteText.hostOf("bank.example?x=1"))
    Check.equal("a fragment without a path", "bank.example", SiteText.hostOf("bank.example#a"))
    Check.equal("a subdomain stays", "login.bank.example", SiteText.hostOf("login.bank.example/"))
    Check.equal("empty text is no site", "", SiteText.hostOf(""))
    Check.equal("blank text is no site", "", SiteText.hostOf("   "))
    Check.equal("only a scheme is no site", "", SiteText.hostOf("https://"))
    Check.equal("a name without a dot is kept (localhost, an intranet)", "localhost", SiteText.hostOf("localhost"))
    Check.equal("253 characters are allowed", "a".repeat(253), SiteText.hostOf("a".repeat(253)))
}

private fun textThatIsNoHostIsRefused() {
    for (bad in listOf(
        "me@bank.example", "https://user:pw@bank.example", "bank.example:8443", "bank example", "bank\texample", "bank\u0000.example",
        ".bank.example", "bank.example.", "bank..example", "bank\\example", "ftp://bank.example", "a".repeat(254),
    )) {
        Check.that("'${bad.take(30)}' is refused", SiteText.hostOf(bad) == null)
    }
    Check.equal("trailing white space is trimmed, not refused", "bank.example", SiteText.hostOf("bank.example\n"))
    Check.that("a user name is refused even behind a path-less scheme", SiteText.hostOf("http://me@bank.example") == null)
    Check.equal("an @ after the path is not part of the host", "bank.example", SiteText.hostOf("bank.example/a@b"))
}

private fun anEditKeepsTheOtherSites() {
    val three = listOf("a.example", "b.example", "c.example")
    Check.equal("an untouched first site keeps all three", three, SiteText.domainsAfterEdit(three, "a.example"))
    Check.equal("a changed first site keeps the others", listOf("z.example", "b.example", "c.example"), SiteText.domainsAfterEdit(three, "z.example"))
    Check.equal("a first site that is already second is not listed twice", listOf("b.example", "c.example"), SiteText.domainsAfterEdit(three, "b.example"))
    Check.equal("an emptied first site drops only the first", listOf("b.example", "c.example"), SiteText.domainsAfterEdit(three, ""))
    Check.equal("no sites and no input", emptyList<String>(), SiteText.domainsAfterEdit(emptyList(), ""))
    Check.equal("no sites and a new one", listOf("a.example"), SiteText.domainsAfterEdit(emptyList(), "a.example"))
    Check.equal("one site replaced", listOf("z.example"), SiteText.domainsAfterEdit(listOf("a.example"), "z.example"))
}
