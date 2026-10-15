// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

public final class LimbCheck {
    private static void near(double expected, double actual, double tol, String what) {
        if (!(Math.abs(expected - actual) <= tol)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static double at(double degrees) {
        return GlobeMath.limbAlpha(Math.cos(Math.toRadians(degrees)));
    }

    public static void main(String[] args) {
        // the rule of the design page: full to 70 degrees (spec 3, ADR-159 (8)) from the middle, then straight down to 0 at 90
        near(1.0, GlobeMath.limbAlpha(1.0), 1e-9, "the middle of the globe");
        near(1.0, at(60), 1e-9, "60 degrees is full");
        near(1.0, at(69.9), 1e-9, "just under 70 degrees is full");
        near(1.0, at(70), 1e-9, "70 degrees is the first angle of the fade");
        near(0.5, at(80), 1e-9, "80 degrees is half");
        near(0.0, at(90), 1e-9, "the horizon");
        near(0.0, GlobeMath.limbAlpha(-0.3), 1e-9, "behind the globe");
        near(0.0, GlobeMath.limbAlpha(-5), 1e-9, "a depth that cannot exist");
        double prev = 2;
        for (double deg = 0; deg <= 90.001; deg += 0.5) {
            double a = at(deg);
            if (a > prev + 1e-12) {
                throw new AssertionError("the opacity grows toward the horizon at " + deg);
            }
            prev = a;
        }
        if (!GlobeMath.tappable(0.5) || !GlobeMath.tappable(1.0)) throw new AssertionError("half is a target");
        if (GlobeMath.tappable(0.49) || GlobeMath.tappable(0.0)) throw new AssertionError("less than half is not");
        System.out.println("PASS LimbCheck");
    }
}
