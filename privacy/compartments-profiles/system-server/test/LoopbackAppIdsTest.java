// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** Host test of LoopbackAppIds: java -ea, no framework. */
public final class LoopbackAppIdsTest {
    private static int checks = 0;

    private static void check(boolean cond, String what) {
        checks++;
        if (!cond) {
            throw new AssertionError(what);
        }
    }

    private static Set<Integer> set(Integer... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    public static void main(String[] args) {
        formatting();
        parsing();
        System.out.println("ok " + checks + " checks");
    }

    private static void formatting() {
        check(LoopbackAppIds.format(Collections.<Integer>emptySet()).isEmpty(), "no id, no text");
        check(LoopbackAppIds.format(set(10123)).equals("10123"), "one id has no comma");
        check(LoopbackAppIds.format(set(10200, 10100, 19999, 10000)).equals("10000,10100,10200,19999"), "ascending, comma, no space");
        check(LoopbackAppIds.format(set(9999, 20000, 1000, -1, 0, 100000, 10100)).equals("10100"), "ids outside 10000 to 19999 are left out");
        check(LoopbackAppIds.format(set(10000)).equals("10000") && LoopbackAppIds.format(set(19999)).equals("19999"),
                "both ends of the range are app ids");
        Set<Integer> withNull = set(10100);
        withNull.add(null);
        check(LoopbackAppIds.format(withNull).equals("10100"), "a null is left out");

        Set<Integer> many = new TreeSet<>();
        for (int id = LoopbackAppIds.FIRST_APP_ID; id <= LoopbackAppIds.LAST_APP_ID; id++) {
            many.add(id);
        }
        String text = LoopbackAppIds.format(many);
        check(text.split(",").length == LoopbackAppIds.MAX_IDS, "at most MAX_IDS are written");
        check(text.startsWith("10000,10001,") && text.endsWith("," + (LoopbackAppIds.FIRST_APP_ID + LoopbackAppIds.MAX_IDS - 1)),
                "the lowest ids are the ones kept");
    }

    private static void parsing() {
        check(LoopbackAppIds.parse(null).isEmpty() && LoopbackAppIds.parse("").isEmpty(), "no text, no ids");
        check(LoopbackAppIds.parse("10123").equals(set(10123)), "one id");
        check(LoopbackAppIds.parse("10000,10100,10200,19999").equals(set(10000, 10100, 10200, 19999)), "several ids");

        Set<Integer> many = new TreeSet<>();
        for (int id = LoopbackAppIds.FIRST_APP_ID; id < LoopbackAppIds.FIRST_APP_ID + LoopbackAppIds.MAX_IDS; id++) {
            many.add(id);
        }
        check(LoopbackAppIds.parse(LoopbackAppIds.format(many)).equals(many), "what format writes, parse reads back");
        check(LoopbackAppIds.parse(LoopbackAppIds.format(many) + "," + (LoopbackAppIds.FIRST_APP_ID + LoopbackAppIds.MAX_IDS)).isEmpty(),
                "one id more than the limit is refused");

        String[] refused = {
            ",", "10100,", ",10100", "10100,,10200", " 10100", "10100 ", "10100, 10200", "10100;10200", "10100\n",
            "10200,10100", "10100,10100", "9999", "20000", "0", "-10100", "+10100", "010100", "0x2774", "1010", "101000",
            "10100,abc", "abc", "10100,10200,", "1.5", "１０１００",
        };
        for (String text : refused) {
            check(LoopbackAppIds.parse(text).isEmpty(), "refused: '" + text + "'");
        }
        check(LoopbackAppIds.parse("10100,10200,x").isEmpty() && LoopbackAppIds.parse("10100,10200x").isEmpty(),
                "one bad token empties the whole list");
        check(LoopbackAppIds.parse("19999").equals(set(19999)) && LoopbackAppIds.parse("10000").equals(set(10000)),
                "both ends are read");
    }
}
