// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

/**
 * The profiles, classes and values of Compartments (ADR-208). Pure Java with no android.* import, so that the host tests
 * compile it as it is.
 */
final class Profiles {
    private Profiles() {}

    enum Profile { TRUSTED, PROTECTED, ISOLATED }

    /** The classes; the order is the wire format of the interface and of the store (version 2 added LOCATION). */
    enum Cls { SENSORS, LOOPBACK, SETTINGS, LOCATION }

    /** REAL: what the phone has. SUBSTITUTE: invented, per app. BLOCKED: refused (for SETTINGS: the 40 keys of ADR-175). */
    enum Value { REAL, SUBSTITUTE, BLOCKED }

    /** What the sensor service does for an app: the unchanged event, the bias folded in (ADR-167), invented data, or nothing. */
    enum SensorMode { FOLDED, RAW, SUBSTITUTE, BLOCKED }

    /** Only the sensors and the location can be substituted; loopback and the settings tables are real or blocked. */
    static boolean isValid(Cls c, Value v) {
        return c == Cls.SENSORS || c == Cls.LOCATION || v != Value.SUBSTITUTE;
    }

    /** The value a profile gives a class when the user has not set that class (spec section 3). */
    static Value defaultValue(Profile p, Cls c) {
        if (c == Cls.SENSORS || c == Cls.LOCATION) {
            return p == Profile.ISOLATED ? Value.SUBSTITUTE : Value.REAL;
        }
        return p == Profile.TRUSTED ? Value.REAL : Value.BLOCKED;
    }

    /** Real sensor data is raw only for a Trusted app; for every other profile the bias stays folded in. */
    static SensorMode sensorMode(Profile p, Value v) {
        switch (v) {
            case SUBSTITUTE:
                return SensorMode.SUBSTITUTE;
            case BLOCKED:
                return SensorMode.BLOCKED;
            default:
                return p == Profile.TRUSTED ? SensorMode.RAW : SensorMode.FOLDED;
        }
    }
}
