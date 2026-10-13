// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.util.List;

/**
 * Where the globe first looks (ADR-160): centred between the two contacted places that lie farthest apart, so that
 * both are in front. When those two are opposite one another, at the average of all places; with none, at Europe
 * and Africa.
 */
public final class StartView {
    public static final double DEFAULT_LAT = 20;
    public static final double DEFAULT_LON = 10;
    /** The midpoint of two places closer to opposite than this (0.2 of a radius) is not a usable centre. */
    private static final double MIN_MIDPOINT = 0.2;

    private StartView() {}

    private static double[] unit(double[] p) {
        double lat = Math.toRadians(p[0]);
        double lon = Math.toRadians(p[1]);
        return new double[] {Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat)};
    }

    private static double[] view(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        double lat = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, z / len))));
        double lon = Math.toDegrees(Math.atan2(y, x));
        return new double[] {GlobeMath.clampTilt(lat), lon};
    }

    /**
     * @param places latitude and longitude in degrees, one pair per place
     * @return {latitude, longitude} of the view centre; the tilt is limited as the globe limits it
     */
    public static double[] of(List<double[]> places) {
        int n = places.size();
        if (n == 0) {
            return new double[] {DEFAULT_LAT, DEFAULT_LON};
        }
        double[][] u = new double[n][];
        for (int i = 0; i < n; i++) {
            u[i] = unit(places.get(i));
        }
        double best = 2;
        int a = 0;
        int b = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double d = u[i][0] * u[j][0] + u[i][1] * u[j][1] + u[i][2] * u[j][2];
                if (d < best) {
                    best = d;
                    a = i;
                    b = j;
                }
            }
        }
        double mx = u[a][0] + u[b][0];
        double my = u[a][1] + u[b][1];
        double mz = u[a][2] + u[b][2];
        if (Math.sqrt(mx * mx + my * my + mz * mz) > MIN_MIDPOINT) {
            return view(mx, my, mz);
        }
        double x = 0;
        double y = 0;
        double z = 0;
        for (double[] v : u) {
            x += v[0];
            y += v[1];
            z += v[2];
        }
        if (Math.sqrt(x * x + y * y + z * z) < 1e-6 * n) {   // everything cancels out
            return new double[] {DEFAULT_LAT, DEFAULT_LON};
        }
        return view(x, y, z);
    }
}
