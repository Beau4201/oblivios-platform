// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

public final class RotationCheck {
    private static void near(double expected, double actual, String what) {
        if (!(Math.abs(expected - actual) <= 1e-6)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        // the same orientation as GlobeMath.project, for any point and any view
        java.util.Random rnd = new java.util.Random(7);
        float[] u = new float[3];
        float[] o = new float[3];
        for (int i = 0; i < 500; i++) {
            double lat = rnd.nextDouble() * 180 - 90;
            double lon = rnd.nextDouble() * 360 - 180;
            double lat0 = rnd.nextDouble() * 140 - 70;
            double lon0 = rnd.nextDouble() * 360 - 180;
            GlobeMath.P p = GlobeMath.project(lat, lon, lat0, lon0);
            Rotation r = new Rotation(lon0, lat0);
            UnitVectors.of(lat, lon, u);
            r.apply(u[0], u[1], u[2], o);
            if (r.depth(u[0], u[1], u[2]) != o[2]) {
                throw new AssertionError("depth alone differs from the depth of apply");
            }
            if (Math.abs(p.x - o[0]) > 2e-6 || Math.abs(p.y - o[1]) > 2e-6 || Math.abs(p.depth - o[2]) > 2e-6) {
                throw new AssertionError("point " + lat + "," + lon + " view " + lat0 + "," + lon0
                        + ": " + p.x + "," + p.y + "," + p.depth + " against " + o[0] + "," + o[1] + "," + o[2]);
            }
        }
        // the land shader: from a screen position back to longitude and latitude with the rows, the way it does it
        float[] rows = new float[9];
        int seen = 0;
        for (int i = 0; i < 2000; i++) {
            double lat = rnd.nextDouble() * 170 - 85;
            double lon = rnd.nextDouble() * 360 - 180;
            double lat0 = rnd.nextDouble() * 140 - 70;
            double lon0 = rnd.nextDouble() * 360 - 180;
            Rotation r = new Rotation(lon0, lat0);
            UnitVectors.of(lat, lon, u);
            r.apply(u[0], u[1], u[2], o);
            if (o[2] < 0.05f) {
                continue;
            }
            seen++;
            r.rows(rows);
            double z = Math.sqrt(1 - o[0] * o[0] - o[1] * o[1]);
            double wx = rows[0] * o[0] + rows[3] * o[1] + rows[6] * z;
            double wy = rows[1] * o[0] + rows[4] * o[1] + rows[7] * z;
            double wz = rows[2] * o[0] + rows[5] * o[1] + rows[8] * z;
            double lat2 = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, wy))));
            double lon2 = Math.toDegrees(Math.atan2(wx, wz));
            double dLon = Math.abs(((lon2 - lon) % 360 + 540) % 360 - 180);
            if (Math.abs(lat2 - lat) > 0.02 || dLon > 0.02 / Math.cos(Math.toRadians(lat))) {
                throw new AssertionError("shader inverse " + lat + "," + lon + " gave " + lat2 + "," + lon2);
            }
        }
        if (seen < 500) {
            throw new AssertionError("too few visible points: " + seen);
        }
        // the view centre is at the middle of the disk and toward the viewer
        UnitVectors.of(10, 20, u);
        new Rotation(20, 10).apply(u[0], u[1], u[2], o);
        near(0, o[0], "centre x");
        near(0, o[1], "centre y");
        near(1, o[2], "centre depth");
        // unit vectors of a ring of longitude/latitude pairs
        float[] ring = UnitVectors.ring(new float[] {0, 0, 90, 0, 0, 90});
        near(0, ring[0], "lon 0 lat 0: x");
        near(0, ring[1], "lon 0 lat 0: y");
        near(1, ring[2], "lon 0 lat 0: z");
        near(1, ring[3], "lon 90 lat 0: x");
        near(1, ring[7], "lat 90: y");
        System.out.println("PASS RotationCheck");
    }
}
