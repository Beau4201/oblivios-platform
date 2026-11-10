/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.datamap;

import com.android.internal.datamap.DataMapSnapshot;
import com.android.internal.datamap.IDataMapObserver;

/**
 * The Binder interface of the Data Map service. Every call acts on the calling user; there is no
 * user argument. Each call needs org.oblivios.permission.READ_DATA_MAP, which only the Data Map
 * app holds.
 *
 * <p>The transaction order is part of the contract with the app, which compiles its own copy of
 * this file: new methods are added at the end.
 */
interface IDataMapService {
    /**
     * The aggregates of the calling user, the owners and publishers they refer to, the documented
     * relations between owners, and the apps that could be linked.
     *
     * @param packages the installed packages the caller wants publishers for (at most 2000).
     */
    DataMapSnapshot getSnapshot(in String[] packages);

    /** The observer is told, at most twice a second, that there is something new to read. */
    void registerObserver(IDataMapObserver observer);

    void unregisterObserver(IDataMapObserver observer);

    /** Names and counts are collected, or not at all. Applies to the calling user. */
    void setCollecting(boolean collecting);

    /** A history of 1 to 90 days is kept on this phone, encrypted, or none. */
    void setHistory(boolean enabled, int days);

    /** Deletes everything collected for the calling user, in memory and on disk. */
    void clear();

    /** The history keeps the hosting country, city and operator of a service, or not (ADR-161). */
    void setServerHistory(boolean enabled);
}
