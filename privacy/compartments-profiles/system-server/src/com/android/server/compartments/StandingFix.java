// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import java.util.Random;

/**
 * The fixes of a phone that stands at a place and has no GNSS fix (ADR-214, spec section 4.2): the position is a mean-reverting
 * random walk around a centre (the model of the Location Anchor design, ADR-037), the accuracy a bounded random walk of its own,
 * and the times lag the moment of delivery by a few tens of milliseconds, as those of a receiver do. There is no altitude, no
 * bearing, no extras and no mock flag; a receiver on the move would report more, and the delivery path never asks for it.
 *
 * <p>Every constant below is an {@code [assumption]} taken from what a stationary phone reports in the open: it has not been
 * measured against real fixes of this device, and the fix is not to be called indistinguishable before it has been. One instance
 * is used under the lock of one {@link PersonaTrack}, so it has none of its own. Pure Java.
 */
public final class StandingFix {
    /** What the fix imitates: the receiver of the phone, or the network location (coarser, and without a speed). */
    public enum Source {
        GNSS(4.5, 3.0, 12.0, true),
        NETWORK(28.0, 12.0, 70.0, false);

        final double medianAccM;
        final double minAccM;
        final double maxAccM;
        final boolean hasSpeed;

        Source(double medianAccM, double minAccM, double maxAccM, boolean hasSpeed) {
            this.medianAccM = medianAccM;
            this.minAccM = minAccM;
            this.maxAccM = maxAccM;
            this.hasSpeed = hasSpeed;
        }
    }

    /** One fix: the values that the delivery path puts into a {@code Location}. */
    public static final class Fix {
        public final double lat;
        public final double lon;
        public final float accuracyM;
        public final boolean hasSpeed;
        public final float speedMps;
        public final float speedAccuracyMps;
        public final long elapsedRealtimeNanos;
        public final long timeMs;

        Fix(double lat, double lon, float accuracyM, boolean hasSpeed, float speedMps, float speedAccuracyMps,
                long elapsedRealtimeNanos, long timeMs) {
            this.lat = lat;
            this.lon = lon;
            this.accuracyM = accuracyM;
            this.hasSpeed = hasSpeed;
            this.speedMps = speedMps;
            this.speedAccuracyMps = speedAccuracyMps;
            this.elapsedRealtimeNanos = elapsedRealtimeNanos;
            this.timeMs = timeMs;
        }
    }

    /** A second call this soon after a step returns the same position and accuracy, so two registrations agree. */
    static final double MIN_STEP_S = 0.2;
    /** Time constants of the pull back to the centre and of the accuracy. */
    static final double TAU_POS_S = 30.0;
    static final double TAU_ACC_S = 60.0;
    /** Spread of the logarithm of the accuracy. */
    static final double SIGMA_LN_ACC = 0.3;
    /**
     * Standard deviation of the position per axis, as a fraction of the accuracy that is reported: with 0.6 about 75% of a
     * still walk lies inside the accuracy, which Android defines as the radius of 68% confidence; the excursions take it to 70%.
     */
    static final double POS_SIGMA_PER_ACC = 0.6;
    /** A hard cap under the model, as a multiple of the accuracy: no pathological draw runs away. */
    static final double MAX_OFFSET_PER_ACC = 2.5;
    /** The chance per second of a larger excursion (multipath, a satellite change) that the pull back then removes. */
    static final double EXCURSION_PER_S = 0.01;
    static final long MIN_LATENCY_MS = 20;
    static final int LATENCY_SPAN_MS = 41;
    private static final double MIN_COS = 0.05;
    private static final double M_PER_DEG = Math.toRadians(Geo.EARTH_RADIUS_M);
    private static final long NANOS_PER_MS = 1_000_000L;

    private final Source source;
    private final Random rnd;
    private boolean started;
    private boolean freshStart;
    private double lnAcc;
    private double offNorthM;
    private double offEastM;
    private long stepAtNs;
    private long lastElapsedNs = Long.MIN_VALUE;

    StandingFix(Source source, Random rnd) {
        this.source = source;
        this.rnd = rnd;
    }

    /**
     * Starts again at the next fix. With {@code fresh} the first position is drawn from the spread of a stationary receiver
     * around the centre; without it the first position is the centre, because the centre is the last position that the app was
     * given and a standing fix then continues from it.
     */
    void reset(boolean fresh) {
        started = false;
        freshStart = fresh;
    }

    /** The next fix around the centre. {@code nowNs} is the elapsed time and {@code wallMs} the wall clock of the moment. */
    Fix next(double centreLat, double centreLon, long nowNs, long wallMs) {
        if (!started) {
            start(nowNs);
        } else {
            double dt = (nowNs - stepAtNs) / 1e9;
            if (dt >= MIN_STEP_S) {
                step(dt);
                stepAtNs = nowNs;
            }
        }
        double acc = Math.exp(lnAcc);
        double cosLat = Math.max(Math.cos(Math.toRadians(centreLat)), MIN_COS);
        double lat = Geo.clampLat(centreLat + offNorthM / M_PER_DEG);
        double lon = Geo.wrapLon(centreLon + offEastM / (M_PER_DEG * cosLat));

        long latencyMs = MIN_LATENCY_MS + rnd.nextInt(LATENCY_SPAN_MS);
        long elapsed = nowNs - latencyMs * NANOS_PER_MS;
        if (elapsed <= lastElapsedNs) {
            elapsed = lastElapsedNs + NANOS_PER_MS;
        }
        lastElapsedNs = elapsed;
        long timeMs = wallMs - (nowNs - elapsed) / NANOS_PER_MS;

        float speed = 0f;
        float speedAcc = 0f;
        if (source.hasSpeed) {
            speed = rnd.nextBoolean() ? 0f : (float) (Math.abs(rnd.nextGaussian()) * 0.05);
            speedAcc = (float) (0.1 + 0.2 * rnd.nextDouble());
        }
        return new Fix(lat, lon, (float) acc, source.hasSpeed, speed, speedAcc, elapsed, timeMs);
    }

    private void start(long nowNs) {
        started = true;
        stepAtNs = nowNs;
        lnAcc = clampLn(Math.log(source.medianAccM) + SIGMA_LN_ACC * rnd.nextGaussian());
        offNorthM = 0.0;
        offEastM = 0.0;
        if (freshStart) {
            double sigma = Math.exp(lnAcc) * POS_SIGMA_PER_ACC;
            offNorthM = sigma * rnd.nextGaussian();
            offEastM = sigma * rnd.nextGaussian();
            cap();
        }
    }

    private void step(double dt) {
        double mu = Math.log(source.medianAccM);
        double rhoAcc = Math.exp(-dt / TAU_ACC_S);
        lnAcc = clampLn(mu + rhoAcc * (lnAcc - mu)
                + SIGMA_LN_ACC * Math.sqrt(1.0 - rhoAcc * rhoAcc) * rnd.nextGaussian());
        double acc = Math.exp(lnAcc);
        double rho = Math.exp(-dt / TAU_POS_S);
        double k = acc * POS_SIGMA_PER_ACC * Math.sqrt(1.0 - rho * rho);
        offNorthM = rho * offNorthM + k * rnd.nextGaussian();
        offEastM = rho * offEastM + k * rnd.nextGaussian();
        if (rnd.nextDouble() < EXCURSION_PER_S * dt) {
            double angle = 2.0 * Math.PI * rnd.nextDouble();
            double size = acc * (0.5 + 0.75 * rnd.nextDouble());
            offNorthM += size * Math.cos(angle);
            offEastM += size * Math.sin(angle);
        }
        cap();
    }

    private void cap() {
        double max = Math.exp(lnAcc) * MAX_OFFSET_PER_ACC;
        double r = Math.hypot(offNorthM, offEastM);
        if (r > max) {
            offNorthM *= max / r;
            offEastM *= max / r;
        }
    }

    private double clampLn(double ln) {
        return Math.max(Math.log(source.minAccM), Math.min(Math.log(source.maxAccM), ln));
    }
}
