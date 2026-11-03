/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.compartments;

import com.android.internal.compartments.CompartmentsAppPolicy;
import com.android.internal.compartments.CompartmentsPlace;

/**
 * The Binder interface of the Compartments policy service (ADR-208). Every call acts on the owner
 * user's store. The first eight methods need org.freeholdos.permission.MANAGE_COMPARTMENTS, which
 * only the exit app holds; getProfile and getIdentity also accept
 * org.freeholdos.permission.READ_COMPARTMENT_PROFILE, which only the Settings app holds.
 *
 * <p>The transaction order is part of the contract with the app, which compiles its own copy of
 * this file: new methods are added at the end. The numbers of the constants are the order of the
 * enums of the policy core and are pinned by its host test.
 */
interface ICompartmentsPolicy {
    const int PROFILE_TRUSTED = 0;
    const int PROFILE_PROTECTED = 1;
    const int PROFILE_ISOLATED = 2;

    /** What getIdentity returns for an app with a class set to a value its profile does not give. */
    const int IDENTITY_CUSTOM = -1;

    /**
     * What getIdentity returns for a package signed with the platform key: part of the system, with no profile of its own
     * (ADR-211). -2 is left out on purpose: the exit app keeps its own -2 for an identity it could not read.
     */
    const int IDENTITY_SYSTEM = -3;

    const int CLASS_SENSORS = 0;
    const int CLASS_LOOPBACK = 1;
    const int CLASS_SETTINGS = 2;
    const int CLASS_LOCATION = 3;

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

    /**
     * The places whose name contains the query, the names that start with it first, at most limit of them (and at most 50);
     * empty when the image has no place data. Needs MANAGE_COMPARTMENTS.
     */
    CompartmentsPlace[] searchPlaces(String query, int limit);

    /** One place by its id, or null. Needs MANAGE_COMPARTMENTS. */
    CompartmentsPlace getPlace(int placeId);

    /**
     * Sets the place of the persona of one app: a place id of searchPlaces, or 0 for Automatic. The app must substitute its
     * location (an Isolated app, or location set to VALUE_SUBSTITUTE); an unknown place is refused.
     */
    void setPlace(String packageName, int placeId);

    /** The place id that the user chose for one app, 0 for Automatic. */
    int getPlaceId(String packageName);

    /**
     * The place that the persona of one app has now: the one the user chose, or the one made for an Automatic app; 0 when the
     * app has no persona (its location is not substituted, or no place can be made). Needs MANAGE_COMPARTMENTS.
     */
    int getPersonaPlaceId(String packageName);

    /**
     * The identity of one package as the Compartments pages name it: PROFILE_* when every class has the value its profile
     * gives, IDENTITY_CUSTOM when the user set one class to another value, IDENTITY_SYSTEM for a package signed with the platform
     * key. Nothing else about the app.
     */
    int getIdentity(String packageName);
}
