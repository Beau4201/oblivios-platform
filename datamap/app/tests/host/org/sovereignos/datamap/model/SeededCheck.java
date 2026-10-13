// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** usage: SeededCheck atlas-seeded.txt  (made by tools/make-seeded-fixture.mjs from the design page) */
public final class SeededCheck {
    private static void near(double expected, double actual, double eps, String what) {
        if (!(Math.abs(expected - actual) <= eps)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String, String[]> f = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(args[0]))) {
            String[] p = line.split("\t");
            f.put(p[0], p);
        }
        near(Double.parseDouble(f.get("cities")[1]), Cities.all().length, 0, "cities");
        Seeded.Points tex = Seeded.texture();
        Seeded.Points lights = Seeded.lights();
        near(Double.parseDouble(f.get("tex")[1]), tex.n, 0, "texture points");
        near(Double.parseDouble(f.get("lights")[1]), lights.n, 0, "lights");
        sums(f.get("texsum"), tex, "texture");
        sums(f.get("lightsum"), lights, "lights");
        String[] lc = f.get("lightcolours");
        int[] counts = new int[3];
        for (int i = 0; i < lights.n; i++) {
            counts[lights.colour[i]]++;
        }
        for (int k = 0; k < 3; k++) {
            near(Double.parseDouble(lc[1 + k]), counts[k], 0, "light colour " + k);
        }
        row(f.get("tex0"), tex, 0, false, "tex0");
        row(f.get("tex1"), tex, 1, false, "tex1");
        row(f.get("texlast"), tex, tex.n - 1, false, "texlast");
        row(f.get("light0"), lights, 0, true, "light0");
        row(f.get("light1"), lights, 1, true, "light1");
        row(f.get("lightlast"), lights, lights.n - 1, true, "lightlast");
        System.out.println("PASS SeededCheck");
    }

    private static void sums(String[] expected, Seeded.Points p, String what) {
        double[] s = new double[5];
        for (int i = 0; i < p.n; i++) {
            s[0] += p.v[3 * i];
            s[1] += p.v[3 * i + 1];
            s[2] += p.v[3 * i + 2];
            s[3] += p.size[i];
            s[4] += p.alpha[i];
        }
        for (int k = 0; k < 5; k++) {
            near(Double.parseDouble(expected[1 + k]), s[k], 2e-3, what + " sum " + k);
        }
    }

    private static void row(String[] e, Seeded.Points p, int i, boolean colour, String what) {
        near(Double.parseDouble(e[1]), p.v[3 * i], 2e-6, what + " x");
        near(Double.parseDouble(e[2]), p.v[3 * i + 1], 2e-6, what + " y");
        near(Double.parseDouble(e[3]), p.v[3 * i + 2], 2e-6, what + " z");
        near(Double.parseDouble(e[4]), p.size[i], 2e-6, what + " size");
        near(Double.parseDouble(e[5]), p.alpha[i], 2e-6, what + " alpha");
        if (colour) {
            near(Double.parseDouble(e[6]), p.colour[i], 0, what + " colour");
        }
    }
}
