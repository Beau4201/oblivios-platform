// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.util.Arrays;

public final class KeepForCheck {
    private static void eq(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        eq("[1, 7, 30, 90]", Arrays.toString(KeepFor.choices(30)), "a preset gives the four presets");
        eq(2, KeepFor.selected(30), "30 is the third");
        eq(0, KeepFor.selected(1), "1 is the first");
        eq(3, KeepFor.selected(90), "90 is the last");
        eq("[1, 2, 7, 30, 90]", Arrays.toString(KeepFor.choices(2)), "2 days is shown between 1 and 7");
        eq(1, KeepFor.selected(2), "and is the selected one");
        eq("[1, 4, 7, 30, 90]", Arrays.toString(KeepFor.choices(4)), "4 days");
        eq("[1, 7, 30, 45, 90]", Arrays.toString(KeepFor.choices(45)), "45 days");
        eq("[1, 7, 30, 90]", Arrays.toString(KeepFor.choices(0)), "0 is not a value: the default is selected");
        eq(2, KeepFor.selected(0), "0 selects 30");
        eq(2, KeepFor.selected(91), "91 selects 30");
        eq(2, KeepFor.selected(-5), "a negative value selects 30");
        eq(true, KeepFor.valid(1), "1 is valid");
        eq(true, KeepFor.valid(90), "90 is valid");
        eq(false, KeepFor.valid(0), "0 is not");
        eq(false, KeepFor.valid(91), "91 is not");
        int[] c = KeepFor.choices(30);
        c[0] = 99;
        eq(1, KeepFor.PRESETS[0], "the presets cannot be changed through the result");
        System.out.println("PASS KeepForCheck");
    }
}
