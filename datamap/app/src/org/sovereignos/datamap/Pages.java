// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.sovereignos.datamap.model.AtlasData;
import org.sovereignos.datamap.model.AtlasModel;
import org.sovereignos.datamap.model.CompanyList;
import org.sovereignos.datamap.model.CompanyType;
import org.sovereignos.datamap.model.MapModel;
import org.sovereignos.datamap.model.PlaceLinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The pages of the design page (ADR-160) that are lists: Apps, Companies, one company and one app, and the panel of the
 * Companies filter. Each is a column of views for the activity to put in a scroll view.
 */
final class Pages {
    /** What a touch on a row, a button or the note does; the activity decides where it leads. */
    interface Nav {
        void openCompany(String ownerId);

        void openApp(String pkg);

        void showAppOnMap(String pkg);

        void showCompanyOnMap(String ownerId);

        void showLimits();

        void turnOnCollection();

        void openFilter();

        void filterChanged();

        /** The choice on the map changed: the system's back key may now have something to close. */
        void stateChanged();
    }

    /** The type and the order of the Companies list. */
    static final class Filter {
        int type = CompanyList.ALL_TYPES;
        int sort = CompanyList.APPS;
    }

    private final Widgets w;
    private final Ds ds;
    private final Context ctx;
    private final Nav nav;
    private final Filter filter;
    private AtlasData data = AtlasData.empty();
    private GlobeView.Prepared land;

    Pages(Widgets widgets, Nav nav, Filter filter) {
        this.w = widgets;
        this.ds = widgets.ds;
        this.ctx = widgets.ds.ctx;
        this.nav = nav;
        this.filter = filter;
    }

    void setData(AtlasData d, GlobeView.Prepared prepared) {
        data = d;
        land = prepared;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private View limitsNote() {
        return w.note(v -> nav.showLimits());
    }

    /** The empty state of a list: nothing collected yet, or collection is off (with the way to switch it on). */
    private View empty(int nothingText, int offText) {
        if (!data.available) {
            return w.emptyState(R.drawable.ic_eye, ctx.getString(R.string.service_missing_title),
                    ctx.getString(R.string.service_missing), null, null);
        }
        if (!data.collecting) {
            return w.emptyState(R.drawable.ic_eye, ctx.getString(R.string.off_title), ctx.getString(offText), null, null);
        }
        return w.emptyState(R.drawable.ic_eye, ctx.getString(R.string.empty_nothing_title), ctx.getString(nothingText),
                null, null);
    }

    private boolean emptyList() {
        return !data.available || !data.collecting || data.nothingCollected();
    }

    // ================================================================================== Apps

    View apps() {
        LinearLayout col = column();
        col.addView(w.title(ctx.getString(R.string.your_apps), null));
        if (emptyList()) {
            col.addView(empty(R.string.empty_apps, R.string.off_apps));
            return col;
        }
        Widgets.Card with = w.card();
        for (AtlasModel.App a : data.atlas.withCompanies) {
            final String pkg = a.pkg;
            with.add(w.appRow(pkg, Texts.connections(ctx, a.connections), v -> nav.openApp(pkg)));
        }
        col.addView(with);
        if (!data.atlas.without.isEmpty()) {
            col.addView(w.h3(ctx.getString(R.string.no_company_contacted)));
            Widgets.Card none = w.card();
            for (AtlasModel.App a : data.atlas.without) {
                View ok = w.icon(R.drawable.ic_ok, 22, R.color.at_onv);
                none.add(w.row(w.appTile(a.pkg, 32), w.apps.label(a.pkg),
                        new Object[] {ok, ctx.getString(R.string.no_company_seen)}, null, false, null));
            }
            col.addView(none);
        }
        col.addView(limitsNote());
        return col;
    }

    // ============================================================================= Companies

    private View filterButton() {
        final boolean active = CompanyList.active(filter.type, filter.sort);
        android.widget.FrameLayout b = new android.widget.FrameLayout(ctx) {
            @Override
            protected void dispatchDraw(android.graphics.Canvas c) {
                super.dispatchDraw(c);
                if (active) {
                    android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    float cx = getWidth() - ds.px(9) - ds.px(4.5f);
                    float cy = ds.px(9) + ds.px(4.5f);
                    p.setColor(ds.color(R.color.at_pill));
                    c.drawCircle(cx, cy, ds.px(6.5f), p);
                    p.setColor(ds.color(R.color.at_acc));
                    c.drawCircle(cx, cy, ds.px(4.5f), p);
                }
            }
        };
        b.setId(R.id.at_filter_button);
        b.setBackground(ds.box(R.color.at_pill, 14, R.color.at_pillbd));
        b.addView(w.icon(R.drawable.ic_tune, 24, R.color.at_on), new android.widget.FrameLayout.LayoutParams(ds.dp(24),
                ds.dp(24), Gravity.CENTER));
        b.setLayoutParams(new LinearLayout.LayoutParams(ds.dp(44), ds.dp(44)));
        b.setClickable(true);
        b.setContentDescription(ctx.getString(R.string.filter_and_sort));
        b.setOnClickListener(v -> nav.openFilter());
        return b;
    }

    private String sortName(int sort) {
        return ctx.getString(sort == CompanyList.NAME ? R.string.sort_name
                : sort == CompanyList.COUNTRY ? R.string.sort_country : R.string.sort_apps);
    }

    View companies() {
        LinearLayout col = column();
        boolean off = emptyList();
        col.addView(w.title(ctx.getString(R.string.tab_companies), off ? null : filterButton()));
        if (off) {
            col.addView(empty(R.string.empty_companies, R.string.off_companies));
            return col;
        }
        if (CompanyList.active(filter.type, filter.sort)) {
            List<String> parts = new ArrayList<>();
            if (filter.type != CompanyList.ALL_TYPES) {
                parts.add(Texts.typeName(ctx, filter.type));
            }
            if (filter.sort != CompanyList.APPS) {
                parts.add(sortName(filter.sort));
            }
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = ds.text(Texts.names(parts).replace(", ", " · "), 13, Ds.REGULAR, R.color.at_onv, 18);
            line.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView clear = w.textButton(ctx.getString(R.string.filter_clear), false, v -> {
                filter.type = CompanyList.ALL_TYPES;
                filter.sort = CompanyList.APPS;
                nav.filterChanged();
            });
            clear.setPadding(ds.dp(8), ds.dp(4), ds.dp(8), ds.dp(4));
            LinearLayout.LayoutParams cp = w.wrap();
            cp.setMarginStart(ds.dp(8));
            line.addView(clear, cp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = -ds.dp(6);
            lp.bottomMargin = ds.dp(14);
            lp.setMarginStart(ds.dp(2));
            lp.setMarginEnd(ds.dp(2));
            col.addView(line, lp);
        }
        List<MapModel.CompanyRow> rows = CompanyList.of(data.out.companies, filter.type, filter.sort,
                cc -> Texts.country(cc), Locale.getDefault());
        Widgets.Card card = w.card();
        for (final MapModel.CompanyRow co : rows) {
            List<String> pkgs = new ArrayList<>(co.apps);
            LinearLayout e = new LinearLayout(ctx);
            e.setOrientation(LinearLayout.HORIZONTAL);
            e.setGravity(Gravity.CENTER_VERTICAL);
            e.addView(w.stack(pkgs));
            e.addView(w.chevron(), new LinearLayout.LayoutParams(ds.dp(22), ds.dp(22)));
            // a company with no known country has the type alone, not "Analytics ·"
            Object[] second = Texts.country(co.owner.countryCode).isEmpty()
                    ? new Object[] {Texts.mainType(ctx, co)}
                    : new Object[] {Texts.mainType(ctx, co) + " ·", w.flag(co.owner.countryCode, 14),
                        Texts.country(co.owner.countryCode)};
            card.add(w.row(w.companyTile(co, 38), co.owner.name, second, e, true,
                    v -> nav.openCompany(co.owner.id)));
        }
        if (rows.isEmpty()) {
            card.add(w.row(null, ctx.getString(R.string.no_company_matches),
                    new Object[] {ctx.getString(R.string.remove_filter)}, null, false, null));
        }
        col.addView(card);
        if (!data.out.serverRows.isEmpty()) {
            col.addView(w.h3(ctx.getString(R.string.hosted_in_title)));
            Widgets.Card hosted = w.card();
            for (MapModel.CompanyRow r : data.out.serverRows) {
                hosted.add(w.row(null, ctx.getString(R.string.hosted_in, Texts.serverName(r) + (r.owner.city.isEmpty() ? ""
                        : ", " + Texts.country(r.owner.countryCode))), new Object[] {Texts.serverSub(ctx, r)},
                        w.stack(new ArrayList<>(r.apps)), false, null));
            }
            col.addView(hosted);
        }
        col.addView(w.h3(ctx.getString(R.string.not_on_the_map)));
        Widgets.Card off2 = w.card();
        for (AtlasModel.Unplaced u : data.atlas.unplaced) {
            String sub;
            String server = Texts.serverLine(ctx, u);
            if (server != null) {
                sub = server;
            } else if (u.key.startsWith("ip:") || !u.viaDns) {
                sub = ctx.getString(R.string.address_no_lookup);
            } else if (u.category == MapModel.CAT_UNKNOWN) {
                sub = ctx.getString(R.string.not_in_list);
            } else {
                sub = ctx.getString(R.string.owner_unknown_line, Texts.typeName(ctx, CompanyType.of(u.category)));
            }
            off2.add(w.row(null, u.address(), new Object[] {sub}, w.stack(new ArrayList<>(u.apps)), false, null));
        }
        if (data.atlas.unplaced.isEmpty()) {
            off2.add(w.row(null, ctx.getString(R.string.unplaced_none), null, null, false, null));
        }
        col.addView(off2);
        col.addView(limitsNote());
        return col;
    }

    /** The panel of the filter: the head, the types with their counts and the orders, a check mark on each choice. */
    View[] filterPanel(final Runnable close) {
        List<View> parts = new ArrayList<>();
        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = ds.text(ctx.getString(R.string.filter_and_sort), 22, Ds.MEDIUM, R.color.at_on, 28);
        head.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (CompanyList.active(filter.type, filter.sort)) {
            head.addView(w.textButton(ctx.getString(R.string.filter_reset), false, v -> {
                filter.type = CompanyList.ALL_TYPES;
                filter.sort = CompanyList.APPS;
                nav.filterChanged();
            }), w.wrap());
        }
        head.addView(w.iconButton(R.drawable.ic_close, ctx.getString(R.string.close), v -> close.run()),
                new LinearLayout.LayoutParams(ds.dp(40), ds.dp(40)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = ds.dp(4);
        hp.bottomMargin = ds.dp(8);
        head.setLayoutParams(hp);
        parts.add(head);
        parts.add(w.h3(ctx.getString(R.string.filter_type)));
        int[] counts = CompanyList.counts(data.out.companies);
        int total = 0;
        for (int n : counts) {
            total += n;
        }
        Widgets.Card types = w.card();
        types.add(option(ctx.getString(R.string.filter_all_types), Integer.toString(total),
                filter.type == CompanyList.ALL_TYPES, () -> filter.type = CompanyList.ALL_TYPES));
        for (int t = 0; t < CompanyType.COUNT; t++) {
            if (counts[t] == 0) {
                continue;
            }
            final int type = t;
            types.add(option(Texts.typeName(ctx, t), Integer.toString(counts[t]), filter.type == t,
                    () -> filter.type = type));
        }
        parts.add(types);
        parts.add(w.h3(ctx.getString(R.string.filter_sort)));
        Widgets.Card sorts = w.card();
        for (final int s : new int[] {CompanyList.APPS, CompanyList.NAME, CompanyList.COUNTRY}) {
            sorts.add(option(sortName(s), null, filter.sort == s, () -> filter.sort = s));
        }
        parts.add(sorts);
        return parts.toArray(new View[0]);
    }

    private View option(CharSequence label, String count, boolean on, final Runnable choose) {
        LinearLayout e = new LinearLayout(ctx);
        e.setOrientation(LinearLayout.HORIZONTAL);
        e.setGravity(Gravity.CENTER_VERTICAL);
        if (count != null) {
            e.addView(ds.text(count, 13, Ds.REGULAR, R.color.at_onv, 18), w.wrap());
        }
        View ok = on ? w.icon(R.drawable.ic_ok, 22, R.color.at_acc) : new View(ctx);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ds.dp(22), ds.dp(22));
        op.setMarginStart(ds.dp(10));
        e.addView(ok, op);
        return w.row(null, label, null, e, false, v -> {
            choose.run();
            nav.filterChanged();
        });
    }

    // ================================================================================ a company

    View company(final String ownerId) {
        LinearLayout col = column();
        final MapModel.CompanyRow co = data.byOwner.get(ownerId);
        if (co == null) {
            return col;
        }
        String country = Texts.country(co.owner.countryCode);
        col.addView(w.identity(w.companyTile(co, 72), co.owner.name,
                new Object[] {w.icon(R.drawable.ic_bizo, 24, R.color.at_onv), Texts.types(ctx, co)}, country));
        col.addView(w.facts(new String[] {ctx.getString(R.string.fact_country), ctx.getString(R.string.fact_type)},
                new String[] {country, Texts.mainType(ctx, co)}));
        View flat = new FlatMapView(ctx, ds, land == null ? null : land.full, co.owner.lat, co.owner.lon, country);
        flat.setContentDescription(ctx.getString(R.string.show_company_on_map, co.owner.name));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ds.dp(132));
        fp.topMargin = ds.dp(16);
        if (!co.apps.isEmpty()) {
            col.addView(w.h3(ctx.getString(R.string.contacted_by, Texts.apps(ctx, co.apps.size()))));
            Widgets.Card apps = w.card();
            for (final String pkg : co.apps) {
                apps.add(w.appRow(pkg, Texts.connections(ctx, data.atlas.connections(pkg, ownerId)),
                        v -> nav.openApp(pkg)));
            }
            col.addView(apps);
            flat.setClickable(true);
            flat.setOnClickListener(v -> nav.showCompanyOnMap(ownerId));
            col.addView(flat, fp);
        } else {
            col.addView(flat, fp);
            col.addView(w.info(ctx.getString(R.string.behind_only), R.drawable.ic_info, R.color.at_onv, null));
        }
        col.addView(limitsNote());
        List<MapModel.CompanyRow> self = Collections.singletonList(co);
        List<PlaceLinks.Joined> joined = PlaceLinks.joined(self, data.atlas.contacted, data.relations());
        if (!joined.isEmpty()) {
            col.addView(w.h3(ctx.getString(R.string.connected_to)));
            Widgets.Card card = w.card();
            for (final PlaceLinks.Joined j : joined) {
                card.add(w.row(w.companyTile(j.other, 32), j.other.owner.name,
                        new Object[] {Texts.via(ctx, w.apps, j, self)}, w.chevron(), false,
                        v -> nav.openCompany(j.other.owner.id)));
            }
            col.addView(card);
        }
        List<MapModel.Relation> rels = new ArrayList<>();
        for (MapModel.Relation r : data.relations()) {
            if (r.ownerA.equals(ownerId) || r.ownerB.equals(ownerId)) {
                rels.add(r);
            }
        }
        if (!rels.isEmpty()) {
            col.addView(w.h3(ctx.getString(R.string.documented_connections)));
            Widgets.Card card = w.card();
            for (MapModel.Relation r : rels) {
                final MapModel.CompanyRow other = data.byOwner.get(r.ownerA.equals(ownerId) ? r.ownerB : r.ownerA);
                MapModel.CompanyRow a = data.byOwner.get(r.ownerA);
                MapModel.CompanyRow b = data.byOwner.get(r.ownerB);
                if (other == null || a == null || b == null) {
                    continue;
                }
                Widgets.Row row = w.row(w.companyTile(other, 32), Texts.sentence(ctx, r, a, b),
                        new Object[] {ctx.getString(R.string.source_id, r.sourceId)}, w.chevron(), false,
                        v -> nav.openCompany(other.owner.id));
                row.titleView.setSingleLine(false);
                row.titleView.setMaxLines(4);
                card.add(row);
            }
            col.addView(card);
        }
        if (!co.services.isEmpty()) {
            col.addView(w.h3(ctx.getString(R.string.addresses_seen)));
            Widgets.Card card = w.card();
            for (String key : co.services) {
                Integer cat = data.atlas.serviceCategory.get(key);
                String address = key.startsWith("ip:") ? key.substring(3) : key;
                card.add(w.row(null, address, new Object[] {Texts.typeName(ctx, CompanyType.of(cat == null ? 0 : cat))},
                        null, false, null));
            }
            col.addView(card);
        }
        return col;
    }

    // ==================================================================================== an app

    View app(final String pkg) {
        LinearLayout col = column();
        AtlasModel.App a = data.atlas.byPkg.get(pkg);
        int companies = a == null ? 0 : a.companies();
        int countries = a == null ? 0 : a.countries;
        col.addView(w.identity(w.appTile(pkg, 72), w.apps.label(pkg),
                new Object[] {ctx.getString(R.string.app_companies_countries, Texts.companies(ctx, companies),
                        Texts.countries(ctx, countries))}, null));
        if (a == null || companies == 0) {
            col.addView(w.info(ctx.getString(R.string.no_company_seen), R.drawable.ic_info, R.color.at_onv, null));
            col.addView(limitsNote());
            return col;
        }
        col.addView(w.actionRow(R.drawable.ic_map, ctx.getString(R.string.show_on_map),
                ctx.getString(R.string.lines_for_only, w.apps.label(pkg)), v -> nav.showAppOnMap(pkg)));
        col.addView(w.h3(ctx.getString(R.string.contacts_n_companies, Texts.companies(ctx, companies))));
        List<MapModel.CompanyRow> rows = new ArrayList<>();
        for (String id : a.perCompany.keySet()) {
            rows.add(data.byOwner.get(id));
        }
        // spec 4: the companies of the app grouped by type, the most connections first in each group
        for (List<MapModel.CompanyRow> group : CompanyList.groupByType(rows, a.perCompany)) {
            col.addView(w.h3(Texts.typeName(ctx, CompanyType.main(group.get(0).categories))));
            Widgets.Card card = w.card();
            for (final MapModel.CompanyRow co : group) {
                card.add(w.companyRow(co, Texts.connections(ctx, a.perCompany.get(co.owner.id)),
                        v -> nav.openCompany(co.owner.id)));
            }
            col.addView(card);
        }
        col.addView(limitsNote());
        return col;
    }
}
