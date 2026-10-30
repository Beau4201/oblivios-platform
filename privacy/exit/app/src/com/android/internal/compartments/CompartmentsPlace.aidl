/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.compartments;

/** One place of the persona data (ADR-208): what the user sees when choosing where an Isolated app is. */
parcelable CompartmentsPlace {
    /** The number that setPlace takes; positive. */
    int id;
    /** The name of the place, as in the data. */
    String name;
    /** The ISO 3166-1 alpha-2 code of its country. */
    String country;
    double latitude;
    double longitude;
}
