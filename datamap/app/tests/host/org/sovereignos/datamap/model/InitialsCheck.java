// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

public final class InitialsCheck {
    private static void eq(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        eq("TI", Initials.of("Tracker Inc"));
        eq("PH", Initials.of("Parent Holdings Ltd"));
        eq("S", Initials.of("signal"));
        eq("AB", Initials.of("a-b c"));
        eq("?", Initials.of(""));
        eq("?", Initials.of("  - . "));
        eq("?", Initials.of(null));
        eq("ÉC", Initials.of("école centrale"));
        eq("𝐀B", Initials.of("𝐀 b"));   // a letter outside the basic plane is one letter
        System.out.println("PASS InitialsCheck");
    }
}
