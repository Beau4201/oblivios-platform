// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

import java.util.HashSet;
import java.util.Set;

public final class CompanyColorsCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    public static void main(String[] args) {
        // the eight colours of the design page, dark and light
        check(CompanyColors.of("google", false) == 0xFF22E58B && CompanyColors.of("google", true) == 0xFF12A85F, "google");
        check(CompanyColors.of("meta", false) == 0xFF2F7BFF && CompanyColors.of("meta", true) == 0xFF1F5FD1, "meta");
        check(CompanyColors.of("apple", false) == 0xFFB9C6F5, "apple");
        check(CompanyColors.of("amazon", true) == 0xFFD98A00, "amazon");
        check(CompanyColors.of("microsoft", false) == 0xFF9B5CFF, "microsoft");
        check(CompanyColors.of("appsflyer", true) == 0xFFD6458F, "appsflyer");
        // a company that is not pinned gets one of the eight, and always the same one
        int c = CompanyColors.of("criteo", false);
        check(c == CompanyColors.of("criteo", false), "stable");
        check(CompanyColors.of("criteo", true) != c, "light differs from dark");
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            seen.add(CompanyColors.of("owner_" + i, false));
        }
        check(seen.size() == 8, "all eight colours are used over 300 companies: " + seen.size());
        check(CompanyColors.of("", false) == CompanyColors.of("", false), "empty id");
        System.out.println("PASS CompanyColorsCheck");
    }
}
