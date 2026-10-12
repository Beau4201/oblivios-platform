// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public final class HistoryCodecCheck {
    private static final long H = HistoryCodec.HOUR_MS;

    private static DataMapStore.Row row(int uid, String key, long first, long last, int count) {
        return new DataMapStore.Row(uid, "com.a", key, first, last, count, count / 2, count - count / 2, true);
    }

    private static void bad(String why, String text) {
        Check.throwsA(HistoryCodec.FormatException.class, () -> HistoryCodec.decode(text), why);
    }

    public static void main(String[] args) throws Exception {
        TreeMap<Integer, List<DataMapStore.Row>> days = new TreeMap<>();
        List<DataMapStore.Row> d1 = new ArrayList<>();
        d1.add(row(10100, "x.com", 100 * H, 200 * H, 4));
        d1.add(row(10100, "ip:203.0.113.7", 150 * H, 250 * H, 1));
        days.put(20000, d1);
        List<DataMapStore.Row> d2 = new ArrayList<>();
        d2.add(row(10200, "y.com", 300 * H, 400 * H, 7));
        days.put(20001, d2);

        String text = HistoryCodec.encode(days);
        Check.isTrue(text.startsWith("datamap-history\t1\n"), "the header");
        TreeMap<Integer, List<DataMapStore.Row>> back = HistoryCodec.decode(text);
        Check.eq(2, back.size(), "two days");
        Check.eq(2, back.get(20000).size(), "two rows on the first day");
        DataMapStore.Row r = back.get(20000).get(0);
        Check.eq("x.com", r.key, "key");
        Check.eq(10100, r.uid, "uid");
        Check.eq(100L * H, r.firstWall, "first");
        Check.eq(200L * H, r.lastWall, "last");
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

        // times are kept to the hour in the file (D1); a file of exact times from before is still read
        List<DataMapStore.Row> mid = new ArrayList<>();
        mid.add(row(10100, "t.com", 5 * H + 1234, 6 * H + 59_999, 2));
        TreeMap<Integer, List<DataMapStore.Row>> midDays = new TreeMap<>();
        midDays.put(7, mid);
        DataMapStore.Row rounded = HistoryCodec.decode(HistoryCodec.encode(midDays)).get(7).get(0);
        Check.eq(5 * H, rounded.firstWall, "the first time is the hour it falls in");
        Check.eq(6 * H, rounded.lastWall, "the last time is the hour it falls in");
        Check.eq(-H, HistoryCodec.toHour(-1), "before the epoch rounds down");
        Check.eq(0L, HistoryCodec.toHour(H - 1), "just before the hour");
        Check.eq(H, HistoryCodec.toHour(H), "on the hour");
        Check.eq(1234L, HistoryCodec.decode("datamap-history\t1\nday\t1\nrow\t1\tp\tk\t1234\t5678\t3\t1\t2\t1\n")
                .get(1).get(0).firstWall, "the exact time of an earlier file");

        // since: what was in memory when the history was switched on is not kept (D2)
        List<DataMapStore.Row> before = new ArrayList<>();
        before.add(row(10100, "old.com", 10, 20, 5));
        before.add(row(10100, "both.com", 30, 40, 4));
        before.add(row(10100, "ev.com", 50, 60, 5));
        List<DataMapStore.Row> now = new ArrayList<>();
        now.add(row(10100, "old.com", 10, 20, 5));      // not seen again: dropped
        now.add(row(10100, "both.com", 30, 900, 7));    // seen before and again: the 3 new contacts
        now.add(row(10100, "ev.com", 800, 850, 2));     // started again after eviction: below its old count, dropped
        now.add(row(10100, "new.com", 800, 850, 2));    // new: kept as it is
        now.add(row(10200, "old.com", 700, 710, 1));    // the same service for another app: new
        List<DataMapStore.Row> kept = HistoryCodec.since(now, before, 500L);
        Check.eq(3, kept.size(), "three rows are kept");
        Check.eq("both.com", kept.get(0).key, "in the order of the live rows");
        Check.eq(3, kept.get(0).count, "7 - 4");
        Check.eq(1, kept.get(0).fg, "3 - 2 in the foreground");
        Check.eq(2, kept.get(0).bg, "4 - 2 in the background");
        Check.eq(500L, kept.get(0).firstWall, "not before the switch-on");
        Check.eq(900L, kept.get(0).lastWall, "the last time stays");
        Check.eq("new.com", kept.get(1).key, "a new row");
        Check.eq(800L, kept.get(1).firstWall, "is kept as it is");
        Check.eq(10200, kept.get(2).uid, "another uid is another row");
        Check.eq(5, HistoryCodec.since(now, new ArrayList<>(), 500L).size(), "no baseline keeps everything");
        Check.eq(0, HistoryCodec.since(new ArrayList<>(), before, 500L).size(), "nothing live, nothing kept");
        Check.passed("HistoryCodecCheck");
    }
}
