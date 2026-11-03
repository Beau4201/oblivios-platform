// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import com.android.server.compartments.StandingFix.Fix;
import com.android.server.compartments.StandingFix.Source;
import java.util.Random;

/** Host test of StandingFix and the standing part of PersonaTrack (ADR-214): java -ea, no framework. */
public final class StandingTest {
    private static final double PLACE_LAT = 23.1667;
    private static final double PLACE_LON = 79.9333;
    private static final long SECOND_NS = 1_000_000_000L;
    private static final double CAP = 2.5;
    private static int checks = 0;

    private static void check(boolean cond, String what) {
        checks++;
        if (!cond) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(double a, double b, double tol) {
        return Math.abs(a - b) <= tol;
    }

    private static double dist(Fix a, double lat, double lon) {
        return Geo.distanceM(a.lat, a.lon, lat, lon);
    }

    /** A Random whose draws the test sets: every gaussian, int and boolean is the same, the doubles come in order and the last repeats. */
    private static final class Scripted extends Random {
        private final double gauss;
        private final int anInt;
        private final boolean aBool;
        private final double[] doubles;
        private int next = 0;

        Scripted(double gauss, int anInt, boolean aBool, double... doubles) {
            this.gauss = gauss;
            this.anInt = anInt;
            this.aBool = aBool;
            this.doubles = doubles;
        }

        @Override
        public double nextGaussian() {
            return gauss;
        }

        @Override
        public int nextInt(int bound) {
            return Math.min(anInt, bound - 1);
        }

        @Override
        public boolean nextBoolean() {
            return aBool;
        }

        @Override
        public double nextDouble() {
            return doubles[Math.min(next++, doubles.length - 1)];
        }
    }

    public static void main(String[] args) {
        pinned();
        accuracyAndSpread();
        startAndRepeat();
        timestamps();
        speed();
        edges();
        continuity();
        scripted();
        timing();
        System.out.println("ok " + checks + " checks");
    }

    /** A run of fixes one second apart around the place. */
    private static Fix[] run(Source source, long seed, int n, double lat, double lon) {
        PersonaTrack t = new PersonaTrack(lat, lon, new Random(seed));
        Fix[] out = new Fix[n];
        for (int i = 0; i < n; i++) {
            out[i] = t.stand(10 * SECOND_NS + i * SECOND_NS, 1_800_000_000_000L + i * 1000L, source);
        }
        return out;
    }

    /** The model is pinned: a change of one of its numbers is a decision about the fix, and it comes with a new measurement. */
    private static void pinned() {
        check(StandingFix.Source.GNSS.medianAccM == 4.5 && StandingFix.Source.GNSS.minAccM == 3.0 && StandingFix.Source.GNSS.maxAccM == 12.0
                && StandingFix.Source.GNSS.hasSpeed, "the GNSS kind");
        check(StandingFix.Source.NETWORK.medianAccM == 28.0 && StandingFix.Source.NETWORK.minAccM == 12.0
                && StandingFix.Source.NETWORK.maxAccM == 70.0 && !StandingFix.Source.NETWORK.hasSpeed, "the network kind");
        check(StandingFix.MIN_STEP_S == 0.2 && StandingFix.TAU_POS_S == 30.0 && StandingFix.TAU_ACC_S == 60.0, "the time constants");
        check(StandingFix.SIGMA_LN_ACC == 0.3 && StandingFix.POS_SIGMA_PER_ACC == 0.6 && StandingFix.MAX_OFFSET_PER_ACC == 2.5, "the spreads");
        check(StandingFix.EXCURSION_PER_S == 0.01 && StandingFix.MIN_LATENCY_MS == 20 && StandingFix.LATENCY_SPAN_MS == 41, "the rest");
        check(PersonaTrack.GRACE_MS == 3_000 && PersonaTrack.MIN_INTERVAL_MS == 1_000 && PersonaTrack.MAX_INTERVAL_MS == 86_400_000L
                && PersonaTrack.MOVING_MPS == 1.5f && PersonaTrack.MOVING_HOLD_MS == 120_000, "the timing");
    }

    private static void accuracyAndSpread() {
        for (Source source : Source.values()) {
            double lo = source.minAccM, hi = source.maxAccM;
            int inside = 0, total = 0, moved = 0, big = 0;
            double sumAcc = 0, maxRatio = 0;
            double[] accs = new double[3 * 4000];
            for (long seed = 1; seed <= 3; seed++) {
                Fix[] fixes = run(source, seed, 4000, PLACE_LAT, PLACE_LON);
                for (int i = 0; i < fixes.length; i++) {
                    Fix f = fixes[i];
                    check(f.accuracyM >= lo - 1e-3 && f.accuracyM <= hi + 1e-3, source + " accuracy in its bounds: " + f.accuracyM);
                    double d = dist(f, PLACE_LAT, PLACE_LON);
                    // the cap: never beyond twice the accuracy that is reported (the accuracy moves slowly, so allow its step)
                    check(d <= CAP * f.accuracyM * 1.2 + 0.5, source + " offset " + d + " for accuracy " + f.accuracyM);
                    maxRatio = Math.max(maxRatio, d / f.accuracyM);
                    if (d <= f.accuracyM) {
                        inside++;
                    }
                    if (d > 1.2 * f.accuracyM) {
                        big++;
                    }
                    total++;
                    accs[total - 1] = f.accuracyM;
                    sumAcc += f.accuracyM;
                    if (i > 0 && (fixes[i - 1].lat != f.lat || fixes[i - 1].lon != f.lon)) {
                        moved++;
                        // the walk is continuous: a step is never more than the cap on both ends
                        check(Geo.distanceM(fixes[i - 1].lat, fixes[i - 1].lon, f.lat, f.lon) <= 2 * CAP * hi,
                                source + " a step of one second is small");
                    }
                }
            }
            check(inside >= 0.64 * total, source + " a bit more than two thirds of the fixes are inside the accuracy: " + inside + "/" + total);
            check(inside <= 0.73 * total, source + " but not nearly all: " + inside + "/" + total);
            check(big > 0, source + " some excursion beyond the accuracy occurs");
            check(maxRatio <= CAP * 1.2 + 0.2, source + " the cap holds: " + maxRatio);
            check(moved >= total - 3 - 50, source + " the position changes between fixes: " + moved);
            java.util.Arrays.sort(accs);
            double median = accs[accs.length / 2];
            check(median > source.medianAccM * 0.85 && median < source.medianAccM * 1.2, source + " median accuracy " + median);
            double mean = sumAcc / total;
            check(mean > lo && mean < hi, source + " mean accuracy in bounds");
            double p10 = accs[(int) (accs.length * 0.1)], p90 = accs[(int) (accs.length * 0.9)];
            check(p10 > median * 0.65 && p10 < median * 0.82, source + " the lower tail of the accuracy: " + p10 / median);
            check(p90 > median * 1.25 && p90 < median * 1.65, source + " the upper tail of the accuracy: " + p90 / median);
            // the accuracy is not one constant and not white noise: it drifts
            check(accs[(int) (accs.length * 0.9)] > accs[(int) (accs.length * 0.1)] * 1.2, source + " accuracy spreads");
        }
        // slow drift of the accuracy: two fixes one second apart differ by little, the run covers a wide range
        Fix[] f = run(Source.GNSS, 7, 3000, PLACE_LAT, PLACE_LON);
        double maxJump = 0;
        for (int i = 1; i < f.length; i++) {
            maxJump = Math.max(maxJump, Math.abs(f[i].accuracyM - f[i - 1].accuracyM));
        }
        check(maxJump < 3.0, "the accuracy changes slowly: " + maxJump);
    }

    private static void startAndRepeat() {
        // fresh: the first position is drawn around the centre, so it is not the centre itself
        int equal = 0;
        for (long seed = 1; seed <= 20; seed++) {
            Fix first = run(Source.GNSS, seed, 1, PLACE_LAT, PLACE_LON)[0];
            if (near(first.lat, PLACE_LAT, 1e-12) && near(first.lon, PLACE_LON, 1e-12)) {
                equal++;
            }
            check(dist(first, PLACE_LAT, PLACE_LON) <= CAP * first.accuracyM + 0.5, "the first fix is near the place");
        }
        check(equal == 0, "a fresh start is not exactly the place");

        // the first accuracy and both axes of the first position are drawn
        java.util.HashSet<Float> firstAcc = new java.util.HashSet<>();
        int latDiffers = 0, lonDiffers = 0;
        for (long seed = 100; seed < 300; seed++) {
            Fix f0 = run(Source.GNSS, seed, 1, PLACE_LAT, PLACE_LON)[0];
            firstAcc.add(f0.accuracyM);
            latDiffers += f0.lat != PLACE_LAT ? 1 : 0;
            lonDiffers += f0.lon != PLACE_LON ? 1 : 0;
        }
        check(firstAcc.size() > 150, "the first accuracy is drawn: " + firstAcc.size());
        check(latDiffers == 200 && lonDiffers == 200, "both axes of a fresh start are drawn: " + latDiffers + "/" + lonDiffers);
        // the network kind starts fresh too, and after a real fix at the centre
        Fix nf = run(Source.NETWORK, 3, 1, PLACE_LAT, PLACE_LON)[0];
        check(nf.lat != PLACE_LAT && nf.lon != PLACE_LON, "a fresh network start is drawn");
        PersonaTrack nt = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(4));
        double[] seen = nt.apply(10.0, 10.0);
        Fix nfirst = nt.stand(5 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        check(near(nfirst.lat, seen[0], 1e-12) && near(nfirst.lon, seen[1], 1e-12), "after a real fix the first network fix is there too");

        // not fresh (the app was put somewhere): the first standing fix is exactly there
        PersonaTrack t = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(3));
        double[] at = t.apply(52.0, 5.0);
        Fix first = t.stand(5 * SECOND_NS, 1_800_000_000_000L, Source.GNSS);
        check(near(first.lat, at[0], 1e-12) && near(first.lon, at[1], 1e-12), "after a real fix the first standing fix is where it left the app");

        // a second call within the minimum step gives the same position and accuracy, with newer times
        Fix again = t.stand(5 * SECOND_NS + 100_000_000L, 1_800_000_000_100L, Source.GNSS);
        check(again.lat == first.lat && again.lon == first.lon && again.accuracyM == first.accuracyM, "two calls within 0.2 s agree");
        check(again.elapsedRealtimeNanos > first.elapsedRealtimeNanos, "and the second is newer");
        Fix later = t.stand(5 * SECOND_NS + 200_000_000L, 1_800_000_000_200L, Source.GNSS);
        check(later.lat != first.lat || later.lon != first.lon, "0.2 s later the walk has stepped");
        Fix almost = t.stand(5 * SECOND_NS + 399_999_999L, 1_800_000_000_399L, Source.GNSS);
        check(almost.lat == later.lat && almost.lon == later.lon, "just under 0.2 s after a step it has not stepped again");
    }

    private static void timestamps() {
        PersonaTrack t = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(11));
        long lastElapsed = Long.MIN_VALUE;
        boolean saw20 = false, saw60 = false, varied = false;
        long prevLag = -1;
        for (int i = 0; i < 2000; i++) {
            long now = 100 * SECOND_NS + i * SECOND_NS;
            long wall = 1_800_000_000_000L + i * 1000L;
            Fix f = t.stand(now, wall, Source.GNSS);
            long lagMs = (now - f.elapsedRealtimeNanos) / 1_000_000L;
            check(lagMs >= 20 && lagMs <= 60, "the fix is made 20 to 60 ms before it is delivered: " + lagMs);
            check(f.elapsedRealtimeNanos > lastElapsed, "elapsed time increases");
            check(f.timeMs == wall - lagMs, "wall time lags by the same amount: " + (wall - f.timeMs) + " vs " + lagMs);
            saw20 |= lagMs == 20;
            saw60 |= lagMs == 60;
            varied |= prevLag >= 0 && lagMs != prevLag;
            prevLag = lagMs;
            lastElapsed = f.elapsedRealtimeNanos;
        }
        check(saw20 && saw60 && varied, "the lag takes its whole range and varies");

        // the same instant three times: still strictly increasing, and the wall time follows the elapsed time
        PersonaTrack u = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(12));
        Fix a = u.stand(50 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        Fix b = u.stand(50 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        Fix c = u.stand(50 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        check(b.elapsedRealtimeNanos > a.elapsedRealtimeNanos && c.elapsedRealtimeNanos > b.elapsedRealtimeNanos, "same instant: increasing");
        check(b.elapsedRealtimeNanos - a.elapsedRealtimeNanos >= 1_000_000L || b.elapsedRealtimeNanos - a.elapsedRealtimeNanos > 0,
                "by at least a step");
        check(b.timeMs - a.timeMs == (b.elapsedRealtimeNanos - a.elapsedRealtimeNanos) / 1_000_000L, "and the wall time follows");
    }

    private static void speed() {
        int zero = 0, nonzero = 0;
        for (Fix f : run(Source.GNSS, 5, 2000, PLACE_LAT, PLACE_LON)) {
            check(f.hasSpeed, "a GNSS fix has a speed");
            check(f.speedMps >= 0f && f.speedMps < 0.3f, "a stationary speed is small: " + f.speedMps);
            check(f.speedAccuracyMps >= 0.1f && f.speedAccuracyMps <= 0.3f + 1e-6f, "and has an accuracy: " + f.speedAccuracyMps);
            if (f.speedMps == 0f) {
                zero++;
            } else {
                nonzero++;
            }
        }
        check(zero > 600 && nonzero > 600, "the speed is sometimes exactly zero and sometimes not: " + zero + "/" + nonzero);
        for (Fix f : run(Source.NETWORK, 5, 200, PLACE_LAT, PLACE_LON)) {
            check(!f.hasSpeed && f.speedMps == 0f && f.speedAccuracyMps == 0f, "a network fix has no speed");
        }
    }

    private static void scripted() {
        // the extremes: a very large draw is held by the bounds of the accuracy and by the cap of the offset
        for (Source source : Source.values()) {
            for (double g : new double[] {10.0, -10.0}) {
                PersonaTrack t = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(g, 20, false, 0.5));
                double bound = g > 0 ? source.maxAccM : source.minAccM;
                for (int i = 0; i < 2; i++) {  // the start, and one step
                    Fix f = t.stand(10 * SECOND_NS + i * SECOND_NS, 1_800_000_000_000L + i * 1000L, source);
                    check(near(f.accuracyM, bound, 1e-3), source + " draw " + g + " is held at the bound: " + f.accuracyM);
                    check(near(dist(f, PLACE_LAT, PLACE_LON), CAP * bound, 0.1), source + " draw " + g + " is held by the cap: "
                            + dist(f, PLACE_LAT, PLACE_LON));
                }
            }
        }

        // a fresh start with a draw of one: the accuracy and the position follow the formulas
        PersonaTrack fresh = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(1.0, 20, false, 0.5));
        Fix f1 = fresh.stand(10 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        double acc1 = Math.exp(Math.log(Source.NETWORK.medianAccM) + 0.3);
        check(near(f1.accuracyM, acc1, 1e-3), "the first accuracy: " + f1.accuracyM + " vs " + acc1);
        double sigma1 = acc1 * 0.6;
        check(near(dist(f1, PLACE_LAT, PLACE_LON), Math.hypot(sigma1, sigma1), 0.05), "the first offset: " + dist(f1, PLACE_LAT, PLACE_LON));
        check(f1.lat > PLACE_LAT && f1.lon > PLACE_LON, "a draw of one is north and east");
        check(near((f1.lat - PLACE_LAT) * 111195.07, sigma1, 0.05), "north offset");
        check(near((f1.lon - PLACE_LON) * 111195.07 * Math.cos(Math.toRadians(PLACE_LAT)), sigma1, 0.05), "east offset");

        // the same, after a real fix: the offset starts at zero
        PersonaTrack kept = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(1.0, 20, false, 0.5));
        double[] at = kept.apply(10.0, 10.0);
        Fix k1 = kept.stand(10 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        check(near(k1.lat, at[0], 1e-12) && near(k1.lon, at[1], 1e-12) && near(k1.accuracyM, acc1, 1e-3), "not fresh: the centre, the same accuracy");
        // one step of one second
        Fix k2 = kept.stand(11 * SECOND_NS, 1_800_000_001_000L, Source.NETWORK);
        double mu = Math.log(Source.NETWORK.medianAccM);
        double rhoA = Math.exp(-1.0 / 60.0);
        double lnAcc2 = mu + rhoA * 0.3 + 0.3 * Math.sqrt(1 - rhoA * rhoA);
        double acc2 = Math.exp(lnAcc2);
        double rho = Math.exp(-1.0 / 30.0);
        double kk = acc2 * 0.6 * Math.sqrt(1 - rho * rho);
        check(near(k2.accuracyM, acc2, 1e-3), "the accuracy after a step: " + k2.accuracyM + " vs " + acc2);
        check(near((k2.lat - at[0]) * 111195.07, kk, 0.02), "north after a step: " + (k2.lat - at[0]) * 111195.07 + " vs " + kk);
        check(near((k2.lon - at[1]) * 111195.07 * Math.cos(Math.toRadians(at[0])), kk, 0.02), "east after a step");
        // a second step keeps what is left of the offset
        Fix k3 = kept.stand(12 * SECOND_NS, 1_800_000_002_000L, Source.NETWORK);
        double lnAcc3 = mu + rhoA * (lnAcc2 - mu) + 0.3 * Math.sqrt(1 - rhoA * rhoA);
        double acc3 = Math.exp(lnAcc3);
        double kk3 = acc3 * 0.6 * Math.sqrt(1 - rho * rho);
        check(near((k3.lat - at[0]) * 111195.07, rho * kk + kk3, 0.05), "north keeps the decayed offset: " + (k3.lat - at[0]) * 111195.07 + " vs " + (rho * kk + kk3));

        check(near((k3.lon - at[1]) * 111195.07 * Math.cos(Math.toRadians(at[0])), rho * kk + kk3, 0.05), "east keeps the decayed offset");

        // an excursion: east by 1.25 times the accuracy, then south by half of it, and none when the draw is too high
        double acc = Source.NETWORK.medianAccM;
        PersonaTrack ex = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(0.0, 20, false, 0.0, 0.25, 1.0));
        double[] c0 = ex.apply(10.0, 10.0);
        ex.stand(10 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        Fix e1 = ex.stand(11 * SECOND_NS, 1_800_000_001_000L, Source.NETWORK);
        check(near(Geo.distanceM(c0[0], c0[1], e1.lat, e1.lon), 1.25 * acc, 0.05), "excursion size: " + Geo.distanceM(c0[0], c0[1], e1.lat, e1.lon));
        check(near(e1.lat, c0[0], 1e-6) && e1.lon > c0[1], "the angle of a quarter turn is east");
        PersonaTrack ex2 = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(0.0, 20, false, 0.0, 0.5, 0.0));
        double[] c2 = ex2.apply(10.0, 10.0);
        ex2.stand(10 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        Fix e2 = ex2.stand(11 * SECOND_NS, 1_800_000_001_000L, Source.NETWORK);
        check(near(Geo.distanceM(c2[0], c2[1], e2.lat, e2.lon), 0.5 * acc, 0.05) && e2.lat < c2[0] && near(e2.lon, c2[1], 1e-6),
                "a half turn is south, at half the accuracy");
        PersonaTrack ex3 = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(0.0, 20, false, 0.5, 0.0, 0.0));
        double[] c3 = ex3.apply(10.0, 10.0);
        ex3.stand(10 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        Fix e3 = ex3.stand(11 * SECOND_NS, 1_800_000_001_000L, Source.NETWORK);
        check(near(e3.lat, c3[0], 1e-12) && near(e3.lon, c3[1], 1e-12), "a draw of 0.5 is no excursion in one second");
        PersonaTrack ex4 = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(0.0, 20, false, 0.5, 0.0, 0.0));
        double[] c4 = ex4.apply(10.0, 10.0);
        ex4.stand(10 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        Fix e4 = ex4.stand(110 * SECOND_NS, 1_800_000_100_000L, Source.NETWORK);
        check(Geo.distanceM(c4[0], c4[1], e4.lat, e4.lon) > 0.4 * acc && e4.lat > c4[0],
                "after 100 s the chance is one: the draw of 0.5 is an excursion, north at half the accuracy");

        // the times: a lag of 20 ms and of 60 ms, and the same instant three times
        for (int[] lag : new int[][] {{0, 20}, {99, 60}}) {
            Fix f = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(0.0, lag[0], false, 0.5))
                    .stand(77 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
            check(f.elapsedRealtimeNanos == 77 * SECOND_NS - lag[1] * 1_000_000L && f.timeMs == 1_800_000_000_000L - lag[1],
                    "the lag " + lag[1] + " ms");
        }
        PersonaTrack same = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(0.0, 0, false, 0.5));
        Fix s1 = same.stand(50 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        Fix s2 = same.stand(50 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        Fix s3 = same.stand(50 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        check(s2.elapsedRealtimeNanos - s1.elapsedRealtimeNanos == 1_000_000L && s3.elapsedRealtimeNanos - s2.elapsedRealtimeNanos == 1_000_000L,
                "the same instant: one millisecond apart");
        check(s1.timeMs == 1_800_000_000_000L - 20 && s2.timeMs == 1_800_000_000_000L - 19 && s3.timeMs == 1_800_000_000_000L - 18,
                "and the wall time follows");

        // the speed: a draw of 2 gives 0.1 m/s unless the coin says zero; the accuracy is 0.1 + 0.2 x the double
        Fix sp = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(2.0, 20, false, 0.5)).stand(5 * SECOND_NS, 1_800_000_000_000L, Source.GNSS);
        check(near(sp.speedMps, 0.1, 1e-6) && near(sp.speedAccuracyMps, 0.2, 1e-6) && sp.hasSpeed, "speed " + sp.speedMps + " " + sp.speedAccuracyMps);
        Fix sn = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(-2.0, 20, false, 0.5)).stand(5 * SECOND_NS, 1_800_000_000_000L, Source.GNSS);
        check(near(sn.speedMps, 0.1, 1e-6), "the speed is never negative: " + sn.speedMps);
        Fix sz = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(2.0, 20, true, 0.5)).stand(5 * SECOND_NS, 1_800_000_000_000L, Source.GNSS);
        check(sz.speedMps == 0f && near(sz.speedAccuracyMps, 0.2, 1e-6), "the coin says zero");
        Fix sh = new PersonaTrack(PLACE_LAT, PLACE_LON, new Scripted(0.0, 20, true, 1.0)).stand(5 * SECOND_NS, 1_800_000_000_000L, Source.GNSS);
        check(near(sh.speedAccuracyMps, 0.3, 1e-6), "the speed accuracy reaches 0.3");

        // near the pole the east offset in degrees stays small (the cosine is bounded)
        PersonaTrack pole = new PersonaTrack(70.0, 20.0, new Scripted(1.0, 20, false, 0.5));
        pole.apply(10.0, 0.0);
        double[] top = pole.apply(29.99, 0.0);
        check(near(top[0], 89.99, 1e-6), "the track is at 89.99 N");
        pole.stand(10 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        Fix pf = pole.stand(11 * SECOND_NS, 1_800_000_001_000L, Source.NETWORK);
        double dLon = Math.abs(Geo.wrapLon(pf.lon - top[1]));
        check(dLon > 1e-5 && dLon < 0.01, "the east step near the pole: " + dLon);
    }

    private static void edges() {
        // near the antimeridian and the northern limit of a place
        for (Fix f : run(Source.NETWORK, 9, 500, 79.9, 179.99999)) {
            check(f.lon >= -180.0 && f.lon < 180.0 && f.lat <= 90.0 && !Double.isNaN(f.lat) && !Double.isNaN(f.lon), "range near the edge");
            check(dist(f, 79.9, 179.99999) < 200, "and still near the place across the antimeridian: " + dist(f, 79.9, 179.99999));
        }
        for (Fix f : run(Source.GNSS, 9, 500, -80.0, -180.0)) {
            check(f.lon >= -180.0 && f.lon < 180.0 && f.lat >= -90.0 && !Double.isNaN(f.lat), "range at the southern limit");
        }
        // a long run keeps its bounds
        Fix[] long1 = run(Source.GNSS, 21, 50000, PLACE_LAT, PLACE_LON);
        for (Fix f : long1) {
            check(dist(f, PLACE_LAT, PLACE_LON) <= CAP * 12.0 + 1.0, "a long run stays near the place");
        }
        // a longitude step grows with the latitude: the metres east are the same at 70 N as at the equator
        double maxEast = 0;
        for (Fix f : run(Source.GNSS, 31, 3000, 70.0, 20.0)) {
            maxEast = Math.max(maxEast, Math.abs(Geo.distanceM(70.0, 20.0, 70.0, f.lon)));
        }
        check(maxEast > 3.0 && maxEast < 30.0, "the east spread at 70 N is in metres, not degrees: " + maxEast);
    }

    private static void continuity() {
        // never anchored: stand, then the first real fix is mapped to the last standing position, and then it moves with it
        PersonaTrack t = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(41));
        Fix last = null;
        for (int i = 0; i < 100; i++) {
            last = t.stand(10 * SECOND_NS + i * SECOND_NS, 1_800_000_000_000L + i * 1000L, Source.GNSS);
        }
        double[] first = t.apply(52.0, 5.0);
        check(near(first[0], last.lat, 1e-12) && near(first[1], last.lon, 1e-12), "the first real fix continues from the last standing fix");
        double[] north = t.apply(52.0 + 100 / 111195.0, 5.0);
        check(near(Geo.distanceM(first[0], first[1], north[0], north[1]), 100, 1.0), "and moves as the real track moves");

        // anchored and then no fix: stand at the last position, and a real fix at the same real place returns to it
        PersonaTrack u = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(42));
        u.apply(52.0, 5.0);
        double[] seen = u.apply(52.0, 5.0);
        Fix s = null;
        for (int i = 0; i < 200; i++) {
            s = u.stand(10 * SECOND_NS + i * SECOND_NS, 1_800_000_000_000L + i * 1000L, Source.GNSS);
            check(dist(s, seen[0], seen[1]) <= CAP * s.accuracyM * 1.2 + 0.5, "standing stays around where the track was");
        }
        double[] back = u.apply(52.0, 5.0);
        check(near(back[0], seen[0], 1e-12) && near(back[1], seen[1], 1e-12), "a real fix at the same real place maps to the same fake place");
        check(Geo.distanceM(s.lat, s.lon, back[0], back[1]) <= CAP * s.accuracyM * 1.2 + 0.5, "the jump is within the accuracy");

        // the NETWORK kind never moves the position that a real fix continues from
        PersonaTrack v = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(43));
        Fix g = v.stand(10 * SECOND_NS, 1_800_000_000_000L, Source.GNSS);
        Fix n = v.stand(11 * SECOND_NS, 1_800_000_001_000L, Source.NETWORK);
        check(n.lat != g.lat || n.lon != g.lon, "the network fix is a different position");
        double[] r = v.apply(10.0, 10.0);
        check(near(r[0], g.lat, 1e-12) && near(r[1], g.lon, 1e-12), "a real fix continues from the GNSS fix, not the network one");

        // network only: a real fix continues from the centre of the run
        PersonaTrack w = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(44));
        w.stand(10 * SECOND_NS, 1_800_000_000_000L, Source.NETWORK);
        w.stand(11 * SECOND_NS, 1_800_000_001_000L, Source.NETWORK);
        double[] c = w.apply(10.0, 10.0);
        check(near(c[0], PLACE_LAT, 1e-9) && near(c[1], PLACE_LON, 1e-9), "after network fixes only a real fix is mapped to the centre");

        // a real fix ends the run: the next run starts where that fix left the app, exactly
        PersonaTrack x = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(45));
        x.stand(10 * SECOND_NS, 1_800_000_000_000L, Source.GNSS);
        double[] real1 = x.apply(10.0, 10.0);
        double[] real2 = x.apply(10.001, 10.0);
        Fix next = x.stand(20 * SECOND_NS, 1_800_000_010_000L, Source.GNSS);
        check(near(next.lat, real2[0], 1e-12) && near(next.lon, real2[1], 1e-12), "the next run starts at the last real fix");
        check(real1[0] != real2[0], "(the real fixes differed)");
        Fix after = x.stand(21 * SECOND_NS, 1_800_000_011_000L, Source.GNSS);
        check(dist(after, real2[0], real2[1]) <= CAP * after.accuracyM, "and walks around it");

        // lastStanding
        check(new PersonaTrack(1, 1, new Random(1)).lastStanding(Source.GNSS) == null, "no standing fix yet");
        check(new PersonaTrack(1, 1, new Random(1)).lastStanding(Source.NETWORK) == null, "no network fix yet");
        check(v.lastStanding(Source.GNSS) == g && v.lastStanding(Source.NETWORK) == n, "the newest of each kind");
        check(u.lastStanding(Source.NETWORK) == null && u.lastStanding(Source.GNSS) == s, "a kind that was not asked for stays empty");
    }

    private static void timing() {
        long min = Long.MIN_VALUE;
        PersonaTrack t = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(51));
        // a registration with no fix: due three seconds after it became active
        check(t.standingDelayMs(10_000, 10_000, min, 5000) == 3000, "due 3 s after activation");
        check(t.standingDelayMs(12_999, 10_000, min, 5000) == 1, "1 ms to go");
        check(t.standingDelayMs(13_000, 10_000, min, 5000) == 0, "due now");
        check(t.standingDelayMs(14_000, 10_000, min, 5000) == -1000, "overdue");
        // then once per interval
        check(t.standingDelayMs(13_000, 10_000, 13_000, 5000) == 5000, "then every interval");
        check(t.standingDelayMs(13_000, 10_000, 13_000, 200) == 1000, "never sooner than one second");
        check(t.standingDelayMs(13_000, 10_000, 13_000, 1000) == 1000, "an interval of one second");
        check(t.standingDelayMs(13_000, 10_000, 13_000, 1001) == 1001, "an interval of 1001 ms");
        check(t.standingDelayMs(13_000, 10_000, 13_000, Long.MAX_VALUE) == PersonaTrack.MAX_INTERVAL_MS, "a huge interval is capped");
        check(t.standingDelayMs(13_000, 10_000, 13_000, PersonaTrack.MAX_INTERVAL_MS) == PersonaTrack.MAX_INTERVAL_MS, "at the cap itself");
        check(t.standingDelayMs(13_000, 10_000, 13_000, PersonaTrack.MAX_INTERVAL_MS - 1) == PersonaTrack.MAX_INTERVAL_MS - 1,
                "and just under it");
        check(t.standingDelayMs(13_000, 10_000, 13_000, 0) == 1000, "a zero interval");
        check(t.standingDelayMs(13_000, 10_000, 13_000, -5) == 1000, "a negative interval");
        // the activation later than the last standing fix wins when it is further out
        check(t.standingDelayMs(20_000, 19_000, 13_000, 1000) == 2000, "a later activation wins over an earlier standing fix");
        check(t.standingDelayMs(20_000, 10_000, 19_500, 1000) == 500, "an earlier activation loses to a later standing fix");

        // a real fix: three seconds after it, not after the activation
        t.noteReal(20_000L * 1_000_000L, 0.0f);
        check(t.standingDelayMs(21_000, 10_000, min, 5000) == 2000, "due 3 s after the last real fix");
        check(t.standingDelayMs(21_000, 20_500, min, 5000) == 2500, "or 3 s after the activation when that is later");
        check(t.standingDelayMs(23_000, 10_000, 22_000, 5000) == 4000, "and still once per interval");
        // an older real fix changes nothing
        t.noteReal(15_000L * 1_000_000L, 0.0f);
        check(t.standingDelayMs(21_000, 10_000, min, 5000) == 2000, "an older real fix is ignored");
        // a newer one moves it
        t.noteReal(21_000L * 1_000_000L, 0.0f);
        check(t.standingDelayMs(21_000, 10_000, min, 5000) == 3000, "a newer real fix moves it");
        // the same time updates the speed (a later report of the same fix)
        t.noteReal(21_000L * 1_000_000L, 2.0f);
        check(t.standingDelayMs(21_000, 10_000, min, 5000) == PersonaTrack.MOVING_HOLD_MS, "a fix at the same time counts: now moving");
        // moving: held back for two minutes after the real fix, whatever the activation
        check(t.standingDelayMs(21_000 + 60_000, 10_000, min, 5000) == PersonaTrack.MOVING_HOLD_MS - 60_000, "moving: held back");
        check(t.standingDelayMs(21_000 + PersonaTrack.MOVING_HOLD_MS, 10_000, min, 5000) == 0, "moving: due at the end of the hold");
        // once past the hold a later activation or standing fix still counts
        check(t.standingDelayMs(21_000 + PersonaTrack.MOVING_HOLD_MS, 10_000, 21_000 + PersonaTrack.MOVING_HOLD_MS, 5000) == 5000,
                "moving: then every interval");
        check(t.standingDelayMs(0, 10_000_000, min, 5000) == 10_003_000, "an activation after the hold wins");
        // the speed limit: just under is walking-slow, at it is moving
        PersonaTrack slow = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(52));
        slow.noteReal(1_000L * 1_000_000L, 1.49f);
        check(slow.standingDelayMs(1_000, 0, min, 5000) == 3000, "1.49 m/s is not moving");
        PersonaTrack limit = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(53));
        limit.noteReal(1_000L * 1_000_000L, 1.5f);
        check(limit.standingDelayMs(1_000, 0, min, 5000) == PersonaTrack.MOVING_HOLD_MS, "1.5 m/s is moving");
        PersonaTrack unknown = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(54));
        unknown.noteReal(1_000L * 1_000_000L, Float.NaN);
        check(unknown.standingDelayMs(1_000, 0, min, 5000) == 3000, "a real fix without a speed is not moving");
        // the speed of a real fix that was held for the hold and then replaced by a slow one
        PersonaTrack stopped = new PersonaTrack(PLACE_LAT, PLACE_LON, new Random(55));
        stopped.noteReal(1_000L * 1_000_000L, 20.0f);
        stopped.noteReal(5_000L * 1_000_000L, 0.1f);
        check(stopped.standingDelayMs(5_000, 0, min, 5000) == 3000, "a slow fix after a fast one ends the hold");
    }
}
