/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.datamap;

/** One service an app contacted, aggregated: when first and last, how often. */
parcelable DataMapEntry {
    String packageName;
    /** The service key: the classifier suffix, a registrable domain, or ip:<address>. */
    String serviceKey;
    /** The owner id of the service in the classifier, or an empty string if it is unknown. */
    String ownerId;
    /** One of the Category codes: 0 unknown, 1 core, 2 cdn, 3 analytics, 4 advertising,
        5 attribution, 6 social, 7 identity. */
    int category;
    long firstSeenWallMs;
    long lastSeenWallMs;
    int count;
    int foregroundCount;
    int backgroundCount;
    /** The service came with a DNS answer for the app. False for a connect to a bare address. */
    boolean viaDns;
    /** What the first address of this service said (ADR-161): 0 nothing, 1 city, 2 country, 3 uncertain, 4 anycast,
        5 steered. This is where the server lies in the data, never where the company is based. */
    int serverKind;
    /** Two capital letters, or an empty string. */
    String serverCountry;
    String serverCity;
    /** Where to draw kind 1 and 2; zero otherwise. */
    float serverLat;
    float serverLon;
    int serverAsn;
    String serverOperator;
}
