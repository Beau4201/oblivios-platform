// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The places of the map (ADR-160): the companies within {@value #JOIN_DEG} degrees of the company that the most apps
 * contact are one place, named after that company. A company joins the lead of a place and never a later member, so
 * the same companies always make the same places. A company that no app contacts is not a place.
 */
public final class Places {
    public static final double JOIN_DEG = 6.0;
    private static final double JOIN_COS = Math.cos(Math.toRadians(JOIN_DEG));

    private Places() {}

    public static final class Place {
        /** The owner id of the lead company. */
        public final String key;
        public final MapModel.CompanyRow lead;
        /** The lead first, then the others in the order of {@link #build}. */
        public final List<MapModel.CompanyRow> members = new ArrayList<>();
        /** Every app that contacts a member of the place. */
        public final Set<String> apps = new LinkedHashSet<>();
        /** The country codes of the members, once each, in the order of the members. */
        public final List<String> countries = new ArrayList<>();
        final double[] unit;

        Place(MapModel.CompanyRow lead) {
            this.lead = lead;
            this.key = lead.owner.id;
            this.unit = unit(lead.owner.lat, lead.owner.lon);
        }

        public double lat() {
            return lead.owner.lat;
        }

        public double lon() {
            return lead.owner.lon;
        }

        void add(MapModel.CompanyRow row) {
            members.add(row);
            apps.addAll(row.apps);
            String cc = row.owner.countryCode;
            if (cc != null && !cc.isEmpty() && !countries.contains(cc)) {
                countries.add(cc);
            }
        }
    }

    /**
     * @param onlyApp the chosen app, or null: with one chosen only the companies it contacts are placed
     */
    public static List<Place> build(List<MapModel.CompanyRow> rows, String onlyApp) {
        List<MapModel.CompanyRow> sorted = new ArrayList<>();
        for (MapModel.CompanyRow r : rows) {
            if (r.contacted && (onlyApp == null || r.apps.contains(onlyApp))) {
                sorted.add(r);
            }
        }
        Collections.sort(sorted, new Comparator<MapModel.CompanyRow>() {
            @Override
            public int compare(MapModel.CompanyRow x, MapModel.CompanyRow y) {
                int byApps = y.apps.size() - x.apps.size();
                return byApps != 0 ? byApps : x.owner.id.compareTo(y.owner.id);
            }
        });
        List<Place> places = new ArrayList<>();
        for (MapModel.CompanyRow r : sorted) {
            double[] u = unit(r.owner.lat, r.owner.lon);
            Place home = null;
            for (Place p : places) {
                if (p.unit[0] * u[0] + p.unit[1] * u[1] + p.unit[2] * u[2] > JOIN_COS) {
                    home = p;
                    break;
                }
            }
            if (home == null) {
                home = new Place(r);
                places.add(home);
            }
            home.add(r);
        }
        return places;
    }

    static double[] unit(double latDeg, double lonDeg) {
        double lat = Math.toRadians(latDeg);
        double lon = Math.toRadians(lonDeg);
        return new double[] {Math.cos(lat) * Math.sin(lon), Math.sin(lat), Math.cos(lat) * Math.cos(lon)};
    }
}
