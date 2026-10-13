/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.datamap;

import com.android.internal.datamap.DataMapEntry;
import com.android.internal.datamap.DataMapLink;
import com.android.internal.datamap.DataMapOwner;
import com.android.internal.datamap.DataMapPublisher;
import com.android.internal.datamap.DataMapRelation;

/** Everything the Data Map app draws, for the calling user. */
parcelable DataMapSnapshot {
    DataMapEntry[] entries;
    DataMapOwner[] owners;
    DataMapRelation[] relations;
    DataMapPublisher[] publishers;
    DataMapLink[] links;
    /** Collecting is on for this user. */
    boolean collecting;
    boolean historyOn;
    int historyDays;
    /** Names the service refused as not valid host names since the user started. */
    int invalidCount;
    long nowWallMs;
    /** The history also keeps server places. */
    boolean serverHistory;
}
