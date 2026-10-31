// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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
    private static final Pattern NUMBER = Pattern.compile("0|[1-9][0-9]{0,8}");
    private static final Pattern LAT = Pattern.compile("-?[0-9]{1,2}(\\.[0-9]{1,7})?");
    private static final Pattern LON = Pattern.compile("-?[0-9]{1,3}(\\.[0-9]{1,7})?");
    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");
    private static final Pattern ZONE = Pattern.compile("[A-Za-z0-9_/+-]{1,64}");

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
        List<Place> out = new ArrayList<>();
        int start = 0;
        int lastId = 0;
        while (start < tsv.length()) {
            int end = tsv.indexOf('\n', start);
            if (out.size() >= MAX_LINES) {
                throw new IllegalArgumentException("too many lines");
            }
            String[] f = tsv.substring(start, end).split("\t", -1);
            start = end + 1;
            if (f.length != 7) {
                throw new IllegalArgumentException("a line has " + f.length + " fields");
            }
            int id = number(f[0]);
            if (id <= lastId) {
                throw new IllegalArgumentException("ids must ascend from 1");
            }
            lastId = id;
            if (!validName(f[1]) || !COUNTRY.matcher(f[2]).matches() || !ZONE.matcher(f[5]).matches()) {
                throw new IllegalArgumentException("a name, country or time zone is not valid");
            }
            if (!LAT.matcher(f[3]).matches() || !LON.matcher(f[4]).matches()) {
                throw new IllegalArgumentException("a coordinate is not valid");
            }
            double lat = Double.parseDouble(f[3]);
            double lon = Double.parseDouble(f[4]);
            if (Math.abs(lat) > 80.0 || Math.abs(lon) > 180.0) {
                throw new IllegalArgumentException("a coordinate is out of range");
            }
            int mcc = number(f[6]);
            if (mcc < 200 || mcc > 799) {
                throw new IllegalArgumentException("mcc");
            }
            if (zones == null || zones.contains(f[5])) {
                out.add(new Place(id, f[1], f[2], lat, lon, f[5], mcc));
            }
        }
        return new Places(out);
    }

    private static int number(String s) {
        if (!NUMBER.matcher(s).matches()) {
            throw new IllegalArgumentException("not a number");
        }
        return Integer.parseInt(s);
    }

    private static boolean validName(String s) {
        if (s.isEmpty() || s.length() > MAX_NAME) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.isISOControl(s.charAt(i))) {
                return false;
            }
        }
        return true;
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
        if (countryIso == null || !COUNTRY.matcher(countryIso).matches()) {
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
