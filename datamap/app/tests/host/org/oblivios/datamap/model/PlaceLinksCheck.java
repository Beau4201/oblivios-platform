// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PlaceLinksCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    private static MapModel.Relation rel(String a, String type, String b, String src) {
        MapModel.Relation r = new MapModel.Relation();
        r.ownerA = a;
        r.type = type;
        r.ownerB = b;
        r.sourceId = src;
        return r;
    }

    private static List<Places.Place> places(MapModel.CompanyRow... rows) {
        return Places.build(Arrays.asList(rows), null);
    }

    public static void main(String[] args) {
        MapModel.CompanyRow g = PlacesCheck.row("g", "US", 37, -122, "sp", "ch");
        MapModel.CompanyRow m = PlacesCheck.row("m", "US", 47, -122, "sp");
        MapModel.CompanyRow s = PlacesCheck.row("s", "KR", 37, 127, "ch");
        MapModel.CompanyRow x = PlacesCheck.row("x", "DE", 52, 13, "wa");
        List<Places.Place> ps = places(g, m, s, x);
        List<MapModel.Relation> none = new ArrayList<>();

        // two places are joined when one app contacts a company at each: sp reaches g and m, ch reaches g and s
        List<PlaceLinks.Pair> pairs = PlaceLinks.pairs(ps, none, null);
        check(pairs.size() == 2, "pairs: " + pairs.size());
        PlaceLinks.Pair gm = find(pairs, "g", "m");
        check(gm != null && gm.apps.size() == 1 && gm.apps.contains("sp") && gm.rels.isEmpty(), "g-m by sp");
        PlaceLinks.Pair gs = find(pairs, "s", "g");
        check(gs != null && gs.apps.contains("ch"), "s-g by ch, found in either order");
        check(find(pairs, "x", "g") == null, "no shared app, no line");

        // with an app chosen only that app joins places
        pairs = PlaceLinks.pairs(places(g, m, s, x), none, "ch");
        check(pairs.size() == 1 && find(pairs, "g", "s") != null, "app filter: " + pairs.size());

        // a documented relation joins two places that share no app, said in either direction, with its source id
        List<MapModel.Relation> rels = Arrays.asList(rel("x", "owns", "g", "S-9"));
        pairs = PlaceLinks.pairs(ps, rels, null);
        PlaceLinks.Pair xg = find(pairs, "x", "g");
        check(xg != null && xg.apps.isEmpty() && xg.rels.size() == 1 && xg.rels.get(0).sourceId.equals("S-9"),
                "relation without a shared app");
        check(find(PlaceLinks.pairs(ps, Arrays.asList(rel("g", "owns", "x", "S-9")), null), "g", "x") != null,
                "relation in the other direction");

        // companies of the same place are never a pair, and two company pairs between two places make one line
        MapModel.CompanyRow g2 = PlacesCheck.row("g2", "US", 37, -121, "sp");
        MapModel.CompanyRow m2 = PlacesCheck.row("m2", "US", 47, -121, "ch");
        pairs = PlaceLinks.pairs(places(g, g2, m, m2), none, null);
        check(pairs.size() == 1 && pairs.get(0).apps.size() == 2, "one line for one pair of places: " + pairs.size());

        // "connected to": the other companies a list of companies is joined to, most apps first, then by name
        List<MapModel.CompanyRow> all = Arrays.asList(g, m, s, x);
        List<PlaceLinks.Joined> j = PlaceLinks.joined(Arrays.asList(g), all, rels);
        check(j.size() == 3, "joined to g: " + j.size());
        check(j.get(0).other.owner.id.equals("m") || j.get(0).other.owner.id.equals("s"), "most apps first");
        check(j.get(2).other.owner.id.equals("x") && j.get(2).rels.size() == 1 && j.get(2).apps.isEmpty(),
                "x only by relation, last");
        for (PlaceLinks.Joined e : j) {
            check(!e.other.owner.id.equals("g"), "a company is not joined to itself");
        }
        // the companies of the list are left out
        check(PlaceLinks.joined(Arrays.asList(g, m), all, none).size() == 1, "list members are not others");
        // most apps first even when the name would put another first: zz shares two apps with g, m shares one
        MapModel.CompanyRow zz = PlacesCheck.row("zz", "JP", 35, 139, "sp", "ch");
        List<PlaceLinks.Joined> byApps = PlaceLinks.joined(Arrays.asList(g), Arrays.asList(g, m, zz), none);
        check(byApps.size() == 2 && byApps.get(0).other.owner.id.equals("zz"), "more shared apps come first");
        System.out.println("PASS PlaceLinksCheck");
    }

    private static PlaceLinks.Pair find(List<PlaceLinks.Pair> pairs, String a, String b) {
        for (PlaceLinks.Pair p : pairs) {
            String ka = p.a.key;
            String kb = p.b.key;
            if ((ka.equals(a) && kb.equals(b)) || (ka.equals(b) && kb.equals(a))) {
                return p;
            }
        }
        return null;
    }
}
