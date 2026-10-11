/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.datamap;

/** A documented connection between two owners, with its source. */
parcelable DataMapRelation {
    String ownerA;
    /** One of: owns, merged_with, partner_of, buys_ad_space_from. */
    String type;
    String ownerB;
    String note;
    String sourceId;
}
