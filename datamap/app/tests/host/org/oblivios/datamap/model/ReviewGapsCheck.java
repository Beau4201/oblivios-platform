// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Cases the first review of the Atlas branch showed that no check covered (each was a surviving mutation). */
public final class ReviewGapsCheck {
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

    private static MapModel.Entry entry(String pkg, String key, String owner, int cat, int count) {
        MapModel.Entry e = new MapModel.Entry();
        e.pkg = pkg;
        e.serviceKey = key;
        e.ownerId = owner;
        e.category = cat;
        e.count = count;
        e.viaDns = true;
        e.lastWall = count;
        return e;
    }

    private static void atlasModel() {
        MapModel.Input in = new MapModel.Input();
        in.owners.put("g", owner("g", "US"));
        in.owners.put("h", owner("h", "US"));
        // two companies in one country: companies() is 2, countries is 1
        in.entries.add(entry("zz", "a.g", "g", MapModel.CAT_ADVERTISING, 2));
        in.entries.add(entry("zz", "a.h", "h", MapModel.CAT_ADVERTISING, 2));
        // equal connections order by package name, whatever the order of the model
        in.entries.add(entry("bb", "b.g", "g", MapModel.CAT_ADVERTISING, 4));
        in.entries.add(entry("aa", "c.g", "g", MapModel.CAT_ADVERTISING, 4));
        // a contact whose owner is not a company of the classifier output is not counted
        in.entries.add(entry("zz", "x.example", "nobody", MapModel.CAT_UNKNOWN, 9));
        AtlasModel m = AtlasModel.of(MapModel.build(in));
        AtlasModel.App zz = m.byPkg.get("zz");
        check(zz.companies() == 2 && zz.countries == 1, "countries are counted once: " + zz.countries);
        check(zz.connections == 4, "a contact of an unknown owner is not a connection: " + zz.connections);
        List<String> order = new ArrayList<>();
        for (AtlasModel.App a : m.withCompanies) {
            order.add(a.pkg);
        }
        check(order.equals(Arrays.asList("aa", "bb", "zz")) || order.equals(Arrays.asList("aa", "bb", "zz")),
                "order by connections, then package: " + order);
    }

    private static void places() {
        // a company without a country code adds no country to its place
        MapModel.CompanyRow a = PlacesCheck.row("a", "", 0, 0, "1");
        MapModel.CompanyRow b = PlacesCheck.row("b", null, 0, 1, "1");
        MapModel.CompanyRow c = PlacesCheck.row("c", "NL", 0, 2, "1");
        List<Places.Place> p = Places.build(Arrays.asList(a, b, c), null);
        check(p.size() == 1 && p.get(0).countries.equals(Arrays.asList("NL")), "countries: " + p.get(0).countries);
    }

    private static void joinedOrder() {
        MapModel.CompanyRow x = PlacesCheck.row("x", "US", 0, 0, "1", "2");
        MapModel.CompanyRow b = PlacesCheck.row("b", "US", 30, 30, "1");
        MapModel.CompanyRow a = PlacesCheck.row("a", "US", -30, -30, "1");
        MapModel.CompanyRow two = PlacesCheck.row("m", "US", 50, 50, "1", "2");
        List<PlaceLinks.Joined> j = PlaceLinks.joined(Arrays.asList(x), Arrays.asList(b, a, two),
                new ArrayList<MapModel.Relation>());
        check(j.size() == 3 && j.get(0).other.owner.id.equals("m") && j.get(1).other.owner.id.equals("a")
                && j.get(2).other.owner.id.equals("b"), "more shared apps first, then by name");
    }

    private static void drawnLines() {
        check(PlaceLinks.MAX_PAIR_LINES == 12, "the limit of the spec (A4)");
        // up to the limit every line is drawn
        check(PlaceLinks.drawn(12, false, false, false), "12 pairs, nothing chosen");
        // above it: nothing chosen draws none, a chosen place draws its own lines only, a chosen app draws its lines
        check(!PlaceLinks.drawn(13, false, false, false), "13 pairs, nothing chosen");
        check(PlaceLinks.drawn(13, true, true, false), "13 pairs, a line of the chosen place");
        check(!PlaceLinks.drawn(13, false, true, false), "13 pairs, a line of another place is not drawn");
        check(PlaceLinks.drawn(13, true, false, true), "13 pairs, a chosen app");
        check(!PlaceLinks.drawn(13, false, true, true), "13 pairs, a place and an app, a line not of the place");
    }

    private static void placesOff() {
        MapModel.Output out = new MapModel.Output();
        check(new AtlasData(out, true, false, 7, 0).showsPlaces(), "collecting: places are shown");
        check(!new AtlasData(out, false, true, 7, 0).showsPlaces(), "collection off: the globe has no places");
        check(!AtlasData.unavailable().showsPlaces(), "no service: no places");
    }

    private static void cardGap() {
        // the second card would start 2 dp after the first: the gap of 4 dp refuses it
        float[] pos = CardPlacer.place(new float[] {200, 322}, new float[] {300, 300}, new float[] {120, 120},
                46, 800, 766);
        check(!(pos[2] == 300 && pos[3] == 242), "cards keep a gap of 4 dp: " + pos[2] + "," + pos[3]);
    }

    private static void groupedByType() {
        MapModel.CompanyRow ad = PlacesCheck.row("ad", "US", 0, 0, "1");
        ad.categories.add(MapModel.CAT_ADVERTISING);
        MapModel.CompanyRow an = PlacesCheck.row("an", "US", 0, 0, "1");
        an.categories.add(MapModel.CAT_ANALYTICS);
        MapModel.CompanyRow ad2 = PlacesCheck.row("ad2", "US", 0, 0, "1");
        ad2.categories.add(MapModel.CAT_ADVERTISING);
        MapModel.CompanyRow unk = PlacesCheck.row("unk", "US", 0, 0, "1");
        java.util.Map<String, Integer> w = new java.util.HashMap<>();
        w.put("ad", 1);
        w.put("ad2", 5);
        w.put("an", 3);
        w.put("unk", 9);
        List<List<MapModel.CompanyRow>> g = CompanyList.groupByType(Arrays.asList(unk, an, ad, ad2), w);
        check(g.size() == 3, "three groups: " + g.size());
        check(CompanyType.main(g.get(0).get(0).categories) == CompanyType.ADVERTISING && g.get(0).size() == 2
                && g.get(0).get(0).owner.id.equals("ad2"), "advertising first, the most connections first");
        check(g.get(1).get(0).owner.id.equals("an"), "analytics second");
        check(g.get(2).get(0).owner.id.equals("unk"), "unknown is last");
        check(CompanyList.groupByType(new ArrayList<MapModel.CompanyRow>(), w).isEmpty(), "none");
    }

    private static void typeRange() {
        check(CompanyList.validType(-1) == -1 && CompanyList.validType(0) == 0 && CompanyList.validType(6) == 6,
                "the range");
        check(CompanyList.validType(7) == -1 && CompanyList.validType(-2) == -1 && CompanyList.validType(99) == -1,
                "a value outside the types is all types");
    }

    private static void words() {
        check(Words.join(" · ", "Analytics", "", "NL").equals("Analytics · NL"), "an empty part is left out");
        check(Words.join(" · ", "", "3 apps").equals("3 apps"), "a leading empty part");
        check(Words.join(" · ", null, "x", null).equals("x"), "null parts");
        check(Words.join(" · ").isEmpty(), "nothing");
    }

    private static byte[] landFile(float[][][] shapes) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(b);
        o.writeBytes("DMLD");
        o.writeByte(1);
        o.writeShort(shapes.length);
        for (float[][] shape : shapes) {
            o.writeShort(shape.length);
            for (float[] ring : shape) {
                o.writeShort(ring.length / 2);
                for (float v : ring) {
                    o.writeShort(Math.round(v * 100));
                }
            }
        }
        return b.toByteArray();
    }

    private static void land() throws Exception {
        // a bump of 1 degree stays under tolerance 0.5 and goes under 1.5
        float[] bumpy = {0, 0, 10, 0, 10, 10, 5, 11, 0, 10, 0, 0};
        LandData land = LandData.read(new ByteArrayInputStream(landFile(new float[][][] {{bumpy}})));
        check(land.decimated(0.5).shapes[0][0].length / 2 == 6, "a bump above the tolerance stays");
        check(land.decimated(1.5).shapes[0][0].length / 2 == 5, "a bump under the tolerance goes");
        // the texture keeps the points on land only
        float[] square = {0, 0, 10, 0, 10, 10, 0, 10, 0, 0};
        LandData sq = LandData.read(new ByteArrayInputStream(landFile(new float[][][] {{square}})));
        Seeded.Points p = new Seeded.Points(2);
        double[][] ll = {{5, 5}, {-40, 100}};
        for (int i = 0; i < 2; i++) {
            double la = Math.toRadians(ll[i][0]);
            double lo = Math.toRadians(ll[i][1]);
            p.v[3 * i] = (float) (Math.cos(la) * Math.sin(lo));
            p.v[3 * i + 1] = (float) Math.sin(la);
            p.v[3 * i + 2] = (float) (Math.cos(la) * Math.cos(lo));
        }
        check(Seeded.onLand(p, sq).n == 1, "only the point on land is kept: " + Seeded.onLand(p, sq).n);
    }

    public static void main(String[] args) throws Exception {
        atlasModel();
        places();
        joinedOrder();
        drawnLines();
        placesOff();
        cardGap();
        groupedByType();
        typeRange();
        words();
        land();
        System.out.println("PASS ReviewGapsCheck");
    }
}
