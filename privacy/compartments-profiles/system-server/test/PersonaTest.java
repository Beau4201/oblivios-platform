// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import com.android.server.compartments.ActivityEstimator.Activity;

/** Host test of Geo, PersonaTrack and ActivityEstimator: java -ea, no framework. */
public final class PersonaTest {
    private static int checks = 0;

    private static void check(boolean cond, String what) {
        checks++;
        if (!cond) {
            throw new AssertionError(what);
        }
    }

    private static boolean throwsIllegal(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean near(double a, double b, double tol) {
        return Math.abs(a - b) <= tol;
    }

    public static void main(String[] args) {
        geo();
        track();
        activity();
        System.out.println("ok " + checks + " checks");
    }

    private static void geo() {
        check(near(Geo.distanceM(0, 0, 0, 1), 111195, 50), "one degree of longitude on the equator");
        check(near(Geo.distanceM(52, 5, 53, 5), 111195, 50), "one degree of latitude");
        check(Geo.distanceM(10, 20, 10, 20) == 0.0, "no distance");
        check(near(Geo.distanceM(0, 179.5, 0, -179.5), 111195, 50), "across the antimeridian");
        check(Geo.wrapLon(190) == -170 && Geo.wrapLon(-190) == 170 && Geo.wrapLon(180) == -180 && Geo.wrapLon(540) == -180,
                "longitude wraps");
        check(Geo.clampLat(91) == 90 && Geo.clampLat(-91) == -90 && Geo.clampLat(12.5) == 12.5, "latitude clamps");
    }

    private static void track() {
        // Amsterdam region -> Madrid
        PersonaTrack t = new PersonaTrack(40.4168, -3.7038);
        double[] first = t.apply(52.3676, 4.9041);
        check(near(first[0], 40.4168, 1e-9) && near(first[1], -3.7038, 1e-9), "the first fix is the place");
        double[] same = t.apply(52.3676, 4.9041);
        check(near(same[0], 40.4168, 1e-9) && near(same[1], -3.7038, 1e-9), "a still phone stays at the place");

        // 1 km north in reality is 1 km north of the place
        double north = 1000 / 111195.0;
        double[] n = t.apply(52.3676 + north, 4.9041);
        check(near(Geo.distanceM(first[0], first[1], n[0], n[1]), 1000, 15), "1 km north stays 1 km");
        check(n[0] > first[0] && near(n[1], first[1], 1e-9), "and north");

        // 1 km east at 52 N becomes 1 km east at 40 N: the longitude step is scaled
        double east = 1000 / (111195.0 * Math.cos(Math.toRadians(52.3676)));
        double[] e = t.apply(52.3676, 4.9041 + east);
        check(near(Geo.distanceM(first[0], first[1], e[0], e[1]), 1000, 15), "1 km east stays 1 km: " + Geo.distanceM(first[0], first[1], e[0], e[1]));
        check(e[1] > first[1], "and east");

        // far from the anchor the track re-anchors without a jump
        PersonaTrack far = new PersonaTrack(40.4168, -3.7038);
        far.apply(52.0, 5.0);
        double[] prev = far.apply(52.0, 5.0);
        double maxStep = 0;
        double realLat = 52.0, realLon = 5.0;
        for (int i = 0; i < 400; i++) {  // 400 steps of about 1 km east: 400 km, beyond the re-anchor distance
            realLon += 1000 / (111195.0 * Math.cos(Math.toRadians(realLat)));
            double[] cur = far.apply(realLat, realLon);
            maxStep = Math.max(maxStep, Geo.distanceM(prev[0], prev[1], cur[0], cur[1]));
            prev = cur;
        }
        check(maxStep < 1050, "no jump when the anchor moves: " + maxStep);
        check(maxStep > 950, "every step is about the real step: " + maxStep);

        // a real track across the antimeridian moves the fake track by the same distance
        PersonaTrack cross = new PersonaTrack(40.0, -3.0);
        double[] c0 = cross.apply(10.0, 179.95);
        double[] c1 = cross.apply(10.0, -179.95);
        double realD = Geo.distanceM(10.0, 179.95, 10.0, -179.95);
        check(near(Geo.distanceM(c0[0], c0[1], c1[0], c1[1]), realD, realD * 0.03), "the antimeridian is crossed in one step");

        // a track that reaches the pole re-anchors there without NaN or infinity
        PersonaTrack up = new PersonaTrack(79.0, 0.0);
        up.apply(10.0, 0.0);
        double prevLon = up.apply(10.0, 0.0)[1];
        for (int i = 1; i <= 12; i++) {
            double[] u = up.apply(10.0 + i, 0.0 + i * 0.1);
            check(Math.abs(Geo.wrapLon(u[1] - prevLon)) <= 3.0, "the longitude does not run away near the pole, step " + i);
            prevLon = u[1];
            check(!Double.isNaN(u[0]) && !Double.isNaN(u[1]) && !Double.isInfinite(u[0]) && !Double.isInfinite(u[1]) && u[0] <= 90.0,
                    "step " + i + " towards the pole");
        }

        // the antimeridian and the poles
        PersonaTrack wrap = new PersonaTrack(-17.7, 179.9);
        wrap.apply(10.0, 10.0);
        double[] w = wrap.apply(10.0, 10.0 + 0.5);
        check(w[1] >= -180 && w[1] < 180, "longitude stays in range across the antimeridian");
        PersonaTrack polar = new PersonaTrack(70.0, 20.0);
        polar.apply(89.9, 0.0);
        double[] p = polar.apply(90.0, 0.0);
        check(p[0] <= 90.0 && !Double.isNaN(p[0]) && !Double.isNaN(p[1]), "no NaN near the pole");

        check(throwsIllegal(() -> new PersonaTrack(85.0, 0)), "a place near the pole is refused");
        check(throwsIllegal(() -> new PersonaTrack(Double.NaN, 0)), "a NaN place is refused");
        check(throwsIllegal(() -> new PersonaTrack(10, 10).apply(Double.NaN, 0)), "a NaN fix is refused");
        check(throwsIllegal(() -> new PersonaTrack(10, 10).apply(91, 0)), "a latitude beyond 90 is refused");
        check(throwsIllegal(() -> new PersonaTrack(10, 10).apply(0, Double.POSITIVE_INFINITY)), "an infinite longitude is refused");

        PersonaTrack safe = new PersonaTrack(40.4168, -3.7038);
        check(safe.tryApply(Double.NaN, 0) == null, "tryApply: a NaN latitude gives null");
        check(safe.tryApply(91, 0) == null, "tryApply: a latitude beyond 90 gives null");
        check(safe.tryApply(0, Double.NaN) == null, "tryApply: a NaN longitude gives null");
        check(safe.tryApply(0, Double.NEGATIVE_INFINITY) == null, "tryApply: an infinite longitude gives null");
        double[] good = safe.tryApply(10.0, 10.0);
        check(good != null && Math.abs(good[0] - 40.4168) < 1e-9 && Math.abs(good[1] + 3.7038) < 1e-9,
                "tryApply: a good first fix is the place, and a refused one did not anchor");
    }

    private static void activity() {
        check(ActivityEstimator.classify(0.0, Activity.STILL) == Activity.STILL, "still");
        check(ActivityEstimator.classify(1.4, Activity.STILL) == Activity.WALKING, "walking");
        check(ActivityEstimator.classify(15.0, Activity.STILL) == Activity.DRIVING, "driving");
        check(ActivityEstimator.classify(0.5, Activity.WALKING) == Activity.WALKING, "hysteresis keeps walking at 0.5");
        check(ActivityEstimator.classify(0.5, Activity.STILL) == Activity.STILL, "and keeps still at 0.5");
        check(ActivityEstimator.classify(4.0, Activity.DRIVING) == Activity.DRIVING, "hysteresis keeps driving at 4");
        check(ActivityEstimator.classify(4.0, Activity.WALKING) == Activity.WALKING, "and keeps walking at 4");
        check(ActivityEstimator.classify(Double.NaN, Activity.WALKING) == Activity.WALKING, "NaN changes nothing");
        check(ActivityEstimator.classify(-1.0, Activity.DRIVING) == Activity.DRIVING, "a negative speed changes nothing");

        ActivityEstimator a = new ActivityEstimator();
        long t = 1000;
        check(a.onFix(1.5, t += 1000) == Activity.STILL, "one walking fix does not change it");
        check(a.onFix(1.5, t += 1000) == Activity.STILL, "two do not");
        check(a.onFix(1.5, t += 1000) == Activity.WALKING, "three do");
        check(a.onFix(0.0, t += 1000) == Activity.WALKING, "one still fix does not undo it");
        check(a.onFix(1.5, t += 1000) == Activity.WALKING, "a walking fix resets the count");
        check(a.onFix(0.0, t += 1000) == Activity.WALKING, "again one");
        check(a.onFix(0.0, t += 1000) == Activity.WALKING, "two");
        check(a.onFix(0.0, t += 1000) == Activity.STILL, "three still fixes");
        for (long past = 5; past < 8; past++) {
            check(a.onFix(1.5, past) == Activity.STILL, "a fix from the past is ignored");
        }
        long now = t;
        for (int i = 0; i < 3; i++) {
            a.onFix(10.0, now += 1000);
        }
        check(a.at(now) == Activity.DRIVING, "driving");
        check(a.at(now + ActivityEstimator.STALE_MS) == Activity.DRIVING, "not stale at the limit");
        check(a.at(now + ActivityEstimator.STALE_MS + 1) == Activity.STILL, "still when no fix has come");
        check(new ActivityEstimator().at(0) == Activity.STILL, "still before any fix");

        // P20: a fix after a stale gap starts from STILL again, so the hysteresis of the old state does not carry over
        ActivityEstimator b = new ActivityEstimator();
        long tb = 1000;
        for (int i = 0; i < 3; i++) {
            b.onFix(10.0, tb += 1000);
        }
        check(b.at(tb) == Activity.DRIVING, "driving before the gap");
        tb += ActivityEstimator.STALE_MS + 1;
        check(b.onFix(3.8, tb) == Activity.STILL, "the first fix after a gap does not keep driving by hysteresis");
        check(b.onFix(3.8, tb += 1000) == Activity.STILL && b.onFix(3.8, tb += 1000) == Activity.WALKING,
                "and three confirm the new activity from still");
        // a gap of exactly STALE_MS is not stale: the state stays
        ActivityEstimator c = new ActivityEstimator();
        long tc = 1000;
        for (int i = 0; i < 3; i++) {
            c.onFix(10.0, tc += 1000);
        }
        check(c.onFix(3.8, tc + ActivityEstimator.STALE_MS) == Activity.DRIVING, "a gap of exactly the stale limit keeps the state");
        // the half-confirmed change is dropped by a gap too: two walking fixes, a gap, one walking fix is not three in a row
        ActivityEstimator d = new ActivityEstimator();
        long td = 1000;
        d.onFix(1.5, td += 1000);
        d.onFix(1.5, td += 1000);
        td += ActivityEstimator.STALE_MS + 1;
        check(d.onFix(1.5, td) == Activity.STILL, "a half-confirmed change does not survive a gap");
    }
}
