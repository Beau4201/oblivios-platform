// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap;

import android.content.Context;

import org.freeholdos.datamap.model.CompanyType;
import org.freeholdos.datamap.model.MapModel;
import org.freeholdos.datamap.model.PlaceLinks;
import org.freeholdos.datamap.model.RelationRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The words of the lists, from the strings of the design page (ADR-160). */
final class Texts {
    private Texts() {}

    static String country(String countryCode) {
        if (countryCode == null || countryCode.isEmpty()) {
            return "";
        }
        String name = new Locale("", countryCode).getDisplayCountry();
        return name.isEmpty() ? countryCode : name;
    }

    /** "Frankfurt", or the name of the country when the place has no city. */
    static String serverName(MapModel.CompanyRow row) {
        return row.owner.city.isEmpty() ? country(row.owner.countryCode) : row.owner.city;
    }

    /** The line of a server place in a list: the services {@code pkg} reached (all when null), and the operators of those. */
    static String serverSub(Context c, MapModel.CompanyRow row, String pkg) {
        List<String> operators = row.operatorsOf(pkg);
        int n = row.servicesOf(pkg).size();
        String services = c.getResources().getQuantityString(R.plurals.n_services, n, n);
        return operators.isEmpty() ? services : services + " · " + (operators.size() > 2
                ? c.getString(R.string.operators_more, operators.get(0), operators.get(1), operators.size() - 2)
                : join(c, operators));
    }

    /** What the data says about the server of a service that is on no place; null when it says nothing. */
    static String serverLine(Context c, org.freeholdos.datamap.model.AtlasModel.Unplaced u) {
        switch (u.serverKind) {
            case MapModel.SRV_UNCERTAIN:
                return u.serverOperator.isEmpty() ? c.getString(R.string.server_uncertain)
                        : c.getString(R.string.server_uncertain) + " · " + u.serverOperator;
            case MapModel.SRV_ANYCAST:
                return u.serverOperator.isEmpty() ? c.getString(R.string.server_anycast)
                        : c.getString(R.string.server_anycast) + " · " + u.serverOperator;
            case MapModel.SRV_STEERED:
                return c.getString(R.string.server_behind,
                        u.serverOperator.isEmpty() ? c.getString(R.string.server_a_network) : u.serverOperator);
            default:
                return null;
        }
    }

    static String apps(Context c, int n) {
        return c.getResources().getQuantityString(R.plurals.n_apps, n, n);
    }

    static String companies(Context c, int n) {
        return c.getResources().getQuantityString(R.plurals.n_companies, n, n);
    }

    static String countries(Context c, int n) {
        return c.getResources().getQuantityString(R.plurals.n_countries, n, n);
    }

    static String connections(Context c, int n) {
        return c.getResources().getQuantityString(R.plurals.n_connections, n, n);
    }

    static String typeName(Context c, int type) {
        return c.getResources().getStringArray(R.array.type_names)[type];
    }

    /** All types of a company, as the page's {@code kindsText}: "Advertising · Analytics". */
    static String types(Context c, MapModel.CompanyRow co) {
        StringBuilder sb = new StringBuilder();
        for (int t : CompanyType.kinds(co.categories)) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(typeName(c, t));
        }
        return sb.toString();
    }

    static String mainType(Context c, MapModel.CompanyRow co) {
        return typeName(c, CompanyType.main(co.categories));
    }

    /** "A", "A and B", "A, B and C". */
    static String join(Context c, List<String> names) {
        int n = names.size();
        if (n == 0) {
            return "";
        }
        if (n == 1) {
            return names.get(0);
        }
        StringBuilder head = new StringBuilder();
        for (int i = 0; i < n - 1; i++) {
            if (i > 0) {
                head.append(", ");
            }
            head.append(names.get(i));
        }
        return c.getString(R.string.and_join, head.toString(), names.get(n - 1));
    }

    static String names(List<String> labels) {
        StringBuilder sb = new StringBuilder();
        for (String s : labels) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * How a company is joined to one of a list: by the apps ("via Spotify, Chrome"), or by the first documented
     * relation with its source id ("Owns Google · Source A-002"). It never says that two companies exchange data.
     */
    static String via(Context c, Apps apps, PlaceLinks.Joined j, List<MapModel.CompanyRow> list) {
        if (!j.rels.isEmpty()) {
            MapModel.Relation r = j.rels.get(0);
            MapModel.CompanyRow viewed = null;
            for (MapModel.CompanyRow t : list) {
                if (t.owner.id.equals(r.ownerA) || t.owner.id.equals(r.ownerB)) {
                    viewed = t;
                    break;
                }
            }
            String id = viewed == null ? "" : viewed.owner.id;
            int role = RelationRole.of(id, r);
            int idx = RelationRole.typeIndex(r.type);
            String[] forms = c.getResources().getStringArray(
                    role == RelationRole.SUBJECT ? R.array.rel_as_subject : R.array.rel_as_object);
            String phrase = String.format(forms[idx], viewed == null ? "" : viewed.owner.name);
            return c.getString(R.string.source_line, phrase, r.sourceId);
        }
        List<String> labels = new ArrayList<>();
        for (String pkg : j.apps) {
            labels.add(apps.label(pkg));
        }
        return c.getString(R.string.via_apps, names(labels));
    }

    /** A documented connection as a sentence: "Alphabet owns Google". */
    static String sentence(Context c, MapModel.Relation r, MapModel.CompanyRow a, MapModel.CompanyRow b) {
        String form = c.getResources().getStringArray(R.array.rel_sentence)[RelationRole.typeIndex(r.type)];
        return String.format(form, a.owner.name, b.owner.name);
    }
}
