// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

public final class MapModelCategoriesCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    public static void main(String[] args) {
        MapModel.Input in = new MapModel.Input();
        for (String id : new String[] {"trk", "par"}) {
            MapModel.Owner o = new MapModel.Owner();
            o.id = id;
            o.name = id;
            o.countryCode = "NL";
            in.owners.put(id, o);
        }
        for (int[] c : new int[][] {{MapModel.CAT_ADVERTISING, 1}, {MapModel.CAT_CDN, 2}, {MapModel.CAT_ADVERTISING, 3}}) {
            MapModel.Entry e = new MapModel.Entry();
            e.pkg = "app";
            e.serviceKey = "s" + c[1] + ".example";
            e.ownerId = "trk";
            e.category = c[0];
            e.count = 1;
            in.entries.add(e);
        }
        MapModel.Relation r = new MapModel.Relation();
        r.ownerA = "par";
        r.type = "owns";
        r.ownerB = "trk";
        in.relations.add(r);
        MapModel.Output out = MapModel.build(in);
        MapModel.CompanyRow trk = null;
        MapModel.CompanyRow par = null;
        for (MapModel.CompanyRow row : out.companies) {
            if (row.owner.id.equals("trk")) {
                trk = row;
            } else {
                par = row;
            }
        }
        check(trk != null && trk.categories.size() == 2, "every category of the contacted services, once");
        check(trk.categories.contains(MapModel.CAT_ADVERTISING) && trk.categories.contains(MapModel.CAT_CDN), "which");
        check(par != null && !par.contacted && par.categories.isEmpty(), "a company behind another has none");
        System.out.println("PASS MapModelCategoriesCheck");
    }
}
