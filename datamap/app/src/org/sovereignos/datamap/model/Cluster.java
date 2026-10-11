// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

/** Groups markers that would overlap on screen, so that the globe draws one marker with a count. */
public final class Cluster {
    private Cluster() {}

    /**
     * Greedy grouping in input order: a marker joins the first earlier group whose first member is
     * closer than {@code minDist}; otherwise it starts a group. The result gives each marker the
     * index of its group's first member.
     */
    public static int[] group(double[] xs, double[] ys, double minDist) {
        int n = xs.length;
        int[] leader = new int[n];
        double min2 = minDist * minDist;
        for (int i = 0; i < n; i++) {
            leader[i] = i;
            for (int j = 0; j < i; j++) {
                if (leader[j] != j) {
                    continue;
                }
                double dx = xs[i] - xs[j];
                double dy = ys[i] - ys[j];
                if (dx * dx + dy * dy < min2) {
                    leader[i] = j;
                    break;
                }
            }
        }
        return leader;
    }
}
