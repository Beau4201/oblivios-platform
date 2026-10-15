// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

/** The two kinds of line on the map, with the numbers of the design page (ADR-160). */
public final class Curves {
    /** The line from "Your apps" is drawn in 36 steps and grows by drawing fewer of them. */
    public static final int ANCHOR_STEPS = 36;
    /** The arc between two places is 48 steps. */
    public static final int ARC_STEPS = 48;
    private static final double LIFT = 0.07;
    private static final double LIFT_FULL_AT_RADIANS = 0.9;

    private Curves() {}

    /**
     * The line from the anchor A to a place P: a cubic that leaves A almost straight up and arrives at P from below.
     *
     * @param grown 0 to 1: the share of the line that is drawn (1 when it stands still)
     * @param out room for {@code 2 * (ANCHOR_STEPS + 1)} numbers
     * @return the number of points written (x and y each), the first one being A
     */
    public static int anchorLine(float ax, float ay, float px, float py, float grown, float[] out) {
        double dy = ay - py;
        double dx = px - ax;
        double c1x = ax + dx * 0.04;
        double c1y = ay - dy * 0.55;
        double c2x = px - dx * 0.28;
        double c2y = py + dy * 0.22;
        int steps = Math.max(1, Math.round(ANCHOR_STEPS * grown));
        out[0] = ax;
        out[1] = ay;
        for (int i = 1; i <= steps; i++) {
            double u = (double) i / ANCHOR_STEPS;
            double v = 1 - u;
            out[2 * i] = (float) (v * v * v * ax + 3 * v * v * u * c1x + 3 * v * u * u * c2x + u * u * u * px);
            out[2 * i + 1] = (float) (v * v * v * ay + 3 * v * v * u * c1y + 3 * v * u * u * c2y + u * u * u * py);
        }
        return steps + 1;
    }

    /**
     * The great circle between two places as unit vectors, lifted off the globe in the middle (by up to 7 percent of
     * the radius, less for places close together) so that it arches over it.
     *
     * @param out room for {@code 3 * (ARC_STEPS + 1)} numbers
     */
    public static void surfaceArc(float[] a, float[] b, float[] out) {
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        double om = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
        double so = Math.sin(om);
        for (int i = 0; i <= ARC_STEPS; i++) {
            double u = (double) i / ARC_STEPS;
            double px;
            double py;
            double pz;
            if (so < 1e-6) {
                px = a[0];
                py = a[1];
                pz = a[2];
            } else {
                double k1 = Math.sin((1 - u) * om) / so;
                double k2 = Math.sin(u * om) / so;
                double lift = 1 + LIFT * Math.sin(Math.PI * u) * Math.min(1, om / LIFT_FULL_AT_RADIANS);
                px = (k1 * a[0] + k2 * b[0]) * lift;
                py = (k1 * a[1] + k2 * b[1]) * lift;
                pz = (k1 * a[2] + k2 * b[2]) * lift;
            }
            out[3 * i] = (float) px;
            out[3 * i + 1] = (float) py;
            out[3 * i + 2] = (float) pz;
        }
    }
}
