// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

/** Great-circle helpers on a sphere of the mean earth radius. Pure Java. */
final class Geo {
    private Geo() {}

    static final double EARTH_RADIUS_M = 6371008.8;

    /** Distance in metres between two points in degrees (haversine). */
    static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        // haversine is periodic in the longitude difference, so it needs no wrapping
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Longitude in [-180, 180). */
    static double wrapLon(double lon) {
        double x = (lon + 180.0) % 360.0;
        if (x < 0) {
            x += 360.0;
        }
        return x - 180.0;
    }

    static double clampLat(double lat) {
        return Math.max(-90.0, Math.min(90.0, lat));
    }
}
