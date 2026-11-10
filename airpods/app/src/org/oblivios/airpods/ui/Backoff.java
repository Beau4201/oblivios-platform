// SPDX-License-Identifier: Apache-2.0
package org.oblivios.airpods.ui;

/** Delay before the next connection attempt: 2 s, doubled each time, never more than 60 s. */
public final class Backoff {
    public static final long FIRST_MS = 2_000;
    public static final long MAX_MS = 60_000;

    private Backoff() {}

    /** @param attempt zero for the first retry */
    public static long delayMs(int attempt) {
        if (attempt < 0) throw new IllegalArgumentException("attempt");
        if (attempt >= 5) return MAX_MS;
        return Math.min(MAX_MS, FIRST_MS << attempt);
    }
}
