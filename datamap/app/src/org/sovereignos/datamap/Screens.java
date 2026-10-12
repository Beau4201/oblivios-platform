// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.sovereignos.datamap.model.MapModel;
import org.sovereignos.datamap.model.RelationRole;

import java.util.Map;

/**
 * The pages and detail screens as views. The wording rule binds every string here: two apps "could be
 * linked", the evidence is named, and a connection that is not listed is not shown as proof of none.
 */
final class Screens {
    interface Nav {
        void openApp(String pkg);

        void openCompany(String ownerId);

        /** Leaves the screen of its own and shows the app on the map, with its sheet. */
        void showAppOnMap(String pkg);

        void showCompanyOnMap(String ownerId);
    }

    private final Ui mUi;
    private final Apps mApps;
    private final Nav mNav;
    private MapModel.Output mModel = new MapModel.Output();
    private Map<String, MapModel.Owner> mOwners = new java.util.HashMap<>();
    private long mNow = System.currentTimeMillis();

    Screens(Ui ui, Apps apps, Nav nav) {
        mUi = ui;
        mApps = apps;
        mNav = nav;
    }

    void set(MapModel.Output model, Map<String, MapModel.Owner> owners, long nowWallMs) {
        mModel = model;
        mOwners = owners;
        mNow = nowWallMs;
    }

    private String category(int code) {
        String[] names = mUi.ctx.getResources().getStringArray(R.array.categories);
        return code >= 0 && code < names.length ? names[code] : names[0];
    }

    private String ago(long wallMs) {
        return DateUtils.getRelativeTimeSpanString(wallMs, mNow, DateUtils.MINUTE_IN_MILLIS).toString();
    }

    private String ownerName(String id) {
        MapModel.Owner o = mOwners.get(id);
        return o == null ? id : o.name;
    }

    private String relation(String type) {
        switch (type) {
            case "owns":
                return mUi.ctx.getString(R.string.rel_owns);
            case "merged_with":
                return mUi.ctx.getString(R.string.rel_merged_with);
            case "partner_of":
                return mUi.ctx.getString(R.string.rel_partner_of);
            case "buys_ad_space_from":
                return mUi.ctx.getString(R.string.rel_buys_ad_space_from);
            default:
                return mUi.ctx.getString(R.string.rel_connected);
        }
    }

    private String countryName(String code) {
        return code == null || code.isEmpty() ? "" : java.util.Locale.of("", code).getDisplayCountry();
    }

    /** The head of a detail: a tile, a name and a line under it. Larger on a screen of its own. */
    private View head(View tile, String name, CharSequence line, int topDp) {
        boolean full = topDp >= 48;
        LinearLayout row = new LinearLayout(mUi.ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(mUi.dp(24), mUi.dp(topDp), mUi.dp(24), 0);
        if (tile != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tile.getLayoutParams());
            lp.setMarginEnd(mUi.dp(16));
            tile.setLayoutParams(lp);
            row.addView(tile);
        }
        LinearLayout tx = new LinearLayout(mUi.ctx);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(mUi.text(name, full ? 32 : 24, false, R.color.dm_on));
        if (line != null && line.length() > 0) {
            tx.addView(mUi.text(line, 14, false, R.color.dm_onv));
        }
        row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** Three figures side by side, each with a label under it. */
    private View trio(String[] figures, String[] labels) {
        LinearLayout row = new LinearLayout(mUi.ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(mUi.dp(24), mUi.dp(18), mUi.dp(24), 0);
        row.setLayoutParams(rp);
        for (int i = 0; i < 3; i++) {
            LinearLayout col = new LinearLayout(mUi.ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(i == 0 ? 0 : mUi.dp(16), 0, 0, 0);
            if (i > 0) {
                col.setBackground(null);
            }
            col.addView(mUi.text(figures[i], 28, false, R.color.dm_on));
            col.addView(mUi.text(labels[i], 12, false, R.color.dm_onv));
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private LinearLayout page() {
        LinearLayout l = new LinearLayout(mUi.ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** One line of counts at the top of a list: "4 apps · 1 company · 1 country". */
    private View counts(int apps, int companies, int countries) {
        android.content.res.Resources r = mUi.ctx.getResources();
        String s = mUi.ctx.getString(R.string.summary_counts,
                r.getQuantityString(R.plurals.n_apps, apps, apps),
                r.getQuantityString(R.plurals.n_companies, companies, companies),
                r.getQuantityString(R.plurals.n_countries, countries, countries));
        TextView t = mUi.text(s, 14, false, R.color.dm_onv);
        t.setPadding(mUi.dp(24), mUi.dp(8), mUi.dp(24), mUi.dp(8));
        return t;
    }

    // ------------------------------------------------------------------------------------ Apps

    View appsPage() {
        LinearLayout p = page();
        if (mModel.apps.isEmpty()) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.empty_nothing)));
        } else {
            java.util.Set<String> met = new java.util.HashSet<>();
            java.util.Set<String> countries = new java.util.HashSet<>();
            for (MapModel.CompanyRow c : mModel.companies) {
                if (c.contacted) {
                    met.add(c.owner.id);
                    countries.add(c.owner.countryCode);
                }
            }
            p.addView(counts(mModel.apps.size(), met.size(), countries.size()));
            java.util.List<MapModel.AppRow> byCompanies = new java.util.ArrayList<>(mModel.apps);
            java.util.Collections.sort(byCompanies, (x, y) -> y.companies - x.companies);
            Ui.Group g = new Ui.Group(mUi);
            for (MapModel.AppRow a : byCompanies) {
                StringBuilder s = new StringBuilder(mUi.ctx.getResources().getQuantityString(
                        R.plurals.n_companies, a.companies, a.companies));
                s.append(" · ").append(ago(a.lastWall));
                final String pkg = a.pkg;
                g.add(mUi.row(mUi.appTile(mApps, pkg, 40), mApps.label(pkg), s.toString(),
                        a.touchesTracker ? mUi.ctx.getString(R.string.tracker_short) : "",
                        v -> mNav.openApp(pkg)));
            }
            p.addView(g.seal().view);
        }
        return p;
    }

    /** The app on a screen of its own. */
    View appDetail(String pkg) {
        LinearLayout p = page();
        p.addView(appBody(pkg, 64));
        p.addView(textButton(R.string.show_on_map, v -> mNav.showAppOnMap(pkg)));
        return p;
    }

    /** The app, for the sheet over the map ({@code topDp} small) or for a screen of its own. */
    View appBody(String pkg, int topDp) {
        LinearLayout p = page();
        MapModel.AppRow a = null;
        for (MapModel.AppRow row : mModel.apps) {
            if (row.pkg.equals(pkg)) {
                a = row;
            }
        }
        String publisher = a == null || !a.onMap ? "" : ownerName(a.publisherId);
        MapModel.Owner po = a == null || a.publisherId == null ? null : mOwners.get(a.publisherId);
        String line = po == null ? "" : mUi.ctx.getString(R.string.app_publisher_line, publisher, po.city);
        p.addView(head(mUi.appTile(mApps, pkg, 56), mApps.label(pkg), line, topDp));
        if (a == null) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.empty_app)));
            return p;
        }
        java.util.Set<String> countries = new java.util.LinkedHashSet<>();
        for (MapModel.Contact c : a.contacts) {
            MapModel.Owner o = c.ownerId.isEmpty() ? null : mOwners.get(c.ownerId);
            if (o != null) {
                countries.add(o.countryCode);
            }
        }
        int total = Math.max(1, a.foreground + a.background);
        p.addView(trio(new String[] {String.valueOf(a.companies), String.valueOf(countries.size()),
                (a.background * 100 / total) + "%"},
                new String[] {mUi.ctx.getResources().getQuantityString(R.plurals.n_companies_label, a.companies),
                        mUi.ctx.getResources().getQuantityString(R.plurals.n_countries_label, countries.size()),
                        mUi.ctx.getString(R.string.in_background)}));
        p.addView(mUi.label(mUi.ctx.getString(R.string.contacted)));
        Ui.Group g = new Ui.Group(mUi);
        for (MapModel.Contact c : a.contacts) {
            final boolean known = !c.ownerId.isEmpty() && mOwners.containsKey(c.ownerId);
            String owner = known ? ownerName(c.ownerId) : mUi.ctx.getString(R.string.owner_unknown);
            String summary = category(c.category) + " · " + owner + " · "
                    + mUi.ctx.getString(R.string.contact_counts, c.count, c.foreground, c.background) + " · "
                    + ago(c.lastWall);
            final String id = c.ownerId;
            g.add(mUi.row(known ? mUi.companyTile(c.ownerId, owner, 40) : mUi.initialsTile("?", true, 40),
                    c.serviceKey.startsWith("ip:") ? mUi.ctx.getString(R.string.unknown_service) : c.serviceKey,
                    summary, "", known ? v -> mNav.openCompany(id) : null));
        }
        p.addView(g.seal().view);
        // Apps that could be linked to this one, with the evidence named (the wording rule).
        Ui.Group linked = new Ui.Group(mUi);
        for (MapModel.Link l : mModel.links) {
            final String other = l.pkgA.equals(pkg) ? l.pkgB : l.pkgB.equals(pkg) ? l.pkgA : null;
            if (other == null) {
                continue;
            }
            StringBuilder ev = new StringBuilder();
            for (String e : l.evidence) {
                if (ev.length() > 0) {
                    ev.append(", ");
                }
                ev.append(l.kind == 2 ? ownerName(e) : e);
            }
            linked.add(mUi.row(mUi.appTile(mApps, other, 40), mApps.label(other),
                    mUi.ctx.getString(l.kind == 2 ? R.string.link_same_owner : R.string.link_same_service,
                            ev.toString()), "", v -> mNav.openApp(other)));
        }
        if (linked.view.getChildCount() > 0) {
            p.addView(mUi.label(mUi.ctx.getString(R.string.could_be_linked_to)));
            p.addView(linked.seal().view);
        }
        return p;
    }

    // -------------------------------------------------------------------------------- Companies

    View companiesPage() {
        LinearLayout p = page();
        if (mModel.companies.isEmpty() && mModel.unplacedServices.isEmpty()) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.empty_nothing)));
            return p;
        }
        java.util.List<MapModel.CompanyRow> several = new java.util.ArrayList<>();
        java.util.List<MapModel.CompanyRow> one = new java.util.ArrayList<>();
        java.util.List<MapModel.CompanyRow> behind = new java.util.ArrayList<>();
        java.util.Set<String> countries = new java.util.HashSet<>();
        for (MapModel.CompanyRow c : mModel.companies) {
            if (!c.contacted) {
                behind.add(c);
            } else if (c.apps.size() > 1) {
                several.add(c);
                countries.add(c.owner.countryCode);
            } else {
                one.add(c);
                countries.add(c.owner.countryCode);
            }
        }
        p.addView(counts(mModel.apps.size(), several.size() + one.size(), countries.size()));
        companyGroup(p, R.string.met_by_several, several);
        companyGroup(p, R.string.met_by_one, one);
        companyGroup(p, R.string.standing_behind, behind);
        if (!mModel.unplacedServices.isEmpty()) {
            p.addView(mUi.label(mUi.ctx.getString(R.string.not_on_map)));
            Ui.Group u = new Ui.Group(mUi);
            for (MapModel.UnplacedService s : mModel.unplacedServices) {
                u.add(mUi.row(mUi.initialsTile("?", true, 40),
                        s.serviceKey.startsWith("ip:") ? mUi.ctx.getString(R.string.unknown_service) : s.serviceKey,
                        mUi.ctx.getResources().getQuantityString(R.plurals.n_apps, s.apps.size(), s.apps.size()),
                        "", null));
            }
            p.addView(u.seal().view);
        }
        return p;
    }

    private void companyGroup(LinearLayout page, int labelRes, java.util.List<MapModel.CompanyRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        page.addView(mUi.label(mUi.ctx.getString(labelRes)));
        Ui.Group g = new Ui.Group(mUi);
        for (MapModel.CompanyRow c : rows) {
            final String id = c.owner.id;
            String where = c.owner.city + ", " + c.owner.countryCode;
            String end = c.contacted
                    ? mUi.ctx.getResources().getQuantityString(R.plurals.n_apps, c.apps.size(), c.apps.size())
                    : mUi.ctx.getString(R.string.behind);
            g.add(mUi.row(mUi.companyTile(id, c.owner.name, 40), c.owner.name, where, end, v -> mNav.openCompany(id)));
        }
        page.addView(g.seal().view);
    }

    /** The company on a screen of its own. */
    View companyDetail(String ownerId) {
        LinearLayout p = page();
        p.addView(companyBody(ownerId, 64));
        p.addView(textButton(R.string.show_on_map, v -> mNav.showCompanyOnMap(ownerId)));
        return p;
    }

    private View textButton(int stringRes, View.OnClickListener onClick) {
        TextView t = mUi.text(mUi.ctx.getString(stringRes), 14, true, R.color.dm_pri);
        t.setPadding(mUi.dp(24), mUi.dp(18), mUi.dp(24), mUi.dp(18));
        t.setOnClickListener(onClick);
        return t;
    }

    View companyBody(String ownerId, int topDp) {
        LinearLayout p = page();
        MapModel.Owner o = mOwners.get(ownerId);
        MapModel.CompanyRow row = null;
        for (MapModel.CompanyRow c : mModel.companies) {
            if (c.owner.id.equals(ownerId)) {
                row = c;
            }
        }
        if (o == null) {
            p.addView(head(mUi.companyTile(ownerId, ownerId, 56), ownerId, "", topDp));
            return p;
        }
        String legal = !o.legalName.isEmpty() && !o.legalName.equals(o.name) ? o.legalName : "";
        p.addView(head(mUi.companyTile(o.id, o.name, 56), o.name, legal, topDp));
        LinearLayout meta = new LinearLayout(mUi.ctx);
        meta.setGravity(android.view.Gravity.CENTER_VERTICAL);
        meta.setPadding(mUi.dp(24), mUi.dp(10), mUi.dp(24), 0);
        ImageView flag = mUi.flag(o.countryCode);
        if (flag != null) {
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(mUi.dp(20), mUi.dp(15));
            fp.setMarginEnd(mUi.dp(8));
            meta.addView(flag, fp);
        }
        String kind = row != null && row.contacted ? " · " + category(row.category) : "";
        meta.addView(mUi.text(mUi.ctx.getString(R.string.company_based, o.city, countryName(o.countryCode)) + kind,
                14, false, R.color.dm_onv));
        p.addView(meta);
        if (row == null || !row.contacted) {
            p.addView(mUi.label(mUi.ctx.getString(R.string.not_contacted_directly)));
        } else {
            int calls = 0;
            for (MapModel.AppRow a : mModel.apps) {
                for (MapModel.Contact c : a.contacts) {
                    if (c.ownerId.equals(ownerId)) {
                        calls += c.count;
                    }
                }
            }
            p.addView(trio(new String[] {String.valueOf(row.apps.size()), String.valueOf(row.services.size()),
                    String.valueOf(calls)},
                    new String[] {mUi.ctx.getResources().getQuantityString(R.plurals.n_apps_label, row.apps.size()),
                            mUi.ctx.getResources().getQuantityString(R.plurals.n_services_label, row.services.size()),
                            mUi.ctx.getString(R.string.calls)}));
            p.addView(mUi.label(mUi.ctx.getString(R.string.apps_that_meet_it)));
            Ui.Group g = new Ui.Group(mUi);
            for (final String pkg : row.apps) {
                g.add(mUi.row(mUi.appTile(mApps, pkg, 40), mApps.label(pkg), "", "", v -> mNav.openApp(pkg)));
            }
            p.addView(g.seal().view);
        }
        if (row != null && !row.connectedTo.isEmpty()) {
            p.addView(mUi.label(mUi.ctx.getString(R.string.connected_companies)));
            Ui.Group g = new Ui.Group(mUi);
            for (MapModel.Relation r : mModel.relations) {
                final String id = RelationRole.other(ownerId, r);
                if (id == null) {
                    continue;
                }
                // The words are said of the other company: it "owns this company" or it "is owned by" it.
                String[] words = mUi.ctx.getResources().getStringArray(
                        RelationRole.of(ownerId, r) == RelationRole.SUBJECT ? R.array.rel_as_subject
                                : R.array.rel_as_object);
                String what = words[RelationRole.typeIndex(r.type)];
                String note = r.note.isEmpty() ? "" : " · " + r.note;
                g.add(mUi.row(mUi.companyTile(id, ownerName(id), 40), ownerName(id), what + note,
                        "", v -> mNav.openCompany(id)));
            }
            p.addView(g.seal().view);
        }
        if (row != null && row.contacted && row.apps.size() > 1 && MapModel.isTracker(row.category)) {
            p.addView(mUi.label(mUi.ctx.getString(R.string.could_be_linked_through)));
            StringBuilder names = new StringBuilder();
            for (String pkg : row.apps) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(mApps.label(pkg));
            }
            p.addView(mUi.para(mUi.ctx.getString(row.apps.size() == 2 ? R.string.linked_through_two
                    : R.string.linked_through_many, names.toString(), o.name)));
        }
        if (row != null && !row.services.isEmpty()) {
            p.addView(mUi.label(mUi.ctx.getString(R.string.services)));
            Ui.Group g = new Ui.Group(mUi);
            for (String sv : row.services) {
                g.add(mUi.row(null, sv, "", "", null));
            }
            p.addView(g.seal().view);
        }
        return p;
    }

    // -------------------------------------------------------------------- relation and group

    /** One documented connection between two companies, as the sheet over the map shows it. */
    View relationBody(int index, int topDp) {
        LinearLayout p = page();
        if (index < 0 || index >= mModel.relations.size()) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.no_relations)));
            return p;
        }
        MapModel.Relation r = mModel.relations.get(index);
        LinearLayout tiles = new LinearLayout(mUi.ctx);
        tiles.setPadding(mUi.dp(24), mUi.dp(topDp), mUi.dp(24), 0);
        for (String id : new String[] {r.ownerA, r.ownerB}) {
            View t = mUi.companyTile(id, ownerName(id), 56);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(t.getLayoutParams());
            tp.setMarginEnd(mUi.dp(8));
            t.setLayoutParams(tp);
            tiles.addView(t);
        }
        p.addView(tiles);
        TextView name = mUi.text(ownerName(r.ownerA) + " " + relation(r.type) + " " + ownerName(r.ownerB),
                22, false, R.color.dm_on);
        name.setPadding(mUi.dp(24), mUi.dp(12), mUi.dp(24), 0);
        p.addView(name);
        p.addView(mUi.para((r.note.isEmpty() ? "" : r.note + " · ")
                + mUi.ctx.getString(R.string.source_id, r.sourceId)));
        Ui.Group g = new Ui.Group(mUi);
        for (final String id : new String[] {r.ownerA, r.ownerB}) {
            MapModel.Owner o = mOwners.get(id);
            g.add(mUi.row(mUi.companyTile(id, ownerName(id), 40), ownerName(id),
                    o == null ? "" : o.city + ", " + o.countryCode, "", v -> mNav.openCompany(id)));
        }
        p.addView(mUi.space(16));
        p.addView(g.seal().view);
        return p;
    }

    /** Companies based close together, which the map shows as one number. */
    View groupBody(java.util.List<String> ownerIds, int topDp) {
        LinearLayout p = page();
        TextView name = mUi.text(mUi.ctx.getResources().getQuantityString(R.plurals.n_companies,
                ownerIds.size(), ownerIds.size()), 24, false, R.color.dm_on);
        name.setPadding(mUi.dp(24), mUi.dp(topDp), mUi.dp(24), mUi.dp(4));
        p.addView(name);
        Ui.Group g = new Ui.Group(mUi);
        for (final String id : ownerIds) {
            String summary = "";
            for (MapModel.CompanyRow c : mModel.companies) {
                if (c.owner.id.equals(id)) {
                    summary = c.contacted ? category(c.category) : mUi.ctx.getString(R.string.not_contacted_directly);
                }
            }
            g.add(mUi.row(mUi.companyTile(id, ownerName(id), 40), ownerName(id), summary, "", v -> mNav.openCompany(id)));
        }
        p.addView(g.seal().view);
        return p;
    }

    // ----------------------------------------------------------------------------------- Links

    View linksPage() {
        LinearLayout p = page();
        p.addView(mUi.label(mUi.ctx.getString(R.string.connected_companies)));
        if (mModel.relations.isEmpty()) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.no_relations)));
        } else {
            Ui.Group g = new Ui.Group(mUi);
            for (MapModel.Relation r : mModel.relations) {
                final String a = r.ownerA;
                String note = r.note.isEmpty() ? "" : " · " + r.note;
                g.add(mUi.row(mUi.companyTile(a, ownerName(a), 40),
                        ownerName(a) + " " + relation(r.type) + " " + ownerName(r.ownerB),
                        mUi.ctx.getString(R.string.source_id, r.sourceId) + note, "", v -> mNav.openCompany(a)));
            }
            p.addView(g.seal().view);
        }
        p.addView(mUi.label(mUi.ctx.getString(R.string.could_be_linked)));
        if (mModel.links.isEmpty()) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.no_links)));
        } else {
            Ui.Group g = new Ui.Group(mUi);
            for (MapModel.Link l : mModel.links) {
                final String a = l.pkgA;
                StringBuilder ev = new StringBuilder();
                for (String e : l.evidence) {
                    if (ev.length() > 0) {
                        ev.append(", ");
                    }
                    ev.append(l.kind == 2 ? ownerName(e) : e);
                }
                String why = mUi.ctx.getString(
                        l.kind == 2 ? R.string.link_same_owner : R.string.link_same_service, ev.toString());
                if (l.kind != 2 && l.stronger) {
                    why += " " + mUi.ctx.getString(R.string.link_stronger);
                }
                g.add(mUi.row(mUi.appTile(mApps, a, 40), mApps.label(a) + " + " + mApps.label(l.pkgB), why, "",
                        v -> mNav.openApp(a)));
            }
            p.addView(g.seal().view);
        }
        p.addView(mUi.note(mUi.ctx.getString(R.string.not_listed_not_proof)));
        return p;
    }
}
