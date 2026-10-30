// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

/**
 * The fake position of one app: the real track, moved to another place (ADR-208, spec section 4.2). The first real fix is
 * mapped to the place and every later fix keeps the real displacement in metres, so speed, stops and heading stay the real
 * ones. After the real position has moved more than {@link #REANCHOR_M} from the anchor the anchor moves with it, to the fake
 * position that the track has reached, so the fake track stays continuous and the east-west scaling stays accurate.
 */
public final class PersonaTrack {
    static final double REANCHOR_M = 100_000.0;
    private static final double MIN_COS = 0.05;

    private final double placeLat;
    private final double placeLon;
    private boolean anchored;
    private double realLat, realLon, fakeLat, fakeLon;

    PersonaTrack(double placeLat, double placeLon) {
        if (!(Math.abs(placeLat) <= 80.0) || Double.isNaN(placeLon)) {
            throw new IllegalArgumentException("place");
        }
        this.placeLat = placeLat;
        this.placeLon = Geo.wrapLon(placeLon);
    }

    /** The fake {latitude, longitude} for a real fix. A fix with a latitude or longitude that is not finite is refused. */
    public synchronized double[] apply(double lat, double lon) {
        if (!validFix(lat, lon)) {
            throw new IllegalArgumentException("fix");
        }
        lon = Geo.wrapLon(lon);
        if (!anchored) {
            setAnchor(lat, lon, placeLat, placeLon);
        } else if (Geo.distanceM(realLat, realLon, lat, lon) > REANCHOR_M) {
            double[] here = translate(lat, lon);
            setAnchor(lat, lon, here[0], here[1]);
        }
        return translate(lat, lon);
    }

    /** Like {@link #apply} but null for a fix that is not finite, so a delivery path never throws on a bad provider value. */
    public double[] tryApply(double lat, double lon) {
        return validFix(lat, lon) ? apply(lat, lon) : null;
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
