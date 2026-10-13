// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.util.Collection;

/**
 * The type of a company as the Companies filter and the company page name it (ADR-160, A6): the categories of the
 * service's classifier, with core and content network as one "Infrastructure" and identity as a type of its own.
 * The numbers are the display order of the design page: advertising, attribution, social, analytics, then identity
 * and infrastructure; unknown is last and is named only when nothing else is known.
 */
public final class CompanyType {
    public static final int ADVERTISING = 0;
    public static final int ATTRIBUTION = 1;
    public static final int SOCIAL = 2;
    public static final int ANALYTICS = 3;
    public static final int IDENTITY = 4;
    public static final int INFRASTRUCTURE = 5;
    public static final int UNKNOWN = 6;
    public static final int COUNT = 7;

    private CompanyType() {}

    /** The type of one category code of {@link MapModel}. */
    public static int of(int category) {
        switch (category) {
            case MapModel.CAT_ADVERTISING:
                return ADVERTISING;
            case MapModel.CAT_ATTRIBUTION:
                return ATTRIBUTION;
            case MapModel.CAT_SOCIAL:
                return SOCIAL;
            case MapModel.CAT_ANALYTICS:
                return ANALYTICS;
            case MapModel.CAT_IDENTITY:
                return IDENTITY;
            case MapModel.CAT_CORE:
            case MapModel.CAT_CDN:
                return INFRASTRUCTURE;
            default:
                return UNKNOWN;
        }
    }

    /** All types of a company in display order, once each; {@code UNKNOWN} alone when no category is known. */
    public static int[] kinds(Collection<Integer> categories) {
        boolean[] has = new boolean[COUNT];
        for (int c : categories) {
            has[of(c)] = true;
        }
        int n = 0;
        for (int t = 0; t < UNKNOWN; t++) {
            if (has[t]) {
                n++;
            }
        }
        if (n == 0) {
            return new int[] {UNKNOWN};
        }
        int[] out = new int[n];
        int k = 0;
        for (int t = 0; t < UNKNOWN; t++) {
            if (has[t]) {
                out[k++] = t;
            }
        }
        return out;
    }

    /** The type that stands for the company: the first of {@link #kinds}. */
    public static int main(Collection<Integer> categories) {
        return kinds(categories)[0];
    }
}
