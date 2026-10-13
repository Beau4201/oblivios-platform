// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

/** The times and curves of the design page's motion (ADR-160), and the physics of a drag and a fling. */
public final class Motion {
    public static final int FLY_PLACE = 320;
    public static final int FLY_APP = 360;
    public static final int FLY_BACK = 400;
    public static final int FLY_HOME = 450;
    public static final int FLY_FULL = 650;
    public static final int ZOOM = 260;
    public static final int LINES = 900;
    /** One frame of the page, at 60 frames a second: the fling decays by one step for every one of them. */
    public static final double FRAME_MS = 1000.0 / 60;
    /** Degrees of rotation per dp of finger movement at zoom 1. */
    private static final double DEG_PER_DP = 0.4;

    private Motion() {}

    private static double clamp01(double u) {
        return Math.max(0.0, Math.min(1.0, u));
    }

    /** Cubic in and out: the flights. */
    public static double inOut(double u) {
        u = clamp01(u);
        return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
    }

    /** Cubic out: the lines that grow from "Your apps". */
    public static double outCubic(double t) {
        t = clamp01(t);
        return 1 - Math.pow(1 - t, 3);
    }

    /** The change of the view for a finger moving {@code dx}, {@code dy} dp: {longitude, latitude} in degrees. */
    public static double[] drag(double dx, double dy, double zoom) {
        double k = DEG_PER_DP / zoom;
        return new double[] {-dx * k, dy * k};
    }

    /** What a finger lifted after a drag does: a fling that carries on, then fades. */
    public static final class Fling {
        private static final double DECAY = 0.94;
        private static final double STOP = 0.12;
        public double vx;
        public double vy;
        private double acc;

        /** @param vx the last movement of the finger in dp per frame */
        public Fling(double vx, double vy) {
            this.vx = vx;
            this.vy = vy;
        }

        /** A drag of more than 6 dp whose last movement was faster than 1.2 dp per frame carries on. */
        public static boolean starts(double movedDp, double vx, double vy) {
            return movedDp > 6 && Math.hypot(vx, vy) > 1.2;
        }

        /**
         * Advances by {@code dtMs}: out[0] and out[1] receive the change of longitude and latitude in degrees.
         *
         * @return false once the fling has stopped
         */
        public boolean step(double dtMs, double zoom, double[] out) {
            out[0] = 0;
            out[1] = 0;
            acc += dtMs;
            while (acc >= FRAME_MS) {
                acc -= FRAME_MS;
                out[0] -= vx * DEG_PER_DP / zoom;
                out[1] += vy * DEG_PER_DP / zoom;
                vx *= DECAY;
                vy *= DECAY;
                if (Math.hypot(vx, vy) < STOP) {
                    return false;
                }
            }
            return true;
        }
    }
}
