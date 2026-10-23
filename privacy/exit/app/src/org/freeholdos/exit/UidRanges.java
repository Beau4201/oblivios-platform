// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ranges of app uids. A range is an {@code int[2]} {start, end}, both inclusive. Pure Java: no Android class, so the host
 * checks run it.
 */
public final class UidRanges {
    /** Android gives every user this many uids: uid = userId * PER_USER + appId. */
    public static final int PER_USER = 100000;
    /** The app ids of ordinary apps (the daemon and the route helper refuse a range below the first). */
    public static final int FIRST_APP = 10000;
    public static final int LAST_APP = 19999;
    /** The largest user id whose app range still fits an int. */
    private static final int MAX_USER = (Integer.MAX_VALUE - LAST_APP) / PER_USER;

    private UidRanges() {}

    /** Whether a uid belongs to an ordinary app of some user (app id from FIRST_APP to LAST_APP). */
    public static boolean isAppUid(int uid) {
        int appId = uid % PER_USER; // a negative uid gives a negative app id, which is below FIRST_APP
        return appId >= FIRST_APP && appId <= LAST_APP;
    }

    /** The app uid range of one user. */
    public static int[] forUser(int userId) {
        if (userId < 0 || userId > MAX_USER) {
            throw new IllegalArgumentException("user id out of range");
        }
        int base = userId * PER_USER;
        return new int[] {base + FIRST_APP, base + LAST_APP};
    }

    /** Sorted, disjoint ranges that cover exactly the given uids; adjacent uids join. Duplicates are one. */
    public static List<int[]> coalesce(int[] uids) {
        int[] sorted = uids.clone();
        Arrays.sort(sorted);
        List<int[]> out = new ArrayList<>();
        for (int uid : sorted) {
            int[] last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && uid <= last[1]) {
                continue;
            }
            if (last != null && uid == last[1] + 1) {
                last[1] = uid;
            } else {
                out.add(new int[] {uid, uid});
            }
        }
        return out;
    }

    /** The part of the sorted, disjoint list {@code from} that no range of the sorted, disjoint list {@code remove} covers. */
    public static List<int[]> subtract(List<int[]> from, List<int[]> remove) {
        List<int[]> out = new ArrayList<>();
        for (int[] r : from) {
            long start = r[0];
            for (int[] cut : remove) {
                if (cut[1] < start || cut[0] > r[1]) {
                    continue;
                }
                if (cut[0] > start) {
                    out.add(new int[] {(int) start, cut[0] - 1});
                }
                start = (long) cut[1] + 1;
                if (start > r[1]) {
                    break;
                }
            }
            if (start <= r[1]) {
                out.add(new int[] {(int) start, r[1]});
            }
        }
        return out;
    }
}
