// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * From the user's choices to what the daemon, the route helper and the platform are told (ADR-186). Pure Java: it holds no
 * state and makes no call, so every case is a host check.
 *
 * <ul>
 *   <li>Tor: the uids of the apps the user set to Tor, in every user where the package is installed.
 *   <li>Exempt: the exit app itself and the updater (the Tor client could not start, or the device could not be updated,
 *       if they were routed through Tor), whatever the choices say, and the apps the user set to {@link Choices.Choice#OPEN}
 *       (never masked, ADR-188).
 *   <li>Masked: every other app uid of every user, installed or not (a package installed later is masked from its first
 *       packet), but only once the user's consent is ON.
 * </ul>
 */
public final class Policy {
    /** {@code OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY} [verified-in-source: OemNetworkPreferences.java:87]. */
    public static final int OEM_PRIVATE_ONLY = 4;
    /** Packages that are never routed through Tor or masked. */
    public static final Set<String> EXEMPT_PACKAGES =
            Collections.unmodifiableSet(new TreeSet<>(java.util.Arrays.asList("org.freeholdos.exit", "app.seamlessupdate.client")));
    /** The limits of the daemon's class table and of the route helper's lists (control.rs MAX_CLASSES, spec.rs MAX_RANGES). */
    public static final int MAX_CLASSES = 255; // the count is one byte on the wire: 256 would be written as 0
    public static final int MAX_RANGES = 128;
    /** The most Exempt ranges: the two system packages and the open apps, so that the three lists fit the daemon's 255. */
    public static final int MAX_EXEMPT_RANGES = 64;

    private Policy() {}

    public enum Kind {
        TOR(1), MASKED(2), EXEMPT(3);

        public final int wire;

        Kind(int wire) {
            this.wire = wire;
        }
    }

    /** One installed package of one user. */
    public static final class App {
        public final String pkg;
        public final int uid;

        public App(String pkg, int uid) {
            this.pkg = pkg;
            this.uid = uid;
        }
    }

    public static final class Cls {
        public final int start;
        public final int end;
        public final Kind kind;

        public Cls(int start, int end, Kind kind) {
            this.start = start;
            this.end = end;
            this.kind = kind;
        }
    }

    public enum Warning {
        /** A VPN is active: it wins over the per-app network and the rules (F04). */
        VPN_ACTIVE,
        /** Private DNS is strict: the resolver does not send the names the masked tier needs to see (MSK-008). */
        PRIVATE_DNS_LIMITED,
        /** Too many apps for the tables: the masked tier is off until some apps are set back to Direct. */
        MASK_TIER_OFF_TOO_MANY_APPS,
        /**
         * More Tor uid ranges than the route helper accepts (48 apps in three users can reach it): the surplus has the network
         * preference but no class in the daemon and no guard rule, so it has no network at all (the daemon refuses a flow of a
         * uid without a class). The app shows it and asks to set some apps back to Direct.
         */
        TOO_MANY_TOR_APPS,
        /** More open uid ranges than the table holds: the surplus is masked like any other app. The app says so. */
        TOO_MANY_OPEN_APPS
    }

    public static final class Out {
        public final List<Cls> classes;
        /** Uids whose DNS and fake-range packets go into the tun (masked destinations). */
        public final List<int[]> steer;
        /** Uids that may use only the Tor network (apps set to Tor). */
        public final List<int[]> guard;
        /** package -> {@link #OEM_PRIVATE_ONLY}, for the apps on Tor. */
        public final Map<String, Integer> oemPrefs;
        public final Set<Warning> warnings;

        Out(List<Cls> classes, List<int[]> steer, List<int[]> guard, Map<String, Integer> oemPrefs, Set<Warning> warnings) {
            this.classes = classes;
            this.steer = steer;
            this.guard = guard;
            this.oemPrefs = oemPrefs;
            this.warnings = warnings;
        }
    }

    public static Out compute(Choices choices, List<App> installed, int[] users, Consent consent, boolean vpnActive,
            boolean privateDnsStrict) {
        Set<Warning> warnings = EnumSet.noneOf(Warning.class);
        if (vpnActive) {
            warnings.add(Warning.VPN_ACTIVE);
        }
        if (privateDnsStrict && consent == Consent.ON) {
            warnings.add(Warning.PRIVATE_DNS_LIMITED);
        }

        List<Integer> torUids = new ArrayList<>();
        List<Integer> exemptUids = new ArrayList<>();
        List<Integer> openUids = new ArrayList<>();
        Map<String, Integer> prefs = new LinkedHashMap<>();
        for (App a : installed) {
            // A package with a system uid (below the app ids) has no place in a table that the daemon and the helper accept.
            int appId = a.uid % UidRanges.PER_USER;
            if (a.uid < 0 || appId < UidRanges.FIRST_APP || appId > UidRanges.LAST_APP) {
                continue;
            }
            if (EXEMPT_PACKAGES.contains(a.pkg)) {
                exemptUids.add(a.uid);
            } else if (choices.choiceOf(a.pkg) == Choices.Choice.TOR) {
                torUids.add(a.uid);
                prefs.put(a.pkg, OEM_PRIVATE_ONLY);
            } else if (choices.choiceOf(a.pkg) == Choices.Choice.OPEN) {
                openUids.add(a.uid);
            }
        }
        List<int[]> tor = UidRanges.coalesce(toArray(torUids));
        List<int[]> exempt = UidRanges.coalesce(toArray(exemptUids));
        // The exit app and the updater are never Tor, even where the choices say so.
        tor = UidRanges.subtract(tor, exempt);
        // An open app is Exempt too, unless it shares its uid with a system package or with an app on Tor (Tor wins: a
        // shared uid cannot be both, and the app on Tor is the user's stronger choice).
        List<int[]> open = UidRanges.subtract(UidRanges.subtract(UidRanges.coalesce(toArray(openUids)), exempt), tor);
        int room = Math.max(0, MAX_EXEMPT_RANGES - exempt.size());
        if (open.size() > room) {
            open = new ArrayList<>(open.subList(0, room));
            warnings.add(Warning.TOO_MANY_OPEN_APPS);
        }

        if (tor.size() > MAX_RANGES) {
            tor = new ArrayList<>(tor.subList(0, MAX_RANGES));
            warnings.add(Warning.TOO_MANY_TOR_APPS);
        }

        List<int[]> masked = new ArrayList<>();
        if (consent == Consent.ON) {
            List<int[]> all = new ArrayList<>();
            for (int user : sortedUnique(users)) {
                all.add(UidRanges.forUser(user));
            }
            masked = UidRanges.subtract(UidRanges.subtract(UidRanges.subtract(all, tor), exempt), open);
            // The class table has at most 128 Tor ranges, 64 Exempt ranges (the system packages and the open apps) and the
            // masked ranges; the masked tier is off when the steer list or the whole table would not fit.
            if (masked.size() > MAX_RANGES || tor.size() + exempt.size() + open.size() + masked.size() > MAX_CLASSES) {
                masked = new ArrayList<>();
                warnings.add(Warning.MASK_TIER_OFF_TOO_MANY_APPS);
            }
        }

        List<Cls> classes = new ArrayList<>();
        for (int[] r : tor) {
            classes.add(new Cls(r[0], r[1], Kind.TOR));
        }
        for (int[] r : masked) {
            classes.add(new Cls(r[0], r[1], Kind.MASKED));
        }
        for (int[] r : exempt) {
            classes.add(new Cls(r[0], r[1], Kind.EXEMPT));
        }
        for (int[] r : open) {
            classes.add(new Cls(r[0], r[1], Kind.EXEMPT));
        }
        classes.sort((x, y) -> Integer.compare(x.start, y.start));
        return new Out(classes, masked, tor, prefs, warnings);
    }

    private static int[] toArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = l.get(i);
        }
        return a;
    }

    private static int[] sortedUnique(int[] users) {
        return new TreeSet<Integer>(toList(users)).stream().mapToInt(Integer::intValue).toArray();
    }

    private static List<Integer> toList(int[] a) {
        List<Integer> l = new ArrayList<>();
        for (int x : a) {
            l.add(x);
        }
        return l;
    }
}
