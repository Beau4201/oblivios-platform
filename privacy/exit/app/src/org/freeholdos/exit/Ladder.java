// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.util.HashMap;
import java.util.Map;

/**
 * The notification window of the failure ladder (ADR-181 section 4.3). The daemon tells the app once per app, company and
 * window that connections through Tor failed or were slow; the daemon forgets that on a restart, so the app keeps its own
 * window and posts at most one notification per app and company in it. The numbers are those of {@code ladder.rs}
 * ({@code WINDOW}, {@code MAX_KEYS}). Pure Java: every call is given the time.
 */
public final class Ladder {
    /** A key is notified once in this many milliseconds. */
    public static final long WINDOW_MS = 10 * 60 * 1000L;
    /** The most keys kept; a new key past this evicts the oldest. */
    public static final int MAX_KEYS = 4096;

    private final Map<Long, Long> firstSeen = new HashMap<>();

    private static long key(int uid, int company) {
        return ((long) uid << 16) | (company & 0xffffL);
    }

    /**
     * True when the app should post a notification for this app and company now: the first report in a window. A report
     * inside the window of one already posted is dropped.
     */
    public boolean shouldNotify(int uid, int company, long nowMs) {
        firstSeen.values().removeIf(t -> nowMs - t > WINDOW_MS);
        Long k = key(uid, company);
        if (firstSeen.containsKey(k)) {
            return false;
        }
        if (firstSeen.size() >= MAX_KEYS) {
            long oldestKey = 0;
            long oldest = Long.MAX_VALUE;
            for (Map.Entry<Long, Long> e : firstSeen.entrySet()) {
                if (e.getValue() < oldest) {
                    oldest = e.getValue();
                    oldestKey = e.getKey();
                }
            }
            firstSeen.remove(oldestKey);
        }
        firstSeen.put(k, nowMs);
        return true;
    }

    public int size() {
        return firstSeen.size();
    }
}
