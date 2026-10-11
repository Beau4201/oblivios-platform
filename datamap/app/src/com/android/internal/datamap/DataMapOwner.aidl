/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.datamap;

/** A company or a publisher and where it is based, from the classifier file. */
parcelable DataMapOwner {
    String id;
    String name;
    String legalName;
    /** Two capital letters, ISO 3166-1. */
    String countryCode;
    String city;
    double latitude;
    double longitude;
    /** The id of the source of these facts in the classifier file. */
    String sourceId;
}
