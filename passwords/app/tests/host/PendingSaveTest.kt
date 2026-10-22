// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.hosttest

import org.freeholdos.passwords.fill.FieldRole
import org.freeholdos.passwords.fill.PendingSaveSlot
import org.freeholdos.passwords.fill.SaveCandidate

private fun candidate(password: String = "hunter2-extra") =
    SaveCandidate.of(listOf(FieldRole.Username to "anna", FieldRole.Password to password))!!

fun main() {
    takeHandsTheLoginOverOnce()
    anExpiredLoginIsWipedNotHandedOver()
    theLastMomentStillCounts()
    aSecondLoginReplacesAndWipesTheFirst()
    clearWipes()
    nothingWaitingGivesNull()
    theTextNeverPrintsThePassword()
    Check.done("PendingSave")
}

private fun takeHandsTheLoginOverOnce() {
    var t = 1_000L
    val slot = PendingSaveSlot(ttlMs = 100) { t }
    slot.put("example.org", "org.browser", candidate())
    val got = slot.take()
    Check.that("the login comes out", got != null)
    Check.equal("with its site", "example.org", got!!.webDomain)
    Check.equal("with its package", "org.browser", got.packageName)
    Check.equal("with its username", "anna", got.candidate.username)
    Check.equal("with its password", "hunter2-extra", String(got.candidate.password))
    Check.that("a second take finds nothing", slot.take() == null)
}

private fun anExpiredLoginIsWipedNotHandedOver() {
    var t = 1_000L
    val slot = PendingSaveSlot(ttlMs = 100) { t }
    val c = candidate()
    slot.put(null, "app.one", c)
    t = 1_101
    Check.that("after the time to live nothing comes out", slot.take() == null)
    Check.that("and the characters are wiped", c.password.all { it == '\u0000' })
}

private fun theLastMomentStillCounts() {
    var t = 1_000L
    val slot = PendingSaveSlot(ttlMs = 100) { t }
    slot.put(null, "app.one", candidate())
    t = 1_100
    Check.that("at exactly the deadline the login is still there", slot.take() != null)
}

private fun aSecondLoginReplacesAndWipesTheFirst() {
    val slot = PendingSaveSlot { 0 }
    val first = candidate("first-password")
    slot.put("a.example", "p", first)
    slot.put("b.example", "p", candidate("second-password"))
    Check.that("the replaced login is wiped", first.password.all { it == '\u0000' })
    val got = slot.take()!!
    Check.equal("the second one is kept", "b.example", got.webDomain)
    Check.equal("with its password", "second-password", String(got.candidate.password))
}

private fun clearWipes() {
    val slot = PendingSaveSlot { 0 }
    val c = candidate()
    slot.put(null, "p", c)
    slot.clear()
    Check.that("clear wipes the characters", c.password.all { it == '\u0000' })
    Check.that("and empties the slot", slot.take() == null)
}

private fun nothingWaitingGivesNull() {
    Check.that("an empty slot gives null", PendingSaveSlot { 0 }.take() == null)
    PendingSaveSlot { 0 }.clear()
    Check.that("clearing an empty slot is harmless", true)
}

private fun theTextNeverPrintsThePassword() {
    val slot = PendingSaveSlot { 0 }
    slot.put("example.org", "p", candidate("very-secret-value"))
    val text = slot.take()!!.toString()
    Check.that("the text has no password", !text.contains("very-secret-value"))
}
