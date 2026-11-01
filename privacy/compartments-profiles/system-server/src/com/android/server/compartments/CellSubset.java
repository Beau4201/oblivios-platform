// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The cell towers of the persona (ADR-208): a few towers per place of {@link Places}, with the operator codes of the country,
 * read once from a read-only file of the image. Parsed strictly, like {@link Places}. Pure Java.
 *
 * <p>Format: one line per cell, five tab-separated decimal fields and a newline after every line: {@code placeId mcc mnc tac
 * cid}. The mcc is 200 to 799, the mnc 0 to 999, the tac 0 to 65535 and the cid 0 to 2^32-1. At most 64 lines belong to one
 * place, and {@link #cellsFor} returns the first 8 of them.
 *
 * <p>The parse is one pass over the characters, without a regular expression and without a split: the file has 400,000 lines,
 * and the first read of it ran under a system lock (the regex version took 10 s on a host in the interpreter, 15 s or more on
 * the phone).
 */
final class CellSubset {
    static final int MAX_LINES = 1000000;
    static final int MAX_PER_PLACE = 64;
    static final int RETURNED = 8;
    private static final int MAX_DIGITS = 10;

    static final class Cell {
        final int mcc;
        final int mnc;
        final int tac;
        final long cid;

        Cell(int mcc, int mnc, int tac, long cid) {
            this.mcc = mcc;
            this.mnc = mnc;
            this.tac = tac;
            this.cid = cid;
        }
    }

    private final Map<Integer, List<Cell>> byPlace;

    private CellSubset(Map<Integer, List<Cell>> byPlace) {
        this.byPlace = byPlace;
    }

    /** @throws IllegalArgumentException for any text that is not exactly a file of the format */
    static CellSubset parse(String tsv) {
        if (tsv == null || tsv.isEmpty() || tsv.charAt(tsv.length() - 1) != '\n') {
            throw new IllegalArgumentException("not a cell file");
        }
        Map<Integer, List<Cell>> out = new HashMap<>();
        long[] v = new long[5];
        int p = 0;
        int lines = 0;
        while (p < tsv.length()) {
            if (++lines > MAX_LINES) {
                throw new IllegalArgumentException("too many lines");
            }
            // five fields, each of the grammar 0|[1-9][0-9]{0,9} (ASCII digits, no sign, no leading zero), the first four
            // ended by a tab and the fifth by the newline; the file ends in a newline, so the inner loop always stops
            for (int k = 0; k < 5; k++) {
                int s = p;
                long n = 0;
                char c;
                while ((c = tsv.charAt(p)) != '\t' && c != '\n') {
                    if (c < '0' || c > '9' || p - s >= MAX_DIGITS) {
                        throw new IllegalArgumentException("not a number");
                    }
                    n = n * 10 + (c - '0');
                    p++;
                }
                if (p == s || (p - s > 1 && tsv.charAt(s) == '0')) {
                    throw new IllegalArgumentException("not a number");
                }
                if (c != (k < 4 ? '\t' : '\n')) {
                    throw new IllegalArgumentException("a line does not have 5 fields");
                }
                v[k] = n;
                p++;
            }
            long place = v[0];
            long mcc = v[1];
            long mnc = v[2];
            long tac = v[3];
            long cid = v[4];
            if (place < 1 || place > Integer.MAX_VALUE || mcc < 200 || mcc > 799 || mnc > 999 || tac > 65535 || cid > 4294967295L) {
                throw new IllegalArgumentException("a value is out of range");
            }
            List<Cell> list = out.computeIfAbsent((int) place, k -> new ArrayList<>());
            if (list.size() >= MAX_PER_PLACE) {
                throw new IllegalArgumentException("too many cells for one place");
            }
            list.add(new Cell((int) mcc, (int) mnc, (int) tac, cid));
        }
        return new CellSubset(out);
    }

    /**
     * The numeric operator of the first cell of the place, as a real operator property holds it: the mcc and the mnc, the mnc
     * with two digits below 100 and three otherwise; "" for a place without cells.
     */
    String operatorFor(int placeId) {
        List<Cell> all = byPlace.get(placeId);
        if (all == null) {
            return "";
        }
        Cell first = all.get(0);
        return first.mcc + (first.mnc < 10 ? "0" : "") + first.mnc;  // not String.format: the digits would follow the locale
    }

    /** At most 8 cells of the place in file order; empty for a place without cells. */
    List<Cell> cellsFor(int placeId) {
        List<Cell> all = byPlace.get(placeId);
        if (all == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(all.subList(0, Math.min(RETURNED, all.size()))));
    }
}
