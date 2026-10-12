// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

/** Which logo a marker shows when several companies stand together, and what its badge says. */
public final class LogoPick {
    private LogoPick() {}

    /**
     * The company whose logo stands for the group: the one that most of the user's apps contact, ties by
     * identifier so that the same group always shows the same logo.
     *
     * @param ids one identifier per marker of the group
     * @param isApp whether the marker is an app (an app never stands for a group)
     * @param appCount for a company, how many apps contact it
     * @return the index of the chosen marker, or -1 if the group holds no company
     */
    public static int representative(String[] ids, boolean[] isApp, int[] appCount) {
        int best = -1;
        for (int i = 0; i < ids.length; i++) {
            if (isApp[i]) {
                continue;
            }
            if (best < 0 || appCount[i] > appCount[best]
                    || (appCount[i] == appCount[best] && ids[i].compareTo(ids[best]) < 0)) {
                best = i;
            }
        }
        return best;
    }

    /** The "+N" of the badge: the other markers of the group; 0 means no badge. */
    public static int extra(int groupSize) {
        return Math.max(0, groupSize - 1);
    }
}
