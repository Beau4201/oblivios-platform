// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.ui;

/**
 * When the connection sheet is shown (ADR-129): once per connection, at the first battery report of a
 * pod (a stored case value is not a report), and not while a call or a ring is going on; when the link
 * drops the sheet goes away. One instance per process; the caller serialises the calls.
 */
public final class ConnectionSheetPolicy {
    public enum Action {
        NONE,
        SHOW,
        CANCEL
    }

    /** This connection was decided (shown, or skipped because of a call). */
    private boolean handled;

    /** The sheet is on screen or in the notification shade for this connection. */
    private boolean shown;

    /** {@code quiet} is false while the phone is in a call or ringing. */
    public Action onState(AirPodsUiState s, boolean quiet) {
        if (!s.isConnected()) {
            boolean cancel = shown;
            handled = false;
            shown = false;
            return cancel ? Action.CANCEL : Action.NONE;
        }
        if (handled || (s.leftPercent < 0 && s.rightPercent < 0)) {
            return Action.NONE;
        }
        handled = true;
        shown = quiet;
        return quiet ? Action.SHOW : Action.NONE;
    }
}
