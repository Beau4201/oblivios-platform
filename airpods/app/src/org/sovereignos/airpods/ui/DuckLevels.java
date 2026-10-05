package org.sovereignos.airpods.ui;

/**
 * How loud the music stays while the owner speaks (conversation awareness, ADR-125), as the percent of
 * the current volume that remains. The own page has a slider for every value from {@link #MIN} to
 * {@link #MAX} (patch 0011 of the Bluetooth module accepts exactly that range); Settings' native
 * device page does not show the choice.
 */
public final class DuckLevels {
    private DuckLevels() {}

    public static final int MIN = 10;
    public static final int MAX = 90;
    public static final int DEFAULT = 50;

    public static boolean isValid(int percent) {
        return percent >= MIN && percent <= MAX;
    }
}
