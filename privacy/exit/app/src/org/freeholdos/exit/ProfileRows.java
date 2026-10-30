// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The rows of the Compartments identity pages (ADR-208): which profiles an app or the defaults may have, and per profile
 * which classes carry a row, which values each row offers and which one is current. No Android class, so the host checks run
 * it. The numbers are those of {@code ICompartmentsPolicy}; the host test of the policy core pins them
 * ({@code ProfilesTest.pinnedNumbers}) and {@code check-static.py} compares the class list with the core.
 */
public final class ProfileRows {
    private ProfileRows() {}

    public static final int PROFILE_TRUSTED = 0;
    public static final int PROFILE_PROTECTED = 1;
    public static final int PROFILE_ISOLATED = 2;

    public static final int CLASS_SENSORS = 0;
    public static final int CLASS_LOOPBACK = 1;
    public static final int CLASS_SETTINGS = 2;

    public static final int VALUE_REAL = 0;
    public static final int VALUE_SUBSTITUTE = 1;
    public static final int VALUE_BLOCKED = 2;

    /** One class of an app: the values it can take, in the order the dialog lists them, and the one it has. */
    public static final class Row {
        public final int cls;
        public final int[] values;
        public final int current;

        Row(int cls, int[] values, int current) {
            this.cls = cls;
            this.values = values;
            this.current = current;
        }
    }

    /**
     * The profiles that can be chosen: for the defaults only Protected and Isolated (Compartments never opens an app by
     * default, and only the user sets Trusted for one app); for one app all three. Always a new array.
     */
    public static int[] profileChoices(boolean forDefaults) {
        return forDefaults
                ? new int[] {PROFILE_PROTECTED, PROFILE_ISOLATED}
                : new int[] {PROFILE_TRUSTED, PROFILE_PROTECTED, PROFILE_ISOLATED};
    }

    /** The class rows of one app, in the order of the page: motion sensors, other apps on this phone, phone settings. */
    public static List<Row> rowsFor(int profile, int sensors, int loopback, int settings) {
        requireProfile(profile);
        List<Row> rows = new ArrayList<>();
        rows.add(row(CLASS_SENSORS, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, sensors));
        rows.add(row(CLASS_LOOPBACK, new int[] {VALUE_REAL, VALUE_BLOCKED}, loopback));
        rows.add(row(CLASS_SETTINGS, new int[] {VALUE_REAL, VALUE_BLOCKED}, settings));
        return Collections.unmodifiableList(rows);
    }

    /** The class rows of the defaults for apps the user has not set: only the sensors can be chosen. */
    public static List<Row> defaultRows(int profile, int sensors) {
        requireProfile(profile);
        if (profile == PROFILE_TRUSTED) {
            throw new IllegalArgumentException("the defaults are never Trusted");
        }
        List<Row> rows = new ArrayList<>();
        rows.add(row(CLASS_SENSORS, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, sensors));
        return Collections.unmodifiableList(rows);
    }

    private static Row row(int cls, int[] values, int current) {
        for (int v : values) {
            if (v == current) {
                return new Row(cls, values, current);
            }
        }
        throw new IllegalArgumentException("value " + current + " is not offered for class " + cls);
    }

    private static void requireProfile(int profile) {
        if (profile < PROFILE_TRUSTED || profile > PROFILE_ISOLATED) {
            throw new IllegalArgumentException("profile " + profile);
        }
    }
}
