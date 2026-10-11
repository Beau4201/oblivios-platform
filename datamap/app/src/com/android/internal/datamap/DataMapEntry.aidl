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
}
