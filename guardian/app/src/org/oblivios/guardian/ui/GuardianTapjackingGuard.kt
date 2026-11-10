// SPDX-License-Identifier: Apache-2.0
package org.oblivios.guardian.ui

import android.view.MotionEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Blocks touches from reaching whatever is below this modifier in the chain
 * whenever the window is fully or partially obscured by another window (the
 * Compose equivalent of View#setFilterTouchesWhenObscured(true), which has no
 * direct Modifier equivalent). Applied to Undo specifically: it is the one
 * Guardian control whose accidental/tapjacked activation reverses a just-
 * applied protection (ADR-034 residual 5).
 *
 * Carried over unchanged from the superseded `GuardianButtons.kt`, which was
 * removed with the rest of the old UI layer. In the current UI the Undo
 * action lives on the incident-review screen rather than on a list row, so
 * [GuardianApp] applies this guard to that whole screen while it is offering
 * Undo — a superset of the control it originally protected, not a weakening.
 */
fun Modifier.filterTouchesWhenObscured(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val motionEvent = event.motionEvent ?: continue
            val obscured = (motionEvent.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0 ||
                (motionEvent.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0
            if (obscured) {
                event.changes.forEach { it.consume() }
            }
        }
    }
}
