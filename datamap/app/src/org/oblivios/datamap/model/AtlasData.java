// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything the screens draw from one snapshot of the service: the lists, the settings and the clock of the service. */
public final class AtlasData {
    public final MapModel.Output out;
    public final AtlasModel atlas;
    public final Map<String, MapModel.CompanyRow> byOwner = new HashMap<>();
    public final boolean collecting;
    public final boolean historyOn;
    public final int historyDays;
    /** The history also keeps the places of servers (ADR-161). */
    public final boolean serverHistory;
    public final long nowWallMs;
    /** False when the service cannot be reached: the lists then say so instead of saying nothing was collected. */
    public final boolean available;

    public AtlasData(MapModel.Output out, boolean collecting, boolean historyOn, int historyDays, long nowWallMs) {
        this(out, collecting, historyOn, historyDays, false, nowWallMs, true);
    }

    public AtlasData(MapModel.Output out, boolean collecting, boolean historyOn, int historyDays,
            boolean serverHistory, long nowWallMs) {
        this(out, collecting, historyOn, historyDays, serverHistory, nowWallMs, true);
    }

    private AtlasData(MapModel.Output out, boolean collecting, boolean historyOn, int historyDays,
            boolean serverHistory, long nowWallMs, boolean available) {
        this.available = available;
        this.out = out;
        this.atlas = AtlasModel.of(out);
        this.collecting = collecting;
        this.historyOn = historyOn;
        this.historyDays = historyDays;
        this.serverHistory = serverHistory;
        this.nowWallMs = nowWallMs;
        for (MapModel.CompanyRow c : out.companies) {
            byOwner.put(c.owner.id, c);
        }
    }

    public static AtlasData empty() {
        return new AtlasData(new MapModel.Output(), true, false, KeepFor.DEFAULT, 0);
    }

    public static AtlasData unavailable() {
        return new AtlasData(new MapModel.Output(), true, false, KeepFor.DEFAULT, false, 0, false);
    }

    /** No app has contacted a company and nothing is waiting in the lists. */
    public boolean nothingCollected() {
        return collecting && out.apps.isEmpty();
    }

    /** Whether the globe has places: with Collection off or no service it is drawn without (spec 6). */
    public boolean showsPlaces() {
        return available && collecting;
    }

    public List<MapModel.Relation> relations() {
        return out.relations;
    }
}
