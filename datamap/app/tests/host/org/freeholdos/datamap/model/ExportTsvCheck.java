// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

public final class ExportTsvCheck {
    private static void eq(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static MapModel.Entry entry(String pkg, String key, String owner, int category, long first, long last, int count,
            int fg, int bg, boolean dns) {
        MapModel.Entry e = new MapModel.Entry();
        e.pkg = pkg;
        e.serviceKey = key;
        e.ownerId = owner;
        e.category = category;
        e.firstWall = first;
        e.lastWall = last;
        e.count = count;
        e.foreground = fg;
        e.background = bg;
        e.viaDns = dns;
        // a server place must never reach the file
        e.serverKind = 1;
        e.serverCountry = "NL";
        e.serverCity = "Amsterdam";
        e.serverLat = 52.37;
        e.serverLon = 4.89;
        e.serverOperator = "SecretHost";
        return e;
    }

    public static void main(String[] args) {
        eq("package\tservice\tcompany\tcategory\tcount\tforeground\tbackground\tfirst_seen\tlast_seen\tvia_dns",
                ExportTsv.HEADER, "the header");
        eq(ExportTsv.HEADER + "\n", ExportTsv.encode(new MapModel.Input()), "an empty snapshot gives the header only");

        MapModel.Input in = new MapModel.Input();
        MapModel.Owner g = new MapModel.Owner();
        g.id = "google";
        g.name = "Google";
        g.city = "Mountain View";
        in.owners.put("google", g);
        in.entries.add(entry("org.b", "z.example", "", 0, 0, 0, 500, 500, 0, false));
        in.entries.add(entry("org.a", "doubleclick.net", "google", 4, 1792684800000L, 1792684861999L, 7, 3, 4, true));
        in.entries.add(entry("org.a", "ip:203.0.113.9", "", 0, 1000, 2000, 90, 0, 90, false));
        in.entries.add(entry("org.a", "cdn.example", "unknown-owner", 2, 1792684800000L, 1792684800000L, 7, 7, 0, true));
        String[] lines = ExportTsv.encode(in).split("\n", -1);
        eq(6, lines.length, "header, four rows, and the empty piece after the last newline");
        eq("", lines[5], "the file ends with a newline");
        eq("org.a\tip:203.0.113.9\t\tunknown\t90\t0\t90\t1970-01-01T00:00:01Z\t1970-01-01T00:00:02Z\t0", lines[1],
                "org.a first: most contacts first");
        eq("org.a\tcdn.example\t\tcdn\t7\t7\t0\t2026-10-22T16:00:00Z\t2026-10-22T16:00:00Z\t1", lines[2],
                "an equal count is ordered by service key; an unknown owner has no company");
        eq("org.a\tdoubleclick.net\tGoogle\tadvertising\t7\t3\t4\t2026-10-22T16:00:00Z\t2026-10-22T16:01:01Z\t1", lines[3],
                "the company name, the category name and the time cut to whole seconds");
        eq("org.b\tz.example\t\tunknown\t500\t500\t0\t\t\t0", lines[4],
                "a service with no time has empty time columns; a package is ordered before the counts");
        for (int i = 1; i <= 4; i++) {
            eq(9, lines[i].length() - lines[i].replace("\t", "").length(), "ten columns in line " + i);
        }
        String all = ExportTsv.encode(in);
        for (String leak : new String[] {"NL", "Amsterdam", "SecretHost", "52.37", "Mountain View"}) {
            eq(false, all.contains(leak), "the file holds no place: " + leak);
        }

        MapModel.Input bad = new MapModel.Input();
        bad.entries.add(entry("org.a\tx", "a\nb\r\u0001c\u007fd", "", 99, 0, 0, 1, 0, 0, false));
        String[] b = ExportTsv.encode(bad).split("\n", -1);
        eq(3, b.length, "a line break in a field does not make a line");
        eq("org.a x\ta b  c d\t\tunknown\t1\t0\t0\t\t\t0", b[1], "control characters become spaces; a category out of range is unknown");
        eq("unknown", ExportTsv.category(-1), "a negative category");
        eq("identity", ExportTsv.category(7), "the last category");
        eq("unknown", ExportTsv.category(8), "one past the last category");

        eq("atlas-services-20261022.tsv", ExportTsv.fileName(1792713599999L), "the day in UTC, last millisecond");
        eq("atlas-services-20261023.tsv", ExportTsv.fileName(1792713600000L), "the day in UTC, first millisecond of the next");
        eq("atlas-services-19700101.tsv", ExportTsv.fileName(0), "the epoch");
        System.out.println("ExportTsvCheck passed");
    }
}
