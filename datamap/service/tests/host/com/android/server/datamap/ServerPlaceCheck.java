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
        // damaged places are refused
        String row = with.split("\n")[2];
        for (String bad : new String[] {row.replace("\t1\tDE\t", "\t9\tDE\t"), row.replace("\t1\tDE\t", "\t1\tDEU\t"),
                row.replace("\t52.52\t", "\tNaN\t"), row.replace("\t24940\t", "\tx\t")}) {
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

        // ---- the setting
        UserSettings d = UserSettings.defaults();
        Check.isTrue(!d.serverHistory, "server places are not kept by default");
        Check.isTrue(d.isDefault(), "the defaults are the defaults");
        UserSettings on = d.withHistory(true, 7).withServerHistory(true);
        Check.isTrue(!on.isDefault(), "keeping server places is not the default");
        Check.isTrue(!d.withServerHistory(true).isDefault(), "the choice alone, with the history off, is not the default");
        Check.isTrue(UserSettings.decode(d.withServerHistory(true).encode()).serverHistory, "the choice alone round trips");
        Check.isTrue(UserSettings.decode(on.encode()).serverHistory, "the choice round trips");
        Check.eq(7, UserSettings.decode(on.encode()).days, "the days round trip with it");
        Check.isTrue(!UserSettings.decode(d.withHistory(true, 7).encode()).serverHistory, "a file without the line is off");
        Check.isTrue(on.withCollecting(false).serverHistory && on.withHistory(true, 9).serverHistory, "other changes keep it");
        Check.isTrue(!on.withServerHistory(false).serverHistory, "it can be switched off");
        Check.isTrue(d.withServerHistory(true).withServerHistory(false).isDefault(), "off again is the default again");
        Check.isTrue(UserSettings.decode("datamap-settings\t1\nserverHistory\t2\n").isDefault(), "a bad flag gives the defaults");
        System.out.println("PASS ServerPlaceCheck");
    }
}
