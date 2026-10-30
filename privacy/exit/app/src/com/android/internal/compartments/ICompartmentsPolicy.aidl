/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.compartments;

import com.android.internal.compartments.CompartmentsAppPolicy;

/**
 * The Binder interface of the Compartments policy service (ADR-208). Every call acts on the owner
 * user's store. The first eight methods need org.freeholdos.permission.MANAGE_COMPARTMENTS, which
 * only the exit app holds; getProfile also accepts org.freeholdos.permission.READ_COMPARTMENT_PROFILE,
 * which only the Settings app holds.
 *
 * <p>The transaction order is part of the contract with the app, which compiles its own copy of
 * this file: new methods are added at the end. The numbers of the constants are the order of the
 * enums of the policy core and are pinned by its host test.
 */
interface ICompartmentsPolicy {
    const int PROFILE_TRUSTED = 0;
    const int PROFILE_PROTECTED = 1;
    const int PROFILE_ISOLATED = 2;

    const int CLASS_SENSORS = 0;
    const int CLASS_LOOPBACK = 1;
    const int CLASS_SETTINGS = 2;

    const int VALUE_REAL = 0;
    const int VALUE_SUBSTITUTE = 1;
    const int VALUE_BLOCKED = 2;

    /** The policy of one package; the defaults if the user has not set it. */
    CompartmentsAppPolicy getApp(String packageName);

    /** What a new app gets: the default profile and its values. */
    CompartmentsAppPolicy getDefaults();

    /** The packages that the user has set. */
    String[] getConfigured();

    /** PROFILE_PROTECTED or PROFILE_ISOLATED; never PROFILE_TRUSTED. */
    void setDefaultProfile(int profile);

    /** The sensors value of the defaults. */
    void setDefaultSensors(int value);

    /** Sets the profile of one app and resets its values to that profile's defaults. */
    void setProfile(String packageName, int profile);

    /** Sets one class of one app; the value must be valid for the class. */
    void setClassValue(String packageName, int cls, int value);

    /** Forgets what the user set for one app: it follows the defaults again. */
    void clearApp(String packageName);

    /** The profile of one package, as one of PROFILE_*. Nothing else about the app. */
    int getProfile(String packageName);
}
