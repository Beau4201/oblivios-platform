// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

public final class MotionCheck {
    private static void near(double expected, double actual, double eps, String what) {
        if (!(Math.abs(expected - actual) <= eps)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        // the ease of the design page: cubic in and out for a flight, cubic out for the lines
        near(0, Motion.inOut(0), 1e-12, "inOut 0");
        near(1, Motion.inOut(1), 1e-12, "inOut 1");
        near(0.5, Motion.inOut(0.5), 1e-12, "inOut half");
        near(0.032, Motion.inOut(0.2), 1e-12, "inOut slow start");
        near(0.968, Motion.inOut(0.8), 1e-12, "inOut slow end");
        near(0.875, Motion.outCubic(0.5), 1e-12, "outCubic");
        near(0, Motion.inOut(-1), 1e-12, "inOut is clamped below");
        near(1, Motion.inOut(2), 1e-12, "inOut is clamped above");

        // the durations of the page, in milliseconds
        near(320, Motion.FLY_PLACE, 0, "to a place");
        near(360, Motion.FLY_APP, 0, "to an app");
        near(400, Motion.FLY_BACK, 0, "after a choice is closed");
        near(450, Motion.FLY_HOME, 0, "centre the map");
        near(650, Motion.FLY_FULL, 0, "full screen");
        near(260, Motion.ZOOM, 0, "zoom");
        near(900, Motion.LINES, 0, "lines grow");

        // a finger moves the globe 0.4 degrees a pixel at zoom 1, less when zoomed in, and the other way round
        double[] d = Motion.drag(10, -5, 1);
        near(-4, d[0], 1e-12, "dragging right turns the view west");
        near(-2, d[1], 1e-12, "dragging up turns the view south");
        near(-2, Motion.drag(10, 0, 2)[0], 1e-12, "zoomed in, the globe turns less");

        // a fling starts after a real drag with some speed, and decays by 0.94 a frame until it is under 0.12
        if (Motion.Fling.starts(3, 5, 5)) {
            throw new AssertionError("a short move is a tap");
        }
        if (Motion.Fling.starts(20, 0.5, 0.5)) {
            throw new AssertionError("a slow finger does not fling");
        }
        if (!Motion.Fling.starts(20, 5, 0)) {
            throw new AssertionError("a drag with speed flings");
        }
        Motion.Fling f = new Motion.Fling(10, 0);
        double[] out = new double[2];
        boolean alive = f.step(Motion.FRAME_MS, 1, out);
        near(-4, out[0], 1e-9, "the first frame turns by v * 0.4");
        near(0, out[1], 1e-12, "no vertical speed");
        near(9.4, f.vx, 1e-9, "the speed decays by 0.94");
        if (!alive) {
            throw new AssertionError("still moving");
        }
        // half a frame does nothing yet, the other half completes it
        Motion.Fling g = new Motion.Fling(10, 0);
        g.step(Motion.FRAME_MS / 2, 1, out);
        near(0, out[0], 1e-12, "half a frame");
        g.step(Motion.FRAME_MS / 2 + 0.001, 1, out);
        near(-4, out[0], 1e-9, "the frame completes");
        // it stops: 10 * 0.94^n < 0.12 first holds at n = 72
        Motion.Fling h = new Motion.Fling(10, 0);
        int frames = 0;
        while (h.step(Motion.FRAME_MS, 1, out)) {
            frames++;
            if (frames > 1000) {
                throw new AssertionError("never stops");
            }
        }
        near(71, frames, 0, "frames until it stops (the last step returns false)");
        System.out.println("PASS MotionCheck");
    }
}
