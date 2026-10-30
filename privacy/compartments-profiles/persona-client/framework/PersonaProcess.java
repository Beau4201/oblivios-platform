/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.compartments;

import android.os.Bundle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

/**
 * The persona of this process (ADR-208): the time zone and the network country and operator of the place of an Isolated app.
 * The system server puts the three values in the core settings that it hands to the process when it binds the application;
 * the process reads them once, when it binds, into this class. A later push of the core settings carries no persona and is not
 * read, so the values stay what the process was started with. Every app without a persona has no values and nothing changes
 * for it.
 *
 * @hide
 */
public final class PersonaProcess {
    public static final String KEY_TIME_ZONE = "freeholdos_persona_tz";
    public static final String KEY_COUNTRY = "freeholdos_persona_country";
    public static final String KEY_OPERATOR = "freeholdos_persona_operator";

    private static final Object LOCK = new Object();
    private static boolean sSet;
    private static volatile String sTimeZone;
    private static volatile String sCountry;
    private static volatile String sOperator;

    private PersonaProcess() {}

    /** The three keys for the core settings of a process; the system server adds them to a copy of the bundle. */
    public static void putInto(Bundle coreSettings, String timeZone, String country, String operator) {
        coreSettings.putString(KEY_TIME_ZONE, timeZone);
        coreSettings.putString(KEY_COUNTRY, country);
        coreSettings.putString(KEY_OPERATOR, operator);
    }

    /**
     * Reads the persona from the core settings of the process, once. Without all three keys the process has no persona. A
     * second call throws: a process is bound once.
     */
    public static void setFrom(Bundle coreSettings) {
        String timeZone = coreSettings == null ? null : coreSettings.getString(KEY_TIME_ZONE);
        String country = coreSettings == null ? null : coreSettings.getString(KEY_COUNTRY);
        String operator = coreSettings == null ? null : coreSettings.getString(KEY_OPERATOR);
        set(timeZone, country, operator);
    }

    /** One time only; null for all three means no persona. */
    static void set(String timeZone, String country, String operator) {
        synchronized (LOCK) {
            if (sSet) {
                throw new IllegalStateException("the persona of a process is set once");
            }
            sSet = true;
            if (timeZone == null || country == null || operator == null) {
                return;
            }
            sTimeZone = timeZone;
            sCountry = country;
            sOperator = operator;
        }
    }

    /** The time zone id of the persona, or null. */
    public static String timeZone() {
        return sTimeZone;
    }

    /** The network country of the persona, a lower-case ISO 3166-1 alpha-2 code, or null. */
    public static String country() {
        return sCountry;
    }

    /** The numeric operator of the persona (mcc and mnc), "" when its place has no cell, or null when there is no persona. */
    public static String operator() {
        return sOperator;
    }

    /**
     * Makes the default time zone of the process the one of the persona, when there is one and it is a time zone that this
     * system knows; otherwise the default is left as it is.
     */
    public static void applyTimeZone() {
        String id = sTimeZone;
        if (id == null) {
            return;
        }
        Set<String> known = new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));
        if (known.contains(id)) {
            TimeZone.setDefault(TimeZone.getTimeZone(id));
        }
    }
}
