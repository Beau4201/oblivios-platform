// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import com.android.server.compartments.Profiles.Cls;
import com.android.server.compartments.Profiles.Profile;
import com.android.server.compartments.Profiles.Value;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The policy of every app: a profile, per-class values and, for an Isolated app, a place, plus the defaults that an app the user
 * has not set follows. Immutable. A store that cannot be read back reads as {@link #EMPTY}, in which every app is Protected.
 */
final class PolicyStore {
    static final int MAX_APPS = 4096;
    private static final int MAGIC = 0x434D5031;
    /** Version 2 added the location class and the place of an app; a version 1 file reads with the new parts at their defaults. */
    private static final int VERSION = 2;
    private static final int VERSION_1_CLASSES = 3;
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");

    /** A profile, the classes the user set on top of it and the place of the persona (0 is Automatic). */
    static final class AppPolicy {
        static final AppPolicy PROTECTED = new AppPolicy(Profile.PROTECTED, new Value[Cls.values().length], 0);

        final Profile profile;
        /** The id of a place of {@link Places}, or 0 for the place that is chosen for the app (Automatic). */
        final int place;
        private final Value[] overrides;

        AppPolicy(Profile profile, Value[] overrides, int place) {
            if (place < 0) {
                throw new IllegalArgumentException("a place is positive, or 0 for Automatic");
            }
            Value[] copy = new Value[Cls.values().length];
            System.arraycopy(overrides, 0, copy, 0, Math.min(overrides.length, copy.length));
            this.profile = profile;
            this.overrides = copy;
            this.place = place;
        }

        Value value(Cls c) {
            Value o = overrides[c.ordinal()];
            return o != null ? o : Profiles.defaultValue(profile, c);
        }

        /** A new profile clears what the user set per class, and the place unless the app stays Isolated. */
        AppPolicy withProfile(Profile p) {
            return new AppPolicy(p, new Value[Cls.values().length], profile == Profile.ISOLATED && p == Profile.ISOLATED ? place : 0);
        }

        AppPolicy withPlace(int id) {
            return new AppPolicy(profile, overrides, id);
        }

        /** A value equal to the profile's own default is kept as no override. */
        AppPolicy withValue(Cls c, Value v) {
            if (!Profiles.isValid(c, v)) {
                throw new IllegalArgumentException(c + " cannot be " + v);
            }
            Value[] next = overrides.clone();
            next[c.ordinal()] = v == Profiles.defaultValue(profile, c) ? null : v;
            return new AppPolicy(profile, next, place);
        }
    }

    static final PolicyStore EMPTY = new PolicyStore(AppPolicy.PROTECTED, new TreeMap<>());

    final AppPolicy defaults;
    private final TreeMap<String, AppPolicy> apps;

    private PolicyStore(AppPolicy defaults, TreeMap<String, AppPolicy> apps) {
        this.defaults = defaults;
        this.apps = apps;
    }

    static boolean validPackage(String name) {
        return name != null && name.length() <= 255 && PACKAGE.matcher(name).matches();
    }

    /**
     * What an app without an entry of its own follows may be Protected or Isolated, with its sensors set; never more. It has no
     * place: a persona is made for an app that has an entry.
     */
    static boolean validDefaults(AppPolicy d) {
        return d.profile != Profile.TRUSTED
                && d.place == 0
                && d.value(Cls.LOOPBACK) == Profiles.defaultValue(d.profile, Cls.LOOPBACK)
                && d.value(Cls.SETTINGS) == Profiles.defaultValue(d.profile, Cls.SETTINGS);
    }

    AppPolicy policyOf(String pkg) {
        AppPolicy p = apps.get(pkg);
        return p != null ? p : defaults;
    }

    /** What is enforced for the app: its own value, or what an app without an entry gets (see {@link #valueWithoutEntry}). */
    Value value(String pkg, Cls c) {
        AppPolicy p = apps.get(pkg);
        return p != null ? p.value(c) : valueWithoutEntry(c);
    }

    /**
     * The value of a class for an app that has no entry. The defaults give it, except that its location is real: a persona is
     * made per app (the place is chosen for the app and the phone), so an app without an entry has none.
     */
    Value valueWithoutEntry(Cls c) {
        return c == Cls.LOCATION ? Value.REAL : defaults.value(c);
    }

    boolean hasEntry(String pkg) {
        return apps.containsKey(pkg);
    }

    Set<String> configured() {
        return Collections.unmodifiableSet(apps.keySet());
    }

    PolicyStore withDefaults(AppPolicy d) {
        if (!validDefaults(d)) {
            throw new IllegalArgumentException("defaults may be Protected or Isolated with their own loopback and settings");
        }
        return new PolicyStore(d, apps);
    }

    PolicyStore withApp(String pkg, AppPolicy p) {
        if (!validPackage(pkg)) {
            throw new IllegalArgumentException("not a package name");
        }
        if (!apps.containsKey(pkg) && apps.size() >= MAX_APPS) {
            throw new IllegalStateException("too many configured apps");
        }
        TreeMap<String, AppPolicy> next = new TreeMap<>(apps);
        next.put(pkg, p);
        return new PolicyStore(defaults, next);
    }

    /** The app follows the defaults again. */
    PolicyStore without(String pkg) {
        if (!apps.containsKey(pkg)) {
            return this;
        }
        TreeMap<String, AppPolicy> next = new TreeMap<>(apps);
        next.remove(pkg);
        return new PolicyStore(defaults, next);
    }

    byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            writePolicy(out, defaults);
            out.writeInt(apps.size());
            for (Map.Entry<String, AppPolicy> e : apps.entrySet()) {
                out.writeUTF(e.getKey());
                writePolicy(out, e.getValue());
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The store that {@code data} holds, or {@link #EMPTY} for anything that is not exactly a store this version wrote. */
    static PolicyStore decode(byte[] data) {
        if (data == null) {
            return EMPTY;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readInt() != MAGIC) {
                return EMPTY;
            }
            int version = in.readByte();
            if (version != 1 && version != VERSION) {
                return EMPTY;
            }
            AppPolicy d = readPolicy(in, version);
            if (d == null || !validDefaults(d)) {
                return EMPTY;
            }
            int n = in.readInt();
            if (n < 0 || n > MAX_APPS) {
                return EMPTY;
            }
            TreeMap<String, AppPolicy> apps = new TreeMap<>();
            for (int i = 0; i < n; i++) {
                String pkg = in.readUTF();
                AppPolicy p = readPolicy(in, version);
                if (p == null || !validPackage(pkg) || apps.put(pkg, p) != null) {
                    return EMPTY;
                }
            }
            return in.read() == -1 ? new PolicyStore(d, apps) : EMPTY;
        } catch (IOException e) {
            return EMPTY;
        }
    }

    private static void writePolicy(DataOutputStream out, AppPolicy p) throws IOException {
        out.writeByte(p.profile.ordinal());
        out.writeByte(Cls.values().length);
        for (Cls c : Cls.values()) {
            Value o = p.overrides[c.ordinal()];
            out.writeByte(o == null ? -1 : o.ordinal());
        }
        out.writeInt(p.place);
    }

    /**
     * Null for a value that is not valid; a file written with fewer classes reads with the rest at their defaults. A version 1
     * policy has three classes at most and no place.
     */
    private static AppPolicy readPolicy(DataInputStream in, int version) throws IOException {
        int profile = in.readUnsignedByte();
        int classes = in.readUnsignedByte();
        if (profile >= Profile.values().length || classes > (version == 1 ? VERSION_1_CLASSES : Cls.values().length)) {
            return null;
        }
        Value[] overrides = new Value[Cls.values().length];
        for (int i = 0; i < classes; i++) {
            int v = in.readByte();
            if (v == -1) {
                continue;
            }
            if (v < 0 || v >= Value.values().length || !Profiles.isValid(Cls.values()[i], Value.values()[v])) {
                return null;
            }
            overrides[i] = Value.values()[v];
        }
        int place = version == 1 ? 0 : in.readInt();
        return place < 0 ? null : new AppPolicy(Profile.values()[profile], overrides, place);
    }
}
