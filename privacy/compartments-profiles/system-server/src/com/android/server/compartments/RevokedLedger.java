// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The location permissions that the service took from an app and has not given back (ADR-208). A revoked runtime permission
 * stays revoked, so without this record an app set from Blocked (or from an Isolated app that had no persona) back to Real data
 * would stay without the permission that the user had given it. Only a permission that was granted at the moment it was taken
 * is recorded, so giving it back never grants more than the user had; only the four location permissions can be read from a
 * file, so a damaged or planted file cannot make the service grant anything else. Pure Java.
 *
 * <p>Format: one line per entry, {@code user TAB package TAB permission} and a newline after every line, in sorted order.
 */
final class RevokedLedger {
    static final int MAX_ENTRIES = 4096;
    static final String[] PERMISSIONS = {
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.ACCESS_MEDIA_LOCATION",  // the real GPS position in the EXIF of the photos of the user
    };
    private static final Pattern USER = Pattern.compile("0|[1-9][0-9]{0,8}");

    static final class Entry implements Comparable<Entry> {
        final int user;
        final String pkg;
        final String permission;

        Entry(int user, String pkg, String permission) {
            this.user = user;
            this.pkg = pkg;
            this.permission = permission;
        }

        @Override
        public int compareTo(Entry o) {
            if (user != o.user) {
                return Integer.compare(user, o.user);
            }
            int c = pkg.compareTo(o.pkg);
            return c != 0 ? c : permission.compareTo(o.permission);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Entry && compareTo((Entry) o) == 0;
        }

        @Override
        public int hashCode() {
            return (user * 31 + pkg.hashCode()) * 31 + permission.hashCode();
        }
    }

    private final TreeSet<Entry> entries = new TreeSet<>();

    /** True when the permission is one that the service may take and give back. */
    static boolean validPermission(String permission) {
        for (String p : PERMISSIONS) {
            if (p.equals(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Notes that a granted permission was taken. False, noting nothing, for a user below 0, a name that is not a package, a
     * permission that is not one of the four, or a full ledger; an entry that is there already is a success.
     */
    boolean record(int user, String pkg, String permission) {
        if (user < 0 || !PolicyStore.validPackage(pkg) || !validPermission(permission)) {
            return false;
        }
        Entry e = new Entry(user, pkg, permission);
        if (entries.contains(e)) {
            return true;
        }
        if (entries.size() >= MAX_ENTRIES) {
            return false;
        }
        return entries.add(e);
    }

    /** The entries of the packages that are not in {@code denied}: what the service owes back. */
    List<Entry> owedTo(Set<String> denied) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (!denied.contains(e.pkg)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Forgets an entry: it was given back, or the package no longer asks for the permission or is gone. */
    void settle(Entry e) {
        entries.remove(e);
    }

    int size() {
        return entries.size();
    }

    String encode() {
        StringBuilder out = new StringBuilder();
        for (Entry e : entries) {
            out.append(e.user).append('\t').append(e.pkg).append('\t').append(e.permission).append('\n');
        }
        return out.toString();
    }

    /** @throws IllegalArgumentException for any text that is not exactly a file of the format */
    static RevokedLedger parse(String text) {
        RevokedLedger out = new RevokedLedger();
        if (text.isEmpty()) {
            return out;
        }
        if (text.charAt(text.length() - 1) != '\n') {
            throw new IllegalArgumentException("not a ledger");
        }
        for (String line : text.substring(0, text.length() - 1).split("\n", -1)) {
            String[] f = line.split("\t", -1);
            int before = out.size();
            if (f.length != 3 || !USER.matcher(f[0]).matches() || !out.record(Integer.parseInt(f[0]), f[1], f[2])
                    || out.size() != before + 1) {
                throw new IllegalArgumentException("a line is not an entry");
            }
        }
        return out;
    }
}
