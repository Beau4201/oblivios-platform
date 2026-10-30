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

    /** The uid of a package in the owner user, or -1 when the package is not installed there. */
    interface UidResolver {
        int uidOf(String pkg);
    }

    static final int USER_OFFSET = 100000;
    static final int FIRST_APP_ID = 10000;
    static final int LAST_APP_ID = 19999;

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

    /** The more protective of two modes: RAW, FOLDED, SUBSTITUTE, BLOCKED in that order. */
    static SensorMode stricter(SensorMode a, SensorMode b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(SensorMode m) {
        switch (m) {
            case RAW:
                return 0;
            case FOLDED:
                return 1;
            case SUBSTITUTE:
                return 2;
            default:
                return 3;
        }
    }

    /**
     * The file that the sensor service reads: "s SALT" (16 hex digits, the per-phone salt of the invented streams), "d MODE"
     * for every app without a line of its own, then "a APPID MODE" for each configured app whose mode differs from that
     * default, in app id order. A line holds an app id, so it reaches the app in every user (and the sandbox of the app, which
     * the reader maps back). Only app ids of ordinary apps are written, because the reader refuses a file with any other id and
     * every choice of the user would then be lost; two packages with one app id get the more protective mode.
     */
    static String sensorModeFile(PolicyStore s, UidResolver r, long salt) {
        SensorMode dflt = mode(s.defaults.profile, s.defaults.value(Cls.SENSORS));
        Map<Integer, SensorMode> perApp = new TreeMap<>();
        for (String pkg : s.configured()) {
            int uid = r.uidOf(pkg);
            int appId = uid % USER_OFFSET;
            if (appId >= FIRST_APP_ID && appId <= LAST_APP_ID) {  // -1 (not installed) is outside the range too
                perApp.merge(appId, mode(s.policyOf(pkg).profile, s.value(pkg, Cls.SENSORS)), MirrorPlan::stricter);
            }
        }
        StringBuilder out = new StringBuilder(String.format("s %016x\n", salt)).append("d ").append(dflt).append('\n');
        for (Map.Entry<Integer, SensorMode> e : perApp.entrySet()) {
            if (e.getValue() != dflt) {
                out.append("a ").append(e.getKey()).append(' ').append(e.getValue()).append('\n');
            }
        }
        return out.toString();
    }

    private static SensorMode mode(Profiles.Profile p, Value v) {
        return Profiles.sensorMode(p, v);
    }
}
