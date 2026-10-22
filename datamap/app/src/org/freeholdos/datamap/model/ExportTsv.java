// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The file of the export row in Settings (ADR-189): one tab-separated line per app and service of the snapshot. It holds
 * the service key (a classifier suffix, a registrable domain or {@code ip:<address>}), never a full host name, and no
 * bytes, server place or relation between companies. A pure function, so that what leaves the app is fixed by a host check.
 */
public final class ExportTsv {
    public static final String HEADER = "package\tservice\tcompany\tcategory\tcount\tforeground\tbackground\tfirst_seen"
            + "\tlast_seen\tvia_dns";
    /** The codes of {@code DataMapEntry.category}. */
    private static final String[] CATEGORY = {"unknown", "core", "cdn", "analytics", "advertising", "attribution", "social",
        "identity"};

    private ExportTsv() {}

    public static String encode(MapModel.Input in) {
        List<MapModel.Entry> rows = new ArrayList<>(in.entries);
        Collections.sort(rows, (a, b) -> {
            int c = a.pkg.compareTo(b.pkg);
            if (c == 0) {
                c = Integer.compare(b.count, a.count);
            }
            return c != 0 ? c : a.serviceKey.compareTo(b.serviceKey);
        });
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (MapModel.Entry e : rows) {
            MapModel.Owner o = in.owners.get(e.ownerId);
            sb.append(clean(e.pkg)).append('\t').append(clean(e.serviceKey)).append('\t').append(o == null ? "" : clean(o.name))
                    .append('\t').append(category(e.category)).append('\t').append(e.count).append('\t').append(e.foreground)
                    .append('\t').append(e.background).append('\t').append(time(e.firstWall)).append('\t')
                    .append(time(e.lastWall)).append('\t').append(e.viaDns ? 1 : 0).append('\n');
        }
        return sb.toString();
    }

    /** {@code atlas-services-20261022.tsv}: the day in UTC, the name the picker suggests. */
    public static String fileName(long nowWallMs) {
        java.time.LocalDate d = Instant.ofEpochMilli(nowWallMs).atZone(ZoneOffset.UTC).toLocalDate();
        return String.format(java.util.Locale.ROOT, "atlas-services-%04d%02d%02d.tsv", d.getYear(), d.getMonthValue(),
                d.getDayOfMonth());
    }

    /** A tab or a line break in a field would shift the columns of every line after it: a control character becomes a space. */
    static String clean(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c < 0x20 || c == 0x7f ? ' ' : c);
        }
        return sb.toString();
    }

    static String category(int code) {
        return code >= 0 && code < CATEGORY.length ? CATEGORY[code] : CATEGORY[0];
    }

    /** Whole seconds in UTC (ISO 8601), or nothing when the service has no time. */
    static String time(long wallMs) {
        return wallMs <= 0 ? "" : Instant.ofEpochSecond(wallMs / 1000).toString();
    }
}
