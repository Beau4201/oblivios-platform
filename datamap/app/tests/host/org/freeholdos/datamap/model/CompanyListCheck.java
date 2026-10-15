// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CompanyListCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    private static MapModel.CompanyRow co(String id, String name, String cc, int cat, String... apps) {
        MapModel.CompanyRow r = PlacesCheck.row(id, cc, 0, 0, apps);
        r.owner.name = name;
        r.categories.add(cat);
        return r;
    }

    private static String ids(List<MapModel.CompanyRow> l) {
        StringBuilder sb = new StringBuilder();
        for (MapModel.CompanyRow r : l) {
            sb.append(r.owner.id).append(' ');
        }
        return sb.toString().trim();
    }

    public static void main(String[] args) {
        CompanyList.Names names = new CompanyList.Names() {
            @Override
            public String country(String cc) {
                return cc.equals("DE") ? "Germany" : cc.equals("US") ? "United States" : "Israel";
            }
        };
        List<MapModel.CompanyRow> rows = new ArrayList<>(Arrays.asList(
                co("b", "Bravo", "US", MapModel.CAT_ADVERTISING, "1"),
                co("a", "alpha", "DE", MapModel.CAT_ANALYTICS, "1", "2", "3"),
                co("c", "Charlie", "IL", MapModel.CAT_ATTRIBUTION, "1", "2"),
                co("d", "Delta", "US", MapModel.CAT_ADVERTISING, "1", "2")));

        // most apps first, then by name; the default of the design
        check(ids(CompanyList.of(rows, CompanyList.ALL_TYPES, CompanyList.APPS, names, Locale.ROOT)).equals("a c d b"),
                ids(CompanyList.of(rows, CompanyList.ALL_TYPES, CompanyList.APPS, names, Locale.ROOT)));
        // A to Z does not care for case
        check(ids(CompanyList.of(rows, CompanyList.ALL_TYPES, CompanyList.NAME, names, Locale.ROOT)).equals("a b c d"),
                "name order");
        // by the name of the country, then the name of the company
        check(ids(CompanyList.of(rows, CompanyList.ALL_TYPES, CompanyList.COUNTRY, names, Locale.ROOT)).equals("a c b d"),
                "country order: " + ids(CompanyList.of(rows, CompanyList.ALL_TYPES, CompanyList.COUNTRY, names, Locale.ROOT)));
        // a type keeps only its companies, in the chosen order
        check(ids(CompanyList.of(rows, CompanyType.ADVERTISING, CompanyList.APPS, names, Locale.ROOT)).equals("d b"),
                "type filter");
        check(CompanyList.of(rows, CompanyType.SOCIAL, CompanyList.APPS, names, Locale.ROOT).isEmpty(), "a type with none");
        // companies no app contacts are not listed
        rows.add(PlacesCheck.row("ghost", "US", 0, 0));
        check(CompanyList.of(rows, CompanyList.ALL_TYPES, CompanyList.APPS, names, Locale.ROOT).size() == 4, "ghost");

        // counts per type, for the filter sheet: only the contacted companies, indexed by the type
        int[] n = CompanyList.counts(rows);
        check(n[CompanyType.ADVERTISING] == 2 && n[CompanyType.ANALYTICS] == 1 && n[CompanyType.ATTRIBUTION] == 1
                && n[CompanyType.SOCIAL] == 0 && n[CompanyType.UNKNOWN] == 0, "counts");
        check(CompanyList.active(CompanyList.ALL_TYPES, CompanyList.APPS) == false, "default is not a filter");
        check(CompanyList.active(CompanyType.SOCIAL, CompanyList.APPS), "a type is a filter");
        check(CompanyList.active(CompanyList.ALL_TYPES, CompanyList.NAME), "another order is a filter");
        System.out.println("PASS CompanyListCheck");
    }
}
