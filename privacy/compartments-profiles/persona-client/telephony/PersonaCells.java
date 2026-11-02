// SPDX-License-Identifier: Apache-2.0
package com.android.phone;

import android.os.SystemClock;
import android.telephony.CellConfigLte;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The cells of the place of an app with a persona (ADR-208): what the Telephony app answers to such an app instead of the
 * serving and neighbouring cells of the real radio. The place comes from the persona file ({@link PersonaReader}), the cells
 * from the read-only data of the image ({@link CellSubset}). Every signal value is one fixed, plausible number, so that nothing
 * of the real radio is in the answer.
 */
final class PersonaCells {
    static final String CELLS_FILE = "/product/etc/compartments/cells.tsv";
    private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    private static final int LTE_CI_MASK = 0x0FFFFFFF;
    private static final int LTE_PCI_COUNT = 504;

    private final PersonaReader mReader = new PersonaReader();
    private final Object mLock = new Object();
    private boolean mRead;
    private CellSubset mCells;

    /** The place of the app of this uid, 0 when it has none: no persona, or a persona without a place (see {@link #hasPersona}). */
    int placeOf(int uid) {
        return mReader.placeOf(uid);
    }

    /** Whether the app of this uid has a persona; one without a place is answered with no cells at all. */
    boolean hasPersona(int uid) {
        return mReader.hasPersona(uid);
    }

    /** The cells of the place, the first one serving; an empty list for a place without cells. */
    List<CellInfo> infos(int placeId) {
        List<CellSubset.Cell> cells = cellsFor(placeId);
        List<CellInfo> out = new ArrayList<>(cells.size());
        long now = SystemClock.elapsedRealtimeNanos();
        for (int i = 0; i < cells.size(); i++) {
            out.add(new CellInfoLte(i == 0 ? CellInfo.CONNECTION_PRIMARY_SERVING : CellInfo.CONNECTION_NONE, i == 0, now,
                    identity(cells.get(i)), new CellSignalStrengthLte(-65, -95, -10, 10, 10, 0), new CellConfigLte(false)));
        }
        return out;
    }

    /** The identity of the first cell of the place; the empty identity of a phone without service for a place without cells. */
    CellIdentity location(int placeId) {
        List<CellSubset.Cell> cells = cellsFor(placeId);
        return cells.isEmpty() ? new CellIdentityGsm() : identity(cells.get(0));
    }

    private static CellIdentityLte identity(CellSubset.Cell c) {
        int ci = (int) (c.cid & LTE_CI_MASK);
        return new CellIdentityLte(ci, ci % LTE_PCI_COUNT, c.tac, CellInfo.UNAVAILABLE, new int[0], CellInfo.UNAVAILABLE,
                String.valueOf(c.mcc), (c.mnc < 10 ? "0" : "") + c.mnc, "", "", Collections.<String>emptyList(), null);
    }

    private List<CellSubset.Cell> cellsFor(int placeId) {
        CellSubset cells;
        synchronized (mLock) {
            if (!mRead) {
                mCells = read();
                mRead = true;
            }
            cells = mCells;
        }
        return cells == null ? Collections.<CellSubset.Cell>emptyList() : cells.cellsFor(placeId);
    }

    /** The cell data of the image; no cells at all when it has none or the file is not exactly a file of the format. */
    private static CellSubset read() {
        File f = new File(CELLS_FILE);
        try {
            if (f.isFile() && f.length() <= MAX_FILE_BYTES) {
                return CellSubset.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            // no cell data: the persona then has no cells
        }
        return null;
    }
}
