// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The Companies list: a type to keep and an order (ADR-160). Only companies that an app contacts are listed. */
public final class CompanyList {
    public static final int ALL_TYPES = -1;
    public static final int APPS = 0;
    public static final int NAME = 1;
    public static final int COUNTRY = 2;

    private CompanyList() {}

    /** The name of a country, in the language of the user; the model has no locale data of its own. */
    public interface Names {
        String country(String countryCode);
    }

    public static List<MapModel.CompanyRow> of(List<MapModel.CompanyRow> rows, int type, final int sort,
            final Names names, Locale locale) {
        final Collator collator = Collator.getInstance(locale);
        List<MapModel.CompanyRow> out = new ArrayList<>();
        for (MapModel.CompanyRow r : rows) {
            if (r.contacted && (type == ALL_TYPES || CompanyType.main(r.categories) == type)) {
                out.add(r);
            }
        }
        Collections.sort(out, new Comparator<MapModel.CompanyRow>() {
            @Override
            public int compare(MapModel.CompanyRow x, MapModel.CompanyRow y) {
                if (sort == APPS) {
                    int byApps = y.apps.size() - x.apps.size();
                    if (byApps != 0) {
                        return byApps;
                    }
                } else if (sort == COUNTRY) {
                    int byCountry = collator.compare(names.country(x.owner.countryCode),
                            names.country(y.owner.countryCode));
                    if (byCountry != 0) {
                        return byCountry;
                    }
                }
                return collator.compare(x.owner.name, y.owner.name);
            }
        });
        return out;
    }

    /** The number of contacted companies of each {@link CompanyType}. */
    public static int[] counts(List<MapModel.CompanyRow> rows) {
        int[] n = new int[CompanyType.COUNT];
        for (MapModel.CompanyRow r : rows) {
            if (r.contacted) {
                n[CompanyType.main(r.categories)]++;
            }
        }
        return n;
    }

    /** Whether the list is not in its default state (all types, most apps first). */
    public static boolean active(int type, int sort) {
        return type != ALL_TYPES || sort != APPS;
    }
}
