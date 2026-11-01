// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Host test of RevokedLedger: java -ea, no framework. */
public final class RevokedLedgerTest {
    private static int checks = 0;
    private static final String FINE = "android.permission.ACCESS_FINE_LOCATION";
    private static final String COARSE = "android.permission.ACCESS_COARSE_LOCATION";
    private static final String BACKGROUND = "android.permission.ACCESS_BACKGROUND_LOCATION";
    private static final String MEDIA = "android.permission.ACCESS_MEDIA_LOCATION";

    private static void check(boolean cond, String what) {
        checks++;
        if (!cond) {
            throw new AssertionError(what);
        }
    }

    private static boolean refused(String text) {
        try {
            RevokedLedger.parse(text);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        recording();
        owing();
        format();
        System.out.println("ok " + checks + " checks");
    }

    private static void recording() {
        RevokedLedger l = new RevokedLedger();
        check(l.size() == 0 && l.encode().isEmpty(), "a new ledger is empty");
        check(l.record(0, "com.example.app", FINE) && l.size() == 1, "an entry is recorded");
        check(l.record(0, "com.example.app", FINE) && l.size() == 1, "the same entry again is a success and adds nothing");
        check(l.record(10, "com.example.app", FINE) && l.size() == 2, "another user is another entry");
        check(l.record(0, "com.example.other", FINE) && l.size() == 3, "another package is another entry");
        check(l.record(0, "com.example.app", COARSE) && l.size() == 4, "another permission is another entry");
        check(l.record(0, "com.example.app", BACKGROUND) && l.record(0, "com.example.app", MEDIA) && l.size() == 6,
                "all four permissions can be recorded");

        int n = l.size();
        check(!l.record(-1, "com.example.app", FINE), "a user below 0 is refused");
        check(!l.record(0, "app", FINE), "a name with no dot is not a package");
        check(!l.record(0, null, FINE), "no package is refused");
        check(!l.record(0, "com.example.app", "android.permission.CAMERA"), "another permission is refused");
        check(!l.record(0, "com.example.app", null), "no permission is refused");
        check(!l.record(0, "com.example.app", FINE + " "), "a permission is matched exactly");
        check(l.size() == n, "a refused entry adds nothing");

        RevokedLedger full = new RevokedLedger();
        for (int user = 0; user < RevokedLedger.MAX_ENTRIES / 4; user++) {
            for (String p : RevokedLedger.PERMISSIONS) {
                check(full.record(user, "com.example.app", p), "the ledger takes entries up to its limit");
            }
        }
        check(full.size() == RevokedLedger.MAX_ENTRIES, "the ledger holds its limit");
        check(!full.record(RevokedLedger.MAX_ENTRIES, "com.example.app", FINE), "an entry beyond the limit is refused");
        check(full.record(0, "com.example.app", FINE), "an entry that is there is a success even when full");

        check(RevokedLedger.validPermission(FINE) && RevokedLedger.validPermission(COARSE)
                && RevokedLedger.validPermission(BACKGROUND) && RevokedLedger.validPermission(MEDIA), "the four permissions are valid");
        check(!RevokedLedger.validPermission(null) && !RevokedLedger.validPermission(""), "no permission is not valid");
        check(RevokedLedger.PERMISSIONS.length == 4, "four permissions");
    }

    private static void owing() {
        RevokedLedger l = new RevokedLedger();
        l.record(10, "com.example.b", FINE);
        l.record(2, "com.example.b", COARSE);
        l.record(2, "com.example.a", FINE);
        l.record(2, "com.example.a", COARSE);
        List<RevokedLedger.Entry> all = l.owedTo(Collections.<String>emptySet());
        check(all.size() == 4, "nothing denied: all are owed");
        check(all.get(0).user == 2 && all.get(0).pkg.equals("com.example.a") && all.get(0).permission.equals(COARSE),
                "sorted by user, then package, then permission (first)");
        check(all.get(1).user == 2 && all.get(1).pkg.equals("com.example.a") && all.get(1).permission.equals(FINE), "sorted (second)");
        check(all.get(2).user == 2 && all.get(2).pkg.equals("com.example.b"), "sorted (third)");
        check(all.get(3).user == 10, "users sort as numbers, 10 after 2");

        List<RevokedLedger.Entry> owed = l.owedTo(new HashSet<>(Arrays.asList("com.example.a")));
        check(owed.size() == 2 && owed.get(0).pkg.equals("com.example.b") && owed.get(1).pkg.equals("com.example.b"),
                "a package that is still denied is owed nothing");
        check(l.owedTo(new HashSet<>(Arrays.asList("com.example.a", "com.example.b"))).isEmpty(), "all denied: nothing is owed");
        check(l.size() == 4, "asking changes nothing");

        l.settle(owed.get(0));
        check(l.size() == 3 && l.owedTo(Collections.<String>emptySet()).size() == 3, "a settled entry is gone");
        l.settle(owed.get(0));
        check(l.size() == 3, "settling it again changes nothing");
        l.settle(new RevokedLedger.Entry(99, "com.example.zzz", FINE));
        check(l.size() == 3, "settling an entry that is not there changes nothing");
        check(l.record(owed.get(0).user, owed.get(0).pkg, owed.get(0).permission) && l.size() == 4,
                "a settled entry can be recorded again");
    }

    private static void format() {
        RevokedLedger l = new RevokedLedger();
        l.record(10, "com.example.b", FINE);
        l.record(0, "com.example.a", MEDIA);
        String text = l.encode();
        check(text.equals("0\tcom.example.a\t" + MEDIA + "\n10\tcom.example.b\t" + FINE + "\n"), "the file is sorted lines");
        RevokedLedger back = RevokedLedger.parse(text);
        check(back.size() == 2 && back.encode().equals(text), "a file reads back as it was written");
        check(RevokedLedger.parse("").size() == 0, "an empty file is an empty ledger");

        check(refused("0\tcom.example.a\t" + FINE), "the last line needs its newline");
        check(refused("0\tcom.example.a\t" + FINE + "x"), "the last character must be the newline, not any character");
        check(refused("\n"), "a blank line is refused");
        check(refused("0\tcom.example.a\t" + FINE + "\n\n"), "a blank line at the end is refused");
        check(refused("0\tcom.example.a\n"), "two fields are refused");
        check(refused("0\tcom.example.a\t" + FINE + "\tx\n"), "four fields are refused");
        check(refused("0\tcom.example.a\t" + FINE + "\t\n"), "an empty fourth field is refused");
        check(refused("00\tcom.example.a\t" + FINE + "\n"), "a user with a leading zero is refused");
        check(refused("-1\tcom.example.a\t" + FINE + "\n"), "a negative user is refused");
        check(refused("x\tcom.example.a\t" + FINE + "\n"), "a user that is not a number is refused");
        check(refused("\tcom.example.a\t" + FINE + "\n"), "no user is refused");
        check(refused("1234567890\tcom.example.a\t" + FINE + "\n"), "a user of ten digits is refused");
        check(!refused("123456789\tcom.example.a\t" + FINE + "\n"), "a user of nine digits is read");
        check(refused("0\tapp\t" + FINE + "\n"), "a name that is not a package is refused");
        check(refused("0\tcom.example.a\tandroid.permission.CAMERA\n"), "a file cannot name another permission");
        check(refused("0\tcom.example.a\t" + FINE + "\n0\tcom.example.a\t" + FINE + "\n"), "a repeated line is refused");

        StringBuilder many = new StringBuilder();
        for (int i = 0; i <= RevokedLedger.MAX_ENTRIES; i++) {
            many.append(i / 4).append("\tcom.example.a\t").append(RevokedLedger.PERMISSIONS[i % 4]).append('\n');
        }
        check(refused(many.toString()), "a file beyond the limit is refused");
    }
}
