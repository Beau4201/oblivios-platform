/*
 * Copyright (C) 2026 FreeholdOS
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

    /** The number of decisions the detector gives for a scenario. */
    private static int countDecisions(GrabDetector d, Scenario s, long endMs) {
        int n = 0;
        for (long t = 0; t <= endMs; t += STEP_MS) {
            for (Map.Entry<Long, Boolean> e : s.touch.subMap(t - STEP_MS, false, t, true)
                    .entrySet()) {
                d.onTouch(e.getKey(), e.getValue());
            }
            if (d.onAccel(t, 0f, 0f, G + (float) s.dev.applyAsDouble(t)) != null) {
                n++;
            }
        }
        return n;
    }

    // a free fall of magnitude 0.3 m/s^2, far below FREE_FALL_MAX
    private static final double FALL = -9.5;

    /** Free fall at every step from fromMs to toMs inclusive, a hard impact (40) at each impact. */
    private static Scenario fallAndImpact(long fromMs, long toMs, long... impactsMs) {
        return new Scenario().dev(t -> {
            for (long i : impactsMs) {
                if (t == i) {
                    return 40;
                }
            }
            return t >= fromMs && t <= toMs ? FALL : 0.1;
        });
    }

    /** The first decision that came from the impact route, and the time it came at in {@code at[0]}. */
    private static Decision firstImpact(GrabDetector d, Scenario s, long endMs, long[] at) {
        for (long t = 0; t <= endMs; t += STEP_MS) {
            for (Map.Entry<Long, Boolean> e : s.touch.subMap(t - STEP_MS, false, t, true).entrySet()) {
                d.onTouch(e.getKey(), e.getValue());
            }
            Decision x = d.onAccel(t, 0f, 0f, G + (float) s.dev.applyAsDouble(t));
            if (x != null && x.impact) {
                at[0] = t;
                return x;
            }
        }
        return null;
    }

    private static java.util.List<Decision> decisions(GrabDetector d, Scenario s, long endMs) {
        java.util.List<Decision> out = new java.util.ArrayList<>();
        for (long t = 0; t <= endMs; t += STEP_MS) {
            for (Map.Entry<Long, Boolean> e : s.touch.subMap(t - STEP_MS, false, t, true)
                    .entrySet()) {
                d.onTouch(e.getKey(), e.getValue());
            }
            Decision x = d.onAccel(t, 0f, 0f, G + (float) s.dev.applyAsDouble(t));
            if (x != null) {
                out.add(x);
            }
        }
        return out;
    }

    /** ADR-195: an impact after a short free fall is a set-down and does not lock. */
    private static void freeFallChecks() {
        // 20 ms steps: 12 samples of free fall are 240 ms
        Params ff = new Params(15f, 400, 150, 200, 800, 1.5f, 2000, 30f, 240);
        Params strict = new Params(15f, 400, 150, 200, 800, 1.5f, 2000, 30f, 0);

        Decision d = fallAndImpact(0, -1, 1000).run(new GrabDetector(ff), 4000);
        check("an impact with no free fall locks", d != null && d.impact && d.trigger);
        check("an impact with no free fall reports none", d != null && d.freeFallMs == 0);

        d = fallAndImpact(900, 980, 1000).run(new GrabDetector(ff), 4000);
        check("an impact after a short free fall does not lock", d != null && d.impact && !d.trigger);
        check("a short free fall is reported in ms", d != null && d.freeFallMs == 100);

        d = fallAndImpact(900, 980, 1000).run(new GrabDetector(strict), 4000);
        check("level 0 (no long-fall limit) locks after a set-down too",
                d != null && d.impact && d.trigger && d.freeFallMs == 100);

        d = fallAndImpact(760, 980, 1000).run(new GrabDetector(ff), 4000);
        check("a free fall as long as the limit locks", d != null && d.trigger && d.freeFallMs == 240);
        d = fallAndImpact(780, 980, 1000).run(new GrabDetector(ff), 4000);
        check("a free fall one sample shorter than the limit does not",
                d != null && !d.trigger && d.freeFallMs == 220);
        d = fallAndImpact(700, 980, 1000).run(new GrabDetector(ff), 4000);
        check("a long fall locks", d != null && d.trigger && d.freeFallMs == 300);

        // the window: a fall that ended exactly 400 ms before the impact counts, 420 ms does not
        d = fallAndImpact(540, 600, 1000).run(new GrabDetector(ff), 4000);
        check("a fall that ended 400 ms before the impact counts", d != null && !d.trigger);
        d = fallAndImpact(520, 580, 1000).run(new GrabDetector(ff), 4000);
        check("a fall that ended 420 ms before the impact does not",
                d != null && d.trigger && d.freeFallMs == 0);

        // the longest fall inside the window counts, not the last one
        Scenario two = new Scenario().dev(t -> t == 1000 ? 40
                : (t >= 640 && t <= 860) || (t >= 900 && t <= 920) ? FALL : 0.1);
        d = two.run(new GrabDetector(ff), 4000);
        check("the longest free fall in the window counts", d != null && d.trigger && d.freeFallMs == 240);
        // an older long fall that left the window does not hide a newer short one
        Scenario old = new Scenario().dev(t -> t == 1000 ? 40
                : (t >= 100 && t <= 340) || (t >= 900 && t <= 920) ? FALL : 0.1);
        d = old.run(new GrabDetector(ff), 4000);
        check("a long fall outside the window does not count",
                d != null && !d.trigger && d.freeFallMs == 40);

        // the rest of the pulse of a set-down is not judged again; a later impact locks
        Scenario pulse = new Scenario().dev(t -> t >= 900 && t <= 980 ? FALL
                : t >= 1000 && t <= 1040 ? 40 : t == 1500 ? 40 : 0.1);
        java.util.List<Decision> l = decisions(new GrabDetector(ff), pulse, 4000);
        check("a set-down is decided once and the next impact locks",
                l.size() == 2 && !l.get(0).trigger && l.get(1).trigger && l.get(1).impact);

        // a fall that happens inside a cool-down still counts for the impact after it
        Scenario cd = new Scenario().dev(t -> t == 1000 || t == 3020 ? 40
                : t >= 2920 && t <= 2960 ? FALL : 0.1);
        l = decisions(new GrabDetector(ff), cd, 5000);
        check("a fall inside the cool-down counts for the next impact",
                l.size() == 2 && l.get(0).trigger && !l.get(1).trigger && l.get(1).freeFallMs == 60);

        // a set-down does not stop the pull rule: the candidate of the next samples still decides
        Scenario pull = new Scenario().touch(500, true).touch(1000, true).touch(1040, false)
                .dev(t -> t >= 900 && t <= 980 ? FALL : t == 1000 || t == 1020 ? 40 : t > 1020 ? 4 : 0.1);
        l = decisions(new GrabDetector(ff), pull, 4000);
        check("the pull rule still decides after a set-down",
                l.size() == 2 && l.get(0).impact && !l.get(0).trigger && !l.get(1).impact
                        && l.get(1).trigger);

        // reset forgets the free fall that has ended, and the quiet period of a set-down
        GrabDetector g = new GrabDetector(ff);
        for (long t = 900; t <= 980; t += STEP_MS) {
            g.onAccel(t, 0f, 0f, 0.3f);
        }
        g.onAccel(1000, 0f, 0f, G);
        g.reset();
        Decision x = g.onAccel(1020, 0f, 0f, G + 40f);
        check("reset forgets the free fall", x != null && x.trigger && x.freeFallMs == 0);
        g = new GrabDetector(ff);
        for (long t = 900; t <= 980; t += STEP_MS) {
            g.onAccel(t, 0f, 0f, 0.3f);
        }
        x = g.onAccel(1000, 0f, 0f, G + 40f);
        check("a set-down starts no cool-down", x != null && x.impact && !x.trigger);
        g.reset();
        x = g.onAccel(1100, 0f, 0f, G + 40f);
        check("reset forgets the quiet period", x != null && x.impact && x.trigger);

        // the quiet period of a set-down is over at its end: the impact there is judged again
        g = new GrabDetector(ff);
        for (long t = 900; t <= 980; t += STEP_MS) {
            g.onAccel(t, 0f, 0f, 0.3f);
        }
        g.onAccel(1000, 0f, 0f, G + 40f);
        x = g.onAccel(1249, 0f, 0f, G + 40f);
        check("inside the quiet period nothing is judged", x == null || !x.impact);
        x = g.onAccel(1250, 0f, 0f, G + 40f);
        check("at the end of the quiet period an impact is judged", x != null && x.impact);

        // with the eight argument constructor (no long-fall limit) a set-down locks, as before
        Params noLimit = new Params(15f, 400, 150, 200, 800, 1.5f, 2000, 30f);
        d = fallAndImpact(900, 980, 1000).run(new GrabDetector(noLimit), 4000);
        check("without a long-fall limit a set-down locks", d != null && d.impact && d.trigger);

        // the impact sample itself in free fall: the run that goes on counts
        Params lowPeak = new Params(5f, 400, 150, 200, 800, 1.5f, 2000, 8f, 240);
        g = new GrabDetector(lowPeak);
        for (long t = 900; t <= 980; t += STEP_MS) {
            g.onAccel(t, 0f, 0f, 3.9f);
        }
        x = g.onAccel(1000, 0f, 0f, 0.3f);
        check("an impact inside a free fall counts the fall", x != null && x.impact && x.freeFallMs == 120);

        // the limit of free fall is a magnitude below FREE_FALL_MAX: exactly at it is not a fall
        g = new GrabDetector(ff);
        for (long t = 900; t <= 980; t += STEP_MS) {
            g.onAccel(t, 0f, 0f, GrabDetector.FREE_FALL_MAX);
        }
        x = g.onAccel(1000, 0f, 0f, G + 40f);
        check("a magnitude exactly at the limit is not a free fall", x != null && x.trigger);
        g = new GrabDetector(ff);
        for (long t = 900; t <= 980; t += STEP_MS) {
            g.onAccel(t, 0f, 0f, GrabDetector.FREE_FALL_MAX - 0.1f);
        }
        x = g.onAccel(1000, 0f, 0f, G + 40f);
        check("a magnitude just below the limit is a free fall", x != null && !x.trigger);

        // all three axes count: a phone on its side at rest is not in free fall
        g = new GrabDetector(ff);
        for (long t = 900; t <= 980; t += STEP_MS) {
            g.onAccel(t, G, 0f, 0.1f);
        }
        x = g.onAccel(1000, 0f, 0f, G + 40f);
        check("a phone on its side is not in free fall", x != null && x.trigger);
    }

    /**
     * An impact with no free fall locks only after a pulse of minPulseMs (owner, 2026-10-29: picking the phone up from a table
     * locked it). Recorded on lynx: 12 grabs are 171 to 310 ms above 15 m/s^2, the spikes of 8 pickups and bumps 4 to 13 ms.
     */
    private static void pulseChecks() {
        // 20 ms steps; a deviation of 40 is a magnitude of about 50 m/s^2, 20 one of about 30: above 15, below impactPeak
        Params pw = new Params(15f, 400, 150, 200, 800, 1.5f, 2000, 30f, 240, 120);

        // a spike of one sample is a pickup or a bump: the verdict waits and then says no
        Scenario spike = new Scenario().dev(t -> t == 1000 ? 40 : 0.1);
        java.util.List<Decision> l = decisions(new GrabDetector(pw), spike, 4000);
        check("a one-sample spike is decided once", l.size() == 1);
        check("a one-sample spike does not lock", l.size() == 1 && l.get(0).impact && !l.get(0).trigger);
        check("a one-sample spike reports its pulse", l.size() == 1 && l.get(0).pulseMs == 20);
        check("a one-sample spike is decided when the window ends",
                spike.run(new GrabDetector(pw), 4000) != null && spike.decidedAtMs == 1000 + GrabDetector.PULSE_WINDOW_MS);

        // a pulse that goes on is a grab: the lock comes as soon as the pulse is long enough (20 ms of the first sample + 5 x 20)
        Scenario grab = new Scenario().dev(t -> t >= 1000 && t <= 1200 ? 40 : 0.1);
        Decision d = grab.run(new GrabDetector(pw), 4000);
        check("a long pulse locks", d != null && d.impact && d.trigger);
        check("a long pulse locks when it is 120 ms long", d != null && grab.decidedAtMs == 1100 && d.pulseMs == 120);
        d = new Scenario().dev(t -> t >= 1000 && t <= 1060 ? 40 : t >= 1080 && t <= 1200 ? 60 : 0.1).run(new GrabDetector(pw), 4000);
        check("a pulse reports its largest peak", d != null && d.trigger && Math.abs(d.peak - 60f) < 0.01f);
        check("a pulse one sample shorter does not lock",
                new Scenario().dev(t -> t >= 1000 && t <= 1080 ? 40 : 0.1).run(new GrabDetector(pw), 4000).trigger == false);

        // the rise before the first sample over impactPeak counts: 220 ms above 15 already, so the lock is at the impact sample
        // (the samples of the rise are over the peak of 15 and report a pull without touch first, so the impact is looked for)
        Scenario rise = new Scenario().dev(t -> t >= 800 && t < 1000 ? 20 : t == 1000 ? 40 : 0.1);
        long[] at = new long[1];
        d = firstImpact(new GrabDetector(pw), rise, 4000, at);
        check("the rise before the impact counts", d != null && d.trigger && at[0] == 1000 && d.pulseMs == 220);
        // but a rise that ended before is not part of it
        Scenario gap = new Scenario().dev(t -> t >= 800 && t < 900 ? 20 : t == 1000 ? 40 : 0.1);
        d = firstImpact(new GrabDetector(pw), gap, 4000, at);
        check("a rise that ended earlier does not count", d != null && !d.trigger && d.pulseMs == 20);

        // a real fall locks at once, whatever the pulse of the landing: the rule is for impacts with no free fall
        Scenario fall = fallAndImpact(700, 980, 1000);
        d = fall.run(new GrabDetector(pw), 4000);
        check("a real fall locks at once", d != null && d.trigger && fall.decidedAtMs == 1000 && d.freeFallMs == 300 && d.pulseMs == 0);

        // a set-down is still no lock, and decided at once as before
        Scenario down = fallAndImpact(900, 980, 1000);
        d = down.run(new GrabDetector(pw), 4000);
        check("a set-down does not lock", d != null && !d.trigger && d.freeFallMs == 100 && down.decidedAtMs == 1000);

        // the rule can be turned off: a spike locks at once
        d = spike.run(new GrabDetector(new Params(15f, 400, 150, 200, 800, 1.5f, 2000, 30f, 240, 0)), 4000);
        check("without a minimum pulse a spike locks at once", d != null && d.trigger && spike.decidedAtMs == 1000);
        check("without a minimum pulse the pulse is not measured", d != null && d.pulseMs == 0);

        // the boundaries: a rise of exactly the minimum locks at the impact sample, a magnitude of exactly 15 is part of the pulse
        final double at15 = 15f - G;
        Scenario edge = new Scenario().dev(t -> t >= 900 && t < 1000 ? 20 : t == 1000 ? 40 : 0.1);
        long[] where = new long[1];
        d = firstImpact(new GrabDetector(pw), edge, 4000, where);
        check("a rise of exactly the minimum locks at the impact sample", d != null && d.trigger && where[0] == 1000 && d.pulseMs == 120);
        Scenario exact = new Scenario().dev(t -> t == 1000 ? 40 : t > 1000 && t <= 1100 ? at15 : 0.1);
        d = firstImpact(new GrabDetector(pw), exact, 4000, where);
        check("samples of exactly 15 m/s^2 count for the pulse", d != null && d.trigger && where[0] == 1100 && d.pulseMs == 120);
        Scenario exactRise = new Scenario().dev(t -> t >= 900 && t < 1000 ? at15 : t == 1000 ? 40 : 0.1);
        d = firstImpact(new GrabDetector(pw), exactRise, 4000, where);
        check("samples of exactly 15 m/s^2 count for the rise", d != null && d.trigger && where[0] == 1000 && d.pulseMs == 120);

        // a pull candidate that was pending when the pulse locked is over: it does not decide a second time later
        Scenario candidate = new Scenario().touch(500, true)
                .dev(t -> t == 900 ? 20 : t >= 1000 && t <= 1200 ? 40 : 0.1);
        l = decisions(new GrabDetector(pw), candidate, 5000);
        check("a lock by pulse ends the pending candidate", l.size() == 1 && l.get(0).impact && l.get(0).trigger);

        // a refused spike keeps the rest of its pulse quiet: a second spike 20 ms after the refusal is not judged
        Scenario echo = new Scenario().dev(t -> t == 1000 || t == 1420 ? 40 : 0.1);
        l = decisions(new GrabDetector(pw), echo, 4000);
        check("the echo of a refused spike is not judged again", l.size() == 1 && !l.get(0).trigger);

        // arming again (reset) leaves no wait behind
        GrabDetector armed = new GrabDetector(pw);
        int count = 0;
        for (long t = 0; t <= 4000; t += STEP_MS) {
            if (t == 1040) {
                armed.reset();
            }
            if (armed.onAccel(t, 0f, 0f, G + (float) (t == 1000 ? 40 : 0.1)) != null) {
                count++;
            }
        }
        check("a reset forgets the wait", count == 0);

        // pulse time is summed inside the window: two spikes 300 ms apart are 40 ms of pulse, not enough
        Scenario two = new Scenario().dev(t -> t == 1000 || t == 1300 ? 40 : 0.1);
        l = decisions(new GrabDetector(pw), two, 4000);
        check("two spikes are not a pulse", l.size() == 1 && !l.get(0).trigger && l.get(0).pulseMs == 40);
        // a pulse made of samples around 15 that dip below it is summed: 6 samples above, 1 below, 1 above is 7 x 20 only counts above
        Scenario dip = new Scenario().dev(t -> t == 1000 ? 40 : t >= 1020 && t <= 1140 && t != 1080 ? 20 : 0.1);
        d = dip.run(new GrabDetector(pw), 4000);
        check("a dip below 15 inside the window does not reset the sum", d != null && d.trigger && d.pulseMs >= 120);

        // after the lock the cool-down holds; after a refusal the next grab still locks
        Scenario twice = new Scenario().dev(t -> t >= 1000 && t <= 1200 ? 40 : t >= 1400 && t <= 1600 ? 40 : 0.1);
        l = decisions(new GrabDetector(pw), twice, 4000);
        check("the cool-down holds after a lock", l.size() == 1 && l.get(0).trigger);
        Scenario after = new Scenario().dev(t -> t == 1000 ? 40 : t >= 2000 && t <= 2200 ? 40 : 0.1);
        l = decisions(new GrabDetector(pw), after, 4000);
        check("a refused spike does not stop the next grab",
                l.size() == 2 && !l.get(0).trigger && l.get(1).trigger && l.get(1).pulseMs == 120);
        // a refused spike is not a cool-down: a grab 100 ms after it, inside the window, is part of the same wait
        Scenario soon = new Scenario().dev(t -> t == 1000 ? 40 : t >= 1100 && t <= 1260 ? 40 : 0.1);
        d = soon.run(new GrabDetector(pw), 4000);
        check("pulses inside the window add up", d != null && d.trigger);
    }

    public static void main(String[] args) {
        // a pull out of the fingers that keeps moving triggers, and says why
        Scenario s = snatch();
        Decision d = s.run(new GrabDetector(P), 4000);
        // the verdict falls exactly settle + sustain (1000 ms) after the peak at 1000 ms
        check("snatch is decided at the end of the window", s.decidedAtMs == 2000);
        check("snatch decides", d != null);
        check("snatch triggers", d != null && d.trigger);
        check("snatch is marked touched", d != null && d.touched);
        check("snatch reports the peak", d != null && Math.abs(d.peak - 25f) < 0.01f);
        check("snatch reports the release", d != null && d.released && d.releaseDeltaMs == 20);
        check("snatch reports the energy", d != null && Math.abs(d.energy - 4f) < 0.01f);

        // a drop: the finger leaves with the peak, but the phone comes to rest
        d = snatch().dev(t -> t == 1000 ? 25 : 0.1).run(new GrabDetector(P), 4000);
        check("drop is a candidate", d != null && d.touched);
        check("drop does not trigger", d != null && !d.trigger && d.released);

        // the same pull with no finger on the screen is not a candidate: it is reported at the
        // peak, marked untouched, so that the shadow log can measure it
        s = new Scenario().dev(t -> t == 1000 ? 25 : 4);
        d = s.run(new GrabDetector(P), 4000);
        check("pull without touch is reported at the peak", d != null && s.decidedAtMs == 1000);
        check("pull without touch is not a trigger", d != null && !d.trigger && !d.touched);
        check("pull without touch reports the peak", d != null && Math.abs(d.peak - 25f) < 0.01f);

        // an untouched peak is reported once per cool-down, not for each sample of the same pull
        GrabDetector quiet = new GrabDetector(P);
        int reports = 0;
        for (long t = 0; t <= 5000; t += STEP_MS) {
            boolean peak = (t >= 1000 && t <= 1040) || t == 3100;
            if (quiet.onAccel(t, 0f, 0f, G + (peak ? 25f : 0.2f)) != null) {
                reports++;
            }
        }
        check("an untouched peak is reported once per cool-down", reports == 2);

        // the finger left long after the peak: a candidate, but not released in time
        d = new Scenario().touch(900, true).touch(1000, true).touch(1400, false)
                .dev(t -> t == 1000 ? 25 : 4).run(new GrabDetector(P), 4000);
        check("late release is a candidate", d != null && d.touched);
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
        d = new GrabDetector(P).onAccel(0, 0f, 0f, G + 30f);
        check("first sample without touch history", d != null && !d.touched && !d.trigger);

        // reset forgets the touch history: the pull after it has no touch in its window
        det = new GrabDetector(P);
        det.onTouch(900, true);
        det.reset();
        d = new Scenario().dev(t -> t == 1000 ? 25 : 4).run(det, 4000);
        check("reset forgets the touch history", d != null && !d.touched);

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
        check("contact at the edge of the touch window is a candidate", d != null && d.touched);
        d = new Scenario().touch(580, true).touch(600, false)
                .dev(t -> t == 1000 ? 25 : 4).run(new GrabDetector(P), 4000);
        check("contact outside the touch window is not a candidate",
                d != null && !d.touched && !d.trigger);

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

        // a new touch voids the release before it: a finger that is down again at the peak has
        // not been pulled out of the fingers (typing on a bus that takes a bump)
        d = new Scenario().touch(900, true).touch(940, false).touch(990, true).touch(1000, true)
                .dev(t -> t == 1000 ? 25 : t < 1000 ? 0.2 : 4).run(new GrabDetector(P), 4000);
        check("a release followed by a new touch does not count",
                d != null && !d.trigger && !d.released);

        // an impact triggers at once: no touch, and the phone stands still afterwards (the thief
        // grabs it and stops)
        Params imp = new Params(15f, 400, 150, 200, 800, 1.5f, 2000, 30f);
        s = new Scenario().dev(t -> t == 1000 ? 40 : 0.1);
        d = s.run(new GrabDetector(imp), 4000);
        check("an impact without touch triggers at the peak",
                d != null && d.trigger && s.decidedAtMs == 1000);
        check("an impact says it is an impact", d != null && d.impact && !d.touched);
        check("an impact reports its peak", d != null && Math.abs(d.peak - 40f) < 0.01f);

        // with a finger on the screen the impact decides once, and the pull rule that started
        // on the same peak does not decide a second time
        Scenario touchedImpact = new Scenario().touch(500, true).touch(1000, true)
                .touch(1020, false).dev(t -> t == 1000 ? 40 : 4);
        d = touchedImpact.run(new GrabDetector(imp), 4000);
        check("an impact with touch is marked touched", d != null && d.impact && d.touched);
        check("an impact with touch decides once",
                countDecisions(new GrabDetector(imp), touchedImpact, 4000) == 1);

        // an impact that arrives while a pull candidate is pending ends that candidate: one
        // decision, not a second one when the cool-down is over
        Scenario inside = new Scenario().touch(500, true).touch(1000, true)
                .dev(t -> t == 1000 ? 20 : t == 1200 ? 40 : 4);
        d = inside.run(new GrabDetector(imp), 5000);
        check("an impact inside a pending candidate decides", d != null && d.impact && d.trigger);
        check("an impact ends the pending candidate",
                countDecisions(new GrabDetector(imp), inside, 5000) == 1);

        // the comparison is >=: exactly at the threshold triggers, just below is the pull rule
        float zImp = G + 30f;
        Params impEdge = new Params(15f, 400, 150, 200, 800, 1.5f, 2000, devOf(zImp));
        d = new Scenario().dev(t -> t == 1000 ? devOf(zImp) : 0.1).run(new GrabDetector(impEdge), 4000);
        check("an impact exactly at the threshold triggers", d != null && d.impact && d.trigger);
        d = new Scenario().dev(t -> t == 1000 ? 29.9 : 0.1).run(new GrabDetector(imp), 4000);
        check("a peak just below the impact threshold does not trigger",
                d != null && !d.impact && !d.trigger);

        // a threshold of zero turns the route off: a hard peak is only reported
        d = new Scenario().dev(t -> t == 1000 ? 200 : 0.1).run(new GrabDetector(P), 4000);
        check("without an impact threshold a hard peak does not trigger",
                d != null && !d.impact && !d.trigger);

        // the cool-down holds for impacts: a second one inside it is ignored, one after it is not
        check("a second impact inside the cool-down is ignored", countDecisions(
                new GrabDetector(imp), new Scenario().dev(t -> (t == 1000 || t == 2100) ? 40 : 0.1),
                5000) == 1);
        check("an impact after the cool-down triggers again", countDecisions(
                new GrabDetector(imp), new Scenario().dev(t -> (t == 1000 || t == 3100) ? 40 : 0.1),
                5000) == 2);

        // the pull rule is untouched: a snatch that keeps moving still triggers, with no impact
        d = snatch().run(new GrabDetector(imp), 4000);
        check("the pull rule still triggers with the impact route on",
                d != null && d.trigger && !d.impact);

        freeFallChecks();
        pulseChecks();

        // the sample time is the sample's own timestamp moved by the sleep, not the moment the callback ran (review B6)
        check("a sample is placed by its own timestamp",
                GrabDetector.sensorUptimeMs(5_000_000_000L, 5_200L, 5_000L) == 4_800L);
        check("a late callback does not move the sample",
                GrabDetector.sensorUptimeMs(5_000_000_000L, 5_300L, 5_100L) == 4_800L);
        check("no timestamp falls back to the callback time", GrabDetector.sensorUptimeMs(0L, 5_200L, 5_000L) == 5_000L);

        System.out.println(failures == 0 ? "all passed" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
