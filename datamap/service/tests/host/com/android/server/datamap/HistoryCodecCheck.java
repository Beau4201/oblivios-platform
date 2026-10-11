// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public final class HistoryCodecCheck {
    private static DataMapStore.Row row(int uid, String key, long first, long last, int count) {
        return new DataMapStore.Row(uid, "com.a", key, first, last, count, count / 2, count - count / 2, true);
    }

    private static void bad(String why, String text) {
        Check.throwsA(HistoryCodec.FormatException.class, () -> HistoryCodec.decode(text), why);
    }

    public static void main(String[] args) throws Exception {
        TreeMap<Integer, List<DataMapStore.Row>> days = new TreeMap<>();
        List<DataMapStore.Row> d1 = new ArrayList<>();
        d1.add(row(10100, "x.com", 100, 200, 4));
        d1.add(row(10100, "ip:203.0.113.7", 150, 250, 1));
        days.put(20000, d1);
        List<DataMapStore.Row> d2 = new ArrayList<>();
        d2.add(row(10200, "y.com", 300, 400, 7));
        days.put(20001, d2);

        String text = HistoryCodec.encode(days);
        Check.isTrue(text.startsWith("datamap-history\t1\n"), "the header");
        TreeMap<Integer, List<DataMapStore.Row>> back = HistoryCodec.decode(text);
        Check.eq(2, back.size(), "two days");
        Check.eq(2, back.get(20000).size(), "two rows on the first day");
        DataMapStore.Row r = back.get(20000).get(0);
        Check.eq("x.com", r.key, "key");
        Check.eq(10100, r.uid, "uid");
        Check.eq(100L, r.firstWall, "first");
        Check.eq(200L, r.lastWall, "last");
        Check.eq(4, r.count, "count");
        Check.eq(2, r.fg, "foreground");
        Check.eq(2, r.bg, "background");
        Check.isTrue(r.viaDns, "dns flag");
        Check.eq(text, HistoryCodec.encode(back), "encode(decode(x)) is x");
        Check.eq(0, HistoryCodec.decode("datamap-history\t1\n").size(), "an empty history is valid");

        // a file that is not ours, or damaged, is refused
        bad("empty text", "");
        bad("no header", "day\t1\n");
        bad("wrong version", "datamap-history\t2\n");
        bad("a row before a day", "datamap-history\t1\nrow\t1\tp\tk\t1\t2\t3\t1\t2\t1\n");
        bad("unknown record", "datamap-history\t1\nday\t1\nfoo\t1\n");
        bad("a row with too few fields", "datamap-history\t1\nday\t1\nrow\t1\tp\tk\n");
        bad("a number that is not one", "datamap-history\t1\nday\t1\nrow\t1\tp\tk\tx\t2\t3\t1\t2\t1\n");
        bad("a negative count", "datamap-history\t1\nday\t1\nrow\t1\tp\tk\t1\t2\t-3\t1\t2\t1\n");
        bad("a dns flag of 2", "datamap-history\t1\nday\t1\nrow\t1\tp\tk\t1\t2\t3\t1\t2\t2\n");
        bad("an empty key", "datamap-history\t1\nday\t1\nrow\t1\tp\t\t1\t2\t3\t1\t2\t1\n");
        bad("a line that is too long", "datamap-history\t1\nday\t1\nrow\t1\tp\t" + "k".repeat(HistoryCodec.MAX_LINE) + "\t1\t2\t3\t1\t2\t1\n");
        StringBuilder many = new StringBuilder("datamap-history\t1\nday\t1\n");
        for (int i = 0; i <= HistoryCodec.MAX_ROWS; i++) {
            many.append("row\t1\tp\tk\t1\t2\t3\t1\t2\t1\n");
        }
        bad("more rows than allowed", many.toString());

        // a tab or a line break cannot be written
        TreeMap<Integer, List<DataMapStore.Row>> evil = new TreeMap<>();
        List<DataMapStore.Row> e = new ArrayList<>();
        e.add(new DataMapStore.Row(1, "com.a", "a\tb", 1, 2, 3, 1, 2, true));
        evil.put(1, e);
        Check.throwsA(IllegalArgumentException.class, () -> HistoryCodec.encode(evil), "a tab in a key");

        // days: local days since 1970, also for a time before the epoch and across a zone
        long dayMs = HistoryCodec.DAY_MS;
        Check.eq(0, HistoryCodec.dayIndex(0, 0), "the epoch is day 0");
        Check.eq(1, HistoryCodec.dayIndex(dayMs, 0), "day 1");
        Check.eq(-1, HistoryCodec.dayIndex(-1, 0), "just before the epoch is day -1");
        Check.eq(1, HistoryCodec.dayIndex(dayMs - 1, 3_600_000L), "an hour east moves 23:59 to the next day");
        Check.eq(0, HistoryCodec.dayIndex(dayMs, -3_600_000L), "an hour west keeps 00:30 on the day before");

        // expiry keeps the days newer than today minus the chosen length
        TreeMap<Integer, List<DataMapStore.Row>> long5 = new TreeMap<>();
        for (int d = 100; d <= 105; d++) {
            long5.put(d, new ArrayList<>());
        }
        Check.eq("[104, 105]", HistoryCodec.prune(long5, 105, 2).keySet().toString(), "keep 2 days");
        Check.eq(6, HistoryCodec.prune(long5, 105, 90).size(), "keep 90 days keeps all");
        Check.eq(0, HistoryCodec.prune(long5, 105, 0).size(), "keep 0 days keeps none");
        Check.eq(0, HistoryCodec.prune(long5, 500, 90).size(), "all of them older than the window");

        // aggregate: one row per (uid, key) over the days and the live rows
        TreeMap<Integer, List<DataMapStore.Row>> two = new TreeMap<>();
        List<DataMapStore.Row> a = new ArrayList<>();
        a.add(row(10100, "x.com", 100, 200, 4));
        two.put(1, a);
        List<DataMapStore.Row> b = new ArrayList<>();
        b.add(row(10100, "x.com", 300, 400, 2));
        b.add(row(10100, "w.com", 50, 60, 1));
        two.put(2, b);
        List<DataMapStore.Row> live = new ArrayList<>();
        live.add(row(10100, "x.com", 500, 600, 3));
        List<DataMapStore.Row> agg = HistoryCodec.aggregate(two, live);
        Check.eq(2, agg.size(), "two services");
        Check.eq("w.com", agg.get(0).key, "in order");
        DataMapStore.Row x = agg.get(1);
        Check.eq(9, x.count, "4 + 2 + 3");
        Check.eq(100L, x.firstWall, "the earliest first");
        Check.eq(600L, x.lastWall, "the latest last");
        Check.eq(0, HistoryCodec.aggregate(new TreeMap<>(), new ArrayList<>()).size(), "nothing in, nothing out");
        Check.passed("HistoryCodecCheck");
    }
}
