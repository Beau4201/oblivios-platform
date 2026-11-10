// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

/** Places on the globe as unit vectors, the form {@link Rotation} turns: x = cos(lat) sin(lon), y = sin(lat), z = cos(lat) cos(lon). */
public final class UnitVectors {
    private UnitVectors() {}

    public static void of(double latDeg, double lonDeg, float[] out) {
        double la = Math.toRadians(latDeg);
        double lo = Math.toRadians(lonDeg);
        double c = Math.cos(la);
        out[0] = (float) (c * Math.sin(lo));
        out[1] = (float) Math.sin(la);
        out[2] = (float) (c * Math.cos(lo));
    }

    /** @param lonLat longitude, latitude pairs in degrees (the order of {@link LandData}) @return three floats per point */
    public static float[] ring(float[] lonLat) {
        int n = lonLat.length / 2;
        float[] out = new float[3 * n];
        float[] v = new float[3];
        for (int i = 0; i < n; i++) {
            of(lonLat[2 * i + 1], lonLat[2 * i], v);
            out[3 * i] = v[0];
            out[3 * i + 1] = v[1];
            out[3 * i + 2] = v[2];
        }
        return out;
    }
}
