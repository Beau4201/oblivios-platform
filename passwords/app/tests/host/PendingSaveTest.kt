// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.hosttest

import org.oblivios.passwords.fill.FieldRole
import org.oblivios.passwords.fill.PendingSaveSlot
import org.oblivios.passwords.fill.SaveCandidate

private fun candidate(password: String = "hunter2-extra") =
    SaveCandidate.of(listOf(FieldRole.Username to "anna", FieldRole.Password to password))!!

fun main() {
    takeHandsTheLoginOverOnce()
    anExpiredLoginIsWipedNotHandedOver()
    theLastMomentStillCounts()
    aSecondLoginReplacesAndWipesTheFirst()
    clearWipes()
    nothingWaitingGivesNull()
    anUntakenLoginIsWipedWhenItsTimeIsUp()
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

/** P-07: the wipe at the deadline does not wait for a take. A fake scheduler keeps the runnables and the test runs them. */
private fun anUntakenLoginIsWipedWhenItsTimeIsUp() {
    var t = 1_000L
    val scheduled = ArrayList<Pair<Long, () -> Unit>>()
    val slot = PendingSaveSlot(ttlMs = 100, schedule = { ms, r -> scheduled.add(ms to r) }) { t }
    val c = candidate()
    slot.put("a.example", "p", c)
    Check.equal("one wipe is scheduled", 1, scheduled.size)
    Check.equal("just after the deadline", 101L, scheduled[0].first)
    t = 1_100
    scheduled[0].second()
    Check.that("at the deadline the login is still kept", !c.password.all { it == '\u0000' })
    t = 1_101
    scheduled[0].second()
    Check.that("after the deadline the scheduled wipe wipes it", c.password.all { it == '\u0000' })
    Check.that("and the slot is empty", slot.take() == null)

    // A wipe that comes late for a login that was replaced, taken or cleared must not touch the one that waits now.
    t = 2_000
    scheduled.clear()
    val first = candidate("first-password")
    slot.put("a.example", "p", first)
    val second = candidate("second-password")
    slot.put("b.example", "p", second)
    t = 2_150
    scheduled[0].second() // the wipe of the replaced login runs after the time of both is up
    Check.equal("the wipe of a replaced login leaves the one that waits now alone", "second-password", String(second.password))
    scheduled[1].second()
    Check.that("its own wipe wipes it", second.password.all { it == '\u0000' })

    // A wipe that comes after the take does nothing to the login that was handed over.
    t = 3_000
    scheduled.clear()
    slot.put("c.example", "p", candidate("third-password"))
    val got = slot.take()!!
    t = 3_200
    scheduled[0].second()
    Check.equal("a wipe after a take leaves the handed-over login", "third-password", String(got.candidate.password))
}

private fun theTextNeverPrintsThePassword() {
    val slot = PendingSaveSlot { 0 }
    slot.put("example.org", "p", candidate("very-secret-value"))
    val text = slot.take()!!.toString()
    Check.that("the text has no password", !text.contains("very-secret-value"))
}
