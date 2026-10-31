// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import com.android.server.compartments.Profiles.Cls;
import com.android.server.compartments.Profiles.SensorMode;
import com.android.server.compartments.Profiles.Value;

import java.util.Collection;
import java.util.Collections;
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

        /** The packages installed in the owner user; an app without an entry is one of them (D5). None by default. */
        default Collection<String> installedPackages() {
            return Collections.emptySet();
        }
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

    /**
     * The packages that the persona plan covers: those with an entry, and, when the defaults are Isolated and the image has
     * place data, every installed package, because an app without an entry follows the defaults (D5). Without place data no
     * persona can be made for anyone, and the permissions of every app would be taken at once, so those apps keep what they
     * had until data is installed.
     */
    static Set<String> locationPackages(PolicyStore s, UidResolver r, Places places) {
        Set<String> out = new TreeSet<>(s.configured());
        if (s.defaults.profile == Profiles.Profile.ISOLATED && places != null) {
            out.addAll(r.installedPackages());
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
    private static int placeOf(PolicyStore.AppPolicy p, int appId, long salt, Places places, String country, double lat, double lon,
            int kept) {
        if (places == null) {
            return 0;
        }
        if (p.place != 0) {
            return places.byId(p.place) != null ? p.place : 0;
        }
        return kept > 0 ? kept : places.automaticFor(country, lat, lon, personaSeed(salt, appId));
    }

    /**
     * The place that an Automatic app keeps (design 4.1: chosen once per app and kept): the one the table of the last run gave
     * it, when the app was Automatic and substituted then as well and the place is still in the data. Without it the place
     * would follow the network country at the moment of each run, and a boot without a network would take the persona of every
     * Automatic app away. 0 when there is nothing to keep.
     */
    private static int keptPlace(PolicyStore before, PersonaFile table, String pkg, int appId, Places places) {
        if (before == null || table == null || places == null) {
            return 0;
        }
        if (before.policyOf(pkg).place != 0 || before.value(pkg, Cls.LOCATION) != Value.SUBSTITUTE) {
            return 0;
        }
        int kept = table.placeOf(appId);
        return kept > 0 && places.byId(kept) != null ? kept : 0;
    }

    /**
     * The packages that must start again when the store changes from {@code before} to {@code after}: a process reads the time
     * zone, the country and the operator of its persona once, and a sensor channel or a position listener that it holds keeps
     * what it was given. These are the packages whose profile, sensor value, location value or place differ. A change of the
     * defaults reaches no package without an entry of its own here (it does not restart them).
     */
    static Set<String> restartNeeded(PolicyStore before, PolicyStore after) {
        Set<String> all = new TreeSet<>(before.configured());
        all.addAll(after.configured());
        Set<String> out = new TreeSet<>();
        for (String pkg : all) {
            PolicyStore.AppPolicy a = before.policyOf(pkg);
            PolicyStore.AppPolicy b = after.policyOf(pkg);
            if (a.profile != b.profile || before.value(pkg, Cls.SENSORS) != after.value(pkg, Cls.SENSORS)
                    || before.value(pkg, Cls.LOCATION) != after.value(pkg, Cls.LOCATION) || a.place != b.place) {
                out.add(pkg);
            }
        }
        return out;
    }

    static final int RETRY_FIRST_MS = 10_000;
    static final int RETRY_MAX_MS = 300_000;

    /** The wait before the next try to write the mirrors again: 10 seconds, then twice as long, at most 5 minutes. */
    static int nextRetryMs(int previousMs) {
        return previousMs <= 0 ? RETRY_FIRST_MS : (int) Math.min(2L * previousMs, RETRY_MAX_MS);
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
        return personaFile(s, r, salt, places, country, lat, lon, null, null);
    }

    /** The same file, where an Automatic app keeps the place that {@code table} (made for {@code before}) gave it, see {@link #keptPlace}. */
    static String personaFile(PolicyStore s, UidResolver r, long salt, Places places, String country, double lat, double lon,
            PolicyStore before, PersonaFile table) {
        Map<Integer, Integer> perApp = new TreeMap<>();
        for (String pkg : locationPackages(s, r, places)) {
            int appId = ordinaryAppId(r.uidOf(pkg));
            if (appId < 0 || s.value(pkg, Cls.LOCATION) != Value.SUBSTITUTE) {
                continue;
            }
            int place = placeOf(s.policyOf(pkg), appId, salt, places, country, lat, lon, keptPlace(before, table, pkg, appId, places));
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
        return locationUnresolved(s, r, salt, places, country, lat, lon, null, null);
    }

    /** The same set, for the table that {@link #personaFile(PolicyStore, UidResolver, long, Places, String, double, double, PolicyStore, PersonaFile)} makes. */
    static Set<String> locationUnresolved(PolicyStore s, UidResolver r, long salt, Places places, String country, double lat,
            double lon, PolicyStore before, PersonaFile table) {
        Set<String> out = new TreeSet<>();
        for (String pkg : locationPackages(s, r, places)) {
            int appId = ordinaryAppId(r.uidOf(pkg));
            if (appId >= 0 && s.value(pkg, Cls.LOCATION) == Value.SUBSTITUTE
                    && placeOf(s.policyOf(pkg), appId, salt, places, country, lat, lon, keptPlace(before, table, pkg, appId, places)) == 0) {
                out.add(pkg);
            }
        }
        return out;
    }

    /**
     * The app ids that are given no position at delivery: the packages of {@link #locationBlocked} and of
     * {@link #locationUnresolved}. The location permissions of these packages are taken as well, but a permission can be given
     * again by the user or a script, and the delivery path must not then hand out the real fix (P4). A permission is per uid,
     * so one withheld package withholds the app id it shares.
     */
    static Set<Integer> locationWithheldAppIds(PolicyStore s, UidResolver r, long salt, Places places, String country, double lat,
            double lon, PolicyStore before, PersonaFile table) {
        Set<String> packages = new TreeSet<>(locationBlocked(s));
        packages.addAll(locationUnresolved(s, r, salt, places, country, lat, lon, before, table));
        Set<Integer> out = new TreeSet<>();
        for (String pkg : packages) {
            int appId = ordinaryAppId(r.uidOf(pkg));
            if (appId >= 0) {
                out.add(appId);
            }
        }
        return out;
    }

    private static SensorMode mode(Profiles.Profile p, Value v) {
        return Profiles.sensorMode(p, v);
    }
}
