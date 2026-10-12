// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What is chosen on the map and what the sheet over it shows, with the rules of the design page
 * (docs/design/live-data-map-2026-10-11.html): choosing an app opens the sheet half way and draws only
 * that app's lines; a company, a relation or a group of companies shows over it; back closes the company
 * first and the app last; the handle switches between half and full height. Pure Java, no Android class.
 */
public final class MapSelection {
    public static final int SHEET_NONE = 0;
    public static final int SHEET_HALF = 1;
    public static final int SHEET_FULL = 2;

    public static final int CONTENT_NONE = 0;
    public static final int CONTENT_APP = 1;
    public static final int CONTENT_COMPANY = 2;
    public static final int CONTENT_RELATION = 3;
    public static final int CONTENT_GROUP = 4;

    private String app;
    private String company;
    private int relation = -1;
    private List<String> group;
    private int sheet = SHEET_NONE;

    public String app() {
        return app;
    }

    public String company() {
        return company;
    }

    public int relation() {
        return relation;
    }

    public List<String> group() {
        return group;
    }

    public int sheet() {
        return sheet;
    }

    /** What the sheet shows: a relation over a company over a group over the app. */
    public int content() {
        if (relation >= 0) {
            return CONTENT_RELATION;
        }
        if (company != null) {
            return CONTENT_COMPANY;
        }
        if (group != null) {
            return CONTENT_GROUP;
        }
        return app != null ? CONTENT_APP : CONTENT_NONE;
    }

    public void selectApp(String pkg) {
        app = pkg;
        company = null;
        relation = -1;
        group = null;
        sheet = SHEET_HALF;
    }

    public void selectCompany(String ownerId) {
        company = ownerId;
        relation = -1;
        if (sheet == SHEET_NONE) {
            sheet = SHEET_HALF;
        }
    }

    public void selectRelation(int index) {
        relation = index;
        company = null;
        group = null;
        if (sheet == SHEET_NONE) {
            sheet = SHEET_HALF;
        }
    }

    public void openGroup(List<String> ownerIds) {
        group = new ArrayList<>(ownerIds);
        company = null;
        relation = -1;
        sheet = SHEET_HALF;
    }

    /** Closes the company, relation or group first; with an app chosen its sheet stays, else all closes. */
    public void back() {
        if (company != null || relation >= 0 || group != null) {
            company = null;
            relation = -1;
            group = null;
            sheet = app != null ? SHEET_HALF : SHEET_NONE;
        } else {
            app = null;
            sheet = SHEET_NONE;
        }
    }

    public void toggleSheet() {
        if (sheet == SHEET_HALF) {
            sheet = SHEET_FULL;
        } else if (sheet == SHEET_FULL) {
            sheet = SHEET_HALF;
        }
    }

    // ------------------------------------------------------------------------------ geometry

    /** The height of the sheet in the unit of {@code height}; the full sheet leaves {@code topMargin}. */
    public static int sheetHeight(int state, int height, int topMargin) {
        if (state == SHEET_HALF) {
            return Math.round(height * 0.52f);
        }
        return state == SHEET_FULL ? height - topMargin : 0;
    }

    public static final class Layout {
        public final float centerY;
        public final float baseRadius;

        Layout(float centerY, float baseRadius) {
            this.centerY = centerY;
            this.baseRadius = baseRadius;
        }
    }

    /**
     * Where the globe sits. The numbers are dp of the design page; {@code density} turns them into
     * pixels, and width, height and sheet height are in pixels.
     */
    public static Layout layout(float width, float height, float sheetHeight, float density) {
        if (sheetHeight > 0) {
            float top = 56 * density;
            float bottom = height - sheetHeight - 8 * density;
            return new Layout((top + bottom) / 2, Math.min(width * 0.30f, (bottom - top) / 2 * 0.9f));
        }
        float top = 72 * density;
        float bottom = height - 96 * density;
        return new Layout((top + bottom) / 2, Math.min(width * 0.44f, (bottom - top) / 2 * 0.94f));
    }

    // ------------------------------------------------------------------------------ dimming

    /** The opacity of the line from an app to a company; 0 when the chosen app is another one. */
    public float edgeAlpha(String pkg, String ownerId) {
        if (app != null && !app.equals(pkg)) {
            return 0f;
        }
        if (relation >= 0) {
            return 0.10f;
        }
        if (company != null) {
            return company.equals(ownerId) ? 1f : 0.10f;
        }
        return 0.7f;
    }

    public float edgeWidthDp(String pkg, String ownerId) {
        return (company != null && company.equals(ownerId)) || app != null ? 2f : 1.3f;
    }

    /** Whether a company is drawn faint; {@code relA} and {@code relB} are the ends of the chosen relation. */
    public boolean companyDimmed(String ownerId, String relA, String relB) {
        if (company != null && !company.equals(ownerId)) {
            return true;
        }
        return relation >= 0 && !(ownerId.equals(relA) || ownerId.equals(relB));
    }

    /** {@code appsOfCompany}: the apps that met the chosen company, or null when no company is chosen. */
    public float appAlpha(String pkg, Set<String> appsOfCompany) {
        float a;
        if (app != null) {
            a = app.equals(pkg) ? 1f : 0.3f;
        } else if (appsOfCompany != null) {
            a = appsOfCompany.contains(pkg) ? 1f : 0.3f;
        } else {
            a = 1f;
        }
        return relation >= 0 ? a * 0.4f : a;
    }

    // ------------------------------------------------------------------------------ visibility

    /** The companies in view: all, or those the chosen app met and the companies documented behind them. */
    public static List<String> visibleCompanies(MapModel.Output out, String app) {
        Set<String> inModel = new LinkedHashSet<>();
        for (MapModel.CompanyRow c : out.companies) {
            inModel.add(c.owner.id);
        }
        if (app == null) {
            return new ArrayList<>(inModel);
        }
        Set<String> met = new LinkedHashSet<>();
        for (MapModel.AppRow a : out.apps) {
            if (a.pkg.equals(app)) {
                for (MapModel.Contact c : a.contacts) {
                    if (inModel.contains(c.ownerId)) {
                        met.add(c.ownerId);
                    }
                }
            }
        }
        Set<String> seen = new LinkedHashSet<>(met);
        for (MapModel.Relation r : out.relations) {
            if (met.contains(r.ownerA) && !met.contains(r.ownerB)) {
                seen.add(r.ownerB);
            }
            if (met.contains(r.ownerB) && !met.contains(r.ownerA)) {
                seen.add(r.ownerA);
            }
        }
        return new ArrayList<>(seen);
    }

    /** The indexes into {@code out.relations} of the relations with both ends in view. */
    public static List<Integer> visibleRelations(MapModel.Output out, String app) {
        Set<String> in = new HashSet<>(visibleCompanies(out, app));
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i < out.relations.size(); i++) {
            MapModel.Relation rel = out.relations.get(i);
            if (in.contains(rel.ownerA) && in.contains(rel.ownerB)) {
                r.add(i);
            }
        }
        return Collections.unmodifiableList(r);
    }
}
