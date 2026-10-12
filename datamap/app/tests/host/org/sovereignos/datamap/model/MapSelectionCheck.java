// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.util.Arrays;
import java.util.List;

public final class MapSelectionCheck {
    private static void eq(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("FAILED: " + what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void near(float expected, float actual, String what) {
        if (Math.abs(expected - actual) > 0.001f) {
            throw new AssertionError("FAILED: " + what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static MapModel.Owner owner(String id) {
        MapModel.Owner o = new MapModel.Owner();
        o.id = id;
        o.name = id;
        return o;
    }

    private static MapModel.Entry entry(String pkg, String key, String owner) {
        MapModel.Entry e = new MapModel.Entry();
        e.pkg = pkg;
        e.serviceKey = key;
        e.ownerId = owner;
        e.category = MapModel.CAT_ANALYTICS;
        e.count = 1;
        return e;
    }

    private static MapModel.Relation rel(String a, String b) {
        MapModel.Relation r = new MapModel.Relation();
        r.ownerA = a;
        r.type = "owns";
        r.ownerB = b;
        return r;
    }

    public static void main(String[] args) {
        // ---- the states of the sheet and what it shows
        MapSelection s = new MapSelection();
        eq(MapSelection.SHEET_NONE, s.sheet(), "nothing chosen, no sheet");
        eq(MapSelection.CONTENT_NONE, s.content(), "nothing to show");

        s.selectApp("com.a");
        eq(MapSelection.SHEET_HALF, s.sheet(), "an app opens the sheet half way");
        eq(MapSelection.CONTENT_APP, s.content(), "the app");
        s.toggleSheet();
        eq(MapSelection.SHEET_FULL, s.sheet(), "the handle takes it to full height");
        s.toggleSheet();
        eq(MapSelection.SHEET_HALF, s.sheet(), "and back");

        s.toggleSheet();                       // full
        s.selectCompany("x");
        eq(MapSelection.CONTENT_COMPANY, s.content(), "a company over the app");
        eq(MapSelection.SHEET_FULL, s.sheet(), "a sheet that is open keeps its height");
        eq("com.a", s.app(), "the app stays chosen");
        s.back();
        eq(MapSelection.CONTENT_APP, s.content(), "back closes the company first");
        eq(MapSelection.SHEET_HALF, s.sheet(), "and the app's sheet is half again");
        s.back();
        eq(MapSelection.CONTENT_NONE, s.content(), "back again closes the app");
        eq(MapSelection.SHEET_NONE, s.sheet(), "and the sheet");
        eq(null, s.app(), "the app is not chosen any more");

        s.selectCompany("x");
        eq(MapSelection.SHEET_HALF, s.sheet(), "a company opens a closed sheet half way");
        s.back();
        eq(MapSelection.SHEET_NONE, s.sheet(), "back closes it: no app is chosen");

        s.selectRelation(2);
        eq(MapSelection.CONTENT_RELATION, s.content(), "a relation");
        eq(2, s.relation(), "its index");
        s.selectCompany("y");
        eq(-1, s.relation(), "choosing a company drops the relation");
        s.selectRelation(0);
        eq(null, s.company(), "choosing a relation drops the company");

        s.back();
        List<String> ids = Arrays.asList("a", "b");
        s.openGroup(ids);
        eq(MapSelection.CONTENT_GROUP, s.content(), "a group of companies");
        eq(MapSelection.SHEET_HALF, s.sheet(), "a group always opens half way");
        s.selectCompany("a");
        eq(MapSelection.CONTENT_COMPANY, s.content(), "a company of the group shows over it");
        s.back();
        eq(MapSelection.CONTENT_NONE, s.content(), "back drops the company and the group together");

        s.toggleSheet();
        eq(MapSelection.SHEET_NONE, s.sheet(), "a closed sheet has no handle to toggle");

        // ---- heights and layout (numbers of the design page, dp)
        eq(0, MapSelection.sheetHeight(MapSelection.SHEET_NONE, 1000, 60), "no sheet");
        eq(520, MapSelection.sheetHeight(MapSelection.SHEET_HALF, 1000, 60), "half is 52 percent");
        eq(521, MapSelection.sheetHeight(MapSelection.SHEET_HALF, 1001, 60), "and rounds");
        eq(940, MapSelection.sheetHeight(MapSelection.SHEET_FULL, 1000, 60), "full leaves 60 at the top");

        MapSelection.Layout plain = MapSelection.layout(400, 800, 0, 1f);
        near(388f, plain.centerY, "no sheet: the globe sits between 72 and H-96");
        near(176f, plain.baseRadius, "no sheet: 44 percent of the width");
        MapSelection.Layout half = MapSelection.layout(400, 800, 416, 1f);
        near(216f, half.centerY, "sheet: the globe sits between 56 and the sheet");
        near(120f, half.baseRadius, "sheet: 30 percent of the width");
        MapSelection.Layout tall = MapSelection.layout(1000, 800, 0, 1f);
        near(297.04f, tall.baseRadius, "the height limits a wide globe");
        MapSelection.Layout dense = MapSelection.layout(800, 1600, 0, 2f);
        near(776f, dense.centerY, "the constants are dp: twice the density, twice the pixels");

        // ---- what is dimmed
        MapSelection d = new MapSelection();
        near(0.7f, d.edgeAlpha("com.a", "x"), "a line, nothing chosen");
        near(1.3f, d.edgeWidthDp("com.a", "x"), "thin when nothing is chosen");
        d.selectCompany("x");
        near(1f, d.edgeAlpha("com.a", "x"), "the line to the chosen company");
        near(0.10f, d.edgeAlpha("com.a", "y"), "other lines fade");
        near(2f, d.edgeWidthDp("com.a", "x"), "the chosen company's line is thick");
        eq(false, d.companyDimmed("x", null, null), "the chosen company is not dimmed");
        eq(true, d.companyDimmed("y", null, null), "another company is");
        d.back();
        d.selectApp("com.a");
        near(0f, d.edgeAlpha("com.b", "x"), "with an app chosen only its lines are drawn");
        near(0.7f, d.edgeAlpha("com.a", "x"), "its own line");
        near(2f, d.edgeWidthDp("com.a", "x"), "thick for the chosen app");
        near(1f, d.appAlpha("com.a", null), "the chosen app");
        near(0.3f, d.appAlpha("com.b", null), "other apps are faint");
        d.back();
        d.selectRelation(1);
        near(0.10f, d.edgeAlpha("com.a", "x"), "a relation chosen: the app lines fade");
        eq(false, d.companyDimmed("p", "p", "q"), "an end of the relation");
        eq(true, d.companyDimmed("z", "p", "q"), "not an end of the relation");
        near(0.4f, d.appAlpha("com.a", null), "apps fade behind a relation");
        d.back();
        d.selectCompany("x");
        near(1f, d.appAlpha("com.a", java.util.Collections.singleton("com.a")), "an app that met the chosen company");
        near(0.3f, d.appAlpha("com.b", java.util.Collections.singleton("com.a")), "an app that did not");

        // ---- which companies and relations are drawn
        MapModel.Input in = new MapModel.Input();
        for (String id : new String[] {"c1", "c2", "c3", "c4", "c5", "c6"}) {
            in.owners.put(id, owner(id));
        }
        in.entries.add(entry("com.a", "one.example", "c1"));
        in.entries.add(entry("com.b", "two.example", "c2"));
        in.relations.add(rel("c1", "c3"));    // c3 stands behind c1
        in.relations.add(rel("c2", "c4"));
        in.relations.add(rel("c5", "c1"));    // c5 stands behind c1, with c1 as the second end
        in.relations.add(rel("c3", "c6"));    // c6 is behind c3, which is behind c1: two steps away
        MapModel.Output out = MapModel.build(in);
        eq(6, MapSelection.visibleCompanies(out, null).size(), "all six without a chosen app");
        List<String> forA = MapSelection.visibleCompanies(out, "com.a");
        eq(true, forA.contains("c1") && forA.contains("c3") && forA.contains("c5") && forA.size() == 3,
                "the app's company and those documented next to it, on either end of a relation: " + forA);
        eq(4, MapSelection.visibleRelations(out, null).size(), "all relations without a chosen app");
        List<Integer> relA = MapSelection.visibleRelations(out, "com.a");
        eq(Arrays.asList(0, 2), relA, "only relations with both ends in view; c3-c6 is two steps away");
        eq(0, MapSelection.visibleCompanies(out, "com.unknown").size(), "an app that contacted nothing");

        System.out.println("PASS MapSelectionCheck");
    }
}
