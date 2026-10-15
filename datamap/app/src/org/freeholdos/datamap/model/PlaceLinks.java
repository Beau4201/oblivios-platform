// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which places the map joins with a line (ADR-160): two places are joined when one app contacts a company at each, or
 * when a documented relation joins a company at each (it carries a source id, ADR-157). A line says who is contacted
 * and who is joined; it never says that two companies exchange data.
 */
public final class PlaceLinks {
    /** Above this many lines between places only the lines of the chosen place or app are drawn (spec A4). */
    public static final int MAX_PAIR_LINES = 12;

    private PlaceLinks() {}

    /**
     * Whether a line between two places is drawn.
     *
     * @param count how many lines the map holds
     * @param involved whether the line has the chosen place at one end
     */
    public static boolean drawn(int count, boolean involved, boolean placeChosen, boolean appChosen) {
        if (count <= MAX_PAIR_LINES) {
            return true;
        }
        return placeChosen ? involved : appChosen;
    }

    public static final class Pair {
        public final Places.Place a;
        public final Places.Place b;
        /** The apps that contact a company at both places (only the chosen app when one is chosen). */
        public final Set<String> apps = new LinkedHashSet<>();
        /** The documented relations between a company at one place and a company at the other. */
        public final List<MapModel.Relation> rels = new ArrayList<>();

        Pair(Places.Place a, Places.Place b) {
            this.a = a;
            this.b = b;
        }
    }

    /** Whether the relation joins the two companies, in either direction. */
    private static boolean joins(MapModel.Relation r, String x, String y) {
        return (r.ownerA.equals(x) && r.ownerB.equals(y)) || (r.ownerA.equals(y) && r.ownerB.equals(x));
    }

    /** @param onlyApp the chosen app, or null */
    public static List<Pair> pairs(List<Places.Place> places, List<MapModel.Relation> relations, String onlyApp) {
        List<MapModel.CompanyRow> cos = new ArrayList<>();
        List<Places.Place> of = new ArrayList<>();
        for (Places.Place p : places) {
            for (MapModel.CompanyRow m : p.members) {
                if (m.server) {
                    continue;   // a server place is joined to nothing: it is where a server lies, not a company
                }
                cos.add(m);
                of.add(p);
            }
        }
        Map<String, Pair> out = new LinkedHashMap<>();
        for (int i = 0; i < cos.size(); i++) {
            for (int j = i + 1; j < cos.size(); j++) {
                Places.Place pa = of.get(i);
                Places.Place pb = of.get(j);
                if (pa == pb) {
                    continue;
                }
                MapModel.CompanyRow a = cos.get(i);
                MapModel.CompanyRow b = cos.get(j);
                List<String> shared = new ArrayList<>();
                for (String app : a.apps) {
                    if (b.apps.contains(app) && (onlyApp == null || onlyApp.equals(app))) {
                        shared.add(app);
                    }
                }
                List<MapModel.Relation> rels = new ArrayList<>();
                for (MapModel.Relation r : relations) {
                    if (joins(r, a.owner.id, b.owner.id)) {
                        rels.add(r);
                    }
                }
                if (shared.isEmpty() && rels.isEmpty()) {
                    continue;
                }
                String key = pa.key.compareTo(pb.key) < 0 ? pa.key + "|" + pb.key : pb.key + "|" + pa.key;
                Pair e = out.get(key);
                if (e == null) {
                    e = new Pair(pa, pb);
                    out.put(key, e);
                }
                e.apps.addAll(shared);
                for (MapModel.Relation r : rels) {
                    if (!e.rels.contains(r)) {
                        e.rels.add(r);
                    }
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    public static final class Joined {
        public final MapModel.CompanyRow other;
        public final List<String> apps = new ArrayList<>();
        public final List<MapModel.Relation> rels = new ArrayList<>();

        Joined(MapModel.CompanyRow other) {
            this.other = other;
        }
    }

    /**
     * "Connected to": the contacted companies, other than those of {@code list}, that an app or a documented relation
     * joins to a company of {@code list}. Most shared apps first, then by name.
     */
    public static List<Joined> joined(List<MapModel.CompanyRow> list, List<MapModel.CompanyRow> contacted,
            List<MapModel.Relation> relations) {
        Set<String> ids = new LinkedHashSet<>();
        for (MapModel.CompanyRow t : list) {
            ids.add(t.owner.id);
        }
        List<Joined> out = new ArrayList<>();
        for (MapModel.CompanyRow o : contacted) {
            if (ids.contains(o.owner.id) || !o.contacted) {
                continue;
            }
            Joined j = new Joined(o);
            for (MapModel.CompanyRow t : list) {
                if (t.server) {
                    continue;   // where a server lies joins no company to another
                }
                for (String app : t.apps) {
                    if (o.apps.contains(app) && !j.apps.contains(app)) {
                        j.apps.add(app);
                    }
                }
                for (MapModel.Relation r : relations) {
                    if (joins(r, t.owner.id, o.owner.id) && !j.rels.contains(r)) {
                        j.rels.add(r);
                    }
                }
            }
            if (!j.apps.isEmpty() || !j.rels.isEmpty()) {
                out.add(j);
            }
        }
        Collections.sort(out, new Comparator<Joined>() {
            @Override
            public int compare(Joined x, Joined y) {
                int byApps = Integer.compare(y.apps.size(), x.apps.size());
                return byApps != 0 ? byApps : x.other.owner.name.compareToIgnoreCase(y.other.owner.name);
            }
        });
        return out;
    }
}
