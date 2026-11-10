// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

import java.util.ArrayList;
import java.util.List;

public final class StartViewCheck {
    private static void near(double expected, double actual, String what) {
        if (!(Math.abs(expected - actual) <= 1e-6)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static List<double[]> places(double... latLon) {
        List<double[]> l = new ArrayList<>();
        for (int i = 0; i < latLon.length; i += 2) {
            l.add(new double[] {latLon[i], latLon[i + 1]});
        }
        return l;
    }

    public static void main(String[] args) {
        double[] none = StartView.of(places());
        near(20, none[0], "no places: latitude");
        near(10, none[1], "no places: longitude");
        double[] one = StartView.of(places(37.3928, -122.0419));
        near(37.3928, one[0], "one place: latitude");
        near(-122.0419, one[1], "one place: longitude");
        double[] two = StartView.of(places(0, 0, 0, 90));
        near(0, two[0], "two places on the equator: latitude");
        near(45, two[1], "two places on the equator: longitude");
        double[] opposite = StartView.of(places(0, 0, 0, 180));
        near(20, opposite[0], "opposite places: default latitude");
        near(10, opposite[1], "opposite places: default longitude");
        double[] dateLine = StartView.of(places(0, 179, 0, -179));
        near(180, Math.abs(dateLine[1]), "across the date line");
        double[] pole = StartView.of(places(89, 0));
        near(GlobeMath.MAX_TILT, pole[0], "the tilt is limited");
        double[] same = StartView.of(places(10, 20, 10, 20, 10, 20));
        near(10, same[0], "the same place three times: latitude");
        near(20, same[1], "the same place three times: longitude");
        // the two places that lie farthest apart decide, not the average of all of them
        double[] far = StartView.of(places(0, 0, 0, 10, 0, 60));
        near(0, far[0], "farthest pair: latitude");
        near(30, far[1], "farthest pair: longitude");
        // 170 degrees apart: the middle is 85 degrees from both, so both are in front
        double[] wide = StartView.of(places(0, -85, 0, 85, 0, 0));
        near(0, wide[1], "170 degrees apart: longitude");
        if (GlobeMath.angle(0, -85, wide[0], wide[1]) > Math.PI / 2 || GlobeMath.angle(0, 85, wide[0], wide[1]) > Math.PI / 2) {
            throw new AssertionError("both places are in front");
        }
        // the farthest two are opposite: the average of all of them, here a third place at 90 degrees
        double[] odd = StartView.of(places(0, 0, 0, 90, 0, 180));
        near(0, odd[0], "opposite pair and a third: latitude");
        near(90, odd[1], "opposite pair and a third: longitude");
        System.out.println("PASS StartViewCheck");
    }
}
