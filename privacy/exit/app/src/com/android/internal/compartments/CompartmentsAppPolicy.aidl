/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.compartments;

/** What the policy store holds for one app, resolved to values. */
parcelable CompartmentsAppPolicy {
    /** One of ICompartmentsPolicy.PROFILE_*. */
    int profile;
    /** One of ICompartmentsPolicy.VALUE_*, for each class. */
    int sensors;
    int loopback;
    int settings;
    /** The user has set this app. False for an app that follows the defaults. */
    boolean configured;
}
