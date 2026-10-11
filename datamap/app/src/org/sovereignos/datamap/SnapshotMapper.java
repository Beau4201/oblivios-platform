// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import com.android.internal.datamap.DataMapEntry;
import com.android.internal.datamap.DataMapLink;
import com.android.internal.datamap.DataMapOwner;
import com.android.internal.datamap.DataMapPublisher;
import com.android.internal.datamap.DataMapRelation;
import com.android.internal.datamap.DataMapSnapshot;

import org.sovereignos.datamap.model.MapModel;

/** Copies the Binder parcelables into the plain classes that the model works on. */
final class SnapshotMapper {
    private SnapshotMapper() {}

    static MapModel.Input toInput(DataMapSnapshot s) {
        MapModel.Input in = new MapModel.Input();
        if (s.entries != null) {
            for (DataMapEntry e : s.entries) {
                MapModel.Entry m = new MapModel.Entry();
                m.pkg = nz(e.packageName);
                m.serviceKey = nz(e.serviceKey);
                m.ownerId = nz(e.ownerId);
                m.category = e.category;
                m.firstWall = e.firstSeenWallMs;
                m.lastWall = e.lastSeenWallMs;
                m.count = e.count;
                m.foreground = e.foregroundCount;
                m.background = e.backgroundCount;
                m.viaDns = e.viaDns;
                in.entries.add(m);
            }
        }
        if (s.owners != null) {
            for (DataMapOwner o : s.owners) {
                MapModel.Owner m = new MapModel.Owner();
                m.id = nz(o.id);
                m.name = nz(o.name);
                m.legalName = nz(o.legalName);
                m.countryCode = nz(o.countryCode);
                m.city = nz(o.city);
                m.lat = o.latitude;
                m.lon = o.longitude;
                m.sourceId = nz(o.sourceId);
                in.owners.put(m.id, m);
            }
        }
        if (s.relations != null) {
            for (DataMapRelation r : s.relations) {
                MapModel.Relation m = new MapModel.Relation();
                m.ownerA = nz(r.ownerA);
                m.type = nz(r.type);
                m.ownerB = nz(r.ownerB);
                m.note = nz(r.note);
                m.sourceId = nz(r.sourceId);
                in.relations.add(m);
            }
        }
        if (s.publishers != null) {
            for (DataMapPublisher p : s.publishers) {
                in.publisherOf.put(nz(p.packageName), nz(p.ownerId));
            }
        }
        if (s.links != null) {
            for (DataMapLink l : s.links) {
                MapModel.Link m = new MapModel.Link();
                m.pkgA = nz(l.packageA);
                m.pkgB = nz(l.packageB);
                m.kind = l.kind;
                m.evidence = l.evidence == null ? new String[0] : l.evidence;
                m.stronger = l.stronger;
                in.links.add(m);
            }
        }
        return in;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
