// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

public final class CurvesCheck {
    private static void near(double expected, double actual, double eps, String what) {
        if (!(Math.abs(expected - actual) <= eps)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        // the line from "Your apps" to a place: a cubic of 36 steps, from the anchor to the point
        float[] out = new float[2 * Curves.ANCHOR_STEPS + 2];
        int n = Curves.anchorLine(200, 600, 300, 200, 1f, out);
        near(37, n, 0, "points when fully grown");
        near(200, out[0], 1e-4, "starts at the anchor x");
        near(600, out[1], 1e-4, "starts at the anchor y");
        near(300, out[2 * (n - 1)], 1e-3, "ends at the place x");
        near(200, out[2 * (n - 1) + 1], 1e-3, "ends at the place y");
        // the middle of the cubic: c1 = (A.x + dx * 0.04, A.y - dy * 0.55), c2 = (P.x - dx * 0.28, P.y + dy * 0.22)
        double dx = 100;
        double dy = 400;
        double c1x = 200 + dx * 0.04;
        double c1y = 600 - dy * 0.55;
        double c2x = 300 - dx * 0.28;
        double c2y = 200 + dy * 0.22;
        double u = 18 / 36.0;
        double v = 1 - u;
        double mx = v * v * v * 200 + 3 * v * v * u * c1x + 3 * v * u * u * c2x + u * u * u * 300;
        double my = v * v * v * 600 + 3 * v * v * u * c1y + 3 * v * u * u * c2y + u * u * u * 200;
        near(mx, out[2 * 18], 1e-3, "middle x");
        near(my, out[2 * 18 + 1], 1e-3, "middle y");
        // growing: the line is cut at round(36 * e) steps, at least one
        near(19, Curves.anchorLine(200, 600, 300, 200, 0.5f, out), 0, "half grown");
        near(2, Curves.anchorLine(200, 600, 300, 200, 0f, out), 0, "not grown: one segment");
        near(2, Curves.anchorLine(200, 600, 300, 200, 0.01f, out), 0, "almost not grown");

        // the arc between two places lies on the globe, lifted in the middle by 7 percent at most
        float[] a = new float[3];
        float[] b = new float[3];
        UnitVectors.of(0, 0, a);
        UnitVectors.of(0, 90, b);
        float[] arc = new float[3 * (Curves.ARC_STEPS + 1)];
        Curves.surfaceArc(a, b, arc);
        near(1, Math.hypot(Math.hypot(arc[0], arc[1]), arc[2]), 1e-5, "starts on the surface");
        near(a[0], arc[0], 1e-5, "starts at the first place");
        int last = 3 * Curves.ARC_STEPS;
        near(b[0], arc[last], 1e-5, "ends at the second place");
        near(1, Math.hypot(Math.hypot(arc[last], arc[last + 1]), arc[last + 2]), 1e-5, "ends on the surface");
        int mid = 3 * (Curves.ARC_STEPS / 2);
        double r = Math.hypot(Math.hypot(arc[mid], arc[mid + 1]), arc[mid + 2]);
        near(1 + 0.07 * (Math.PI / 2 / 0.9 > 1 ? 1 : Math.PI / 2 / 0.9), r, 1e-5, "lift of a quarter of the globe");
        // places close together are lifted less: 0.45 radians gives half of the full lift
        UnitVectors.of(0, 0, a);
        UnitVectors.of(0, 25.783, b);
        Curves.surfaceArc(a, b, arc);
        double om = Math.toRadians(25.783);
        r = Math.hypot(Math.hypot(arc[mid], arc[mid + 1]), arc[mid + 2]);
        near(1 + 0.07 * (om / 0.9), r, 1e-4, "lift of a short arc");
        // the same place twice does not give NaN
        Curves.surfaceArc(a, a.clone(), arc);
        for (float x : arc) {
            if (Float.isNaN(x)) {
                throw new AssertionError("NaN for the same place");
            }
        }
        System.out.println("PASS CurvesCheck");
    }
}
