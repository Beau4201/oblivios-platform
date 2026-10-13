// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the lists of Atlas show, built from one {@link MapModel.Output}: the apps with the number of connections they
 * made to each company, the apps that met no company, the contacted companies and the addresses no owner stands behind.
 * A connection is one counted contact; it says an app reached a company, nothing more.
 */
public final class AtlasModel {
    public static final class App {
        public final String pkg;
        public int connections;
        /** The connections to each contacted company, in the order the contacts were met. */
        public final Map<String, Integer> perCompany = new LinkedHashMap<>();
        public int countries;

        App(String pkg) {
            this.pkg = pkg;
        }

        public int companies() {
            return perCompany.size();
        }
    }

    /** A name or an address that no owner of the classifier stands behind. */
    public static final class Unplaced {
        public final String key;
        public int category;
        public boolean viaDns;
        public final Set<String> apps = new LinkedHashSet<>();

        Unplaced(String key) {
            this.key = key;
        }

        /** The address of a service key that is one ({@code ip:93.184.216.34}), else the key. */
        public String address() {
            return key.startsWith("ip:") ? key.substring(3) : key;
        }
    }

    /** Apps with at least one company, most connections first, then by package name. */
    public final List<App> withCompanies = new ArrayList<>();
    /** Apps that met no company, in the order of the model (most recent first). */
    public final List<App> without = new ArrayList<>();
    public final Map<String, App> byPkg = new HashMap<>();
    /** The companies that an app contacts, in the order of the model. */
    public final List<MapModel.CompanyRow> contacted = new ArrayList<>();
    public final List<Unplaced> unplaced = new ArrayList<>();
    /** The strongest category with which each service key was contacted, for the "Addresses seen" of a company. */
    public final Map<String, Integer> serviceCategory = new HashMap<>();

    private AtlasModel() {}

    public static AtlasModel of(MapModel.Output out) {
        AtlasModel m = new AtlasModel();
        Map<String, MapModel.CompanyRow> placed = new HashMap<>();
        for (MapModel.CompanyRow c : out.companies) {
            if (c.contacted) {
                m.contacted.add(c);
                placed.put(c.owner.id, c);
            }
        }
        for (MapModel.AppRow a : out.apps) {
            App app = new App(a.pkg);
            for (MapModel.Contact c : a.contacts) {
                Integer seen = m.serviceCategory.get(c.serviceKey);
                if (seen == null || MapModel.rank(c.category) > MapModel.rank(seen)) {
                    m.serviceCategory.put(c.serviceKey, c.category);
                }
                if (!c.ownerId.isEmpty() && placed.containsKey(c.ownerId)) {
                    Integer old = app.perCompany.get(c.ownerId);
                    app.perCompany.put(c.ownerId, (old == null ? 0 : old) + c.count);
                    app.connections += c.count;
                }
            }
            Set<String> countries = new HashSet<>();
            for (String id : app.perCompany.keySet()) {
                countries.add(placed.get(id).owner.countryCode);
            }
            app.countries = countries.size();
            m.byPkg.put(a.pkg, app);
            (app.perCompany.isEmpty() ? m.without : m.withCompanies).add(app);
        }
        Collections.sort(m.withCompanies, new Comparator<App>() {
            @Override
            public int compare(App x, App y) {
                int byConnections = Integer.compare(y.connections, x.connections);
                return byConnections != 0 ? byConnections : x.pkg.compareTo(y.pkg);
            }
        });
        for (MapModel.UnplacedService s : out.unplacedServices) {
            Unplaced u = new Unplaced(s.serviceKey);
            u.category = s.category;
            u.viaDns = s.viaDns;
            u.apps.addAll(s.apps);
            m.unplaced.add(u);
        }
        return m;
    }

    /** The connections of an app to a company, 0 if it made none. */
    public int connections(String pkg, String ownerId) {
        App a = byPkg.get(pkg);
        Integer n = a == null ? null : a.perCompany.get(ownerId);
        return n == null ? 0 : n;
    }
}
