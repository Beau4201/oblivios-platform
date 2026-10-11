// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.airpods.ui;

import java.util.List;
import org.sovereignos.airpods.ui.EarDetectionPolicy.Action;

/** Host-runnable check: javac + java -ea with airpods-core.jar on the class path. */
public final class EarDetectionCheck {
    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static void is(List<Action> got, String what, Action... want) {
        check(got.equals(List.of(want)), what + ": got " + got + ", wanted " + List.of(want));
    }

    public static void main(String[] args) {
        // One pod out pauses (resumable); back resumes only what we paused.
        EarDetectionPolicy p = new EarDetectionPolicy(true);
        is(p.onEars(2), "baseline with both in does nothing");
        is(p.onEars(1), "one out pauses", Action.PAUSE_RESUMABLE);
        p.notePaused();
        is(p.onEars(2), "back resumes", Action.RESUME);
        is(p.onEars(2), "same count again does nothing");

        // Nothing playing: the executor does not call notePaused, so nothing is resumed.
        p = new EarDetectionPolicy(true);
        p.onEars(2);
        is(p.onEars(1), "one out", Action.PAUSE_RESUMABLE);
        is(p.onEars(2), "back without our pause does not resume");

        // Repeated in and out keeps resuming what was paused each time.
        p = new EarDetectionPolicy(true);
        p.onEars(2);
        for (int i = 0; i < 3; i++) {
            is(p.onEars(1), "flap out " + i, Action.PAUSE_RESUMABLE);
            p.notePaused();
            is(p.onEars(2), "flap in " + i, Action.RESUME);
        }

        // Both out: final pause and route away, never a resume; one back routes back, no resume.
        p = new EarDetectionPolicy(true);
        p.onEars(2);
        is(p.onEars(0), "both out", Action.PAUSE_FINAL, Action.ROUTE_AWAY);
        is(p.onEars(0), "still both out does nothing");
        is(p.onEars(1), "one back routes back", Action.ROUTE_BACK);
        is(p.onEars(2), "second back does nothing");

        // One out (paused by us), then the other out: the pause is final, so no resume later.
        p = new EarDetectionPolicy(true);
        p.onEars(2);
        p.onEars(1);
        p.notePaused();
        is(p.onEars(0), "second pod out", Action.PAUSE_FINAL, Action.ROUTE_AWAY);
        is(p.onEars(1), "one back", Action.ROUTE_BACK);
        is(p.onEars(2), "both back does not resume what became final");

        // Baseline with no pod in the ear routes away at once; putting one in routes back.
        p = new EarDetectionPolicy(true);
        is(p.onEars(0), "baseline both out", Action.ROUTE_AWAY);
        is(p.onEars(0), "repeat does nothing");
        is(p.onEars(2), "both in", Action.ROUTE_BACK);

        // A baseline with a pod in the ear does nothing.
        p = new EarDetectionPolicy(true);
        is(p.onEars(1), "baseline one in");

        // Pods swapping places (same count) do nothing.
        p = new EarDetectionPolicy(true);
        p.onEars(1);
        is(p.onEars(1), "same count");

        // Switch off: nothing happens, even for both out.
        p = new EarDetectionPolicy(false);
        is(p.onEars(2), "off, both in");
        is(p.onEars(1), "off, one out");
        is(p.onEars(0), "off, both out");
        is(p.setEnabled(true), "turning on while both out routes away", Action.ROUTE_AWAY);
        is(p.setEnabled(true), "turning on again does nothing");
        is(p.setEnabled(false), "turning off routes back", Action.ROUTE_BACK);
        is(p.setEnabled(false), "turning off again does nothing");
        is(p.onEars(2), "off again, pods in");

        // Turning on while the pods are in the ears does nothing.
        p = new EarDetectionPolicy(false);
        p.onEars(2);
        is(p.setEnabled(true), "turning on with pods in");

        // Turning the switch off or on forgets a pause we made.
        p = new EarDetectionPolicy(true);
        p.onEars(2);
        p.onEars(1);
        p.notePaused();
        is(p.setEnabled(false), "off while paused by us");
        is(p.setEnabled(true), "on again with one pod out");
        is(p.onEars(2), "back after toggling does not resume");

        // End of session: restore a route we removed, once.
        p = new EarDetectionPolicy(true);
        p.onEars(0);
        is(p.end(), "end while routed away", Action.ROUTE_BACK_IF_LINKED);
        is(p.end(), "second end does nothing");
        p = new EarDetectionPolicy(true);
        p.onEars(2);
        is(p.end(), "end when not routed away");

        // Out of range counts are a programming error, not input to act on.
        for (int bad : new int[] {-1, 3}) {
            boolean threw = false;
            try {
                new EarDetectionPolicy(true).onEars(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(threw, "count " + bad + " is refused");
        }
        System.out.println("EarDetectionCheck OK");
    }
}
