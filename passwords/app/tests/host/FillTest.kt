// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.hosttest

import org.freeholdos.passwords.fill.Account
import org.freeholdos.passwords.fill.FieldRole
import org.freeholdos.passwords.fill.FillPlan
import org.freeholdos.passwords.fill.InputTypes
import org.freeholdos.passwords.fill.ParsedField
import org.freeholdos.passwords.fill.SaveCandidate
import org.freeholdos.passwords.fill.SiteLookup
import org.freeholdos.passwords.fill.classify
import org.freeholdos.passwords.fill.plan
import org.freeholdos.passwords.fill.webIdEntry
import org.freeholdos.passwords.fill.webInputType
import org.freeholdos.passwords.lock.LockState
import org.freeholdos.passwords.lock.Reason

private class FakeSites(
    private val withAccount: Set<String> = emptySet(),
    private val accounts: List<Account> = emptyList(),
) : SiteLookup {
    var accountsAsked = 0
    override fun hasAccount(webDomain: String?, packageName: String) = (webDomain ?: packageName) in withAccount
    override fun accounts(webDomain: String?, packageName: String): List<Account> {
        accountsAsked++
        return if (hasAccount(webDomain, packageName)) accounts else emptyList()
    }
}

private val user = ParsedField("u1", FieldRole.Username)
private val pass = ParsedField("p1", FieldRole.Password)
private val other = ParsedField("o1", FieldRole.Other)
private val anna = Account(byteArrayOf(1), "anna")
private val bob = Account(byteArrayOf(2), "bob@example.org")

fun main() {
    hintsWinOverHeuristics()
    aSearchBoxIsNotALoginField()
    heuristicsFromTheInputTypeAndTheId()
    fieldsAsVanadiumReportsThem()
    lockedWithAMatchOffersOneGenericAuthentication()
    lockedWithoutAMatchOffersNothingAndLeaksNothing()
    unlockedOffersOneDatasetPerAccountLabelledWithTheUsernameOnly()
    nothingWithoutALoginField()
    multiStepAndNewPasswordForms()
    everyLockedStateAsksForAuthentication()
    saveCandidates()
    everyTokenOfTheLists()
    Check.done("FillTest")
}

private fun classifyId(id: String?, inputType: Int = InputTypes.TEXT, hints: List<String> = emptyList()) = classify(hints, inputType, id)

private fun hintsWinOverHeuristics() {
    Check.equal("password hint beats a user-looking id", FieldRole.Password, classify(listOf("password"), InputTypes.TEXT, "username"))
    Check.equal("username hint beats a password input type", FieldRole.Username, classify(listOf("username"), InputTypes.TEXT_PASSWORD, "pwd"))
    Check.equal("newPassword is a password", FieldRole.Password, classify(listOf("newPassword"), InputTypes.TEXT, null))
    Check.equal("emailAddress is a username", FieldRole.Username, classify(listOf("emailAddress"), InputTypes.TEXT, null))
    Check.equal("a card number hint is not a login field, whatever the id says", FieldRole.Other, classify(listOf("creditCardNumber"), InputTypes.TEXT, "user_card"))
    Check.equal("an unknown hint falls through to the heuristics", FieldRole.Password, classify(listOf("x-custom"), InputTypes.TEXT_PASSWORD, null))
    for (h in listOf("search", "phone", "smsOTPCode", "postalAddress", "creditCardNumber", "creditCardSecurityCode")) {
        Check.equal("hint $h is not a login field", FieldRole.Other, classify(listOf(h), InputTypes.TEXT, "username"))
    }
    Check.equal("hints are compared without case", FieldRole.Username, classify(listOf("USERNAME"), InputTypes.TEXT, null))
    Check.equal("the first hint that means something wins", FieldRole.Username, classify(listOf("x-custom", "username", "password"), InputTypes.TEXT, null))
}

private fun aSearchBoxIsNotALoginField() {
    Check.equal("search box id", FieldRole.Other, classifyId("search_box"))
    Check.equal("query id", FieldRole.Other, classifyId("etQuery"))
    Check.equal("a user search is still a search", FieldRole.Other, classifyId("user_search_input"))
    Check.equal("a promo code", FieldRole.Other, classifyId("promoCode"))
    Check.equal("a phone number", FieldRole.Other, classifyId("login_phone"))
    Check.equal("a one-time code", FieldRole.Other, classifyId("otp"))
    Check.equal("a password input type is a password even in a field called search", FieldRole.Password, classifyId("search", InputTypes.TEXT_PASSWORD))
    Check.equal("an id with both words is a password", FieldRole.Password, classifyId("user_password"))
    Check.equal("a search box with an email input type", FieldRole.Other, classifyId("search", InputTypes.TEXT_EMAIL))
}

private fun heuristicsFromTheInputTypeAndTheId() {
    Check.equal("text password", FieldRole.Password, classifyId(null, InputTypes.TEXT_PASSWORD))
    Check.equal("visible password", FieldRole.Password, classifyId(null, InputTypes.TEXT_VISIBLE_PASSWORD))
    Check.equal("web password", FieldRole.Password, classifyId(null, InputTypes.TEXT_WEB_PASSWORD))
    Check.equal("number password", FieldRole.Password, classifyId(null, InputTypes.NUMBER_PASSWORD))
    Check.equal("email address type", FieldRole.Username, classifyId(null, InputTypes.TEXT_EMAIL))
    Check.equal("web email address type", FieldRole.Username, classifyId(null, InputTypes.TEXT_WEB_EMAIL))
    Check.equal("id: username", FieldRole.Username, classifyId("username"))
    Check.equal("id: camel case userName", FieldRole.Username, classifyId("etUserName"))
    Check.equal("id: login_email", FieldRole.Username, classifyId("login_email"))
    Check.equal("id: account-id with dashes", FieldRole.Username, classifyId("account-id"))
    Check.equal("id: pwd", FieldRole.Password, classifyId("pwd"))
    Check.equal("id: password_confirm", FieldRole.Password, classifyId("password_confirm"))
    Check.equal("id: edit_password", FieldRole.Password, classifyId("edit_password"))
    Check.equal("a plain field with no hint", FieldRole.Other, classifyId(null))
    Check.equal("an id that only contains a word like pass", FieldRole.Other, classifyId("passenger_count"))
    Check.equal("a number class that is not a password", FieldRole.Other, classifyId(null, InputTypes.NUMBER))
    Check.equal("an empty id", FieldRole.Other, classifyId(""))
}

/** The nodes of the dump of two real fill requests in Vanadium (PWM-004, lynx 2026102302, 2026-10-28): no id entry, input type 0. */
private fun fieldsAsVanadiumReportsThem() {
    fun web(hints: List<String>, type: String?, id: String?, name: String?) =
        classify(hints, webInputType(0, type), webIdEntry(null, id, name))
    // github.com/login
    Check.equal("github: the user name (autocomplete=username)", FieldRole.Username, web(listOf("username"), "text", "login_field", "login"))
    Check.equal("github: the password (autocomplete=current-password)", FieldRole.Password, web(listOf("current-password"), "password", "password", "password"))
    Check.equal("github: the hidden trap field is not a login field", FieldRole.Other, web(emptyList(), "text", "", "required_field_3d21"))
    // the-internet.herokuapp.com/login: no autocomplete attribute at all
    Check.equal("herokuapp: the user name from its id and name", FieldRole.Username, web(emptyList(), "text", "username", "username"))
    Check.equal("herokuapp: the password from its type", FieldRole.Password, web(emptyList(), "password", "password", "password"))
    // the helpers
    Check.equal("a password type of the HTML is a web password", InputTypes.TEXT_WEB_PASSWORD, webInputType(0, "password"))
    Check.equal("the type is read without case", InputTypes.TEXT_WEB_PASSWORD, webInputType(0, "PASSWORD"))
    Check.equal("an email type of the HTML is a web email", InputTypes.TEXT_WEB_EMAIL, webInputType(0, "email"))
    Check.equal("a text type says nothing", 0, webInputType(0, "text"))
    Check.equal("no type says nothing", 0, webInputType(0, null))
    Check.equal("an input type of the node itself is kept", InputTypes.TEXT_EMAIL, webInputType(InputTypes.TEXT_EMAIL, "password"))
    Check.equal("the id entry of the node comes first", "etUser", webIdEntry("etUser", "id1", "name1"))
    Check.equal("an empty id entry falls back to the HTML id and name", "id1 name1", webIdEntry("", "id1", "name1"))
    Check.equal("no id entry and no HTML id", "name1", webIdEntry(null, null, "name1"))
    Check.equal("an empty id and name give no id words", null, webIdEntry(null, "", ""))
    // the HTML autocomplete tokens of the page
    Check.equal("new-password is a password", FieldRole.Password, classify(listOf("new-password"), 0, null))
    Check.equal("email is a user name", FieldRole.Username, classify(listOf("email"), 0, null))
    Check.equal("a web password hint is read without case", FieldRole.Password, classify(listOf("Current-Password"), 0, null))
}

private fun lockedWithAMatchOffersOneGenericAuthentication() {
    val sites = FakeSites(withAccount = setOf("github.com"), accounts = listOf(anna, bob))
    val p = plan(listOf(user, pass, other), "github.com", "com.android.chrome", LockState.Locked, sites)
    Check.that("one authentication", p is FillPlan.Authenticate)
    val a = p as FillPlan.Authenticate
    Check.equal("it covers the username and the password field, nothing else", listOf("u1", "p1"), a.fieldKeys)
    Check.equal("the locked path never asked for the accounts", 0, sites.accountsAsked)
}

private fun lockedWithoutAMatchOffersNothingAndLeaksNothing() {
    val sites = FakeSites(withAccount = setOf("github.com"), accounts = listOf(anna))
    Check.equal("no chip on a site without an account", FillPlan.None, plan(listOf(user, pass), "example.org", "com.android.chrome", LockState.Locked, sites))
    Check.equal("not for an app either", FillPlan.None, plan(listOf(user, pass), null, "org.example.app", LockState.Locked, sites))
    Check.equal("and the accounts were not asked for", 0, sites.accountsAsked)
}

private fun unlockedOffersOneDatasetPerAccountLabelledWithTheUsernameOnly() {
    val sites = FakeSites(withAccount = setOf("github.com"), accounts = listOf(anna, bob))
    val p = plan(listOf(user, pass), "github.com", "com.android.chrome", LockState.Unlocked(7), sites)
    Check.that("datasets", p is FillPlan.Datasets)
    val d = (p as FillPlan.Datasets).items
    Check.equal("one per account", 2, d.size)
    Check.equal("labelled with the username", listOf("anna", "bob@example.org"), d.map { it.label })
    Check.that("each knows its entry", d[0].entryId.contentEquals(byteArrayOf(1)) && d[1].entryId.contentEquals(byteArrayOf(2)))
    Check.equal("and its fields", listOf("u1") to listOf("p1"), d[0].usernameKeys to d[0].passwordKeys)
    Check.that("the plan carries no password", !p.toString().contains("hunter2"))
    val blank = plan(listOf(user, pass), "github.com", "x", LockState.Unlocked(7), FakeSites(setOf("github.com"), listOf(Account(byteArrayOf(3), ""))))
    Check.that("an account without a username has no label", (blank as FillPlan.Datasets).items[0].label == null)
    Check.equal("unlocked, but no account for the site", FillPlan.None, plan(listOf(user, pass), "example.org", "x", LockState.Unlocked(7), sites))
}

private fun nothingWithoutALoginField() {
    val sites = FakeSites(withAccount = setOf("github.com"), accounts = listOf(anna))
    for (state in listOf(LockState.Locked, LockState.Unlocked(1))) {
        Check.equal("only Other fields: nothing ($state)", FillPlan.None, plan(listOf(other), "github.com", "x", state, sites))
        Check.equal("no fields: nothing ($state)", FillPlan.None, plan(emptyList(), "github.com", "x", state, sites))
    }
}

private fun multiStepAndNewPasswordForms() {
    val sites = FakeSites(withAccount = setOf("github.com"), accounts = listOf(anna))
    val onlyUser = plan(listOf(user), "github.com", "x", LockState.Unlocked(1), sites) as FillPlan.Datasets
    Check.equal("a username-only step has no password key", emptyList<String>(), onlyUser.items[0].passwordKeys)
    val onlyPass = plan(listOf(pass), "github.com", "x", LockState.Unlocked(1), sites) as FillPlan.Datasets
    Check.equal("a password-only step has no username key", emptyList<String>(), onlyPass.items[0].usernameKeys)
    val two = plan(listOf(user, pass, ParsedField("p2", FieldRole.Password)), "github.com", "x", LockState.Unlocked(1), sites) as FillPlan.Datasets
    Check.equal("both password fields get the password", listOf("p1", "p2"), two.items[0].passwordKeys)
}

private fun everyLockedStateAsksForAuthentication() {
    val sites = FakeSites(withAccount = setOf("github.com"), accounts = listOf(anna))
    for (state in listOf(LockState.Locked, LockState.NeedsPassphrase(Reason.Restart), LockState.NeedsPassphrase(Reason.Expired), LockState.SecretLost)) {
        Check.that("a match while $state asks for authentication", plan(listOf(user, pass), "github.com", "x", state, sites) is FillPlan.Authenticate)
        Check.equal("no match while $state offers nothing", FillPlan.None, plan(listOf(user, pass), "example.org", "x", state, sites))
    }
}

private fun saveCandidates() {
    val c = SaveCandidate.of(listOf(FieldRole.Username to "anna", FieldRole.Password to "hunter2".toCharArray().concatToString()))
    Check.that("username and password are taken", c != null && c.username == "anna" && String(c.password) == "hunter2")
    Check.that("the password never appears in toString", !c.toString().contains("hunter2"))
    Check.that("no password, no candidate", SaveCandidate.of(listOf(FieldRole.Username to "anna")) == null)
    Check.that("an empty password is no candidate", SaveCandidate.of(listOf(FieldRole.Password to "")) == null)
    Check.equal("the first username field is the username", "a", SaveCandidate.of(listOf(FieldRole.Username to "a", FieldRole.Username to "b", FieldRole.Password to "p"))!!.username)
    Check.that("a username is optional", SaveCandidate.of(listOf(FieldRole.Password to "pw"))!!.username == "")
    Check.that("fields of the wrong kind are ignored", SaveCandidate.of(listOf(FieldRole.Other to "x", FieldRole.Password to "pw"))!!.username == "")
    val last = SaveCandidate.of(listOf(FieldRole.Password to "old", FieldRole.Password to "new"))!!
    Check.equal("the last non-empty password field is the one submitted", "new", String(last.password))
    c!!.wipe()
    Check.that("wipe clears the password", c.password.all { it == '\u0000' })
}

private fun everyTokenOfTheLists() {
    for (t in listOf("user", "username", "userid", "uname", "login", "loginid", "email", "mail", "emailaddress", "account", "accountid", "uid")) {
        Check.equal("id $t is a username", FieldRole.Username, classifyId(t))
    }
    for (t in listOf("pass", "password", "passwd", "pwd", "passphrase", "passcode")) {
        Check.equal("id $t is a password", FieldRole.Password, classifyId(t))
    }
    for (t in listOf("search", "query", "q", "find", "filter", "coupon", "promo", "zip", "postal", "phone", "tel", "mobile", "otp", "captcha", "address", "city", "country", "comment", "message", "title", "subject")) {
        Check.equal("id user_$t is not a login field", FieldRole.Other, classifyId("user_$t"))
    }
    Check.equal("a number class with another variation is not a password", FieldRole.Other, classifyId(null, InputTypes.CLASS_NUMBER or 0x20))
    Check.equal("a text class with another variation is not a password", FieldRole.Other, classifyId(null, InputTypes.CLASS_TEXT or 0x40))
    Check.equal("text password variation without the text class is not a password", FieldRole.Other, classifyId(null, 0x80))
}
