// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

/** Plain assertions for the host checks. No test framework: the checks are ordinary main programs. */
final class Check {
    interface Thrower {
        void run() throws Exception;
    }

    private Check() {}

    static void isTrue(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("FAILED: " + what);
        }
    }

    static void eq(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("FAILED: " + what + ": expected <" + expected + "> but was <"
                    + actual + ">");
        }
    }

    static void throwsA(Class<? extends Throwable> type, Thrower t, String what) {
        try {
            t.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) {
                return;
            }
            throw new AssertionError("FAILED: " + what + ": threw " + e + " instead of " + type);
        }
        throw new AssertionError("FAILED: " + what + ": nothing was thrown");
    }

    static void passed(String name) {
        System.out.println("PASS " + name);
    }
}
