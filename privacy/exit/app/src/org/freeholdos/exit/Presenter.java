// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What the settings screens list, as plain data: the apps with their choice, filtered by the search text, and the
 * (app, company) pairs the user chose to show the real address to. The activity only draws these rows, so the choices that
 * lead to a {@link Policy.Out} are host checks. Pure Java.
 */
public final class Presenter {
    private Presenter() {}

    /** An installed app that can be listed: its package, the label it shows and its uid in the owner user. */
    public static final class Listed {
        public final String pkg;
        public final String label;
        public final int uid;

        public Listed(String pkg, String label, int uid) {
            this.pkg = pkg;
            this.label = label;
            this.uid = uid;
        }
    }

    public static final class AppRow {
        public final String pkg;
        public final String label;
        public final boolean tor;

        AppRow(String pkg, String label, boolean tor) {
            this.pkg = pkg;
            this.label = label;
            this.tor = tor;
        }
    }

    public static final class OverrideRow {
        public final String pkg;
        public final int company;
        /** The company's name, or null when the list does not know it. */
        public final String companyName;

        OverrideRow(String pkg, int company, String companyName) {
            this.pkg = pkg;
            this.company = company;
            this.companyName = companyName;
        }
    }

    /**
     * The apps whose label or package contains {@code query} (any case), sorted by label and then by package. The exit app
     * and the updater are left out: they are never routed through Tor or masked, so there is nothing to choose.
     */
    public static List<AppRow> appRows(Choices choices, List<Listed> apps, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<AppRow> rows = new ArrayList<>();
        for (Listed a : apps) {
            if (Policy.EXEMPT_PACKAGES.contains(a.pkg)) {
                continue;
            }
            if (!q.isEmpty() && !a.label.toLowerCase(Locale.ROOT).contains(q) && !a.pkg.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            rows.add(new AppRow(a.pkg, a.label, choices.choiceOf(a.pkg) == Choices.Choice.TOR));
        }
        Collections.sort(rows, (x, y) -> {
            int c = x.label.compareToIgnoreCase(y.label);
            return c != 0 ? c : x.pkg.compareTo(y.pkg);
        });
        return rows;
    }

    /** The choice that a tap on an app row makes: the other one. */
    public static Choices.Choice toggled(Choices choices, String pkg) {
        return choices.choiceOf(pkg) == Choices.Choice.TOR ? Choices.Choice.DIRECT : Choices.Choice.TOR;
    }

    /** Every (app, company) pair with "show my address", sorted by package and company. */
    public static List<OverrideRow> overrideRows(Choices choices, CompanyNames names) {
        List<OverrideRow> rows = new ArrayList<>();
        for (String pkg : choices.packagesWithOverrides()) {
            for (int company : choices.shownCompanies(pkg)) {
                rows.add(new OverrideRow(pkg, company, names.nameOf(company)));
            }
        }
        return rows;
    }
}
