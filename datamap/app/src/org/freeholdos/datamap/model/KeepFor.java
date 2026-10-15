// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

/**
 * The choices for how long the history is kept: four presets. A value the service holds that is none of them
 * (the first image had a slider) is shown as an extra choice until a preset is picked.
 */
public final class KeepFor {
    public static final int[] PRESETS = {1, 7, 30, 90};
    public static final int DEFAULT = 30;
    private static final int MAX = 90;

    private KeepFor() {}

    /** A number of days the service accepts (1 to 90). */
    public static boolean valid(int days) {
        return days >= 1 && days <= MAX;
    }

    private static int effective(int stored) {
        return valid(stored) ? stored : DEFAULT;
    }

    /** The choices to show, in ascending order: the presets, plus the stored value if it is none of them. */
    public static int[] choices(int stored) {
        int v = effective(stored);
        for (int p : PRESETS) {
            if (p == v) {
                return PRESETS.clone();
            }
        }
        int[] out = new int[PRESETS.length + 1];
        int k = 0;
        boolean placed = false;
        for (int p : PRESETS) {
            if (!placed && v < p) {
                out[k++] = v;
                placed = true;
            }
            out[k++] = p;
        }
        return out;   // a valid value is below 90, so it was placed
    }

    /** The index into {@link #choices} of the stored value. */
    public static int selected(int stored) {
        int v = effective(stored);
        int[] c = choices(stored);
        for (int i = 0; i < c.length; i++) {
            if (c[i] == v) {
                return i;
            }
        }
        return 0;
    }
}
