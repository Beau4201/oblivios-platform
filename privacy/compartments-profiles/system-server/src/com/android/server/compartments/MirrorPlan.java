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
        return sensorModeFile(s, r, salt, ActivityEstimator.Activity.STILL);
    }

    /**
     * The same file with what the phone is doing, for the invented streams: a line "t ACTIVITY" after the default when the
     * activity is not STILL (a file without the line reads as STILL).
     */
    static String sensorModeFile(PolicyStore s, UidResolver r, long salt, ActivityEstimator.Activity activity) {
        SensorMode dflt = mode(s.defaults.profile, s.defaults.value(Cls.SENSORS));
        Map<Integer, SensorMode> perApp = new TreeMap<>();
        for (String pkg : s.configured()) {
            int appId = ordinaryAppId(r.uidOf(pkg));
            if (appId >= 0) {
                perApp.merge(appId, mode(s.policyOf(pkg).profile, s.value(pkg, Cls.SENSORS)), MirrorPlan::stricter);
            }
        }
        StringBuilder out = new StringBuilder(String.format("s %016x\n", salt)).append("d ").append(dflt).append('\n');
        if (activity != ActivityEstimator.Activity.STILL) {
            out.append("t ").append(activity).append('\n');
        }
        for (Map.Entry<Integer, SensorMode> e : perApp.entrySet()) {
            if (e.getValue() != dflt) {
                out.append("a ").append(e.getKey()).append(' ').append(e.getValue()).append('\n');
            }
        }
        return out.toString();
    }

    /** The packages whose location is blocked: the service takes the location permissions of these in every user. */
    static Set<String> locationBlocked(PolicyStore s) {
        Set<String> out = new TreeSet<>();
        for (String pkg : s.configured()) {
            if (s.value(pkg, Cls.LOCATION) == Value.BLOCKED) {
                out.add(pkg);
            }
        }
        return out;
    }

    /** The seed of the Automatic place of an app on this phone: the same every time, different for each app and each phone. */
    static long personaSeed(long salt, int appId) {
        return salt ^ (appId * 0x9E3779B97F4A7C15L);
    }

    /**
     * The place of a substituted app: the one the user chose when {@code places} knows it, else the one chosen for the app in
     * the real country; 0 when there is none (no place data, no country, a country without a place, an unknown chosen place).
     */
    private static int placeOf(PolicyStore.AppPolicy p, int appId, long salt, Places places, String country, double lat, double lon) {
        if (places == null) {
            return 0;
        }
        if (p.place != 0) {
            return places.byId(p.place) != null ? p.place : 0;
        }
        return places.automaticFor(country, lat, lon, personaSeed(salt, appId));
    }

    /** The app id of an ordinary app, which every user shares; -1 for anything else (a platform or sandbox uid, -1: not installed). */
    private static int ordinaryAppId(int uid) {
        int appId = uid % USER_OFFSET;
        return appId >= FIRST_APP_ID && appId <= LAST_APP_ID ? appId : -1;
    }

    /**
     * The persona file ({@link PersonaFile}): a line for each app id of a configured app whose location is substituted and that
     * has a place (see {@link #placeOf}); an app that has none is in {@link #locationUnresolved} instead. Two packages with one
     * app id give the lower place. Pure: the real country and position are arguments (a NaN position is unknown).
     */
    static String personaFile(PolicyStore s, UidResolver r, long salt, Places places, String country, double lat, double lon) {
        Map<Integer, Integer> perApp = new TreeMap<>();
        for (String pkg : s.configured()) {
            int appId = ordinaryAppId(r.uidOf(pkg));
            if (appId < 0 || s.value(pkg, Cls.LOCATION) != Value.SUBSTITUTE) {
                continue;
            }
            int place = placeOf(s.policyOf(pkg), appId, salt, places, country, lat, lon);
            if (place > 0) {
                perApp.merge(appId, place, Math::min);
            }
        }
        return PersonaFile.format(salt, perApp);
    }

    /**
     * The installed packages whose location is substituted but for which no persona can be made. The service takes their
     * location permissions, so that an app is never given the real position when its persona is missing.
     */
    static Set<String> locationUnresolved(PolicyStore s, UidResolver r, long salt, Places places, String country, double lat,
            double lon) {
        Set<String> out = new TreeSet<>();
        for (String pkg : s.configured()) {
            int appId = ordinaryAppId(r.uidOf(pkg));
            if (appId >= 0 && s.value(pkg, Cls.LOCATION) == Value.SUBSTITUTE
                    && placeOf(s.policyOf(pkg), appId, salt, places, country, lat, lon) == 0) {
                out.add(pkg);
            }
        }
        return out;
    }

    private static SensorMode mode(Profiles.Profile p, Value v) {
        return Profiles.sensorMode(p, v);
    }
}
