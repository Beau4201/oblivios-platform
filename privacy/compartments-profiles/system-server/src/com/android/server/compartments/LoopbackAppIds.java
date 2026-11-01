// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * The app ids that may use the loopback interface although they do not hold USE_LOOPBACK_INTERFACE (ADR-208). That permission is
 * a runtime permission, and a runtime grant needs the app to declare it, which almost no app does: a Trusted app that does not
 * declare it stayed refused (CMP-005). The policy service writes the app ids of the Trusted apps into a global setting with
 * {@link #format}, and the connectivity module reads them with {@link #parse} and adds the bit to the uid of those apps in every
 * user. One copy of this file lives in each of the two trees (see tools/check-copies.py). Pure Java.
 *
 * <p>Format: decimal app ids from 10000 to 19999 in strictly ascending order, separated by a comma, no space, no leading zero.
 */
final class LoopbackAppIds {
    static final int FIRST_APP_ID = 10000;
    static final int LAST_APP_ID = 19999;
    static final int MAX_IDS = 4096;

    private LoopbackAppIds() {
    }

    /** The text for {@code appIds}: ids outside the range of ordinary apps are left out, and at most {@link #MAX_IDS} are written. */
    static String format(Set<Integer> appIds) {
        TreeSet<Integer> sorted = new TreeSet<>();
        for (Integer id : appIds) {
            if (id != null && id >= FIRST_APP_ID && id <= LAST_APP_ID) {
                sorted.add(id);
            }
        }
        StringBuilder out = new StringBuilder();
        int written = 0;
        for (int id : sorted) {
            if (written++ >= MAX_IDS) {
                break;
            }
            out.append(written == 1 ? "" : ",").append(id);
        }
        return out.toString();
    }

    /** The app ids in {@code text}; none for null, for the empty text and for any text that {@link #format} could not have written. */
    static Set<Integer> parse(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }
        TreeSet<Integer> out = new TreeSet<>();
        int p = 0;
        int last = -1;
        while (true) {
            int start = p;
            int id = 0;
            char c;
            while (p < text.length() && (c = text.charAt(p)) >= '0' && c <= '9') {
                if (p - start >= 5) {
                    return Collections.emptySet();
                }
                id = id * 10 + (c - '0');
                p++;
            }
            if (p == start || id < FIRST_APP_ID || id > LAST_APP_ID || id <= last || out.size() >= MAX_IDS) {
                return Collections.emptySet();
            }
            out.add(id);
            last = id;
            if (p == text.length()) {
                return out;
            }
            if (text.charAt(p) != ',') {
                return Collections.emptySet();
            }
            p++;
        }
    }
}
