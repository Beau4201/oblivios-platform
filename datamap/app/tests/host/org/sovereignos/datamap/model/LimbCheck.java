// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

public final class LimbCheck {
    private static void near(double expected, double actual, double tol, String what) {
        if (!(Math.abs(expected - actual) <= tol)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        near(1.0, GlobeMath.limbAlpha(1.0), 1e-9, "the middle of the globe");
        near(1.0, GlobeMath.limbAlpha(Math.cos(Math.toRadians(70))), 1e-9, "70 degrees is still full");
        near(1.0, GlobeMath.limbAlpha(Math.cos(Math.toRadians(60))), 1e-9, "60 degrees is full");
        near(0.5077, GlobeMath.limbAlpha(Math.cos(Math.toRadians(80))), 0.001, "80 degrees is about half");
        near(0.0, GlobeMath.limbAlpha(0.0), 1e-9, "the horizon");
        near(0.0, GlobeMath.limbAlpha(-0.3), 1e-9, "behind the globe");
        // the gradient of the border lines uses these three radii
        near(1.0, GlobeMath.limbAlphaAtRadius(0.94), 0.01, "radius 0.94");
        near(0.5, GlobeMath.limbAlphaAtRadius(0.985), 0.01, "radius 0.985");
        near(0.0, GlobeMath.limbAlphaAtRadius(1.0), 1e-9, "the rim");
        near(0.0, GlobeMath.limbAlphaAtRadius(1.5), 1e-9, "outside the rim");
        near(1.0, GlobeMath.limbAlphaAtRadius(0.0), 1e-9, "the middle by radius");
        double prev = 2;
        for (double rho = 0; rho <= 1.0001; rho += 0.01) {
            double a = GlobeMath.limbAlphaAtRadius(rho);
            if (a > prev + 1e-12) {
                throw new AssertionError("the opacity grows toward the rim at " + rho);
            }
            prev = a;
        }
        if (!GlobeMath.tappable(0.5) || !GlobeMath.tappable(1.0)) throw new AssertionError("half is a target");
        if (GlobeMath.tappable(0.49) || GlobeMath.tappable(0.0)) throw new AssertionError("less than half is not");
        System.out.println("PASS LimbCheck");
    }
}
