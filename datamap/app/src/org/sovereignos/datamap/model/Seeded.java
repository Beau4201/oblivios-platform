// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

/**
 * The fine texture on the land and the lights of the cities of the night globe (ADR-160). Both are generated from
 * a fixed seed with the generator of the design page, so the globe has the same points as the page. Decoration, not
 * data: nothing here says anything about the user.
 */
public final class Seeded {
    private static final double D2R = Math.PI / 180;

    private Seeded() {}

    /** Unit vectors with a size and an opacity each; {@code colour} is 0 amber, 1 pale, 2 cyan for the lights. */
    public static final class Points {
        public final int n;
        public final float[] v;
        public final float[] size;
        public final float[] alpha;
        public final byte[] colour;

        Points(int n) {
            this.n = n;
            v = new float[3 * n];
            size = new float[n];
            alpha = new float[n];
            colour = new byte[n];
        }
    }

    /** The generator of the page: s = s * 16807 mod 2147483647, as a number in (0, 1). */
    private static final class Lcg {
        private long s;

        Lcg(long seed) {
            s = seed;
        }

        double next() {
            s = (s * 16807) % 2147483647L;
            return s / 2147483647.0;
        }
    }

    private static void vec(double latDeg, double lonDeg, Points p, int i) {
        double lo = lonDeg * D2R;
        double la = latDeg * D2R;
        double c = Math.cos(la);
        p.v[3 * i] = (float) (c * Math.sin(lo));
        p.v[3 * i + 1] = (float) Math.sin(la);
        p.v[3 * i + 2] = (float) (c * Math.cos(lo));
    }

    /** 3400 points spread over the sphere; the globe keeps those on land (see {@link #onLand}). */
    public static Points texture() {
        Lcg r = new Lcg(5);
        Points p = new Points(3400);
        for (int i = 0; i < p.n; i++) {
            double la = Math.asin(2 * r.next() - 1) / D2R;
            double lo = r.next() * 360 - 180;
            vec(la, lo, p, i);
            p.size[i] = (float) (0.5 + r.next() * 0.9);
            p.alpha[i] = (float) (0.05 + r.next() * 0.14);
        }
        return p;
    }

    /** The lights: 9 to 18 points scattered around each city, the first three close to it. */
    public static Points lights() {
        Lcg r = new Lcg(11);
        double[][] cities = Cities.all();
        int total = 0;
        int[] counts = new int[cities.length];
        // the page draws the count first, then the points of that city: the counts need the same draws in order
        Lcg probe = new Lcg(11);
        for (int c = 0; c < cities.length; c++) {
            counts[c] = 9 + (int) Math.floor(probe.next() * 10);
            for (int i = 0; i < counts[c]; i++) {
                for (int k = 0; k < 5; k++) {
                    probe.next();
                }
            }
            total += counts[c];
        }
        Points p = new Points(total);
        int at = 0;
        for (int c = 0; c < cities.length; c++) {
            int n = 9 + (int) Math.floor(r.next() * 10);
            for (int i = 0; i < n; i++) {
                double sp = i < 3 ? 1.2 : 3.2;
                double la2 = cities[c][0] + (r.next() - 0.5) * sp;
                double lo2 = cities[c][1] + (r.next() - 0.5) * sp * 1.3;
                double k = r.next();
                vec(la2, lo2, p, at);
                p.colour[at] = (byte) (k < 0.7 ? 0 : k < 0.85 ? 1 : 2);
                p.size[at] = (float) (0.45 + r.next() * 0.9);
                p.alpha[at] = (float) (0.45 + r.next() * 0.55);
                at++;
            }
        }
        return p;
    }

    /** The points that lie on land: the page clips the texture to the land, here it is done once. */
    public static Points onLand(Points p, LandData land) {
        int[] keep = new int[p.n];
        int n = 0;
        for (int i = 0; i < p.n; i++) {
            double lat = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, p.v[3 * i + 1]))));
            double lon = Math.toDegrees(Math.atan2(p.v[3 * i], p.v[3 * i + 2]));
            if (land.contains(lat, lon)) {
                keep[n++] = i;
            }
        }
        Points out = new Points(n);
        for (int k = 0; k < n; k++) {
            int i = keep[k];
            System.arraycopy(p.v, 3 * i, out.v, 3 * k, 3);
            out.size[k] = p.size[i];
            out.alpha[k] = p.alpha[i];
            out.colour[k] = p.colour[i];
        }
        return out;
    }
}
