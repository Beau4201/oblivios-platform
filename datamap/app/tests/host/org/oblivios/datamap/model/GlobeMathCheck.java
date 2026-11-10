// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

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
        near(GlobeMath.wrapLon(190), -170, 1e-12, "wrap east");
        near(GlobeMath.wrapLon(-190), 170, 1e-12, "wrap west");
        near(GlobeMath.wrapLon(540), -180, 1e-12, "wrap 540");
        // the limits of the page: tilt 70, zoom 0.8 to 2.4
        near(GlobeMath.clampTilt(95), 70, 0, "tilt max");
        near(GlobeMath.clampTilt(-95), -70, 0, "tilt min");
        near(GlobeMath.clampTilt(12), 12, 0, "tilt inside");
        near(GlobeMath.clampZoom(0.2), 0.8, 0, "zoom min");
        near(GlobeMath.clampZoom(99), 2.4, 0, "zoom max");
        near(GlobeMath.clampZoom(1.5), 1.5, 0, "zoom inside");
        if (Math.abs(GlobeMath.angle(0, 0, 0, 90) - Math.PI / 2) > 1e-9) throw new AssertionError("a quarter turn");
        if (GlobeMath.angle(10, 20, 10, 20) != 0.0) throw new AssertionError("the same place");
        if (Math.abs(GlobeMath.angle(0, 179, 0, -179) - Math.toRadians(2)) > 1e-9) throw new AssertionError("across the date line");
        if (Math.abs(GlobeMath.angle(90, 0, -90, 0) - Math.PI) > 1e-9) throw new AssertionError("pole to pole");
        System.out.println("PASS GlobeMathCheck");
    }
}
