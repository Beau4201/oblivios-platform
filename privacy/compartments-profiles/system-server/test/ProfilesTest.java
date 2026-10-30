// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import com.android.server.compartments.PolicyStore.AppPolicy;
import com.android.server.compartments.Profiles.Cls;
import com.android.server.compartments.Profiles.Profile;
import com.android.server.compartments.Profiles.SensorMode;
import com.android.server.compartments.Profiles.Value;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.Map;

/** Host test of Profiles, PolicyStore and MirrorPlan: java -ea, no framework. Prints "ok" or fails an assertion. */
public final class ProfilesTest {
    private static int checks = 0;

    private static void check(boolean cond, String what) {
        checks++;
        if (!cond) {
            throw new AssertionError(what);
        }
    }

    private static boolean throwsIllegal(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        defaultsTable();
        pinnedNumbers();
        sensorModes();
        policyBehaviour();
        codec();
        mirrors();
        System.out.println("ok " + checks + " checks");
    }

    private static void defaultsTable() {
        // spec section 3, one cell each
        check(Profiles.defaultValue(Profile.TRUSTED, Cls.SENSORS) == Value.REAL, "trusted sensors");
        check(Profiles.defaultValue(Profile.PROTECTED, Cls.SENSORS) == Value.REAL, "protected sensors");
        check(Profiles.defaultValue(Profile.ISOLATED, Cls.SENSORS) == Value.SUBSTITUTE, "isolated sensors");
        check(Profiles.defaultValue(Profile.TRUSTED, Cls.LOOPBACK) == Value.REAL, "trusted loopback");
        check(Profiles.defaultValue(Profile.PROTECTED, Cls.LOOPBACK) == Value.BLOCKED, "protected loopback");
        check(Profiles.defaultValue(Profile.ISOLATED, Cls.LOOPBACK) == Value.BLOCKED, "isolated loopback");
        check(Profiles.defaultValue(Profile.TRUSTED, Cls.SETTINGS) == Value.REAL, "trusted settings");
        check(Profiles.defaultValue(Profile.PROTECTED, Cls.SETTINGS) == Value.BLOCKED, "protected settings");
        check(Profiles.defaultValue(Profile.ISOLATED, Cls.SETTINGS) == Value.BLOCKED, "isolated settings");
        check(Profiles.isValid(Cls.SENSORS, Value.SUBSTITUTE), "sensors may be substituted");
        check(!Profiles.isValid(Cls.LOOPBACK, Value.SUBSTITUTE), "loopback is never substituted");
        check(!Profiles.isValid(Cls.SETTINGS, Value.SUBSTITUTE), "settings are never substituted");
    }

    /** The numbers of the Binder interface constants (ICompartmentsPolicy): the order of the enums is the wire format. */
    private static void pinnedNumbers() {
        check(Profile.TRUSTED.ordinal() == 0, "PROFILE_TRUSTED is 0");
        check(Profile.PROTECTED.ordinal() == 1, "PROFILE_PROTECTED is 1");
        check(Profile.ISOLATED.ordinal() == 2, "PROFILE_ISOLATED is 2");
        check(Cls.SENSORS.ordinal() == 0, "CLASS_SENSORS is 0");
        check(Cls.LOOPBACK.ordinal() == 1, "CLASS_LOOPBACK is 1");
        check(Cls.SETTINGS.ordinal() == 2, "CLASS_SETTINGS is 2");
        check(Value.REAL.ordinal() == 0, "VALUE_REAL is 0");
        check(Value.SUBSTITUTE.ordinal() == 1, "VALUE_SUBSTITUTE is 1");
        check(Value.BLOCKED.ordinal() == 2, "VALUE_BLOCKED is 2");
        check(Profile.values().length == 3 && Cls.values().length == 3 && Value.values().length == 3, "three of each");
    }

    private static void sensorModes() {
        check(Profiles.sensorMode(Profile.TRUSTED, Value.REAL) == SensorMode.RAW, "trusted real is raw");
        check(Profiles.sensorMode(Profile.PROTECTED, Value.REAL) == SensorMode.FOLDED, "protected real is folded");
        check(Profiles.sensorMode(Profile.ISOLATED, Value.REAL) == SensorMode.FOLDED, "isolated real motion is folded");
        check(Profiles.sensorMode(Profile.TRUSTED, Value.SUBSTITUTE) == SensorMode.SUBSTITUTE, "trusted may substitute");
        check(Profiles.sensorMode(Profile.PROTECTED, Value.BLOCKED) == SensorMode.BLOCKED, "blocked");
    }

    private static void policyBehaviour() {
        PolicyStore s = PolicyStore.EMPTY;
        check(s.value("com.bank.app", Cls.SENSORS) == Value.REAL, "an unknown app follows the defaults");
        check(s.policyOf("com.bank.app").profile == Profile.PROTECTED, "a new app is Protected");

        AppPolicy bank = AppPolicy.PROTECTED.withProfile(Profile.TRUSTED);
        s = s.withApp("com.bank.app", bank);
        check(s.value("com.bank.app", Cls.LOOPBACK) == Value.REAL, "trusted app loopback");

        // defaults: Isolated for unconfigured apps, the configured app keeps its own
        s = s.withDefaults(AppPolicy.PROTECTED.withProfile(Profile.ISOLATED));
        check(s.value("com.game.app", Cls.SENSORS) == Value.SUBSTITUTE, "default Isolated reaches an unconfigured app");
        check(s.policyOf("com.bank.app").profile == Profile.TRUSTED, "a configured app keeps its profile");

        check(throwsIllegal(() -> PolicyStore.EMPTY.withDefaults(AppPolicy.PROTECTED.withProfile(Profile.TRUSTED))),
                "Trusted cannot be the default");
        check(throwsIllegal(() -> PolicyStore.EMPTY.withDefaults(
                AppPolicy.PROTECTED.withValue(Cls.LOOPBACK, Value.REAL))), "loopback cannot be opened by default");
        check(throwsIllegal(() -> PolicyStore.EMPTY.withDefaults(
                AppPolicy.PROTECTED.withValue(Cls.SETTINGS, Value.REAL))), "settings cannot be opened by default");
        check(PolicyStore.EMPTY.withDefaults(AppPolicy.PROTECTED.withValue(Cls.SENSORS, Value.SUBSTITUTE))
                .value("x.y", Cls.SENSORS) == Value.SUBSTITUTE, "default sensors may be substituted");

        check(throwsIllegal(() -> AppPolicy.PROTECTED.withValue(Cls.LOOPBACK, Value.SUBSTITUTE)), "invalid value refused");

        // a new profile clears the per-class values; a value equal to the default is no override
        AppPolicy custom = AppPolicy.PROTECTED.withValue(Cls.SENSORS, Value.SUBSTITUTE);
        check(custom.value(Cls.SENSORS) == Value.SUBSTITUTE, "override read back");
        check(custom.withProfile(Profile.ISOLATED).value(Cls.SENSORS) == Value.SUBSTITUTE, "isolated default");
        check(custom.withProfile(Profile.TRUSTED).value(Cls.SENSORS) == Value.REAL, "profile change clears overrides");

        check(Arrays.equals(
                PolicyStore.EMPTY.withApp("x.y", AppPolicy.PROTECTED.withValue(Cls.SENSORS, Value.REAL)).encode(),
                PolicyStore.EMPTY.withApp("x.y", AppPolicy.PROTECTED).encode()), "a value equal to the default is no override");

        s = s.without("com.bank.app");
        check(s.policyOf("com.bank.app").profile == Profile.ISOLATED, "without() follows the defaults again");

        for (String bad : new String[] {"", "nodot", "a,b.c", "a.b\nc.d", "a.b c.d", "1a.b", "a..b", null}) {
            check(!PolicyStore.validPackage(bad), "invalid package name " + bad);
        }
        check(throwsIllegal(() -> PolicyStore.EMPTY.withApp("a,b.c", bank)), "invalid package refused");
        check(PolicyStore.validPackage("org.freeholdos.exit"), "valid package name");

        PolicyStore many = PolicyStore.EMPTY;
        for (int i = 0; i < PolicyStore.MAX_APPS; i++) {
            many = many.withApp("p.a" + i, bank);
        }
        PolicyStore full = many;
        check(throwsIllegal(() -> full.withApp("p.more", bank)), "the number of apps is bounded");
        check(full.withApp("p.a0", AppPolicy.PROTECTED).hasEntry("p.a0"), "an existing entry can still change when full");
    }

    private static byte[] sample() {
        return PolicyStore.EMPTY
                .withDefaults(AppPolicy.PROTECTED.withProfile(Profile.ISOLATED))
                .withApp("com.bank.app", AppPolicy.PROTECTED.withProfile(Profile.TRUSTED))
                .withApp("com.game.app", AppPolicy.PROTECTED.withValue(Cls.SENSORS, Value.BLOCKED))
                .encode();
    }

    private static void codec() throws Exception {
        PolicyStore back = PolicyStore.decode(sample());
        check(back.defaults.profile == Profile.ISOLATED, "defaults round trip");
        check(back.value("com.bank.app", Cls.SETTINGS) == Value.REAL, "trusted round trip");
        check(back.value("com.game.app", Cls.SENSORS) == Value.BLOCKED, "override round trip");
        check(back.configured().size() == 2, "entries round trip");
        check(Arrays.equals(back.encode(), sample()), "encoding is stable");

        check(PolicyStore.decode(null) == PolicyStore.EMPTY, "null reads as empty");
        check(PolicyStore.decode(new byte[0]) == PolicyStore.EMPTY, "empty file reads as empty");
        byte[] good = sample();
        for (int n = 0; n < good.length; n++) {
            check(PolicyStore.decode(Arrays.copyOf(good, n)) == PolicyStore.EMPTY, "truncated at " + n);
        }
        byte[] trailing = Arrays.copyOf(good, good.length + 1);
        check(PolicyStore.decode(trailing) == PolicyStore.EMPTY, "trailing byte");
        byte[] badMagic = good.clone();
        badMagic[0] ^= 1;
        check(PolicyStore.decode(badMagic) == PolicyStore.EMPTY, "bad magic");
        byte[] badVersion = good.clone();
        badVersion[4] = 9;
        check(PolicyStore.decode(badVersion) == PolicyStore.EMPTY, "bad version");
        byte[] badProfile = good.clone();
        badProfile[5] = 7;
        check(PolicyStore.decode(badProfile) == PolicyStore.EMPTY, "bad profile ordinal");

        // a Trusted default is refused when read, as when written
        byte[] trusted = good.clone();
        trusted[5] = (byte) Profile.TRUSTED.ordinal();
        check(PolicyStore.decode(trusted) == PolicyStore.EMPTY, "Trusted default in the file");

        check(PolicyStore.decode(raw(Profile.PROTECTED, new int[] {-1, 2, -1}, new String[0])) != PolicyStore.EMPTY,
                "blocked sensors are valid");
        check(PolicyStore.decode(raw(Profile.PROTECTED, new int[] {-1, 1, -1}, new String[0])) == PolicyStore.EMPTY,
                "substituted loopback in the file is refused");
        check(PolicyStore.decode(raw(Profile.PROTECTED, new int[] {-1, -1, -1}, new String[] {"a.b", "a.b"})) == PolicyStore.EMPTY,
                "duplicate package");
        check(PolicyStore.decode(raw(Profile.PROTECTED, new int[] {-1, -1, -1}, new String[] {"nodot"})) == PolicyStore.EMPTY,
                "invalid package in the file");
        check(PolicyStore.decode(withEntryOverrides(new int[] {-1, 2, -1})) != PolicyStore.EMPTY, "an app with blocked loopback");
        check(PolicyStore.decode(withEntryOverrides(new int[] {-1, 1, -1})) == PolicyStore.EMPTY, "an app with substituted loopback");
        check(PolicyStore.decode(withEntryOverrides(new int[] {-1, -1, 9})) == PolicyStore.EMPTY, "an app with an unknown value");
        check(PolicyStore.decode(withCount(PolicyStore.MAX_APPS)) != PolicyStore.EMPTY, "a full file is valid");
        check(PolicyStore.decode(withCount(PolicyStore.MAX_APPS + 1)) == PolicyStore.EMPTY, "too many apps in the file");
        // a file written before a class existed reads with that class at its default
        check(PolicyStore.decode(raw(Profile.PROTECTED, new int[] {-1}, new String[] {"a.b"})) != PolicyStore.EMPTY,
                "a shorter class list is accepted");
        check(PolicyStore.decode(raw(Profile.PROTECTED, new int[] {-1, -1, -1, -1}, new String[0])) == PolicyStore.EMPTY,
                "a longer class list than this version knows is refused");
    }

    /** A file whose one app, a.b, has the given per-class override bytes. */
    private static byte[] withEntryOverrides(int[] overrides) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x434D5031);
        out.writeByte(1);
        out.writeByte(Profile.PROTECTED.ordinal());
        out.writeByte(0);
        out.writeInt(1);
        out.writeUTF("a.b");
        out.writeByte(Profile.PROTECTED.ordinal());
        out.writeByte(overrides.length);
        for (int o : overrides) {
            out.writeByte(o);
        }
        return bytes.toByteArray();
    }

    private static byte[] withCount(int count) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x434D5031);
        out.writeByte(1);
        out.writeByte(Profile.PROTECTED.ordinal());
        out.writeByte(0);
        out.writeInt(count);
        for (int i = 0; i < count; i++) {
            out.writeUTF("p.a" + i);
            out.writeByte(Profile.PROTECTED.ordinal());
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    /** A file with the given default overrides and, for every package, a Protected entry with no overrides. */
    private static byte[] raw(Profile defaults, int[] overrides, String[] pkgs) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x434D5031);
        out.writeByte(1);
        out.writeByte(defaults.ordinal());
        out.writeByte(overrides.length);
        for (int o : overrides) {
            out.writeByte(o);
        }
        out.writeInt(pkgs.length);
        for (String p : pkgs) {
            out.writeUTF(p);
            out.writeByte(Profile.PROTECTED.ordinal());
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static void mirrors() {
        PolicyStore s = PolicyStore.EMPTY
                .withDefaults(AppPolicy.PROTECTED.withProfile(Profile.ISOLATED))
                .withApp("com.bank.app", AppPolicy.PROTECTED.withProfile(Profile.TRUSTED))
                .withApp("com.a.app", AppPolicy.PROTECTED.withProfile(Profile.TRUSTED).withValue(Cls.SETTINGS, Value.BLOCKED))
                .withApp("com.z.app", AppPolicy.PROTECTED)
                .withApp("com.gone.app", AppPolicy.PROTECTED.withProfile(Profile.TRUSTED));
        check(MirrorPlan.settingsRealPackages(s).equals("com.bank.app,com.gone.app"), "settings list is sorted, Real only");
        check(MirrorPlan.settingsRealPackages(PolicyStore.EMPTY).isEmpty(), "no list when nobody is trusted");
        check(MirrorPlan.loopbackAllowed(s).toString().equals("[com.a.app, com.bank.app, com.gone.app]"), "loopback holders");

        Map<String, Integer> uids = Map.of("com.bank.app", 10200, "com.a.app", 10100, "com.z.app", 10300);
        String file = MirrorPlan.sensorModeFile(s, pkg -> uids.getOrDefault(pkg, -1), 0xABCDEF0123456789L);
        // default is Isolated -> SUBSTITUTE; com.a.app and com.bank.app are Trusted -> RAW; com.z.app is Protected -> FOLDED;
        // com.gone.app is not installed and has no line
        check(file.equals("s abcdef0123456789\nd SUBSTITUTE\na 10100 RAW\na 10200 RAW\na 10300 FOLDED\n"), "sensor file: " + file);

        String plain = MirrorPlan.sensorModeFile(PolicyStore.EMPTY, pkg -> -1, 1L);
        check(plain.equals("s 0000000000000001\nd FOLDED\n"), "an empty store gives the status quo: " + plain);
        // a configured app whose mode equals the default has no line
        PolicyStore same = PolicyStore.EMPTY.withApp("com.z.app", AppPolicy.PROTECTED);
        check(MirrorPlan.sensorModeFile(same, pkg -> 10300, 1L).equals("s 0000000000000001\nd FOLDED\n"), "no line equal to the default");

        // an app id of the second user is the same app: the line holds the app id, not the uid of one user
        check(MirrorPlan.sensorModeFile(s, pkg -> pkg.equals("com.bank.app") ? 110200 : -1, 1L)
                .equals("s 0000000000000001\nd SUBSTITUTE\na 10200 RAW\n"), "an app id is written, not a uid");

        // two packages with one app id get the more protective mode, whatever their names are
        PolicyStore shared = PolicyStore.EMPTY
                .withApp("com.a.shared", AppPolicy.PROTECTED.withProfile(Profile.TRUSTED))
                .withApp("com.b.shared", AppPolicy.PROTECTED.withValue(Cls.SENSORS, Value.SUBSTITUTE));
        check(MirrorPlan.sensorModeFile(shared, pkg -> 10400, 1L).equals("s 0000000000000001\nd FOLDED\na 10400 SUBSTITUTE\n"),
                "shared app id, loose first");
        PolicyStore sharedReverse = PolicyStore.EMPTY
                .withApp("com.a.shared", AppPolicy.PROTECTED.withValue(Cls.SENSORS, Value.BLOCKED))
                .withApp("com.b.shared", AppPolicy.PROTECTED.withProfile(Profile.TRUSTED));
        check(MirrorPlan.sensorModeFile(sharedReverse, pkg -> 10400, 1L).equals("s 0000000000000001\nd FOLDED\na 10400 BLOCKED\n"),
                "shared app id, strict first");
        check(MirrorPlan.stricter(Profiles.SensorMode.RAW, Profiles.SensorMode.FOLDED) == Profiles.SensorMode.FOLDED, "raw < folded");
        check(MirrorPlan.stricter(Profiles.SensorMode.BLOCKED, Profiles.SensorMode.SUBSTITUTE) == Profiles.SensorMode.BLOCKED, "blocked > substitute");
        check(MirrorPlan.stricter(Profiles.SensorMode.SUBSTITUTE, Profiles.SensorMode.FOLDED) == Profiles.SensorMode.SUBSTITUTE, "substitute > folded");

        // a package that is not an ordinary app (a platform uid, a sandbox or isolated uid) is left out and the file stays valid
        for (int uid : new int[] {0, 1000, 9999, 20000, 25000, 90000, 99999}) {
            PolicyStore one = PolicyStore.EMPTY.withApp("com.x.app", AppPolicy.PROTECTED.withProfile(Profile.ISOLATED));
            check(MirrorPlan.sensorModeFile(one, pkg -> uid, 1L).equals("s 0000000000000001\nd FOLDED\n"), "uid " + uid + " has no line");
        }
        check(MirrorPlan.sensorModeFile(PolicyStore.EMPTY.withApp("com.x.app", AppPolicy.PROTECTED.withProfile(Profile.ISOLATED)),
                pkg -> 19999, 1L).contains("a 19999 SUBSTITUTE"), "the last ordinary app id is written");
        check(MirrorPlan.sensorModeFile(PolicyStore.EMPTY.withApp("com.x.app", AppPolicy.PROTECTED.withProfile(Profile.ISOLATED)),
                pkg -> 10000, 1L).contains("a 10000 SUBSTITUTE"), "the first ordinary app id is written");
    }
}
