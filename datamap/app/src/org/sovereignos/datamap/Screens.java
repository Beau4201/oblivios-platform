// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.text.format.DateUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.sovereignos.datamap.model.MapModel;

import java.util.Map;

/**
 * The pages and detail screens as views. The wording rule binds every string here: two apps "could be
 * linked", the evidence is named, and a connection that is not listed is not shown as proof of none.
 */
final class Screens {
    interface Nav {
        void openApp(String pkg);

        void openCompany(String ownerId);
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

    private LinearLayout page() {
        LinearLayout l = new LinearLayout(mUi.ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private View title(int stringRes) {
        TextView t = mUi.text(mUi.ctx.getString(stringRes), 36, false, R.color.dm_on);
        t.setPadding(mUi.dp(24), mUi.dp(64), mUi.dp(24), mUi.dp(8));
        return t;
    }

    // ------------------------------------------------------------------------------------ Apps

    View appsPage() {
        LinearLayout p = page();
        p.addView(title(R.string.tab_apps));
        if (mModel.apps.isEmpty()) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.empty_nothing)));
        } else {
            Ui.Group g = new Ui.Group(mUi);
            for (MapModel.AppRow a : mModel.apps) {
                StringBuilder s = new StringBuilder(mUi.ctx.getResources().getQuantityString(
                        R.plurals.n_companies, a.companies, a.companies));
                s.append(" · ").append(ago(a.lastWall));
                if (!a.onMap) {
                    s.append(" · ").append(mUi.ctx.getString(R.string.not_on_map));
                }
                final String pkg = a.pkg;
                g.add(mUi.row(mUi.appTile(mApps, pkg, 40), mApps.label(pkg), s.toString(),
                        a.touchesTracker ? mUi.ctx.getString(R.string.tracker_short) : "",
                        v -> mNav.openApp(pkg)));
            }
            p.addView(g.seal().view);
        }
        p.addView(mUi.note(mUi.ctx.getString(R.string.blind_spots)));
        return p;
    }

    /** The app on a screen of its own. */
    View appDetail(String pkg) {
        return appBody(pkg, 64);
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
        TextView name = mUi.text(mApps.label(pkg), 28, false, R.color.dm_on);
        name.setPadding(mUi.dp(24), mUi.dp(topDp), mUi.dp(24), mUi.dp(4));
        p.addView(name);
        if (a == null) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.empty_app)));
            return p;
        }
        p.addView(mUi.para(mUi.ctx.getString(R.string.app_totals, a.count, a.foreground, a.background)));
        p.addView(mUi.para(mUi.ctx.getString(R.string.app_span, ago(a.firstWall), ago(a.lastWall))));
        if (!a.onMap) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.app_not_on_map)));
        } else {
            p.addView(mUi.para(mUi.ctx.getString(R.string.app_publisher, ownerName(a.publisherId))));
        }
        p.addView(mUi.label(mUi.ctx.getString(R.string.contacted)));
        Ui.Group g = new Ui.Group(mUi);
        for (MapModel.Contact c : a.contacts) {
            final boolean known = !c.ownerId.isEmpty() && mOwners.containsKey(c.ownerId);
            String owner = known ? ownerName(c.ownerId) : mUi.ctx.getString(R.string.owner_unknown);
            String summary = category(c.category) + " · " + owner + " · "
                    + mUi.ctx.getString(R.string.contact_counts, c.count, c.foreground, c.background) + " · "
                    + ago(c.lastWall);
            final String id = c.ownerId;
            g.add(mUi.row(known ? mUi.companyTile(owner, 40) : mUi.initialsTile("?", true, 40),
                    c.serviceKey.startsWith("ip:") ? mUi.ctx.getString(R.string.unknown_service) : c.serviceKey,
                    summary, "", known ? v -> mNav.openCompany(id) : null));
        }
        p.addView(g.seal().view);
        return p;
    }

    // -------------------------------------------------------------------------------- Companies

    View companiesPage() {
        LinearLayout p = page();
        p.addView(title(R.string.tab_companies));
        if (mModel.companies.isEmpty() && mModel.unplacedServices.isEmpty()) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.empty_nothing)));
            return p;
        }
        Ui.Group g = new Ui.Group(mUi);
        for (MapModel.CompanyRow c : mModel.companies) {
            final String id = c.owner.id;
            String summary = c.contacted
                    ? category(c.category) + " · " + mUi.ctx.getResources().getQuantityString(
                            R.plurals.n_apps, c.apps.size(), c.apps.size())
                    : mUi.ctx.getString(R.string.not_contacted_directly);
            g.add(mUi.row(mUi.companyTile(c.owner.name, 40), c.owner.name, summary, c.owner.countryCode,
                    v -> mNav.openCompany(id)));
        }
        if (g.view.getChildCount() > 0) {
            p.addView(g.seal().view);
        }
        if (!mModel.unplacedServices.isEmpty()) {
            p.addView(mUi.label(mUi.ctx.getString(R.string.not_on_map)));
            p.addView(mUi.para(mUi.ctx.getString(R.string.not_on_map_explained)));
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

    /** The company on a screen of its own. */
    View companyDetail(String ownerId) {
        return companyBody(ownerId, 64);
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
        TextView name = mUi.text(o == null ? ownerId : o.name, 28, false, R.color.dm_on);
        name.setPadding(mUi.dp(24), mUi.dp(topDp), mUi.dp(24), mUi.dp(4));
        p.addView(name);
        if (o == null) {
            return p;
        }
        if (!o.legalName.isEmpty() && !o.legalName.equals(o.name)) {
            p.addView(mUi.para(o.legalName));
        }
        p.addView(mUi.para(mUi.ctx.getString(R.string.company_based, o.city, o.countryCode)));
        p.addView(mUi.note(mUi.ctx.getString(R.string.company_based_note)));
        if (row != null && row.contacted) {
            p.addView(mUi.para(category(row.category)));
        } else if (row != null) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.not_contacted_directly)));
        }
        if (row != null && !row.services.isEmpty()) {
            p.addView(mUi.label(mUi.ctx.getString(R.string.services)));
            Ui.Group g = new Ui.Group(mUi);
            for (String s : row.services) {
                g.add(mUi.row(null, s, "", "", null));
            }
            p.addView(g.seal().view);
        }
        if (row != null && !row.apps.isEmpty()) {
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
                String other = r.ownerA.equals(ownerId) ? r.ownerB : r.ownerB.equals(ownerId) ? r.ownerA : null;
                if (other == null) {
                    continue;
                }
                final String id = other;
                String what = r.ownerA.equals(ownerId) ? relation(r.type) : mUi.ctx.getString(R.string.rel_reverse,
                        relation(r.type));
                String note = r.note.isEmpty() ? "" : " · " + r.note;
                g.add(mUi.row(mUi.companyTile(ownerName(other), 40), ownerName(other), what + note,
                        "", v -> mNav.openCompany(id)));
            }
            p.addView(g.seal().view);
            p.addView(mUi.note(mUi.ctx.getString(R.string.relations_note)));
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
        TextView name = mUi.text(ownerName(r.ownerA) + " — " + ownerName(r.ownerB), 24, false, R.color.dm_on);
        name.setPadding(mUi.dp(24), mUi.dp(topDp), mUi.dp(24), mUi.dp(4));
        p.addView(name);
        p.addView(mUi.para(relation(r.type) + (r.note.isEmpty() ? "" : " · " + r.note)));
        Ui.Group g = new Ui.Group(mUi);
        for (final String id : new String[] {r.ownerA, r.ownerB}) {
            MapModel.Owner o = mOwners.get(id);
            g.add(mUi.row(mUi.companyTile(ownerName(id), 40), ownerName(id),
                    o == null ? "" : o.city + ", " + o.countryCode, "", v -> mNav.openCompany(id)));
        }
        p.addView(g.seal().view);
        p.addView(mUi.note(mUi.ctx.getString(R.string.relations_note)));
        return p;
    }

    /** Companies based close together, which the map shows as one number. */
    View groupBody(java.util.List<String> ownerIds, int topDp) {
        LinearLayout p = page();
        TextView name = mUi.text(mUi.ctx.getResources().getQuantityString(R.plurals.n_companies,
                ownerIds.size(), ownerIds.size()), 24, false, R.color.dm_on);
        name.setPadding(mUi.dp(24), mUi.dp(topDp), mUi.dp(24), mUi.dp(4));
        p.addView(name);
        p.addView(mUi.para(mUi.ctx.getString(R.string.group_here)));
        Ui.Group g = new Ui.Group(mUi);
        for (final String id : ownerIds) {
            String summary = "";
            for (MapModel.CompanyRow c : mModel.companies) {
                if (c.owner.id.equals(id)) {
                    summary = c.contacted ? category(c.category) : mUi.ctx.getString(R.string.not_contacted_directly);
                }
            }
            g.add(mUi.row(mUi.companyTile(ownerName(id), 40), ownerName(id), summary, "", v -> mNav.openCompany(id)));
        }
        p.addView(g.seal().view);
        return p;
    }

    // ----------------------------------------------------------------------------------- Links

    View linksPage() {
        LinearLayout p = page();
        p.addView(title(R.string.tab_links));
        p.addView(mUi.label(mUi.ctx.getString(R.string.could_be_linked)));
        if (mModel.links.isEmpty()) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.no_links)));
        } else {
            Ui.Group g = new Ui.Group(mUi);
            for (MapModel.Link l : mModel.links) {
                final String a = l.pkgA;
                final String b = l.pkgB;
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
                g.add(mUi.row(mUi.appTile(mApps, a, 40), mApps.label(a) + " + " + mApps.label(b), why, "",
                        v -> mNav.openApp(a)));
            }
            p.addView(g.seal().view);
        }
        p.addView(mUi.label(mUi.ctx.getString(R.string.connected_companies)));
        if (mModel.relations.isEmpty()) {
            p.addView(mUi.para(mUi.ctx.getString(R.string.no_relations)));
        } else {
            Ui.Group g = new Ui.Group(mUi);
            for (MapModel.Relation r : mModel.relations) {
                final String a = r.ownerA;
                String note = r.note.isEmpty() ? "" : " · " + r.note;
                g.add(mUi.row(mUi.companyTile(ownerName(a), 40),
                        ownerName(a) + " — " + ownerName(r.ownerB), relation(r.type) + note, "",
                        v -> mNav.openCompany(a)));
            }
            p.addView(g.seal().view);
        }
        p.addView(mUi.note(mUi.ctx.getString(R.string.relations_note)));
        return p;
    }
}
