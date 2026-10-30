// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import com.android.server.compartments.Profiles.Cls;
import com.android.server.compartments.Profiles.SensorMode;
import com.android.server.compartments.Profiles.Value;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** What the enforcement points are told, computed from a store. Pure: the service applies the results. */
final class MirrorPlan {
    private MirrorPlan() {}

    /** The owner user's uid of a package, or -1 when the package is not installed there. */
    interface UidResolver {
        int uidOf(String pkg);
    }

    /** The value of the global setting that lists the packages allowed to read every settings key (ADR-175). */
    static String settingsRealPackages(PolicyStore s) {
        StringBuilder out = new StringBuilder();
        for (String pkg : new TreeSet<>(s.configured())) {
            if (s.value(pkg, Cls.SETTINGS) == Value.REAL) {
                out.append(out.length() == 0 ? "" : ",").append(pkg);
            }
        }
        return out.toString();
    }

    /** The packages that hold the loopback permission: only those the user set; the default is blocked. */
    static Set<String> loopbackAllowed(PolicyStore s) {
        Set<String> out = new TreeSet<>();
        for (String pkg : s.configured()) {
            if (s.value(pkg, Cls.LOOPBACK) == Value.REAL) {
                out.add(pkg);
            }
        }
        return out;
    }

    /**
     * The file that the sensor service reads: "s SALT" (16 hex digits, the per-phone salt of the invented streams), "d MODE"
     * for every app uid without a line of its own, then "u UID MODE" for each configured app whose mode differs from that
     * default, in uid order.
     */
    static String sensorModeFile(PolicyStore s, UidResolver r, long salt) {
        SensorMode dflt = mode(s.defaults.profile, s.defaults.value(Cls.SENSORS));
        Map<Integer, SensorMode> perUid = new TreeMap<>();
        for (String pkg : s.configured()) {
            int uid = r.uidOf(pkg);
            SensorMode m = mode(s.policyOf(pkg).profile, s.value(pkg, Cls.SENSORS));
            if (uid >= 0 && m != dflt) {
                perUid.put(uid, m);
            }
        }
        StringBuilder out = new StringBuilder(String.format("s %016x\n", salt)).append("d ").append(dflt).append('\n');
        for (Map.Entry<Integer, SensorMode> e : perUid.entrySet()) {
            out.append("u ").append(e.getKey()).append(' ').append(e.getValue()).append('\n');
        }
        return out.toString();
    }

    private static SensorMode mode(Profiles.Profile p, Value v) {
        return Profiles.sensorMode(p, v);
    }
}
