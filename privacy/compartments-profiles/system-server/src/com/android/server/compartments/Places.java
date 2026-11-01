// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The places of the persona (ADR-208): the offline list of cities that an Isolated app can be placed in. Read once from a
 * read-only file of the image, parsed strictly: anything that is not exactly a file of the format makes the whole file unusable,
 * and the service then reports "no place data" instead of a partial list. Pure Java.
 *
 * <p>Format: one line per place, seven tab-separated fields, a newline after every line, ids ascending (the GeoNames id of the city, at most nine digits):
 * {@code id name country latitude longitude timeZone mcc}. The country is two capital ASCII letters, the latitude is within 80
 * degrees (the cities of the list stop there, and the track of the persona does not work near the poles), the longitude within
 * 180, the time zone an Olson id, the mcc the mobile country code of the place (200 to 799).
 */
final class Places {
    static final int MAX_LINES = 100000;
    static final double MIN_AUTOMATIC_DISTANCE_M = 150000.0;
    private static final int MAX_NAME = 100;
    private static final int MAX_ZONE = 64;

    static final class Place {
        final int id;
        final String name;
        final String country;
        final double lat;
        final double lon;
        final String timeZone;
        final int mcc;

        Place(int id, String name, String country, double lat, double lon, String timeZone, int mcc) {
            this.id = id;
            this.name = name;
            this.country = country;
            this.lat = lat;
            this.lon = lon;
            this.timeZone = timeZone;
            this.mcc = mcc;
        }
    }

    private final List<Place> places;

    private Places(List<Place> places) {
        this.places = Collections.unmodifiableList(places);
    }

    /** @throws IllegalArgumentException for any text that is not exactly a file of the format */
    static Places parse(String tsv) {
        return parse(tsv, null);
    }

    /**
     * Like {@link #parse(String)}, but a well-formed place whose time zone is not in {@code zones} is left out (null: keep every
     * place). The persona must never have a zone that the image does not know: the process would then keep the real one (B1).
     */
    static Places parse(String tsv, java.util.Set<String> zones) {
        if (tsv == null || tsv.isEmpty() || tsv.charAt(tsv.length() - 1) != '\n') {
            throw new IllegalArgumentException("not a places file");
        }
        // One pass over the characters, no regular expression and no split: a field is a range of tsv, and a string is made
        // only for a line that is valid (the first read ran under a system lock, so the parse has to be quick).
        List<Place> out = new ArrayList<>();
        int[] cut = new int[8];  // cut[k]: the character before field k (the previous tab); cut[7]: the newline
        int start = 0;
        int lastId = 0;
        while (start < tsv.length()) {
            if (out.size() >= MAX_LINES) {
                throw new IllegalArgumentException("too many lines");
            }
            cut[0] = start - 1;
            int tabs = 0;
            int end = start;
            for (char c; (c = tsv.charAt(end)) != '\n'; end++) {  // the file ends in a newline, so this stops
                if (c == '\t') {
                    if (tabs == 6) {
                        throw new IllegalArgumentException("a line has more than 7 fields");
                    }
                    cut[++tabs] = end;
                }
            }
            if (tabs != 6) {
                throw new IllegalArgumentException("a line has " + (tabs + 1) + " fields");
            }
            cut[7] = end;
            start = end + 1;
            int id = number(tsv, cut[0] + 1, cut[1]);
            if (id <= lastId) {
                throw new IllegalArgumentException("ids must ascend from 1");
            }
            lastId = id;
            if (!validName(tsv, cut[1] + 1, cut[2]) || !validCountry(tsv, cut[2] + 1, cut[3])
                    || !validZone(tsv, cut[5] + 1, cut[6])) {
                throw new IllegalArgumentException("a name, country or time zone is not valid");
            }
            if (!validCoordinate(tsv, cut[3] + 1, cut[4], 2) || !validCoordinate(tsv, cut[4] + 1, cut[5], 3)) {
                throw new IllegalArgumentException("a coordinate is not valid");
            }
            double lat = coordinate(tsv, cut[3] + 1, cut[4]);
            double lon = coordinate(tsv, cut[4] + 1, cut[5]);
            if (Math.abs(lat) > 80.0 || Math.abs(lon) > 180.0) {
                throw new IllegalArgumentException("a coordinate is out of range");
            }
            int mcc = number(tsv, cut[6] + 1, cut[7]);
            if (mcc < 200 || mcc > 799) {
                throw new IllegalArgumentException("mcc");
            }
            String zone = tsv.substring(cut[5] + 1, cut[6]);
            if (zones == null || zones.contains(zone)) {
                out.add(new Place(id, tsv.substring(cut[1] + 1, cut[2]), tsv.substring(cut[2] + 1, cut[3]), lat, lon, zone, mcc));
            }
        }
        return new Places(out);
    }

    /** The grammar 0|[1-9][0-9]{0,8} of s[from, to): ASCII digits, no sign, no leading zero. */
    private static int number(String s, int from, int to) {
        int len = to - from;
        if (len < 1 || len > 9 || (len > 1 && s.charAt(from) == '0')) {
            throw new IllegalArgumentException("not a number");
        }
        int v = 0;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("not a number");
            }
            v = v * 10 + (c - '0');
        }
        return v;
    }

    private static boolean validName(String s, int from, int to) {
        if (to - from < 1 || to - from > MAX_NAME) {
            return false;
        }
        for (int i = from; i < to; i++) {
            if (Character.isISOControl(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** The grammar [A-Z]{2} of s[from, to). */
    private static boolean validCountry(String s, int from, int to) {
        return to - from == 2 && s.charAt(from) >= 'A' && s.charAt(from) <= 'Z' && s.charAt(from + 1) >= 'A' && s.charAt(from + 1) <= 'Z';
    }

    /** The grammar [A-Za-z0-9_/+-]{1,64} of s[from, to). */
    private static boolean validZone(String s, int from, int to) {
        if (to - from < 1 || to - from > MAX_ZONE) {
            return false;
        }
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '/' || c == '+' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    private static final double[] POW10 = {1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7};

    /**
     * The value of a coordinate that {@link #validCoordinate} accepted, equal to Double.parseDouble of it: the digits are at
     * most 10 (below 2^53) and the divisor is a power of ten up to 1e7 (exact), so one division is correctly rounded.
     */
    private static double coordinate(String s, int from, int to) {
        boolean negative = s.charAt(from) == '-';
        long digits = 0;
        int decimals = -1;
        for (int i = negative ? from + 1 : from; i < to; i++) {
            char c = s.charAt(i);
            if (c == '.') {
                decimals = 0;
            } else {
                digits = digits * 10 + (c - '0');
                if (decimals >= 0) {
                    decimals++;
                }
            }
        }
        double v = decimals <= 0 ? (double) digits : digits / POW10[decimals];
        return negative ? -v : v;
    }

    /** The grammar -?[0-9]{1,maxInt}(\.[0-9]{1,7})? of s[from, to). */
    private static boolean validCoordinate(String s, int from, int to, int maxInt) {
        int i = from;
        if (i < to && s.charAt(i) == '-') {
            i++;
        }
        int d = i;
        while (i < to && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            i++;
        }
        if (i - d < 1 || i - d > maxInt) {
            return false;
        }
        if (i == to) {
            return true;
        }
        if (s.charAt(i) != '.') {
            return false;
        }
        d = ++i;
        while (i < to && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            i++;
        }
        return i == to && i - d >= 1 && i - d <= 7;
    }

    int size() {
        return places.size();
    }

    /** The place with this id, or null. */
    Place byId(int id) {
        int lo = 0;
        int hi = places.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int v = places.get(mid).id;
            if (v == id) {
                return places.get(mid);
            }
            if (v < id) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return null;
    }

    /** Places whose name contains the query, names that start with it first, then by name, then by id; at most {@code limit}. */
    List<Place> search(String query, int limit) {
        if (query == null || query.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        final String q = query.toLowerCase(Locale.ROOT);
        List<Place> starts = new ArrayList<>();
        List<Place> inside = new ArrayList<>();
        for (Place p : places) {
            String name = p.name.toLowerCase(Locale.ROOT);
            if (name.startsWith(q)) {
                starts.add(p);
            } else if (name.contains(q)) {
                inside.add(p);
            }
        }
        Comparator<Place> order = (a, b) -> {
            int c = a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT));
            return c != 0 ? c : Integer.compare(a.id, b.id);
        };
        Collections.sort(starts, order);
        Collections.sort(inside, order);
        starts.addAll(inside);
        return new ArrayList<>(starts.subList(0, Math.min(limit, starts.size())));
    }

    /**
     * The place that an app is given when the user has not chosen one: a place of this country at least 150 km from the real
     * position, picked by the seed, so the same app on the same phone gets the same place every time. When no place of the
     * country is that far, the farthest one; 0 when the country has no place at all (or is not a country code), which the
     * caller reports as "no persona possible". A real position that is not finite counts every place of the country as far
     * enough.
     */
    int automaticFor(String countryIso, double realLat, double realLon, long seed) {
        if (countryIso == null || !validCountry(countryIso, 0, countryIso.length())) {
            return 0;
        }
        boolean known = !Double.isNaN(realLat) && !Double.isInfinite(realLat) && !Double.isNaN(realLon) && !Double.isInfinite(realLon);
        List<Place> far = new ArrayList<>();
        Place farthest = null;
        double farthestM = -1.0;
        for (Place p : places) {
            if (!p.country.equals(countryIso)) {
                continue;
            }
            double d = known ? Geo.distanceM(realLat, realLon, p.lat, p.lon) : Double.MAX_VALUE;
            if (d >= MIN_AUTOMATIC_DISTANCE_M) {
                far.add(p);
            }
            if (d > farthestM) {
                farthestM = d;
                farthest = p;
            }
        }
        if (far.isEmpty()) {
            return farthest == null ? 0 : farthest.id;
        }
        long countryHash = countryIso.charAt(0) * 31L + countryIso.charAt(1);
        return far.get((int) Math.floorMod(mix(seed ^ countryHash), (long) far.size())).id;
    }

    /** splitmix64, the function of SensorModeTable::mix, so that the choice is stable per app and phone. */
    static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
