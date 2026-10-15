// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

import java.util.Arrays;
import java.util.List;

public final class PlacesCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    static MapModel.CompanyRow row(String id, String cc, double lat, double lon, String... apps) {
        MapModel.Owner o = new MapModel.Owner();
        o.id = id;
        o.name = id.toUpperCase();
        o.countryCode = cc;
        o.lat = lat;
        o.lon = lon;
        MapModel.CompanyRow r = new MapModel.CompanyRow();
        r.owner = o;
        r.contacted = apps.length > 0;
        r.apps.addAll(Arrays.asList(apps));
        return r;
    }

    public static void main(String[] args) {
        // one place from two companies 1 degree apart: the lead is the one more apps contact
        List<Places.Place> p = Places.build(Arrays.asList(
                row("small", "US", 37.0, -122.0, "a"), row("big", "US", 37.0, -121.0, "a", "b")), null);
        check(p.size() == 1, "joined: " + p.size());
        check(p.get(0).key.equals("big") && p.get(0).lead.owner.id.equals("big"), "lead is the one with more apps");
        check(p.get(0).members.get(0).owner.id.equals("big") && p.get(0).members.get(1).owner.id.equals("small"),
                "the lead is first");
        check(p.get(0).apps.size() == 2, "apps of the place are the union");

        // a tie in apps goes by identifier, so that the same place is always named after the same company
        p = Places.build(Arrays.asList(row("b", "US", 10, 10, "x"), row("a", "US", 10, 11, "y")), null);
        check(p.size() == 1 && p.get(0).key.equals("a"), "tie by id: " + p.get(0).key);

        // 6 degrees is the limit: inside 5.99 joins, 6.01 does not (the rule is measured on the globe)
        check(Places.build(Arrays.asList(row("a", "XX", 0, 0, "x"), row("b", "XX", 0, 5.99, "x")), null).size() == 1,
                "5.99 degrees joins");
        check(Places.build(Arrays.asList(row("a", "XX", 0, 0, "x"), row("b", "XX", 0, 6.01, "x")), null).size() == 2,
                "6.01 degrees does not join");

        // a company joins the lead, never a later member: C is 10 degrees from the lead A although 5 from B
        p = Places.build(Arrays.asList(row("a", "XX", 0, 0, "1", "2", "3"), row("b", "XX", 0, 5, "1", "2"),
                row("c", "XX", 0, 10, "1")), null);
        check(p.size() == 2 && p.get(0).members.size() == 2 && p.get(1).key.equals("c"), "chain: " + p.size());

        // countries are listed once, in the order of the members
        p = Places.build(Arrays.asList(row("a", "US", 0, 0, "1", "2"), row("b", "CA", 0, 1, "1"),
                row("c", "US", 0, 2, "1")), null);
        check(p.get(0).countries.equals(Arrays.asList("US", "CA")), "countries: " + p.get(0).countries);

        // places are ordered by the apps that contact their lead, then by identifier
        p = Places.build(Arrays.asList(row("z", "XX", 0, 0, "1", "2", "3"), row("m", "XX", 40, 40, "1"),
                row("c", "XX", -40, -40, "1")), null);
        check(p.get(0).key.equals("z") && p.get(1).key.equals("c") && p.get(2).key.equals("m"), "order");

        // a company no app contacts is not a place
        p = Places.build(Arrays.asList(row("ghost", "XX", 0, 0), row("a", "XX", 20, 20, "1")), null);
        check(p.size() == 1 && p.get(0).key.equals("a"), "a company behind another is no place");

        // with an app chosen only the companies that app contacts stand on the globe, grouped again among themselves
        p = Places.build(Arrays.asList(row("a", "XX", 0, 0, "1", "2"), row("b", "XX", 0, 1, "1"),
                row("c", "XX", 0, 2, "2")), "1");
        check(p.size() == 1 && p.get(0).members.size() == 2, "app filter: " + p.get(0).members.size());
        check(p.get(0).apps.contains("2"), "the apps of a place are those of its members, not only the chosen one");

        check(Places.build(Arrays.<MapModel.CompanyRow>asList(), null).isEmpty(), "no companies");
        System.out.println("PASS PlacesCheck");
    }
}
