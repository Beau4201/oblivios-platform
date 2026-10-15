/*
 * Copyright (C) 2026 SovereignOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.grab;

import com.android.server.grab.GrabDetector.Decision;
import com.android.server.grab.GrabDetector.Params;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongToDoubleFunction;

/** Host test of GrabDetector. Plain asserts: prints FAILED for each failed check, exit 1. */
public final class GrabDetectorTest {
    private static final float G = GrabDetector.GRAVITY;
    private static final int STEP_MS = 20;
    private static int failures;

    // peak 15, touch window 400, touch loss 150, settle 200, sustain 800, energy 1.5, cooldown 2000
    private static final Params P = new Params(15f, 400, 150, 200, 800, 1.5f, 2000);

    /** A scripted run: touch events by time and the acceleration deviation from gravity by time. */
    private static final class Scenario {
        final TreeMap<Long, Boolean> touch = new TreeMap<>();
        LongToDoubleFunction dev = t -> 0.2;
        long decidedAtMs = -1;

        Scenario touch(long tMs, boolean contact) {
            touch.put(tMs, contact);
            return this;
        }

        Scenario dev(LongToDoubleFunction f) {
            dev = f;
            return this;
        }

        /** Returns the first decision, or null if the detector never decided. */
        Decision run(GrabDetector d, long endMs) {
            Decision first = null;
            for (long t = 0; t <= endMs; t += STEP_MS) {
                for (Map.Entry<Long, Boolean> e : touch.subMap(t - STEP_MS, false, t, true)
                        .entrySet()) {
                    d.onTouch(e.getKey(), e.getValue());
                }
                Decision x = d.onAccel(t, 0f, 0f, G + (float) dev.applyAsDouble(t));
                if (x != null && first == null) {
                    first = x;
                    decidedAtMs = t;
                }
            }
            return first;
        }
    }

    private static void check(String name, boolean ok) {
        if (!ok) {
            System.out.println("FAILED " + name);
            failures++;
        }
    }

    /** The deviation the detector computes for a sample with only z set. */
    private static float devOf(float z) {
        return (float) Math.abs((double) z - (double) G);
    }

    /** Touching since 500, pull at 1000, finger gone at 1020, running afterwards (deviation 4). */
    private static Scenario snatch() {
        return new Scenario()
                .touch(500, true).touch(700, true).touch(900, true).touch(1000, true)
                .touch(1020, false)
                .dev(t -> t == 1000 ? 25 : t < 1000 ? 0.2 : 4);
    }

    /** Turns the phone through all three axes at constant magnitude while touching. */
    private static int runTilt(GrabDetector d) {
        int decisions = 0;
        d.onTouch(0, true);
        for (long t = 0; t <= 3000; t += STEP_MS) {
            int axis = (int) ((t / STEP_MS) % 3);
            float[] v = {0f, 0f, 0f};
            v[axis] = G;
            if (d.onAccel(t, v[0], v[1], v[2]) != null) {
                decisions++;
            }
        }
        return decisions;
    }

    public static void main(String[] args) {
        // a pull out of the fingers that keeps moving triggers, and says why
        Scenario s = snatch();
        Decision d = s.run(new GrabDetector(P), 4000);
        // the verdict falls exactly settle + sustain (1000 ms) after the peak at 1000 ms
        check("snatch is decided at the end of the window", s.decidedAtMs == 2000);
        check("snatch decides", d != null);
        check("snatch triggers", d != null && d.trigger);
        check("snatch reports the peak", d != null && Math.abs(d.peak - 25f) < 0.01f);
        check("snatch reports the release", d != null && d.released && d.releaseDeltaMs == 20);
        check("snatch reports the energy", d != null && Math.abs(d.energy - 4f) < 0.01f);

        // a drop: the finger leaves with the peak, but the phone comes to rest
        d = snatch().dev(t -> t == 1000 ? 25 : 0.1).run(new GrabDetector(P), 4000);
        check("drop is a candidate", d != null);
        check("drop does not trigger", d != null && !d.trigger && d.released);

        // the same pull with no finger on the screen is not a candidate at all
        d = new Scenario().dev(t -> t == 1000 ? 25 : 4).run(new GrabDetector(P), 4000);
        check("pull without touch is ignored", d == null);

        // the finger left long after the peak: a candidate, but not released in time
        d = new Scenario().touch(900, true).touch(1000, true).touch(1400, false)
                .dev(t -> t == 1000 ? 25 : 4).run(new GrabDetector(P), 4000);
        check("late release is a candidate", d != null);
        check("late release does not trigger", d != null && !d.trigger && !d.released);

        // the finger left 100 ms before the sensor saw the peak: still released
        d = new Scenario().touch(800, true).touch(900, true).touch(920, false)
                .dev(t -> t == 1000 ? 25 : t < 1000 ? 0.2 : 4).run(new GrabDetector(P), 4000);
        check("early release triggers", d != null && d.trigger && d.releaseDeltaMs == -80);

        // the finger left 200 ms before the peak: outside the interval
        d = new Scenario().touch(700, true).touch(780, false)
                .dev(t -> t == 1000 ? 25 : 4).run(new GrabDetector(P), 4000);
        check("release too early does not trigger", d != null && !d.trigger);

        // a peak below the threshold is not a candidate
        d = snatch().dev(t -> t == 1000 ? 14.9 : 4).run(new GrabDetector(P), 4000);
        check("small peak is ignored", d == null);

        // typing while walking: constant contact, deviation 3, never a peak
        d = new Scenario().touch(0, true).touch(2000, true).touch(4000, true)
                .dev(t -> 3).run(new GrabDetector(P), 4000);
        check("walking while typing is ignored", d == null);

        // the peak comparison is >=, exactly at the threshold is a candidate
        float zEdge = G + 15f;
        Params edge = new Params(devOf(zEdge), 400, 150, 200, 800, 1.5f, 2000);
        d = new Scenario().touch(900, true).touch(1000, true).touch(1020, false)
                .dev(t -> t == 1000 ? devOf(zEdge) : 4).run(new GrabDetector(edge), 4000);
        check("peak exactly at the threshold triggers", d != null && d.trigger);

        // the energy comparison is >=, exactly at the threshold triggers
        Params eEdge = new Params(15f, 400, 150, 200, 800, 2.0f, 2000);
        d = snatch().dev(t -> t == 1000 ? 25 : t < 1000 ? 0.2 : 2.0)
                .run(new GrabDetector(eEdge), 4000);
        check("energy exactly at the threshold triggers", d != null && d.trigger);
        d = snatch().dev(t -> t == 1000 ? 25 : t < 1000 ? 0.2 : 1.9)
                .run(new GrabDetector(eEdge), 4000);
        check("energy just below the threshold does not", d != null && !d.trigger);

        // the movement in the settle time is not counted: only the window after it
        d = snatch().dev(t -> t == 1000 ? 25 : t < 1200 ? 9 : 0.1).run(new GrabDetector(P), 4000);
        check("movement during the pull itself does not count", d != null && !d.trigger);

        // only one decision per pull, and a second pull inside the cool-down is ignored
        GrabDetector det = new GrabDetector(P);
        Scenario twice = new Scenario()
                .touch(500, true).touch(1000, true).touch(1020, false)
                .touch(2100, true).touch(2120, false)
                .dev(t -> (t == 1000 || t == 2100) ? 25 : t < 1000 ? 0.2 : 4);
        int decisions = 0;
        for (long t = 0; t <= 5000; t += STEP_MS) {
            for (Map.Entry<Long, Boolean> e : twice.touch.subMap(t - STEP_MS, false, t, true)
                    .entrySet()) {
                det.onTouch(e.getKey(), e.getValue());
            }
            if (det.onAccel(t, 0f, 0f, G + (float) twice.dev.applyAsDouble(t)) != null) {
                decisions++;
            }
        }
        check("a second pull inside the cool-down is ignored", decisions == 1);

        // a first sample at time 0 with no touch history must not overflow or throw
        check("first sample without touch history",
                new GrabDetector(P).onAccel(0, 0f, 0f, G + 30f) == null);

        // reset forgets the touch history: the pull after it has no touch in its window
        det = new GrabDetector(P);
        det.onTouch(900, true);
        det.reset();
        check("reset forgets the touch history",
                new Scenario().dev(t -> t == 1000 ? 25 : 4).run(det, 4000) == null);

        // rotation alone does not move the magnitude, so it is not a peak
        Params low = new Params(5f, 400, 150, 200, 800, 1.5f, 2000);
        check("a tilt at constant magnitude is ignored", runTilt(new GrabDetector(low)) == 0);

        // contact held since long before, with no move events, still counts as touching
        d = new Scenario().touch(500, true).touch(1520, false)
                .dev(t -> t == 1500 ? 25 : t < 1500 ? 0.2 : 4).run(new GrabDetector(P), 5000);
        check("held contact counts as touching", d != null && d.trigger);

        // the touch window: exactly at its edge counts, 20 ms outside it does not
        d = new Scenario().touch(600, true).touch(620, false)
                .dev(t -> t == 1000 ? 25 : 4).run(new GrabDetector(P), 4000);
        check("contact at the edge of the touch window is a candidate", d != null);
        d = new Scenario().touch(580, true).touch(600, false)
                .dev(t -> t == 1000 ? 25 : 4).run(new GrabDetector(P), 4000);
        check("contact outside the touch window is ignored", d == null);

        // the touch-loss interval: exactly at its edge counts, 20 ms outside it does not
        d = new Scenario().touch(900, true).touch(1000, true).touch(1150, false)
                .dev(t -> t == 1000 ? 25 : 4).run(new GrabDetector(P), 4000);
        check("release exactly at the interval after the peak counts",
                d != null && d.trigger && d.releaseDeltaMs == 150);
        d = new Scenario().touch(900, true).touch(1000, true).touch(1170, false)
                .dev(t -> t == 1000 ? 25 : 4).run(new GrabDetector(P), 4000);
        check("release just outside the interval after the peak does not",
                d != null && !d.trigger);
        d = new Scenario().touch(700, true).touch(850, false)
                .dev(t -> t == 1000 ? 25 : t < 1000 ? 0.2 : 4).run(new GrabDetector(P), 4000);
        check("release exactly at the interval before the peak counts",
                d != null && d.trigger && d.releaseDeltaMs == -150);

        // a release with no contact before it is noise and does not count
        d = new Scenario().touch(700, true).touch(720, false).touch(1010, false)
                .dev(t -> t == 1000 ? 25 : t < 1000 ? 0.2 : 4).run(new GrabDetector(P), 4000);
        check("a release without contact is ignored", d != null && !d.trigger);

        // the measured window is [peak + settle, peak + settle + sustain)
        Params one = new Params(15f, 400, 150, 200, 800, 1.0f, 2000);
        d = snatch().dev(t -> t == 1000 ? 25 : t == 1200 ? 42 : 0).run(new GrabDetector(one), 4000);
        check("the first sample after the settle time is measured", d != null && d.trigger);
        d = snatch().dev(t -> t == 1000 ? 25 : t == 2000 ? 84 : 0).run(new GrabDetector(P), 4000);
        check("the sample that ends the window is not measured", d != null && !d.trigger);

        // a drop in the magnitude (free fall) deviates from gravity just like a rise
        Params small = new Params(5f, 400, 150, 200, 800, 1.5f, 2000);
        d = snatch().dev(t -> t == 1000 ? -9 : t < 1000 ? 0.2 : 4).run(new GrabDetector(small), 4000);
        check("a drop in magnitude counts as a peak", d != null && d.trigger);

        System.out.println(failures == 0 ? "all passed" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
