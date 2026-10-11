/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.datamap;

/** Two apps that could be linked, and why. Never "are linked": that is not visible. */
parcelable DataMapLink {
    String packageA;
    String packageB;
    /** 1: both contacted the same service. 2: different services of the same owner. */
    int kind;
    /** Service keys for kind 1, owner ids for kind 2. */
    String[] evidence;
    /** Kind 1 only: both contacted a shared service within the same window of each other. */
    boolean stronger;
}
