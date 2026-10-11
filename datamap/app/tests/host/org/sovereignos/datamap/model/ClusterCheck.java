// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

public final class ClusterCheck {
    public static void main(String[] args) {
        double[] xs = {0, 3, 100, 101, 400};
        double[] ys = {0, 4, 100, 100, 0};
        int[] g = Cluster.group(xs, ys, 10);
        if (g[0] != 0 || g[1] != 0) throw new AssertionError("0 and 1 are 5 apart");
        if (g[2] != 2 || g[3] != 2) throw new AssertionError("2 and 3 are 1 apart");
        if (g[4] != 4) throw new AssertionError("4 is alone");
        // exactly at the limit is not inside it
        int[] h = Cluster.group(new double[] {0, 10}, new double[] {0, 0}, 10);
        if (h[1] != 1) throw new AssertionError("distance equal to the limit does not join");
        // a marker joins a group by its first member, never by a later one: 16 is within 10 of 8 but not of 0
        int[] chain = Cluster.group(new double[] {0, 8, 16}, new double[] {0, 0, 0}, 10);
        if (chain[1] != 0 || chain[2] != 2) throw new AssertionError("chain: " + chain[1] + " " + chain[2]);
        // no markers
        if (Cluster.group(new double[0], new double[0], 10).length != 0) throw new AssertionError("empty");
        System.out.println("PASS ClusterCheck");
    }
}
