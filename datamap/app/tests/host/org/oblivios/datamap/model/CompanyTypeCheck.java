// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

import java.util.Arrays;
import java.util.Collections;

public final class CompanyTypeCheck {
    private static void eq(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        eq(CompanyType.ADVERTISING, CompanyType.of(MapModel.CAT_ADVERTISING), "advertising");
        eq(CompanyType.ATTRIBUTION, CompanyType.of(MapModel.CAT_ATTRIBUTION), "attribution");
        eq(CompanyType.SOCIAL, CompanyType.of(MapModel.CAT_SOCIAL), "social");
        eq(CompanyType.ANALYTICS, CompanyType.of(MapModel.CAT_ANALYTICS), "analytics");
        eq(CompanyType.IDENTITY, CompanyType.of(MapModel.CAT_IDENTITY), "identity is a type of its own (A6)");
        eq(CompanyType.INFRASTRUCTURE, CompanyType.of(MapModel.CAT_CORE), "core is infrastructure (A6)");
        eq(CompanyType.INFRASTRUCTURE, CompanyType.of(MapModel.CAT_CDN), "a content network is infrastructure (A6)");
        eq(CompanyType.UNKNOWN, CompanyType.of(MapModel.CAT_UNKNOWN), "unknown");
        eq(CompanyType.UNKNOWN, CompanyType.of(99), "a code that does not exist is unknown");

        // the display order of the design page: advertising, attribution, social, analytics, then the added types
        int[] k = CompanyType.kinds(Arrays.asList(MapModel.CAT_ANALYTICS, MapModel.CAT_ADVERTISING, MapModel.CAT_CDN));
        if (!Arrays.equals(new int[] {CompanyType.ADVERTISING, CompanyType.ANALYTICS, CompanyType.INFRASTRUCTURE}, k)) {
            throw new AssertionError("order: " + Arrays.toString(k));
        }
        // core and a content network are one type, shown once
        k = CompanyType.kinds(Arrays.asList(MapModel.CAT_CORE, MapModel.CAT_CDN));
        if (!Arrays.equals(new int[] {CompanyType.INFRASTRUCTURE}, k)) {
            throw new AssertionError("merge: " + Arrays.toString(k));
        }
        // unknown is named only when nothing else is known
        k = CompanyType.kinds(Arrays.asList(MapModel.CAT_UNKNOWN, MapModel.CAT_SOCIAL));
        if (!Arrays.equals(new int[] {CompanyType.SOCIAL}, k)) {
            throw new AssertionError("unknown beside a known type: " + Arrays.toString(k));
        }
        k = CompanyType.kinds(Collections.<Integer>emptyList());
        if (!Arrays.equals(new int[] {CompanyType.UNKNOWN}, k)) {
            throw new AssertionError("no category: " + Arrays.toString(k));
        }
        // the main type is the first of the order, not the strongest category of the model
        eq(CompanyType.ADVERTISING, CompanyType.main(Arrays.asList(MapModel.CAT_SOCIAL, MapModel.CAT_ADVERTISING)), "main");
        System.out.println("PASS CompanyTypeCheck");
    }
}
