package org.sovereignos.airpods.ui;

import java.util.List;

/**
 * The choices for how loud the music stays while the owner speaks (conversation awareness, ADR-125),
 * as the percent of the current volume that remains. Settings' native device page has no slider
 * item, so the page offers these four buttons; a continuous slider belongs to Plan 7B's own page.
 */
public final class DuckLevels {
    private DuckLevels() {}

    public static final int DEFAULT = 50;

    private static final List<Integer> OPTIONS = List.of(20, 35, 50, 70);

    public static List<Integer> options() {
        return OPTIONS;
    }

    public static boolean isValid(int percent) {
        return OPTIONS.contains(percent);
    }

    public static int indexOf(int percent) {
        return OPTIONS.indexOf(percent);
    }

    /** The choice at a button index, or null when the index is outside the buttons. */
    public static Integer at(int index) {
        return index >= 0 && index < OPTIONS.size() ? OPTIONS.get(index) : null;
    }
}
