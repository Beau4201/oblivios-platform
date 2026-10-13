// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

public final class AtlasModelCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    private static MapModel.Owner owner(String id, String cc) {
        MapModel.Owner o = new MapModel.Owner();
        o.id = id;
        o.name = id;
        o.countryCode = cc;
        return o;
    }

    private static MapModel.Entry entry(String pkg, String key, String owner, int cat, int count, boolean dns) {
        MapModel.Entry e = new MapModel.Entry();
        e.pkg = pkg;
        e.serviceKey = key;
        e.ownerId = owner;
        e.category = cat;
        e.count = count;
        e.viaDns = dns;
        e.lastWall = count;
        return e;
    }

    public static void main(String[] args) {
        MapModel.Input in = new MapModel.Input();
        in.owners.put("g", owner("g", "US"));
        in.owners.put("s", owner("s", "KR"));
        in.entries.add(entry("sp", "a.g.example", "g", MapModel.CAT_ADVERTISING, 2, true));
        in.entries.add(entry("sp", "b.g.example", "g", MapModel.CAT_ANALYTICS, 3, true));
        in.entries.add(entry("sp", "s.example", "s", MapModel.CAT_ANALYTICS, 1, true));
        in.entries.add(entry("ch", "a.g.example", "g", MapModel.CAT_ADVERTISING, 7, true));
        in.entries.add(entry("lone", "mystery.example", "", MapModel.CAT_UNKNOWN, 4, true));
        in.entries.add(entry("lone", "ip:93.184.216.34", "", MapModel.CAT_UNKNOWN, 5, false));
        in.entries.add(entry("lone2", "mystery.example", "", MapModel.CAT_CDN, 1, true));
        AtlasModel m = AtlasModel.of(MapModel.build(in));

        // apps with a company: most connections first (ch 7, sp 2 + 3 + 1 = 6); then the apps without one
        check(m.withCompanies.size() == 2 && m.withCompanies.get(0).pkg.equals("ch")
                && m.withCompanies.get(1).pkg.equals("sp"), "order: " + m.withCompanies.size());
        check(m.byPkg.get("sp").connections == 6 && m.byPkg.get("ch").connections == 7, "connections");
        check(m.connections("sp", "g") == 5 && m.connections("sp", "s") == 1 && m.connections("sp", "zz") == 0,
                "connections of an app to a company");
        check(m.byPkg.get("sp").companies() == 2 && m.byPkg.get("sp").countries == 2, "companies, countries");
        check(m.without.size() == 2, "apps with no company: " + m.without.size());
        check(m.without.get(0).connections == 0, "an app with only unplaced services has no connections to a company");

        // the contacted companies, and the addresses that no owner stands behind
        check(m.contacted.size() == 2, "contacted companies");
        check(m.unplaced.size() == 2, "unplaced: " + m.unplaced.size());
        AtlasModel.Unplaced ip = null;
        AtlasModel.Unplaced named = null;
        for (AtlasModel.Unplaced u : m.unplaced) {
            if (u.key.startsWith("ip:")) {
                ip = u;
            } else {
                named = u;
            }
        }
        check(ip != null && ip.address().equals("93.184.216.34") && !ip.viaDns && ip.apps.contains("lone"), "an address");
        check(named != null && named.apps.size() == 2 && named.category == MapModel.CAT_CDN, "a name seen by two apps");
        // the category of a service is the strongest one any app met it with
        check(m.serviceCategory.get("a.g.example") == MapModel.CAT_ADVERTISING, "category of a service");
        check(m.serviceCategory.get("mystery.example") == MapModel.CAT_CDN, "the strongest category wins");
        check(AtlasModel.of(MapModel.build(new MapModel.Input())).withCompanies.isEmpty(), "nothing collected");
        System.out.println("PASS AtlasModelCheck");
    }
}
