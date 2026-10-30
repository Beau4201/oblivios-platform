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
        /** What the app is set to: Direct (trackers hidden), Tor or Open (never hidden). */
        public final Choices.Choice choice;

        AppRow(String pkg, String label, Choices.Choice choice) {
            this.pkg = pkg;
            this.label = label;
            this.tor = choice == Choices.Choice.TOR;
            this.choice = choice;
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
            // A package with a system uid gets no preference and no rule (Policy.compute skips it), so a row for it would
            // show a choice that is never applied (review of 2026-10-23, C2-03).
            if (!UidRanges.isAppUid(a.uid)) {
                continue;
            }
            if (!q.isEmpty() && !a.label.toLowerCase(Locale.ROOT).contains(q) && !a.pkg.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            rows.add(new AppRow(a.pkg, a.label, choices.choiceOf(a.pkg)));
        }
        Collections.sort(rows, (x, y) -> {
            int c = x.label.compareToIgnoreCase(y.label);
            return c != 0 ? c : x.pkg.compareTo(y.pkg);
        });
        return rows;
    }

    /** What the place of an Isolated app asks of its exit (ADR-208). */
    public enum Exit {
        /** The place is in the user's own country, or the app has none: nothing to change. */
        NONE,
        /** The place is abroad and the app is on Tor: its connections leave through exits in that country. */
        COUNTRY,
        /** The place is abroad and the app is not on Tor: its address still shows the user's country (the page says so). */
        MISMATCH
    }

    /**
     * What the exit of an app does about the country of its persona. A country that is not two capital letters is no
     * country. An app on Direct or Open is not moved by itself: only the user may send it through Tor.
     */
    public static Exit exitFor(String personaCountry, String ownCountry, Choices.Choice stand) {
        if (!ControlCodec.validCountry(personaCountry) || personaCountry.equals(ownCountry)) {
            return Exit.NONE;
        }
        return stand == Choices.Choice.TOR ? Exit.COUNTRY : Exit.MISMATCH;
    }

    /** The choices a tap on an app row offers, in the order of the dialog. */
    public static Choices.Choice[] options() {
        return new Choices.Choice[] {Choices.Choice.DIRECT, Choices.Choice.TOR, Choices.Choice.OPEN};
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
