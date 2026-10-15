// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.ui;

import org.freeholdos.airpods.ui.AirPodsUiState.Feature;
import org.freeholdos.airpods.ui.ConnectionSheetPolicy.Action;

/** Host-runnable check of when the connection sheet is shown. */
public final class ConnectionSheetCheck {
    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static AirPodsUiState connected(int left, int right) {
        return new AirPodsUiState.Builder().address("AA:BB:CC:DD:EE:FF").percents(left, right, -1)
                .feature(Feature.EAR_DETECTION).build();
    }

    public static void main(String[] args) {
        ConnectionSheetPolicy p = new ConnectionSheetPolicy();
        check(p.onState(AirPodsUiState.DISCONNECTED, true) == Action.NONE, "nothing to do while disconnected");
        check(p.onState(connected(-1, -1), true) == Action.NONE, "no sheet before the first battery report of a pod");
        check(p.onState(connected(80, -1), true) == Action.SHOW, "shown at the first report");
        check(p.onState(connected(80, 82), true) == Action.NONE, "once per connection");
        check(p.onState(connected(79, 82), true) == Action.NONE, "not again for a new level");
        check(p.onState(AirPodsUiState.DISCONNECTED, true) == Action.CANCEL, "the link dropped: the sheet goes away");
        check(p.onState(AirPodsUiState.DISCONNECTED, true) == Action.NONE, "nothing more to cancel");
        check(p.onState(connected(70, 70), true) == Action.SHOW, "a new connection shows it again");
        p.onState(AirPodsUiState.DISCONNECTED, true);

        ConnectionSheetPolicy call = new ConnectionSheetPolicy();
        check(call.onState(connected(70, 70), false) == Action.NONE, "not during a call or a ring");
        check(call.onState(connected(70, 71), true) == Action.NONE, "and not later in the same connection either");
        check(call.onState(AirPodsUiState.DISCONNECTED, true) == Action.NONE, "nothing was shown, nothing to cancel");
        check(call.onState(connected(70, 70), true) == Action.SHOW, "the next connection shows it");

        ConnectionSheetPolicy storedCase = new ConnectionSheetPolicy();
        check(storedCase.onState(new AirPodsUiState.Builder().address("AA:BB:CC:DD:EE:FF").percents(-1, -1, 90).build(), true) == Action.NONE,
                "a stored case value alone is not a report");
        System.out.println("ConnectionSheetCheck passed");
    }
}
