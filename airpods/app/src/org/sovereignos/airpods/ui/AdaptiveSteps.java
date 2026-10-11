// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.airpods.ui;

/**
 * The adaptive strength slider has three positions because the pair keeps three values: whatever is
 * written is rounded to the nearest of 0, 50 and 100 (AIR-164: 20 gave 0, 40 gave 50, 70 gave 50,
 * 100 gave 100). A position is an index 0 to 2; the percent is what is sent.
 */
public final class AdaptiveSteps {
    private AdaptiveSteps() {}

    public static final int STEPS = 3;

    /** The nearest position (a quarter counts up), or -1 for an unknown or out-of-range percent. */
    public static int stepOf(int percent) {
        if (percent < 0 || percent > 100) {
            return -1;
        }
        return (percent + 25) / 50;
    }

    public static int percentOf(int step) {
        if (step < 0 || step >= STEPS) {
            throw new IllegalArgumentException("step");
        }
        return step * 50;
    }
}
