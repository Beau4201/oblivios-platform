// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** What the store, the history file and the settings do with a server place (ADR-161). */
public final class ServerPlaceCheck {
    private static final ServerPlace BERLIN = new ServerPlace(ServerPlace.CITY, "DE", "Berlin", 52.52f, 13.4f, 24940, "Hetzner Online GmbH");
    private static final ServerPlace FRANKFURT = new ServerPlace(ServerPlace.CITY, "DE", "Frankfurt", 50.11f, 8.68f, 24940, "Hetzner Online GmbH");
    private static final ServerPlace ANY = new ServerPlace(ServerPlace.ANYCAST, "US", "", 0f, 0f, 13335, "Cloudflare, Inc.");

    private static DataMapStore.Row only(DataMapStore s, String key) {
        for (DataMapStore.Row r : s.snapshot(0)) {
            if (r.key.equals(key)) {
                return r;
            }
        }
        throw new AssertionError("no row " + key);
    }

    public static void main(String[] args) throws Exception {
        // ---- the address a DNS answer is looked up from
        Check.eq("1.2.3.4", ServiceKeys.preferredAddress(new String[] {"2001:db8::1", "1.2.3.4", "5.6.7.8"}, 10), "the first IPv4 wins");
        Check.eq("2001:db8::1", ServiceKeys.preferredAddress(new String[] {"2001:db8::1", "2001:db8::2"}, 10), "else the first address");
        Check.eq("2001:db8::1", ServiceKeys.preferredAddress(new String[] {null, "", "2001:db8::1"}, 10), "empty entries are skipped");
        Check.eq(null, ServiceKeys.preferredAddress(new String[] {null, ""}, 10), "no usable address");
        Check.eq(null, ServiceKeys.preferredAddress(null, 10), "no answer");
        Check.eq("2001:db8::1", ServiceKeys.preferredAddress(new String[] {"2001:db8::1", "1.2.3.4"}, 1), "only the first max addresses are read");

        // ---- the store keeps the first place that says something
        DataMapStore s = new DataMapStore(10, 100);
        s.record(0, 10100, "com.a", "x.com", false, true, 1000, ServerPlace.NOTHING);
        Check.eq(null, only(s, "x.com").place, "a place that says nothing is not kept");
        s.record(0, 10100, "com.a", "x.com", false, true, 2000, BERLIN);
        Check.eq(BERLIN, only(s, "x.com").place, "the first place that says something is kept");
        s.record(0, 10100, "com.a", "x.com", false, true, 3000, FRANKFURT);
        Check.eq(BERLIN, only(s, "x.com").place, "a later address does not replace it");
        s.record(0, 10100, "com.a", "x.com", false, true, 4000, null);
        Check.eq(BERLIN, only(s, "x.com").place, "no address does not remove it");
        s.record(0, 10100, "com.a", "y.com", false, true, 1000, ANY);
        Check.eq(ANY, only(s, "y.com").place, "an anycast place is kept as it is");
        Check.eq(4, only(s, "x.com").count, "the counts are unchanged by places");

        // a place that can be drawn takes the place of one that cannot, and is then kept (one service, one answer)
        ServerPlace uncertain = new ServerPlace(ServerPlace.UNCERTAIN, "DE", "", 0f, 0f, 24940, "Hetzner Online GmbH");
        ServerPlace steered = new ServerPlace(ServerPlace.STEERED, "US", "", 0f, 0f, 16509, "AMAZON-02");
        ServerPlace asnOnly = new ServerPlace(ServerPlace.NONE, "", "", 0f, 0f, 24940, "Hetzner Online GmbH");
        ServerPlace country = new ServerPlace(ServerPlace.COUNTRY, "DE", "", 51f, 10f, 24940, "Hetzner Online GmbH");
        for (ServerPlace first : new ServerPlace[] {ANY, uncertain, steered, asnOnly}) {
            DataMapStore t = new DataMapStore(10, 100);
            t.record(0, 10100, "com.a", "p.com", false, true, 1000, first);
            Check.eq(first, only(t, "p.com").place, "the first place that says something is kept: kind " + first.kind);
            t.record(0, 10100, "com.a", "p.com", false, true, 2000, BERLIN);
            Check.eq(BERLIN, only(t, "p.com").place, "a place that can be drawn replaces kind " + first.kind);
            t.record(0, 10100, "com.a", "p.com", false, true, 3000, ANY);
            Check.eq(BERLIN, only(t, "p.com").place, "and an anycast answer does not take it back");
        }
        DataMapStore u = new DataMapStore(10, 100);
        u.record(0, 10100, "com.a", "p.com", false, true, 1000, country);
        u.record(0, 10100, "com.a", "p.com", false, true, 2000, BERLIN);
        Check.eq(country, only(u, "p.com").place, "a place that is drawn is not replaced by another that is drawn");

        // needsPlace: the service looks an address up only for a row that has no place to draw yet
        DataMapStore n = new DataMapStore(10, 100);
        Check.isTrue(n.needsPlace(0, 10100, "q.com"), "a service not seen yet needs one");
        n.record(0, 10100, "com.a", "q.com", false, true, 1000, null);
        Check.isTrue(n.needsPlace(0, 10100, "q.com"), "a row with no place needs one");
        n.record(0, 10100, "com.a", "q.com", false, true, 1000, ANY);
        Check.isTrue(n.needsPlace(0, 10100, "q.com"), "a row whose place cannot be drawn needs one");
        n.record(0, 10100, "com.a", "q.com", false, true, 1000, BERLIN);
        Check.isTrue(!n.needsPlace(0, 10100, "q.com"), "a row with a place to draw does not");
        Check.isTrue(n.needsPlace(0, 10101, "q.com") && n.needsPlace(1, 10100, "q.com"), "another app or user is another row");
        Check.eq(1, n.snapshot(0).size(), "asking creates no row");

        // the reply is bounded by bytes, not only by rows: a row is counted as the parcel will hold it
        DataMapStore.Row small = new DataMapStore.Row(10100, "com.a", "x.com", 1000, 2000, 3, 1, 2, true);
        int smallBytes = DataMapStore.parcelBytes(small);
        // 8 for the parcelable, 52 for the numbers; a string is 4 + its UTF-16 length plus a terminator, to 4 bytes; the owner
        // id (not in a row) is counted at its longest, 64 characters: 136
        Check.eq(8 + 52 + 136 + 16 + 16 + 8 + 8 + 8, smallBytes, "a row without a place");
        DataMapStore.Row big = new DataMapStore.Row(10100, "p".repeat(255), "k".repeat(253), 1000, 2000, 3, 1, 2, true,
                new ServerPlace(ServerPlace.CITY, "DE", "c".repeat(255), 1f, 2f, 3, "o".repeat(255)));
        Check.eq(8 + 52 + 136 + 516 + 512 + 12 + 516 + 516, DataMapStore.parcelBytes(big), "a row with the longest names");
        List<DataMapStore.Row> many = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            many.add(new DataMapStore.Row(10100, "com.a", "h" + i + ".com", i, i, 1, 1, 0, true));
        }
        Check.eq(100, DataMapStore.within(many, 1 << 20).size(), "all rows fit a large budget");
        int each = DataMapStore.parcelBytes(many.get(0));
        List<DataMapStore.Row> cut = DataMapStore.within(many, each * 10 + each / 2);
        Check.eq(10, cut.size(), "the rows that fit the budget are kept");
        Check.eq(many.get(90), cut.get(0), "the rows seen last are the ones kept");
        Check.eq(many.get(99), cut.get(9), "in their order");
        Check.eq(10, DataMapStore.within(many.subList(0, 15), each * 10 + each / 2).size(), "15 rows over a budget of 10 and a half");
        Check.eq(15, DataMapStore.within(many.subList(0, 15), each * 15).size(), "15 rows in a budget of exactly 15");
        Check.eq(0, DataMapStore.within(many, 1).size(), "a budget that fits no row keeps none");
        Check.eq(0, DataMapStore.within(new ArrayList<DataMapStore.Row>(), 100).size(), "no rows, no rows");

        // ---- rows restored from the history keep their place, and do not override one in memory
        DataMapStore r = new DataMapStore(10, 100);
        List<DataMapStore.Row> rows = new ArrayList<>();
        rows.add(new DataMapStore.Row(10100, "com.a", "x.com", 1000, 2000, 3, 1, 2, true, BERLIN));
        rows.add(new DataMapStore.Row(10100, "com.a", "z.com", 1000, 2000, 1, 1, 0, true));
        r.restore(0, rows);
        Check.eq(BERLIN, only(r, "x.com").place, "a restored row keeps its place");
        Check.eq(null, only(r, "z.com").place, "a row from an old file has none");
        r.record(0, 10100, "com.a", "z.com", false, true, 3000, FRANKFURT);
        Check.eq(FRANKFURT, only(r, "z.com").place, "a row with no place takes the first one it hears");
        r.record(0, 10100, "com.a", "x.com", false, true, 3000, FRANKFURT);
        Check.eq(BERLIN, only(r, "x.com").place, "a restored place is not replaced");

        // a row restored from an old file must not erase the place that is in memory, nor replace it
        DataMapStore m = new DataMapStore(10, 100);
        m.record(0, 10100, "com.a", "x.com", false, true, 1000, BERLIN);
        List<DataMapStore.Row> older = new ArrayList<>();
        older.add(new DataMapStore.Row(10100, "com.a", "x.com", 500, 900, 1, 1, 0, true));
        m.restore(0, older);
        Check.eq(BERLIN, only(m, "x.com").place, "a restored row with no place keeps the place in memory");
        List<DataMapStore.Row> other = new ArrayList<>();
        other.add(new DataMapStore.Row(10100, "com.a", "x.com", 500, 900, 1, 1, 0, true, FRANKFURT));
        m.restore(0, other);
        Check.eq(BERLIN, only(m, "x.com").place, "a restored place does not replace the one in memory");

        // a restored place that can be drawn replaces one in memory that cannot; one that cannot be drawn replaces nothing
        DataMapStore up = new DataMapStore(10, 100);
        up.record(0, 10100, "com.a", "x.com", false, true, 1000, ANY);
        List<DataMapStore.Row> better = new ArrayList<>();
        better.add(new DataMapStore.Row(10100, "com.a", "x.com", 500, 900, 1, 1, 0, true, BERLIN));
        up.restore(0, better);
        Check.eq(BERLIN, only(up, "x.com").place, "a restored place that is drawn replaces one that is not");
        List<DataMapStore.Row> worse = new ArrayList<>();
        worse.add(new DataMapStore.Row(10100, "com.a", "x.com", 500, 900, 1, 1, 0, true, ANY));
        up.restore(0, worse);
        Check.eq(BERLIN, only(up, "x.com").place, "a restored place that is not drawn replaces nothing");

        // ---- the history file
        TreeMap<Integer, List<DataMapStore.Row>> days = new TreeMap<>();
        List<DataMapStore.Row> day = new ArrayList<>();
        day.add(new DataMapStore.Row(10100, "com.a", "x.com", 3_600_000L, 7_200_000L, 3, 1, 2, true, BERLIN));
        day.add(new DataMapStore.Row(10100, "com.a", "y.com", 3_600_000L, 7_200_000L, 1, 1, 0, true, ANY));
        day.add(new DataMapStore.Row(10100, "com.a", "z.com", 3_600_000L, 7_200_000L, 1, 1, 0, true));
        days.put(5, day);
        String with = HistoryCodec.encode(days, true);
        String without = HistoryCodec.encode(days, false);
        Check.isTrue(with.contains("Berlin") && with.contains("Hetzner"), "the file holds the place when it is chosen");
        Check.isTrue(!without.contains("Berlin") && !without.contains("Hetzner") && !without.contains("Cloudflare"),
                "the file holds no place when it is not chosen");
        for (String line : without.split("\n")) {
            if (line.startsWith("row\t")) {
                Check.eq(10, line.split("\t", -1).length, "a row without places has the ten fields of the first format");
            }
        }
        Check.eq(HistoryCodec.encode(days), without, "the old call writes no places");
        List<DataMapStore.Row> back = HistoryCodec.decode(with).get(5);
        Check.eq(BERLIN, back.get(0).place, "the place round trips");
        Check.eq(ANY, back.get(1).place, "an anycast place round trips");
        Check.eq(null, back.get(2).place, "a row with no place round trips");
        Check.eq(with, HistoryCodec.encode(HistoryCodec.decode(with), true), "encode(decode(x)) is x");
        Check.eq(null, HistoryCodec.decode(without).get(5).get(0).place, "a file without places decodes with none");
        // a field with a tab is not written as a place: the row stays, the place goes
        TreeMap<Integer, List<DataMapStore.Row>> evil = new TreeMap<>();
        List<DataMapStore.Row> evilDay = new ArrayList<>();
        evilDay.add(new DataMapStore.Row(10100, "com.a", "x.com", 3_600_000L, 3_600_000L, 1, 1, 0, true,
                new ServerPlace(ServerPlace.CITY, "DE", "Ber\tlin", 1f, 2f, 3, "op")));
        evil.put(1, evilDay);
        String evilText = HistoryCodec.encode(evil, true);
        Check.eq(null, HistoryCodec.decode(evilText).get(1).get(0).place, "a place with a tab is dropped, not written");
        // a field with a line break or a carriage return is not written either, in any of the three texts
        for (ServerPlace bad : new ServerPlace[] {new ServerPlace(ServerPlace.CITY, "DE", "Ber\nlin", 1f, 2f, 3, "op"),
                new ServerPlace(ServerPlace.CITY, "DE", "Berlin", 1f, 2f, 3, "o\rp"),
                new ServerPlace(ServerPlace.CITY, "DE", "Berlin", 1f, 2f, 3, "o\tp"),
                new ServerPlace(ServerPlace.CITY, "D\tE", "Berlin", 1f, 2f, 3, "op")}) {
            TreeMap<Integer, List<DataMapStore.Row>> bd = new TreeMap<>();
            List<DataMapStore.Row> bl = new ArrayList<>();
            bl.add(new DataMapStore.Row(10100, "com.a", "x.com", 3_600_000L, 3_600_000L, 1, 1, 0, true, bad));
            bd.put(1, bl);
            Check.eq(null, HistoryCodec.decode(HistoryCodec.encode(bd, true)).get(1).get(0).place, "an unwritable place is dropped");
        }
        // a place that says nothing is not written, even when places are chosen: the row keeps its ten fields
        TreeMap<Integer, List<DataMapStore.Row>> nothing = new TreeMap<>();
        List<DataMapStore.Row> nl = new ArrayList<>();
        nl.add(new DataMapStore.Row(10100, "com.a", "x.com", 3_600_000L, 3_600_000L, 1, 1, 0, true, ServerPlace.NOTHING));
        nothing.put(1, nl);
        Check.eq(10, HistoryCodec.encode(nothing, true).split("\n")[2].split("\t", -1).length, "nothing known is not written");
        // an AS number of 2**31 or more is written as the number it is and read back
        ServerPlace bigPlace = new ServerPlace(ServerPlace.CITY, "NL", "Amsterdam", 52.37f, 4.9f, (int) 4200000001L, "Example Transit");
        TreeMap<Integer, List<DataMapStore.Row>> bigAsn = new TreeMap<>();
        List<DataMapStore.Row> ba = new ArrayList<>();
        ba.add(new DataMapStore.Row(10100, "com.a", "x.com", 3_600_000L, 3_600_000L, 1, 1, 0, true, bigPlace));
        bigAsn.put(1, ba);
        String bigText = HistoryCodec.encode(bigAsn, true);
        Check.isTrue(bigText.contains("\t4200000001\t") && !bigText.contains("\t-"), "the AS number is written unsigned");
        Check.eq(bigPlace, HistoryCodec.decode(bigText).get(1).get(0).place, "and read back as the same 32 bits");
        // a row that is as long as the data allows is read back, whatever the names are: one long row never loses the history
        TreeMap<Integer, List<DataMapStore.Row>> longDays = new TreeMap<>();
        List<DataMapStore.Row> longRows = new ArrayList<>();
        longRows.add(new DataMapStore.Row(10100, "p".repeat(100), "h".repeat(249), 3_600_000L, 7_200_000L, 5, 3, 2, true,
                new ServerPlace(ServerPlace.CITY, "DE", "c".repeat(100), 50.11f, 8.68f, 24940, "o".repeat(100))));
        longRows.add(new DataMapStore.Row(10100, "q".repeat(255), "k".repeat(253), 3_600_000L, 7_200_000L, 5, 3, 2, true,
                new ServerPlace(ServerPlace.CITY, "DE", "c".repeat(255), 50.11f, 8.68f, 24940, "o".repeat(255))));
        longRows.add(new DataMapStore.Row(10100, "com.a", "z.com", 3_600_000L, 7_200_000L, 1, 1, 0, true, BERLIN));
        // a row that is too long for the decoder even without a place cannot be written: it is left out, the file stays whole
        longRows.add(new DataMapStore.Row(10100, "r".repeat(1100), "m.com", 3_600_000L, 7_200_000L, 1, 1, 0, true, BERLIN));
        longDays.put(1, longRows);
        List<DataMapStore.Row> longBack = HistoryCodec.decode(HistoryCodec.encode(longDays, true)).get(1);
        Check.eq(3, longBack.size(), "no row is lost but the one that cannot be read back");
        Check.eq(longRows.get(0).place, longBack.get(0).place, "a row of 634 characters keeps its place");
        Check.eq(null, longBack.get(1).place, "a row that is too long for the decoder is written without its place");
        Check.eq(longRows.get(1).key, longBack.get(1).key, "and is still there");
        Check.eq(BERLIN, longBack.get(2).place, "the rows after it keep theirs");
        for (String line : HistoryCodec.encode(longDays, true).split("\n")) {
            Check.isTrue(line.length() <= HistoryCodec.MAX_LINE, "no line is longer than the decoder reads");
        }
        // damaged places are refused
        String row = with.split("\n")[2];
        for (String bad : new String[] {row.replace("\t1\tDE\t", "\t9\tDE\t"), row.replace("\t1\tDE\t", "\t1\tDEU\t"),
                row.replace("\t52.52\t", "\tNaN\t"), row.replace("\t52.52\t", "\tInfinity\t"), row.replace("\t13.4\t", "\tNaN\t"),
                row.replace("\t13.4\t", "\t-Infinity\t"), row.replace("\t24940\t", "\tx\t")}) {
            final String text = "datamap-history\t1\nday\t5\n" + bad + "\n";
            Check.throwsA(HistoryCodec.FormatException.class, () -> HistoryCodec.decode(text), "a damaged place: " + bad);
        }
        Check.throwsA(HistoryCodec.FormatException.class,
                () -> HistoryCodec.decode("datamap-history\t1\nday\t5\n" + row + "\textra\n"), "eighteen fields");

        // the places survive the two operations that rebuild rows
        List<DataMapStore.Row> live = new ArrayList<>();
        live.add(new DataMapStore.Row(10100, "com.a", "x.com", 1000, 9000, 5, 3, 2, true, BERLIN));
        List<DataMapStore.Row> base = new ArrayList<>();
        base.add(new DataMapStore.Row(10100, "com.a", "x.com", 1000, 4000, 2, 1, 1, true, BERLIN));
        Check.eq(BERLIN, HistoryCodec.since(live, base, 5000).get(0).place, "since keeps the place");
        TreeMap<Integer, List<DataMapStore.Row>> one = new TreeMap<>();
        one.put(0, base);
        Check.eq(BERLIN, HistoryCodec.aggregate(one, live).get(0).place, "aggregate keeps the place");
        List<DataMapStore.Row> noPlace = new ArrayList<>();
        noPlace.add(new DataMapStore.Row(10100, "com.a", "x.com", 1000, 4000, 2, 1, 1, true));
        TreeMap<Integer, List<DataMapStore.Row>> two = new TreeMap<>();
        two.put(0, noPlace);
        Check.eq(BERLIN, HistoryCodec.aggregate(two, live).get(0).place, "aggregate takes the place of the row that has one");

        // aggregate: the place that can be drawn wins, the older one only among equals
        List<DataMapStore.Row> newerCity = new ArrayList<>();
        newerCity.add(new DataMapStore.Row(10100, "com.a", "x.com", 1000, 9000, 5, 3, 2, true, BERLIN));
        List<DataMapStore.Row> olderAny = new ArrayList<>();
        olderAny.add(new DataMapStore.Row(10100, "com.a", "x.com", 1000, 4000, 2, 1, 1, true, ANY));
        TreeMap<Integer, List<DataMapStore.Row>> anyDay = new TreeMap<>();
        anyDay.put(0, olderAny);
        Check.eq(BERLIN, HistoryCodec.aggregate(anyDay, newerCity).get(0).place, "a place that is drawn beats an older one that is not");
        List<DataMapStore.Row> newerAny = new ArrayList<>();
        newerAny.add(new DataMapStore.Row(10100, "com.a", "x.com", 1000, 9000, 5, 3, 2, true, ANY));
        List<DataMapStore.Row> olderCity = new ArrayList<>();
        olderCity.add(new DataMapStore.Row(10100, "com.a", "x.com", 1000, 4000, 2, 1, 1, true, BERLIN));
        anyDay.put(0, olderCity);
        Check.eq(BERLIN, HistoryCodec.aggregate(anyDay, newerAny).get(0).place, "and one that is not never replaces one that is");
        anyDay.put(0, java.util.Collections.singletonList(new DataMapStore.Row(10100, "com.a", "x.com", 1000, 4000, 2, 1, 1, true, FRANKFURT)));
        Check.eq(FRANKFURT, HistoryCodec.aggregate(anyDay, newerCity).get(0).place, "between two that are drawn the older one stays");

        List<DataMapStore.Row> liveNone = new ArrayList<>();
        liveNone.add(new DataMapStore.Row(10100, "com.a", "x.com", 1000, 9000, 5, 3, 2, true));
        anyDay.put(0, olderCity);
        Check.eq(BERLIN, HistoryCodec.aggregate(anyDay, liveNone).get(0).place, "a live row with no place takes nothing away");
        anyDay.put(0, olderAny);
        Check.eq(ANY, HistoryCodec.aggregate(anyDay, liveNone).get(0).place, "nor does it take away a place that cannot be drawn");

        // ---- the history file is bounded when it is written: the oldest days go, never the newest, and never all
        TreeMap<Integer, List<DataMapStore.Row>> manyDays = new TreeMap<>();
        for (int d = 1; d <= 30; d++) {
            List<DataMapStore.Row> rs = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                rs.add(new DataMapStore.Row(10100, "com.a", "h" + i + ".example.com", 3_600_000L, 7_200_000L, 3, 1, 2, true, BERLIN));
            }
            manyDays.put(d, rs);
        }
        int whole = HistoryCodec.encode(manyDays, true).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        Check.eq(HistoryCodec.encode(manyDays, true), HistoryCodec.encodeWithin(manyDays, true, whole), "under the bound nothing goes");
        String half = HistoryCodec.encodeWithin(manyDays, true, whole / 2);
        Check.isTrue(half.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= whole / 2, "the file fits the bound");
        TreeMap<Integer, List<DataMapStore.Row>> kept = HistoryCodec.decode(half);
        Check.isTrue(kept.size() >= 14 && kept.size() < 30, "about half of the days are kept: " + kept.size());
        Check.eq(30, kept.lastKey().intValue(), "the newest day is kept");
        Check.eq(30 - kept.size() + 1, kept.firstKey().intValue(), "the days kept are the newest, in a row");
        Check.eq(manyDays.size(), 30, "the input is not changed");
        TreeMap<Integer, List<DataMapStore.Row>> lone = new TreeMap<>();
        lone.put(7, manyDays.get(7));
        Check.eq(1, HistoryCodec.decode(HistoryCodec.encodeWithin(lone, true, 10)).size(), "the last day is written even when it is over the bound");
        // names that take more than a byte a character count as bytes
        TreeMap<Integer, List<DataMapStore.Row>> wide = new TreeMap<>();
        List<DataMapStore.Row> wr = new ArrayList<>();
        wr.add(new DataMapStore.Row(10100, "com.a", "x.example.com", 3_600_000L, 7_200_000L, 3, 1, 2, true,
                new ServerPlace(ServerPlace.CITY, "DE", "\u00fc".repeat(200), 1f, 2f, 3, "op")));
        wide.put(1, wr);
        wide.put(2, wr);
        String wtext = HistoryCodec.encode(wide, true);
        Check.isTrue(wtext.length() < wtext.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, "the fixture has multibyte text");
        int wbytes = wtext.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        Check.eq(1, HistoryCodec.decode(HistoryCodec.encodeWithin(wide, true, wbytes - 1)).size(), "bytes, not characters");
        Check.eq(2, HistoryCodec.decode(HistoryCodec.encodeWithin(wide, true, wbytes)).size(), "exactly the bound keeps both");

        // ---- the setting
        UserSettings d = UserSettings.defaults();
        Check.isTrue(!d.serverHistory, "server places are not kept by default");
        Check.isTrue(d.isDefault(), "the defaults are the defaults");
        UserSettings on = d.withHistory(true, 7).withServerHistory(true);
        Check.isTrue(!on.isDefault(), "the history on is not the default");
        // the choice is kept in a file of its own, so that a build without it reads the settings file as it always did
        Check.eq(d.withHistory(true, 7).encode(), on.encode(), "the settings text does not hold the choice");
        Check.isTrue(!on.encode().contains("serverHistory"), "no key of it in the settings file");
        Check.eq(d.withHistory(true, 30).encode(), d.withHistory(true, 30).withServerHistory(true).encode(), "the choice changes nothing in the settings file");
        // the choice belongs to the history: it cannot be switched on without it, and it goes when the history goes
        Check.isTrue(!d.withServerHistory(true).serverHistory, "the choice cannot be switched on while the history is off");
        Check.isTrue(on.serverHistory && on.withHistory(true, 9).serverHistory, "it stays on when the days change");
        Check.isTrue(!on.withHistory(false, 7).serverHistory, "it goes off with the history");
        Check.isTrue(!on.withHistory(false, 7).withHistory(true, 7).serverHistory, "and is off when the history comes back");
        Check.isTrue(!on.withCollecting(false).withHistory(false, 7).serverHistory, "also with collecting off");
        Check.isTrue(!UserSettings.decode(on.encode()).serverHistory, "reading the settings file never turns it on");
        Check.eq(7, UserSettings.decode(on.encode()).days, "the days round trip");
        Check.isTrue(on.withCollecting(false).serverHistory && on.withHistory(true, 9).serverHistory, "other changes keep it");
        Check.isTrue(!on.withServerHistory(false).serverHistory, "it can be switched off");
        Check.isTrue(UserSettings.decode("datamap-settings\t1\nhistory\t1\nserverHistory\t1\n").isDefault(),
                "the key of the choice is unknown to the settings file: the file is refused, as for any unknown key");
        System.out.println("PASS ServerPlaceCheck");
    }
}
