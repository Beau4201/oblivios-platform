package org.sovereignos.airpods.ui;

import java.util.List;

/**
 * How loud the music stays while the owner speaks (conversation awareness, ADR-125), as the percent of
 * the current volume that remains. The own page has a slider for every value from {@link #MIN} to
 * {@link #MAX} (patch 0011 of the Bluetooth module accepts exactly that range); Settings' native
 * device page has no slider item, so it keeps four buttons and shows the nearest one.
 */
public final class DuckLevels {
    private DuckLevels() {}

    public static final int MIN = 10;
    public static final int MAX = 90;
    public static final int DEFAULT = 50;

    /** The four buttons of the native page. */
    private static final List<Integer> OPTIONS = List.of(20, 35, 50, 70);

    public static List<Integer> options() {
        return OPTIONS;
    }

    public static boolean isValid(int percent) {
        return percent >= MIN && percent <= MAX;
    }

    /** The button index of exactly this value, or -1 when no button has it. */
    public static int indexOf(int percent) {
        return OPTIONS.indexOf(percent);
    }

    /** The button nearest to this value; on a tie the lower button. Display only: a tap sets the exact value of the button. */
    public static int nearestIndex(int percent) {
        int best = 0;
        for (int i = 1; i < OPTIONS.size(); i++) {
            if (Math.abs(OPTIONS.get(i) - percent) < Math.abs(OPTIONS.get(best) - percent)) {
                best = i;
            }
        }
        return best;
    }

    /** The choice at a button index, or null when the index is outside the buttons. */
    public static Integer at(int index) {
        return index >= 0 && index < OPTIONS.size() ? OPTIONS.get(index) : null;
    }
}
