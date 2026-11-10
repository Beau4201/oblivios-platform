// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

public final class FlagEmojiCheck {
    private static void eq(String expected, String actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("FAILED: " + what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        // a flag is the two regional indicator symbols of the country code
        eq("🇺🇸", FlagEmoji.of("US"), "US");
        eq("🇳🇱", FlagEmoji.of("NL"), "NL");
        eq("🇦🇦", FlagEmoji.of("AA"), "the first letters");
        eq("🇿🇿", FlagEmoji.of("ZZ"), "the last letters");
        eq(FlagEmoji.of("US"), FlagEmoji.of("us"), "lower case is the same country");
        // anything that is not two letters has no flag, and never throws
        eq(null, FlagEmoji.of(null), "null");
        eq(null, FlagEmoji.of(""), "empty");
        eq(null, FlagEmoji.of("U"), "one letter");
        eq(null, FlagEmoji.of("USA"), "three letters");
        eq(null, FlagEmoji.of("U1"), "a digit");
        eq(null, FlagEmoji.of("ÉE"), "a letter outside A to Z");
        System.out.println("PASS FlagEmojiCheck");
    }
}
