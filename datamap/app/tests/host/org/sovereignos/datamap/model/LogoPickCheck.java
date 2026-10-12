// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

public final class LogoPickCheck {
    private static void eq(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        eq(1, LogoPick.representative(new String[] {"pubmatic", "google", "alphabet", "com.x"},
                new boolean[] {false, false, false, true}, new int[] {2, 2, 1, 5}),
                "most apps, then the smaller identifier; an app never stands for the group");
        eq(0, LogoPick.representative(new String[] {"b", "a"}, new boolean[] {false, false}, new int[] {3, 1}),
                "more apps win over a smaller identifier");
        eq(2, LogoPick.representative(new String[] {"b", "c", "a"}, new boolean[] {false, false, false},
                new int[] {1, 1, 1}), "all equal: the smallest identifier");
        eq(-1, LogoPick.representative(new String[] {"com.x", "com.y"}, new boolean[] {true, true},
                new int[] {1, 1}), "a group of apps has no logo");
        eq(-1, LogoPick.representative(new String[0], new boolean[0], new int[0]), "an empty group");
        eq(0, LogoPick.extra(0), "no markers");
        eq(0, LogoPick.extra(1), "one marker has no badge");
        eq(2, LogoPick.extra(3), "three markers: +2");
        System.out.println("PASS LogoPickCheck");
    }
}
