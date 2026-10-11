/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.datamap;

/** The publisher of an installed package, where the classifier file knows it. */
parcelable DataMapPublisher {
    String packageName;
    String ownerId;
}
