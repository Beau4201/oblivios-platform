// SPDX-License-Identifier: Apache-2.0
package org.oblivios.passwords.hosttest

import org.oblivios.passwords.core.ClipGuard

fun main() {
    nothingChangedMeansClear()
    theRepeatedNotificationOfOurOwnCopyIsOurs()
    aChangeAfterTheWindowIsSomebodyElses()
    theWindowEndsAtItsLastMillisecond()
    oneLateChangeIsKept()
    Check.done("ClipGuard")
}

private fun nothingChangedMeansClear() {
    Check.that("no change, so the clip is cleared", !ClipGuard(5_000L).changedByOthers)
}

private fun theRepeatedNotificationOfOurOwnCopyIsOurs() {
    val g = ClipGuard(5_000L)
    g.onChanged(5_000L)
    g.onChanged(5_014L)
    g.onChanged(5_999L)
    Check.that("the notifications of our own set in the first second are ours", !g.changedByOthers)
}

private fun aChangeAfterTheWindowIsSomebodyElses() {
    val g = ClipGuard(5_000L)
    g.onChanged(5_000L)
    g.onChanged(12_000L)
    Check.that("a copy seven seconds later is somebody else's", g.changedByOthers)
}

private fun theWindowEndsAtItsLastMillisecond() {
    Check.that("999 ms is still ours", !ClipGuard(0L).also { it.onChanged(ClipGuard.OWN_WINDOW_MS - 1) }.changedByOthers)
    Check.that("1000 ms is somebody else's", ClipGuard(0L).also { it.onChanged(ClipGuard.OWN_WINDOW_MS) }.changedByOthers)
    Check.equal("the window is one second", 1_000L, ClipGuard.OWN_WINDOW_MS)
}

private fun oneLateChangeIsKept() {
    val g = ClipGuard(0L)
    g.onChanged(40_000L)
    g.onChanged(10L)
    Check.that("a later own-looking notification does not undo it", g.changedByOthers)
}
