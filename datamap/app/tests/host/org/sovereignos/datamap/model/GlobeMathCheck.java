// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

public final class GlobeMathCheck {
    private static void near(double a, double b, double eps, String what) {
        if (Math.abs(a - b) > eps) {
            throw new AssertionError(what + ": " + a + " != " + b);
        }
    }

    public static void main(String[] args) {
        // the point at the centre of the view faces the viewer
        GlobeMath.P c = GlobeMath.project(10, 20, 10, 20);
        near(c.x, 0, 1e-12, "centre x");
        near(c.y, 0, 1e-12, "centre y");
        near(c.depth, 1, 1e-12, "centre depth");
        // the far side is not visible
        if (GlobeMath.project(0, 180, 0, 0).visible()) throw new AssertionError("antipode visible");
        // east is to the right, north is up
        if (!(GlobeMath.project(0, 30, 0, 0).x > 0)) throw new AssertionError("east not right");
        if (!(GlobeMath.project(30, 0, 0, 0).y > 0)) throw new AssertionError("north not up");
        // unproject inverts project for visible points, over a grid of views
        for (int lat0 = -80; lat0 <= 80; lat0 += 20) {
            for (int lon0 = -180; lon0 < 180; lon0 += 45) {
                for (int lat = -85; lat <= 85; lat += 17) {
                    for (int lon = -175; lon <= 175; lon += 35) {
                        GlobeMath.P p = GlobeMath.project(lat, lon, lat0, lon0);
                        if (p.depth < 0.02) continue;
                        double[] q = GlobeMath.unproject(p.x, p.y, lat0, lon0);
                        near(q[0], lat, 1e-6, "lat roundtrip");
                        near(GlobeMath.wrapLon(q[1] - lon), 0, 1e-6, "lon roundtrip");
                    }
                }
            }
        }
        if (GlobeMath.unproject(1.1, 0, 0, 0) != null) throw new AssertionError("off globe");
        // a point behind the globe is moved to the rim, a point in front stays
        double[] rim = GlobeMath.toRim(GlobeMath.project(0, 120, 0, 0));
        near(Math.hypot(rim[0], rim[1]), 1.0, 1e-12, "rim length");
        near(rim[0], 1.0, 1e-12, "rim direction east");
        double[] front = GlobeMath.toRim(GlobeMath.project(10, 10, 0, 0));
        near(front[0], GlobeMath.project(10, 10, 0, 0).x, 0, "front x unchanged");
        double[] behind = GlobeMath.toRim(GlobeMath.project(0, 180, 0, 0));
        near(Math.hypot(behind[0], behind[1]), 1.0, 1e-12, "straight behind is on the rim");
        near(GlobeMath.wrapLon(190), -170, 1e-12, "wrap east");
        near(GlobeMath.wrapLon(-190), 170, 1e-12, "wrap west");
        near(GlobeMath.wrapLon(540), -180, 1e-12, "wrap 540");
        near(GlobeMath.clampTilt(95), 80, 0, "tilt max");
        near(GlobeMath.clampTilt(-95), -80, 0, "tilt min");
        near(GlobeMath.clampZoom(0.2), 1, 0, "zoom min");
        near(GlobeMath.clampZoom(99), 6, 0, "zoom max");
        // an arc starts and ends on the surface and is highest in the middle
        double[][] arc = GlobeMath.arc(52.4, 4.9, 37.4, -122.1, 16, 0.25);
        near(arc[0][0], 52.4, 1e-9, "arc start lat");
        near(arc[16][0], 37.4, 1e-9, "arc end lat");
        near(arc[0][2], 0, 1e-12, "arc start lift");
        near(arc[16][2], 0, 1e-12, "arc end lift");
        if (!(arc[8][2] > arc[2][2])) throw new AssertionError("arc not highest in the middle");
        // two places at the same spot give a flat arc, not NaN
        double[][] same = GlobeMath.arc(10, 10, 10, 10, 4, 0.25);
        for (double[] p : same) {
            if (Double.isNaN(p[0]) || Double.isNaN(p[1]) || Math.abs(p[2]) > 1e-12) throw new AssertionError("same-spot arc");
        }
        // antipodal places do not produce NaN either
        for (double[] p : GlobeMath.arc(0, 0, 0, 180, 8, 0.25)) {
            if (Double.isNaN(p[0]) || Double.isNaN(p[1])) throw new AssertionError("antipodal arc");
        }
        // the angle between two places, for turning to a relation and choosing a zoom
        if (Math.abs(GlobeMath.angle(0, 0, 0, 90) - Math.PI / 2) > 1e-9) throw new AssertionError("a quarter turn");
        if (GlobeMath.angle(10, 20, 10, 20) != 0.0) throw new AssertionError("the same place");
        if (Math.abs(GlobeMath.angle(0, 179, 0, -179) - Math.toRadians(2)) > 1e-9) throw new AssertionError("across the date line");
        if (Math.abs(GlobeMath.angle(90, 0, -90, 0) - Math.PI) > 1e-9) throw new AssertionError("pole to pole");
        if (Math.abs(GlobeMath.zoomForSpan(0.1) - 2.6) > 1e-9) throw new AssertionError("close places: the largest zoom 2.6");
        if (Math.abs(GlobeMath.zoomForSpan(1.0) - 1.7) > 1e-9) throw new AssertionError("1.7 over the span");
        if (Math.abs(GlobeMath.zoomForSpan(3.0) - 1.5) > 1e-9) throw new AssertionError("far places: at least 1.5");
        System.out.println("PASS GlobeMathCheck");
    }
}
