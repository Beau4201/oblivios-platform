// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.core

/**
 * Decides whether the clipboard may still be cleared 30 seconds after a copy of the app (PWM-014). The platform reports one
 * copy more than once: on lynx 2026110203 two changes came 14 ms apart, and counting the second as somebody else's copy left the
 * password on the clipboard. A change in the first [OWN_WINDOW_MS] after our own set is therefore ours; a later one is a copy of
 * somebody else and is left alone (P-06). A copy by somebody else inside the window is cleared with ours, which errs on the side
 * of not leaving a password behind.
 */
class ClipGuard(private val setAtMs: Long) {
    /** True once the clipboard changed after the window that follows our own set. */
    var changedByOthers = false
        private set

    /** One change notification of the clipboard, at [nowMs] on the same clock as [setAtMs]. */
    fun onChanged(nowMs: Long) {
        if (nowMs - setAtMs >= OWN_WINDOW_MS) changedByOthers = true
    }

    companion object {
        const val OWN_WINDOW_MS = 1_000L
    }
}
