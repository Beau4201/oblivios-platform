// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import java.util.HashMap;
import java.util.Map;

/**
 * The names of the companies, from the mask list file of the image ({@code exit-mask-list TAB 1}, then
 * {@code domain TAB company id TAB company name}). The daemon reports a company id and never a host name; this is how the
 * notification names the company. Pure Java: it parses text and reads no file.
 */
public final class CompanyNames {
    private final Map<Integer, String> names = new HashMap<>();

    /** A name for the id, or null when the list does not know it (the notification then says "a hidden host"). */
    public String nameOf(int company) {
        return names.get(company);
    }

    public int size() {
        return names.size();
    }

    /** Reads what it can: a row it cannot read is skipped, a file that is not a list gives no names. */
    public static CompanyNames parse(String text) {
        CompanyNames c = new CompanyNames();
        if (text == null) {
            return c;
        }
        String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !"exit-mask-list\t1".equals(lines[0])) {
            return c;
        }
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split("\t", -1);
            if (f.length != 3 || !f[1].matches("[0-9]{1,9}") || f[2].isEmpty() || f[2].length() > 64) {
                continue;
            }
            int id = Integer.parseInt(f[1]);
            if (id >= 1) { // company 0 is a protected row, not a company
                c.names.putIfAbsent(id, f[2].replaceAll("\\p{Cntrl}", ""));
            }
        }
        return c;
    }
}
