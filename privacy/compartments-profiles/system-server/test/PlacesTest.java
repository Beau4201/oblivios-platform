// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import com.android.server.compartments.Places.Place;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Host test of Places, CellSubset and PersonaFile (the data of the persona): java -ea, no framework. */
public final class PlacesTest {
    private static int checks = 0;

    private static void check(boolean cond, String what) {
        checks++;
        if (!cond) {
            throw new AssertionError(what);
        }
    }

    private static boolean throwsIllegal(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static String ln(Object... fields) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            b.append(i == 0 ? "" : "\t").append(fields[i]);
        }
        return b.append('\n').toString();
    }

    /** id, name, country, lat, lon, time zone, mcc */
    private static final String FIXTURE =
            ln(1, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214)
            + ln(2, "Madridejos", "ES", "39.4667", "-3.5333", "Europe/Madrid", 214)
            + ln(3, "Barcelona", "ES", "41.3851", "2.1734", "Europe/Madrid", 214)
            + ln(4, "Amsterdam", "NL", "52.3676", "4.9041", "Europe/Amsterdam", 204)
            + ln(5, "Rotterdam", "NL", "51.9244", "4.4777", "Europe/Amsterdam", 204)
            + ln(6, "New York", "US", "40.7128", "-74.0060", "America/New_York", 310)
            + ln(7, "Los Angeles", "US", "34.0522", "-118.2437", "America/Los_Angeles", 310)
            + ln(8, "Auckland", "NZ", "-36.8485", "174.7633", "Pacific/Auckland", 530)
            + ln(9, "Smadrid Plaza", "ES", "40.0000", "-4.0000", "Europe/Madrid", 214);

    public static void main(String[] args) {
        places();
        automatic();
        cells();
        personaFile();
        System.out.println("ok " + checks + " checks");
    }

    private static void places() {
        Places p = Places.parse(FIXTURE);
        check(p.size() == 9, "nine places");
        Place madrid = p.byId(1);
        check(madrid != null && madrid.id == 1 && madrid.name.equals("Madrid") && madrid.country.equals("ES")
                && madrid.lat == 40.4168 && madrid.lon == -3.7038 && madrid.timeZone.equals("Europe/Madrid") && madrid.mcc == 214,
                "the fields of a place");
        check(p.byId(0) == null && p.byId(99) == null && p.byId(-1) == null, "an unknown id is null");
        check(p.byId(9).name.equals("Smadrid Plaza"), "the last place");
        // the limits themselves are valid
        check(Places.parse(ln(1, "Pole", "AQ", "80", "180", "Antarctica/Troll", 799)).size() == 1, "latitude 80, longitude 180, mcc 799");
        check(Places.parse(ln(1, "Pole", "AQ", "-80", "-180", "Etc/GMT+12", 200)).size() == 1, "latitude -80, longitude -180, mcc 200, plus sign");
        check(Places.parse(ln(1, "A", "AA", "0", "0", "UTC", 214) + ln(3, "B", "AA", "1", "1", "UTC", 214)).byId(3) != null, "ids may have gaps");

        // search: names that start with the query first, then by name, then by id; case does not matter
        List<Place> s = p.search("mad", 5);
        check(s.size() == 3 && s.get(0).id == 1 && s.get(1).id == 2 && s.get(2).id == 9, "search mad: " + s.size());
        check(p.search("MAD", 5).size() == 3, "search is case-insensitive");
        check(p.search("mad", 2).size() == 2 && p.search("mad", 2).get(1).id == 2, "search honours the limit");
        check(p.search("mad", 0).isEmpty() && p.search("mad", -1).isEmpty(), "limit 0 or less");
        check(p.search("", 5).isEmpty() && p.search(null, 5).isEmpty(), "an empty query finds nothing");
        check(p.search("zzz", 5).isEmpty(), "no match");
        check(p.search("a", 100).size() == 8, "contains: " + p.search("a", 100).size());
        List<Place> tie = Places.parse(ln(1, "Aa", "AA", "1", "1", "UTC", 214) + ln(2, "Aa", "AB", "2", "2", "UTC", 214)).search("aa", 5);
        check(tie.get(0).id == 1 && tie.get(1).id == 2, "equal names by id");

        // names order the results; the id only breaks a tie
        Places byName = Places.parse(ln(1, "Zeta", "AA", "1", "1", "UTC", 214) + ln(2, "Alpha", "AA", "1", "1", "UTC", 214)
                + ln(3, "Beta", "AA", "1", "1", "UTC", 214) + ln(4, "Gamma Alpha", "AA", "1", "1", "UTC", 214)
                + ln(5, "Epsilon", "AA", "1", "1", "UTC", 214) + ln(6, "Abel", "AA", "1", "1", "UTC", 214)
                + ln(7, "Aaron", "AA", "1", "1", "UTC", 214));
        List<Place> a = byName.search("a", 10);
        check(a.size() == 6 && a.get(0).name.equals("Aaron") && a.get(1).name.equals("Abel") && a.get(2).name.equals("Alpha")
                && a.get(3).name.equals("Beta") && a.get(4).name.equals("Gamma Alpha") && a.get(5).name.equals("Zeta"),
                "a: the names that start with it first, then by name");
        List<Place> e = byName.search("e", 10);
        check(e.size() == 4 && e.get(0).name.equals("Epsilon") && e.get(1).name.equals("Abel") && e.get(2).name.equals("Beta")
                && e.get(3).name.equals("Zeta"), "e: starts first, then by name and not by id");
        check(Places.mix(0) == 0xE220A8397B1DCDAFL && Places.mix(1) == 0x910A2DEC89025CC1L && Places.mix(0x0123456789ABCDEFL) == 0x157A3807A48FAA9DL
                && Places.mix(-1L) == 0xE4D971771B652C20L, "mix is splitmix64, the function of SensorModeTable::mix");

        // strictness
        String ok = ln(1, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214);
        check(throwsIllegal(() -> Places.parse(ln("01", "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214))), "id with a leading zero");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", "0214"))), "mcc with a leading zero");
        check(throwsIllegal(() -> Places.parse(ln("١", "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214))), "id in another script");
        check(!throwsIllegal(() -> Places.parse(ok)), "the fixture line is valid");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid"))), "6 fields");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214, "x"))), "8 fields");
        check(throwsIllegal(() -> Places.parse(ok + ok)), "a repeated id");
        check(throwsIllegal(() -> Places.parse(ln(2, "A", "AA", "1", "1", "UTC", 214) + ln(1, "B", "AA", "1", "1", "UTC", 214))), "ids that descend");
        check(throwsIllegal(() -> Places.parse(ln(0, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214))), "id 0");
        check(throwsIllegal(() -> Places.parse(ln(-1, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214))), "id -1");
        check(throwsIllegal(() -> Places.parse(ln("+1", "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214))), "id with a plus sign");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "80.5", "-3.7038", "Europe/Madrid", 214))), "latitude 80.5");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "-80.5", "-3.7038", "Europe/Madrid", 214))), "latitude -80.5");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "40.4168", "181", "Europe/Madrid", 214))), "longitude 181");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "40.4168", "-181", "Europe/Madrid", 214))), "longitude -181");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "NaN", "-3.7038", "Europe/Madrid", 214))), "latitude NaN");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "40.4168", "1e2", "Europe/Madrid", 214))), "longitude with an exponent");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "40.4168", "", "Europe/Madrid", 214))), "empty longitude");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "es", "40.4168", "-3.7038", "Europe/Madrid", 214))), "lower-case country");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ESP", "40.4168", "-3.7038", "Europe/Madrid", 214))), "three-letter country");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "E1", "40.4168", "-3.7038", "Europe/Madrid", 214))), "country with a digit");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Mad rid", 214))), "time zone with a space");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "40.4168", "-3.7038", "", 214))), "empty time zone");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 199))), "mcc 199");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 800))), "mcc 800");
        check(throwsIllegal(() -> Places.parse(ln(1, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", "21x"))), "mcc not a number");
        check(throwsIllegal(() -> Places.parse(ln(1, "Mad\u0001rid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214))), "control character in a name");
        check(throwsIllegal(() -> Places.parse(ln(1, "Mad\u007frid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214))), "delete in a name");
        check(throwsIllegal(() -> Places.parse(ln(1, "", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214))), "empty name");
        check(throwsIllegal(() -> Places.parse(ln(1, "x".repeat(101), "ES", "40.4168", "-3.7038", "Europe/Madrid", 214))), "name of 101 characters");
        check(!throwsIllegal(() -> Places.parse(ln(1, "x".repeat(100), "ES", "40.4168", "-3.7038", "Europe/Madrid", 214))), "name of 100 characters");
        check(!throwsIllegal(() -> Places.parse(ln(1, "São Paulo", "BR", "-23.55", "-46.63", "America/Sao_Paulo", 724))), "a name with a letter beyond ASCII");
        check(throwsIllegal(() -> Places.parse(ok.substring(0, ok.length() - 1))), "no final newline");
        check(throwsIllegal(() -> Places.parse("")), "an empty file");
        check(throwsIllegal(() -> Places.parse(ok.replace("\n", "\r\n"))), "CRLF");
        check(throwsIllegal(() -> Places.parse(ok + "\n")), "an empty line");
        check(throwsIllegal(() -> Places.parse(null)), "null");
        StringBuilder many = new StringBuilder();
        for (int i = 1; i <= 100000; i++) {
            many.append(i).append("\tA\tAA\t1\t1\tUTC\t214\n");
        }
        check(Places.parse(many.toString()).size() == 100000, "100000 lines");
        many.append(100001).append("\tA\tAA\t1\t1\tUTC\t214\n");
        check(throwsIllegal(() -> Places.parse(many.toString())), "100001 lines");
    }

    private static void automatic() {
        Places p = Places.parse(FIXTURE);
        // from Amsterdam: every Spanish place is at least 150 km away
        Set<Integer> seen = new HashSet<>();
        for (long seed = 1; seed <= 50; seed++) {
            int id = p.automaticFor("ES", 52.37, 4.90, seed);
            check(id == 1 || id == 2 || id == 3 || id == 9, "a place of ES: " + id);
            check(id == p.automaticFor("ES", 52.37, 4.90, seed), "the same arguments give the same place");
            check(Geo.distanceM(52.37, 4.90, p.byId(id).lat, p.byId(id).lon) >= 150000, "far enough");
            seen.add(id);
        }
        check(seen.size() > 1, "the seed changes the choice: " + seen);
        // 150 km is the limit and not any larger distance: from Madrid, Valencia (300 km), Seville (390 km) and Barcelona (505 km)
        // are all far enough, Madridejos (106 km) is not, and the three far places are all chosen for some seed
        Places spain = Places.parse(ln(1, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214)
                + ln(2, "Madridejos", "ES", "39.4667", "-3.5333", "Europe/Madrid", 214)
                + ln(3, "Valencia", "ES", "39.4699", "-0.3763", "Europe/Madrid", 214)
                + ln(4, "Sevilla", "ES", "37.3891", "-5.9845", "Europe/Madrid", 214)
                + ln(5, "Barcelona", "ES", "41.3851", "2.1734", "Europe/Madrid", 214));
        Set<Integer> far = new HashSet<>();
        for (long seed = 1; seed <= 200; seed++) {
            far.add(spain.automaticFor("ES", 40.4168, -3.7038, seed));
        }
        check(far.equals(new HashSet<>(java.util.Arrays.asList(3, 4, 5))), "the places 150 km or more away: " + far);
        // from Madrid: Madrid, Madridejos and the place at 40.0/-4.0 are within 150 km, Barcelona is not
        for (long seed = 1; seed <= 20; seed++) {
            check(p.automaticFor("ES", 40.42, -3.70, seed) == 3, "only Barcelona is far enough from Madrid");
        }
        check(p.automaticFor("NL", 40.42, -3.70, 7) != 0 && p.byId(p.automaticFor("NL", 40.42, -3.70, 7)).country.equals("NL"), "a place of NL");
        // no place far enough: the farthest of the country
        Places two = Places.parse(ln(1, "Madrid", "ES", "40.4168", "-3.7038", "Europe/Madrid", 214)
                + ln(2, "Madridejos", "ES", "39.4667", "-3.5333", "Europe/Madrid", 214));
        check(two.automaticFor("ES", 40.4168, -3.7038, 1) == 2, "the farthest when none is far enough");
        check(two.automaticFor("ES", 39.4667, -3.5333, 1) == 1, "the farthest, the other way");
        // no place of the country at all
        check(p.automaticFor("FR", 0, 0, 1) == 0, "no place of FR");
        check(p.automaticFor(null, 0, 0, 1) == 0 && p.automaticFor("es", 0, 0, 1) == 0 && p.automaticFor("", 0, 0, 1) == 0, "an invalid country");
        // a fix that is not finite or out of range gives no distance: the choice must still be a place of the country
        check(p.byId(p.automaticFor("ES", Double.NaN, 0, 1)).country.equals("ES"), "a NaN position still gives a place");
    }

    private static String cellLines(int place, int count) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < count; i++) {
            b.append(ln(place, 214, 1 + (i % 3), 100 + i, 1000 + i));
        }
        return b.toString();
    }

    private static void cells() {
        CellSubset c = CellSubset.parse(cellLines(1, 9) + ln(4, 204, 4, 200, 4294967295L) + ln(7, 310, 260, 65535, 0));
        List<CellSubset.Cell> one = c.cellsFor(1);
        check(one.size() == 8, "at most 8 cells: " + one.size());
        for (int i = 0; i < 8; i++) {
            check(one.get(i).cid == 1000 + i && one.get(i).tac == 100 + i && one.get(i).mcc == 214 && one.get(i).mnc == 1 + (i % 3), "file order " + i);
        }
        check(c.cellsFor(4).size() == 1 && c.cellsFor(4).get(0).cid == 4294967295L && c.cellsFor(4).get(0).mnc == 4, "the largest cid");
        check(c.cellsFor(7).get(0).tac == 65535 && c.cellsFor(7).get(0).mnc == 260 && c.cellsFor(7).get(0).cid == 0, "the largest tac, cid 0");
        check(c.cellsFor(2).isEmpty() && c.cellsFor(0).isEmpty() && c.cellsFor(-3).isEmpty(), "an unknown place has no cells");
        CellSubset ops = CellSubset.parse(ln(1, 214, 7, 1, 1) + ln(1, 310, 260, 1, 2) + ln(2, 262, 99, 1, 3) + ln(3, 505, 100, 1, 4) + ln(4, 214, 0, 1, 5));
        check(ops.operatorFor(1).equals("21407"), "the operator is mcc and a two-digit mnc of the FIRST cell");
        check(ops.operatorFor(2).equals("26299"), "mnc 99 has two digits");
        check(ops.operatorFor(3).equals("505100"), "mnc 100 has three digits");
        check(ops.operatorFor(4).equals("21400"), "mnc 0 is padded to two digits");
        check(ops.operatorFor(9).isEmpty() && ops.operatorFor(0).isEmpty(), "no cell, no operator");
        java.util.Locale saved = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG-u-nu-arab"));
            check(ops.operatorFor(4).equals("21400") && ops.operatorFor(1).equals("21407"), "the digits do not follow the locale");
        } finally {
            java.util.Locale.setDefault(saved);
        }
        check(CellSubset.parse(cellLines(5, 64)).cellsFor(5).size() == 8, "64 lines of one place");
        check(throwsIllegal(() -> CellSubset.parse(cellLines(5, 65))), "65 lines of one place");
        check(CellSubset.parse(cellLines(5, 64) + cellLines(6, 64)).cellsFor(6).size() == 8, "the limit is per place");
        check(CellSubset.parse(cellLines(1, 3) + cellLines(2, 3) + cellLines(1, 3)).cellsFor(1).size() == 6, "lines of one place need not be adjacent");
        String ok = ln(1, 214, 1, 100, 5);
        check(!throwsIllegal(() -> CellSubset.parse(ok)), "a valid line");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 214, 1, 100))), "4 fields");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 214, 1, 100, 5, 6))), "6 fields");
        check(throwsIllegal(() -> CellSubset.parse(ln("01", 214, 1, 100, 5))), "place with a leading zero");
        check(throwsIllegal(() -> CellSubset.parse(ln(0, 214, 1, 100, 5))), "place 0");
        check(throwsIllegal(() -> CellSubset.parse(ln(-1, 214, 1, 100, 5))), "place -1");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 199, 1, 100, 5))), "mcc 199");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 800, 1, 100, 5))), "mcc 800");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 214, -1, 100, 5))), "mnc -1");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 214, 1000, 100, 5))), "mnc 1000");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 214, 1, 65536, 5))), "tac 65536");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 214, 1, -1, 5))), "tac -1");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 214, 1, 100, 4294967296L))), "cid 2^32");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 214, 1, 100, -1))), "cid -1");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 214, 1, 100, "+5"))), "cid with a plus sign");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 214, 1, 100, "0x5"))), "cid in hex");
        check(throwsIllegal(() -> CellSubset.parse(ln(1, 214, 1, 100, "٥"))), "cid in another script");
        check(throwsIllegal(() -> CellSubset.parse(ok.substring(0, ok.length() - 1))), "no final newline");
        check(throwsIllegal(() -> CellSubset.parse(ok.replace("\n", "\r\n"))), "CRLF");
        check(throwsIllegal(() -> CellSubset.parse("")), "an empty file");
        check(throwsIllegal(() -> CellSubset.parse(null)), "null");
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 1000000; i++) {
            many.append(i / 64 + 1).append("\t214\t1\t1\t").append(i % 64).append('\n');
        }
        check(CellSubset.parse(many.toString()).cellsFor(1).size() == 8, "a million lines");
        many.append("20000\t214\t1\t1\t1\n");
        check(throwsIllegal(() -> CellSubset.parse(many.toString())), "more than a million lines");
    }

    private static Map<Integer, Integer> map(int... pairs) {
        Map<Integer, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    private static void personaFile() {
        String text = PersonaFile.format(5, map(10200, 3, 10100, 7));
        check(text.equals("s 0000000000000005\np 10100 7\np 10200 3\n"), "the format: " + text.replace("\n", "|"));
        PersonaFile f = PersonaFile.parse(text);
        check(f.salt() == 5, "the salt");
        check(f.placeOf(10100) == 7 && f.placeOf(10200) == 3, "the places of two apps");
        check(f.placeOf(110100) == 7, "the same app in another user");
        check(f.placeOf(20100) == 7, "the SDK sandbox of an app");
        check(f.placeOf(120100) == 7, "the sandbox in another user");
        check(f.placeOf(10300) == 0 && f.placeOf(1000) == 0 && f.placeOf(90001) == 0 && f.placeOf(-1) == 0 && f.placeOf(0) == 0, "no place");
        check(f.placeOf(19999) == 0 && f.placeOf(29999) == 0, "the last app id without a line");
        check(PersonaFile.parse(PersonaFile.format(-1L, map())).salt() == -1L, "a negative salt");
        check(PersonaFile.format(-1L, map()).equals("s ffffffffffffffff\n"), "a salt of all ones");
        check(PersonaFile.format(0L, map()).equals("s 0000000000000000\n"), "a salt of zero is padded to sixteen digits");
        check(PersonaFile.format(0xabcL, map()).equals("s 0000000000000abc\n"), "a short salt is padded to sixteen digits");
        check(PersonaFile.parse(PersonaFile.format(0, map(10000, 1, 19999, 2147483647))).placeOf(19999) == 2147483647, "the limits of an app id and a place");
        check(PersonaFile.parse(PersonaFile.format(1, map(10000, 1))).placeOf(20000) == 1, "the first sandbox answers to the first app");
        check(PersonaFile.parse(PersonaFile.format(1, map(19999, 1))).placeOf(29999) == 1, "the last sandbox answers to the last app");

        check(throwsIllegal(() -> PersonaFile.format(1, map(9999, 1))), "format: app id 9999");
        check(throwsIllegal(() -> PersonaFile.format(1, map(20000, 1))), "format: app id 20000");
        check(throwsIllegal(() -> PersonaFile.format(1, map(10100, 0))), "format: place 0");
        check(throwsIllegal(() -> PersonaFile.format(1, map(10100, -1))), "format: place -1");
        check(throwsIllegal(() -> PersonaFile.format(1, null)), "format: null");

        String s = "s 0000000000000005\n";
        check(!throwsIllegal(() -> PersonaFile.parse(s)), "a file with only the salt");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p 10100 7\np 10100 8\n")), "a repeated app id");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p 9999 7\n")), "app id 9999");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p 20000 7\n")), "app id 20000");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p 10100 0\n")), "place 0");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p 10100 -3\n")), "a negative place");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p 10100 2147483648\n")), "a place beyond int");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p 10100 +3\n")), "a place with a plus sign");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p 10100 ٣\n")), "a place in another script");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p 010100 3\n")), "an app id with a leading zero");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p 10100 03\n")), "a place with a leading zero");
        check(throwsIllegal(() -> PersonaFile.parse(s + "x 10100 3\n")), "another line type");
        check(throwsIllegal(() -> PersonaFile.parse(s + "s 0000000000000006\n")), "a second salt line");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p 10100\n")), "a line with too few fields");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p 10100 3 4\n")), "a line with too many fields");
        check(throwsIllegal(() -> PersonaFile.parse(s + "p  10100 3\n")), "two spaces");
        check(throwsIllegal(() -> PersonaFile.parse("p 10100 3\n" + s)), "the salt is not first");
        check(throwsIllegal(() -> PersonaFile.parse("p 10100 3\n")), "no salt");
        check(throwsIllegal(() -> PersonaFile.parse("")), "an empty file");
        check(throwsIllegal(() -> PersonaFile.parse("s 000000000000005\n")), "15 hex digits");
        check(throwsIllegal(() -> PersonaFile.parse("s 00000000000000005\n")), "17 hex digits");
        check(throwsIllegal(() -> PersonaFile.parse("s 000000000000000A\n")), "upper-case hex");
        check(throwsIllegal(() -> PersonaFile.parse("s 000000000000000g\n")), "a character that is not hex");
        check(throwsIllegal(() -> PersonaFile.parse(s.substring(0, s.length() - 1))), "no final newline");
        check(throwsIllegal(() -> PersonaFile.parse(s.replace("\n", "\r\n"))), "CRLF");
        check(throwsIllegal(() -> PersonaFile.parse(s + "\n")), "an empty line");
        check(throwsIllegal(() -> PersonaFile.parse(null)), "null");
        StringBuilder b = new StringBuilder(s);
        for (int i = 0; i < 8191; i++) {
            b.append("p ").append(10000 + i).append(" 1\n");
        }
        check(PersonaFile.parse(b.toString()).placeOf(10000 + 8190) == 1, "8192 lines");
        b.append("p 18191 1\n");
        check(throwsIllegal(() -> PersonaFile.parse(b.toString())), "8193 lines");
        Map<Integer, Integer> big = new LinkedHashMap<>();
        for (int i = 0; i < 8192; i++) {
            big.put(10000 + i, 1);
        }
        check(throwsIllegal(() -> PersonaFile.format(1, big)), "format: more entries than the reader accepts");
    }
}
