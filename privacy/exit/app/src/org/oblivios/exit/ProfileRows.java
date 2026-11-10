// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

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
    public static final int CLASS_LOCATION = 3;

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

    /** What {@link #identityOf} returns when one detail of an app differs from what its profile gives. */
    public static final int IDENTITY_CUSTOM = -1;

    /** What a reader of the service keeps for an app whose identity it could not read; never a profile and never Custom. */
    public static final int IDENTITY_UNKNOWN = -2;

    /** What {@link #selectedChoice} returns for an app that has no entry and so follows the defaults. */
    public static final int CHOICE_FOLLOW_DEFAULT = -1;

    /**
     * The class rows of one app, in the order of the page (ADR-209): location, motion sensors, other apps on this phone, phone
     * settings. The place of the persona is not a class (it is set by {@code setPlace}, not by {@code setClassValue}); see
     * {@link #placeRow}.
     */
    public static List<Row> rowsFor(int profile, int sensors, int loopback, int settings, int location) {
        requireProfile(profile);
        List<Row> rows = new ArrayList<>();
        rows.add(row(CLASS_LOCATION, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, location));
        rows.add(row(CLASS_SENSORS, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, sensors));
        rows.add(row(CLASS_LOOPBACK, new int[] {VALUE_REAL, VALUE_BLOCKED}, loopback));
        rows.add(row(CLASS_SETTINGS, new int[] {VALUE_REAL, VALUE_BLOCKED}, settings));
        return Collections.unmodifiableList(rows);
    }

    /**
     * The value a profile gives a class, the same table as {@code Profiles.defaultValue} of the policy core: sensors and
     * location are substitute data for Isolated and real otherwise; loopback and phone settings are real for Trusted and
     * blocked otherwise.
     */
    public static int profileDefault(int profile, int cls) {
        requireProfile(profile);
        switch (cls) {
            case CLASS_SENSORS:
            case CLASS_LOCATION:
                return profile == PROFILE_ISOLATED ? VALUE_SUBSTITUTE : VALUE_REAL;
            case CLASS_LOOPBACK:
            case CLASS_SETTINGS:
                return profile == PROFILE_TRUSTED ? VALUE_REAL : VALUE_BLOCKED;
            default:
                throw new IllegalArgumentException("class " + cls);
        }
    }

    /**
     * The identity an app shows: its profile when all four values are those the profile gives, else {@link #IDENTITY_CUSTOM}.
     * A value that cannot exist for its class is refused as in {@link #rowsFor}.
     */
    public static int identityOf(int profile, int sensors, int loopback, int settings, int location) {
        rowsFor(profile, sensors, loopback, settings, location);
        boolean plain = sensors == profileDefault(profile, CLASS_SENSORS) && loopback == profileDefault(profile, CLASS_LOOPBACK)
                && settings == profileDefault(profile, CLASS_SETTINGS) && location == profileDefault(profile, CLASS_LOCATION);
        return plain ? profile : IDENTITY_CUSTOM;
    }

    /** The row selected on the identity page of an app: only an app without an entry follows the defaults. */
    public static int selectedChoice(boolean configured, int profile) {
        return configured ? profile : CHOICE_FOLLOW_DEFAULT;
    }

    /** Whether the page of an app shows the row of its place, and whether that row can be used. */
    public enum PlaceRow { HIDDEN, NOT_AVAILABLE, SHOWN }

    /**
     * The place row exists only for an app whose location is substitute data (the service refuses {@code setPlace}
     * otherwise); with no place for its persona (id 0 or below, the image carries no place data) it is shown as not available.
     */
    public static PlaceRow placeRow(int location, int personaPlaceId) {
        if (location != VALUE_SUBSTITUTE) {
            return PlaceRow.HIDDEN;
        }
        return personaPlaceId <= 0 ? PlaceRow.NOT_AVAILABLE : PlaceRow.SHOWN;
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
