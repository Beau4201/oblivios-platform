/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.datamap;

/**
 * Tells the Data Map app that there is something new to read. It carries no data: the app calls
 * getSnapshot.
 */
oneway interface IDataMapObserver {
    void onChanged();
}
