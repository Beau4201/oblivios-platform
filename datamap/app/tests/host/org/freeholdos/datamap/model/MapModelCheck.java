// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

public final class MapModelCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    private static MapModel.Owner owner(String id, String name, double lat, double lon) {
        MapModel.Owner o = new MapModel.Owner();
        o.id = id;
        o.name = name;
        o.countryCode = "NL";
        o.city = "Testcity";
        o.lat = lat;
        o.lon = lon;
        o.sourceId = "s1";
        return o;
    }

    private static MapModel.Entry entry(String pkg, String key, String owner, int cat, long last, int count) {
        MapModel.Entry e = new MapModel.Entry();
        e.pkg = pkg;
        e.serviceKey = key;
        e.ownerId = owner;
        e.category = cat;
        e.firstWall = last - 1000;
        e.lastWall = last;
        e.count = count;
        e.foreground = count;
        e.viaDns = true;
        return e;
    }

    private static MapModel.Relation rel(String a, String type, String b) {
        MapModel.Relation r = new MapModel.Relation();
        r.ownerA = a;
        r.type = type;
        r.ownerB = b;
        r.sourceId = "s1";
        return r;
    }

    public static void main(String[] args) {
        MapModel.Input in = new MapModel.Input();
        in.owners.put("pub", owner("pub", "Publisher", 52, 5));
        in.owners.put("trk", owner("trk", "Tracker Inc", 37, -122));
        in.owners.put("par", owner("par", "Parent Holdings", 51, 0));
        in.owners.put("far", owner("far", "Far Away", -33, 151));
        in.owners.put("oth", owner("oth", "Other", 10, 10));
        in.owners.put("sub", owner("sub", "Subsidiary", 48, 2));
        in.publisherOf.put("app.one", "pub");
        in.publisherOf.put("app.two", "ghost");   // a publisher the classifier has no owner for

        in.entries.add(entry("app.one", "pub.example", "pub", MapModel.CAT_CORE, 5000, 3));
        in.entries.add(entry("app.one", "ads.trk.example", "trk", MapModel.CAT_ADVERTISING, 6000, 4));
        in.entries.add(entry("app.one", "cdn.trk.example", "trk", MapModel.CAT_CDN, 4000, 2));
        in.entries.add(entry("app.one", "mystery.example", "", MapModel.CAT_UNKNOWN, 3000, 1));
        in.entries.add(entry("app.two", "ads.trk.example", "trk", MapModel.CAT_ADVERTISING, 9000, 7));
        in.entries.add(entry("app.three", "x.example", "missing", MapModel.CAT_CORE, 1000, 1));
        in.relations.add(rel("par", "owns", "trk"));
        in.relations.add(rel("far", "partner_of", "oth"));    // neither is contacted: not shown
        in.relations.add(rel("par", "owns", "nobody"));       // an owner that is not known: ignored
        in.relations.add(rel("trk", "owns", "sub"));          // the contacted company is the first of the pair

        MapModel.Output out = MapModel.build(in);

        // the trackers are exactly the codes 3 to 7
        for (int c = 0; c <= 8; c++) {
            check(MapModel.isTracker(c) == (c >= 3 && c <= 7), "isTracker " + c);
        }

        // apps, newest first
        check(out.apps.size() == 3, "three apps");
        check(out.apps.get(0).pkg.equals("app.two"), "newest app first");
        MapModel.AppRow one = out.apps.get(1);
        check(one.pkg.equals("app.one"), "app.one second");
        check(one.onMap && "pub".equals(one.publisherId), "app.one is on the map");
        check(one.count == 10 && one.foreground == 10 && one.background == 0, "app.one totals");
        check(one.firstWall == 2000 && one.lastWall == 6000, "app.one times");
        check(one.touchesTracker, "app.one touches a tracker");
        check(one.companies == 2, "app.one contacted two companies (pub, trk), not the unknown one");
        check(one.contacts.get(0).serviceKey.equals("ads.trk.example"), "contacts newest first");
        check(!out.apps.get(0).onMap, "a publisher without an owner is not on the map");
        check(out.unplacedApps.contains("app.two") && out.unplacedApps.contains("app.three"),
                "apps without a place are listed");
        check(!out.unplacedApps.contains("app.one"), "app.one has a place");

        // lines: never to the app's own publisher, once per company, only to known owners
        boolean toPub = false;
        int oneToTrk = 0;
        for (MapModel.Edge e : out.edges) {
            toPub |= e.pkg.equals("app.one") && e.ownerId.equals("pub");
            if (e.pkg.equals("app.one") && e.ownerId.equals("trk")) oneToTrk++;
        }
        check(!toPub, "no line from an app to its own publisher");
        check(oneToTrk == 1, "one line per app and company");
        check(out.edges.size() == 2, "two lines: app.one to trk and app.two to trk");

        // services of unknown owner, and of an owner the snapshot does not carry, are not placed
        check(out.unplacedServices.size() == 2, "two unplaced services");
        boolean sawMystery = false, sawMissing = false;
        for (MapModel.UnplacedService u : out.unplacedServices) {
            sawMystery |= u.serviceKey.equals("mystery.example") && u.apps.contains("app.one");
            sawMissing |= u.serviceKey.equals("x.example");
        }
        check(sawMystery && sawMissing, "the unplaced services are the unknown one and the missing owner");

        // companies: the contacted one with its strongest category, and the one behind it
        MapModel.CompanyRow trk = null, par = null, pub = null;
        for (MapModel.CompanyRow c : out.companies) {
            if (c.owner.id.equals("trk")) trk = c;
            if (c.owner.id.equals("par")) par = c;
            if (c.owner.id.equals("pub")) pub = c;
        }
        check(trk != null && trk.contacted, "trk contacted");
        check(trk.category == MapModel.CAT_ADVERTISING, "the strongest category wins over cdn");
        check(trk.apps.size() == 2 && trk.services.size() == 2, "trk apps and services");
        check(par != null && !par.contacted, "par is behind trk and not contacted");
        check(pub != null, "pub is contacted by app.one (its own service), so it is a company too");
        check(trk.connectedTo.contains("par") && par.connectedTo.contains("trk"), "connections both ways");
        check(out.relations.size() == 2, "only the relations between two drawn companies");
        MapModel.CompanyRow sub = null;
        for (MapModel.CompanyRow c : out.companies) {
            if (c.owner.id.equals("sub")) sub = c;
        }
        check(sub != null && !sub.contacted && sub.connectedTo.contains("trk"), "sub is behind trk");
        for (MapModel.CompanyRow c : out.companies) {
            check(!c.owner.id.equals("far") && !c.owner.id.equals("oth"), "far and oth are not drawn");
        }
        check(out.companies.get(0).owner.id.equals("trk"), "the company with most apps first");

        // an empty snapshot gives an empty output
        MapModel.Output none = MapModel.build(new MapModel.Input());
        check(none.apps.isEmpty() && none.companies.isEmpty() && none.edges.isEmpty(), "empty in, empty out");

        System.out.println("PASS MapModelCheck");
    }
}
