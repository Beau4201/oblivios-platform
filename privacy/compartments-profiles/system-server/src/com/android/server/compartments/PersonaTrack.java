// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import java.security.SecureRandom;
import java.util.Random;

/**
 * The fake position of one app: the real track, moved to another place (ADR-208, spec section 4.2). The first real fix is
 * mapped to the place and every later fix keeps the real displacement in metres, so speed, stops and heading stay the real
 * ones. After the real position has moved more than {@link #REANCHOR_M} from the anchor the anchor moves with it, to the fake
 * position that the track has reached, so the fake track stays continuous and the east-west scaling stays accurate.
 *
 * <p>ADR-214: while the phone has no real fix the track stands at the place, or where the last fix left it, and gives the
 * fixes of a stationary receiver ({@link #stand}); the first real fix after that is mapped to the position of the last
 * standing fix, so the app sees no jump. The track also says when the next standing fix is due ({@link #standingDelayMs}).
 */
public final class PersonaTrack {
    static final double REANCHOR_M = 100_000.0;
    /** No real fix for this long, and the phone is taken to have none (a cold start of a receiver takes longer than this). */
    static final long GRACE_MS = 3_000;
    /** A standing fix is never due sooner than this after the one before it on the same registration. */
    static final long MIN_INTERVAL_MS = 1_000;
    /** A request for fixes that is further apart than this is served at this spacing, so the arithmetic cannot overflow. */
    static final long MAX_INTERVAL_MS = 24L * 3_600_000L;
    /** After a real fix at this speed or more a gap is a gap (a tunnel), not a phone that stands still: no standing fix yet. */
    static final float MOVING_MPS = 1.5f;
    static final long MOVING_HOLD_MS = 120_000;
    private static final double MIN_COS = 0.05;
    private static final long NONE = Long.MIN_VALUE;

    private final double placeLat;
    private final double placeLon;
    private final StandingFix gnss;
    private final StandingFix network;
    private boolean anchored;
    private double realLat, realLon, fakeLat, fakeLon;
    // the last position that the app was given: a real fix moved to the place, or a standing fix of the GNSS kind
    private boolean hasLast;
    private double lastLat, lastLon;
    // a run of standing fixes: the centre it stands around, kept until a real fix ends it
    private boolean inEpisode;
    private double centreLat, centreLon;
    private long lastRealMs = NONE;
    private float lastRealSpeed = Float.NaN;
    private StandingFix.Fix lastGnssFix, lastNetworkFix;

    PersonaTrack(double placeLat, double placeLon) {
        this(placeLat, placeLon, new Random(new SecureRandom().nextLong()));
    }

    PersonaTrack(double placeLat, double placeLon, Random rnd) {
        if (!(Math.abs(placeLat) <= 80.0) || Double.isNaN(placeLon)) {
            throw new IllegalArgumentException("place");
        }
        this.placeLat = placeLat;
        this.placeLon = Geo.wrapLon(placeLon);
        this.gnss = new StandingFix(StandingFix.Source.GNSS, rnd);
        this.network = new StandingFix(StandingFix.Source.NETWORK, rnd);
    }

    /** The fake {latitude, longitude} for a real fix. A fix with a latitude or longitude that is not finite is refused. */
    public synchronized double[] apply(double lat, double lon) {
        if (!validFix(lat, lon)) {
            throw new IllegalArgumentException("fix");
        }
        lon = Geo.wrapLon(lon);
        if (!anchored) {
            // the first real fix continues from where the app was last put: the place, or the last standing fix
            setAnchor(lat, lon, hasLast ? lastLat : placeLat, hasLast ? lastLon : placeLon);
        } else if (Geo.distanceM(realLat, realLon, lat, lon) > REANCHOR_M) {
            double[] here = translate(lat, lon);
            setAnchor(lat, lon, here[0], here[1]);
        }
        double[] out = translate(lat, lon);
        hasLast = true;
        lastLat = out[0];
        lastLon = out[1];
        inEpisode = false;
        return out;
    }

    /** Like {@link #apply} but null for a fix that is not finite, so a delivery path never throws on a bad provider value. */
    public double[] tryApply(double lat, double lon) {
        return validFix(lat, lon) ? apply(lat, lon) : null;
    }

    /**
     * Notes that a real fix with this elapsed time and speed (NaN when it has none) has reached the app. A fix older than the
     * newest one noted changes nothing, so a last location from the past does not hold back the standing fixes.
     */
    public synchronized void noteReal(long elapsedRealtimeNanos, float speedMps) {
        long ms = elapsedRealtimeNanos / 1_000_000L;
        if (ms >= lastRealMs) {
            lastRealMs = ms;
            lastRealSpeed = speedMps;
        }
    }

    /**
     * Milliseconds until the next standing fix is due for a registration that became active at {@code activeMs} and was last
     * given a standing fix at {@code lastStandingMs} ({@link Long#MIN_VALUE} for none), asked at {@code nowMs}; zero or less
     * means now. All times are elapsed-realtime milliseconds. A standing fix is due {@link #GRACE_MS} after the registration
     * began or the last real fix, whichever is later, and then once per request interval; after a real fix at walking speed
     * or more it is held back for {@link #MOVING_HOLD_MS}.
     */
    public synchronized long standingDelayMs(long nowMs, long activeMs, long lastStandingMs, long intervalMs) {
        long due = Math.max(activeMs, lastRealMs) + GRACE_MS;
        if (lastRealSpeed >= MOVING_MPS) {  // NaN until a real fix has been noted
            due = Math.max(due, lastRealMs + MOVING_HOLD_MS);
        }
        due = Math.max(due, lastStandingMs + Math.min(Math.max(intervalMs, MIN_INTERVAL_MS), MAX_INTERVAL_MS));
        return due - nowMs;
    }

    /**
     * A standing fix: the position of a receiver that stands at the place, or where the last fix put the app, with the accuracy
     * and times of a real one. {@code elapsedNowNs} and {@code wallNowMs} are the moment it is made for. The first real fix after
     * a run of these continues from the last GNSS-kind one. The NETWORK kind does not move that position, so a coarse fix never
     * pulls the track away from the one that a navigation request is given.
     */
    public synchronized StandingFix.Fix stand(long elapsedNowNs, long wallNowMs, StandingFix.Source source) {
        if (!inEpisode) {
            centreLat = hasLast ? lastLat : placeLat;
            centreLon = hasLast ? lastLon : placeLon;
            gnss.reset(!hasLast);
            network.reset(!hasLast);
            inEpisode = true;
        }
        if (source == StandingFix.Source.GNSS) {
            lastGnssFix = gnss.next(centreLat, centreLon, elapsedNowNs, wallNowMs);
            hasLast = true;
            lastLat = lastGnssFix.lat;
            lastLon = lastGnssFix.lon;
            return lastGnssFix;
        }
        lastNetworkFix = network.next(centreLat, centreLon, elapsedNowNs, wallNowMs);
        return lastNetworkFix;
    }

    /** The newest standing fix of this kind, or null when there is none. */
    public synchronized StandingFix.Fix lastStanding(StandingFix.Source source) {
        return source == StandingFix.Source.GNSS ? lastGnssFix : lastNetworkFix;
    }

    private static boolean validFix(double lat, double lon) {
        return Math.abs(lat) <= 90.0 && !Double.isNaN(lon) && !Double.isInfinite(lon);
    }

    private void setAnchor(double rLat, double rLon, double fLat, double fLon) {
        realLat = rLat;
        realLon = rLon;
        fakeLat = Geo.clampLat(fLat);
        fakeLon = Geo.wrapLon(fLon);
        anchored = true;
    }

    private double[] translate(double lat, double lon) {
        double north = lat - realLat;
        double east = Geo.wrapLon(lon - realLon);
        double scale = Math.max(Math.cos(Math.toRadians(realLat)), MIN_COS) / Math.max(Math.cos(Math.toRadians(fakeLat)), MIN_COS);
        return new double[] {Geo.clampLat(fakeLat + north), Geo.wrapLon(fakeLon + east * scale)};
    }
}
