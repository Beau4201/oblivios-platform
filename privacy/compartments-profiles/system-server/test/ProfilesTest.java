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
        sensorActivity();
        placeBehaviour();
        codecVersion2();
        personaMirrors();
        keptPlacesAndRestarts();
        isolatedDefaults();
        platformPackagesFollowNoDefaults();
        withheldAppIds();
        largestStore();
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
        check(Profiles.defaultValue(Profile.TRUSTED, Cls.LOCATION) == Value.REAL, "trusted location");
        check(Profiles.defaultValue(Profile.PROTECTED, Cls.LOCATION) == Value.REAL, "protected location");
        check(Profiles.defaultValue(Profile.ISOLATED, Cls.LOCATION) == Value.SUBSTITUTE, "isolated location");
        check(Profiles.isValid(Cls.LOCATION, Value.REAL), "location may be real");
        check(Profiles.isValid(Cls.LOCATION, Value.SUBSTITUTE), "location may be substituted");
        check(Profiles.isValid(Cls.LOCATION, Value.BLOCKED), "location may be blocked");
    }

    /** The numbers of the Binder interface constants (ICompartmentsPolicy): the order of the enums is the wire format. */
    private static void pinnedNumbers() {
        check(Profile.TRUSTED.ordinal() == 0, "PROFILE_TRUSTED is 0");
        check(Profile.PROTECTED.ordinal() == 1, "PROFILE_PROTECTED is 1");
        check(Profile.ISOLATED.ordinal() == 2, "PROFILE_ISOLATED is 2");
        check(Cls.SENSORS.ordinal() == 0, "CLASS_SENSORS is 0");
        check(Cls.LOOPBACK.ordinal() == 1, "CLASS_LOOPBACK is 1");
        check(Cls.SETTINGS.ordinal() == 2, "CLASS_SETTINGS is 2");
        check(Cls.LOCATION.ordinal() == 3, "CLASS_LOCATION is 3");
        check(Value.REAL.ordinal() == 0, "VALUE_REAL is 0");
        check(Value.SUBSTITUTE.ordinal() == 1, "VALUE_SUBSTITUTE is 1");
        check(Value.BLOCKED.ordinal() == 2, "VALUE_BLOCKED is 2");
        check(Profile.values().length == 3 && Cls.values().length == 4 && Value.values().length == 3, "three profiles and values, four classes");
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

        // the identity reads Custom (ADR-209) when a class differs from the profile, and only then; the service answers
        // getIdentity from this (the info page of an app in Settings reads that answer)
        check(!AppPolicy.PROTECTED.isCustom(), "a plain profile is no custom identity");
        check(!AppPolicy.PROTECTED.withProfile(Profile.ISOLATED).isCustom(), "a plain isolated profile is no custom identity");
        check(custom.isCustom(), "a class that differs from the profile is a custom identity");
        check(AppPolicy.PROTECTED.withProfile(Profile.ISOLATED).withValue(Cls.LOCATION, Value.REAL).isCustom(),
                "isolated with the real location is custom");
        check(AppPolicy.PROTECTED.withValue(Cls.LOCATION, Value.SUBSTITUTE).isCustom(), "the last class counts too");
        check(!AppPolicy.PROTECTED.withValue(Cls.SENSORS, Value.REAL).isCustom(), "a value equal to the default is not custom");
        check(!custom.withProfile(Profile.TRUSTED).isCustom(), "a new profile clears the custom identity");
        check(!PolicyStore.EMPTY.policyOf("x.y").isCustom(), "an app without an entry follows the defaults, not custom");

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
        // the loopback list: the Trusted apps that are installed (com.gone.app is not), ascending; a Protected app is not in it
        check(MirrorPlan.loopbackAppIds(s, pkg -> uids.getOrDefault(pkg, -1)).equals("10100,10200"), "loopback app ids");
        check(MirrorPlan.loopbackAppIds(PolicyStore.EMPTY, pkg -> 10100).isEmpty(), "no Trusted app, no loopback ids");
        check(MirrorPlan.loopbackAppIds(s, pkg -> 1000).isEmpty() && MirrorPlan.loopbackAppIds(s, pkg -> 20000).isEmpty(),
                "a platform uid or a sandbox uid is no loopback id");
        check(MirrorPlan.loopbackAppIds(s, pkg -> 1010100).equals("10100"), "an app id is the uid of any user");

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

    private static final AppPolicy ISO = AppPolicy.PROTECTED.withProfile(Profile.ISOLATED);

    /** The optional activity line of the sensor file: after the default, only when the phone is not still. */
    private static void sensorActivity() {
        PolicyStore s = PolicyStore.EMPTY
                .withDefaults(ISO)
                .withApp("com.bank.app", AppPolicy.PROTECTED.withProfile(Profile.TRUSTED))
                .withApp("com.z.app", AppPolicy.PROTECTED);
        Map<String, Integer> uids = Map.of("com.bank.app", 10200, "com.z.app", 10300);
        MirrorPlan.UidResolver r = pkg -> uids.getOrDefault(pkg, -1);
        String still = MirrorPlan.sensorModeFile(s, r, 1L);
        check(still.equals("s 0000000000000001\nd SUBSTITUTE\na 10200 RAW\na 10300 FOLDED\n"), "no activity line: " + still);
        check(MirrorPlan.sensorModeFile(s, r, 1L, ActivityEstimator.Activity.STILL).equals(still), "still writes no line");
        check(MirrorPlan.sensorModeFile(s, r, 1L, ActivityEstimator.Activity.WALKING)
                .equals("s 0000000000000001\nd SUBSTITUTE\nt WALKING\na 10200 RAW\na 10300 FOLDED\n"), "walking line");
        check(MirrorPlan.sensorModeFile(s, r, 1L, ActivityEstimator.Activity.DRIVING)
                .equals("s 0000000000000001\nd SUBSTITUTE\nt DRIVING\na 10200 RAW\na 10300 FOLDED\n"), "driving line");
        check(MirrorPlan.sensorModeFile(PolicyStore.EMPTY, pkg -> -1, 1L, ActivityEstimator.Activity.DRIVING)
                .equals("s 0000000000000001\nd FOLDED\nt DRIVING\n"), "the line stands without app lines");
    }

    private static void placeBehaviour() {
        check(ISO.place == 0, "a policy starts Automatic");
        check(ISO.withPlace(7).place == 7, "a place is kept");
        check(throwsIllegal(() -> ISO.withPlace(-1)), "a negative place is refused");
        check(ISO.withPlace(7).withPlace(0).place == 0, "back to Automatic");
        check(ISO.withPlace(7).withProfile(Profile.ISOLATED).place == 7, "an app that stays Isolated keeps its place");
        check(ISO.withPlace(7).withProfile(Profile.PROTECTED).place == 0, "leaving Isolated for Protected resets the place");
        check(ISO.withPlace(7).withProfile(Profile.TRUSTED).place == 0, "leaving Isolated for Trusted resets the place");
        check(AppPolicy.PROTECTED.withPlace(7).withProfile(Profile.ISOLATED).place == 0, "entering Isolated starts Automatic");
        check(ISO.withPlace(7).withValue(Cls.SENSORS, Value.BLOCKED).place == 7, "a class value keeps the place");
        check(ISO.withPlace(7).withValue(Cls.LOCATION, Value.BLOCKED).place == 7, "a blocked location keeps the place");
        check(throwsIllegal(() -> PolicyStore.EMPTY.withDefaults(ISO.withPlace(7))), "the defaults stay Automatic");
        check(PolicyStore.EMPTY.withDefaults(ISO).defaults.place == 0, "Isolated defaults are valid");

        check(ISO.value(Cls.LOCATION) == Value.SUBSTITUTE, "an Isolated app substitutes its location");
        check(AppPolicy.PROTECTED.withValue(Cls.LOCATION, Value.SUBSTITUTE).value(Cls.LOCATION) == Value.SUBSTITUTE,
                "a Protected app may substitute its location");
        check(AppPolicy.PROTECTED.withValue(Cls.LOCATION, Value.REAL).value(Cls.LOCATION) == Value.REAL, "real location");
        check(ISO.withValue(Cls.LOCATION, Value.REAL).value(Cls.LOCATION) == Value.REAL, "an Isolated app may keep its real location");

        // an app without an entry follows the defaults; only Isolated defaults reach its location (D5)
        PolicyStore d = PolicyStore.EMPTY.withDefaults(ISO);
        check(d.value("com.game.app", Cls.SENSORS) == Value.SUBSTITUTE, "default Isolated reaches the sensors of an unconfigured app");
        check(d.value("com.game.app", Cls.LOCATION) == Value.SUBSTITUTE, "default Isolated substitutes the location of an unconfigured app");
        check(d.valueWithoutEntry(Cls.LOCATION) == Value.SUBSTITUTE && d.valueWithoutEntry(Cls.SENSORS) == Value.SUBSTITUTE,
                "the value of an app without an entry");
        PolicyStore p = PolicyStore.EMPTY.withDefaults(AppPolicy.PROTECTED);
        check(p.value("com.game.app", Cls.LOCATION) == Value.REAL && PolicyStore.EMPTY.valueWithoutEntry(Cls.LOCATION) == Value.REAL,
                "Protected defaults leave the location of an unconfigured app real");
        check(d.withApp("com.game.app", ISO).value("com.game.app", Cls.LOCATION) == Value.SUBSTITUTE, "a configured app has its own");
    }

    private static byte[] hex(String h) {
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    /** The bytes of sample() as it stood at commit 5aeb09a, when the store was version 1 (three classes, no place). */
    private static final String VERSION_1_SAMPLE = "434d5031010203ffffff00000002000c636f6d2e62616e6b2e6170700003ffffff"
            + "000c636f6d2e67616d652e617070010302ffff";

    /** A file with one app a.b whose policy has the given version, class bytes and place; the defaults have no override. */
    private static byte[] app(int version, int[] classes, int place) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x434D5031);
        out.writeByte(version);
        out.writeByte(Profile.PROTECTED.ordinal());
        out.writeByte(0);
        if (version >= 2) {
            out.writeInt(0);
        }
        out.writeInt(1);
        out.writeUTF("a.b");
        out.writeByte(Profile.ISOLATED.ordinal());
        out.writeByte(classes.length);
        for (int c : classes) {
            out.writeByte(c);
        }
        if (version >= 2) {
            out.writeInt(place);
        }
        return bytes.toByteArray();
    }

    private static void codecVersion2() throws Exception {
        check(sample()[4] == 2, "a store is written as version 2");
        PolicyStore old = PolicyStore.decode(hex(VERSION_1_SAMPLE));
        check(old != PolicyStore.EMPTY, "a version 1 store is read");
        check(old.defaults.profile == Profile.ISOLATED, "version 1 defaults");
        check(old.value("com.bank.app", Cls.SETTINGS) == Value.REAL, "version 1 trusted app");
        check(old.value("com.game.app", Cls.SENSORS) == Value.BLOCKED, "version 1 override");
        check(old.value("com.game.app", Cls.LOCATION) == Value.REAL, "version 1 location is the default of the profile");
        check(old.value("com.bank.app", Cls.LOCATION) == Value.REAL, "version 1 trusted location");
        check(old.policyOf("com.bank.app").place == 0 && old.policyOf("com.game.app").place == 0, "version 1 has no place");
        check(old.configured().size() == 2, "version 1 entries");
        check(Arrays.equals(old.encode(), sample()), "version 1 is rewritten as the same store in version 2");

        PolicyStore p = PolicyStore.EMPTY
                .withApp("com.a.app", ISO.withPlace(12))
                .withApp("com.b.app", ISO.withValue(Cls.LOCATION, Value.BLOCKED))
                .withApp("com.c.app", AppPolicy.PROTECTED.withValue(Cls.LOCATION, Value.SUBSTITUTE));
        PolicyStore back = PolicyStore.decode(p.encode());
        check(back != PolicyStore.EMPTY, "a version 2 store is read");
        check(back.policyOf("com.a.app").place == 12, "place round trip");
        check(back.policyOf("com.b.app").place == 0, "no place round trip");
        check(back.value("com.b.app", Cls.LOCATION) == Value.BLOCKED, "blocked location round trip");
        check(back.value("com.c.app", Cls.LOCATION) == Value.SUBSTITUTE, "substituted location of a Protected app round trip");
        check(back.value("com.a.app", Cls.LOCATION) == Value.SUBSTITUTE, "default location of an Isolated app round trip");
        check(Arrays.equals(back.encode(), p.encode()), "version 2 encoding is stable");
        byte[] good = p.encode();
        for (int n = 0; n < good.length; n++) {
            check(PolicyStore.decode(Arrays.copyOf(good, n)) == PolicyStore.EMPTY, "version 2 truncated at " + n);
        }
        check(PolicyStore.decode(Arrays.copyOf(good, good.length + 1)) == PolicyStore.EMPTY, "version 2 trailing byte");

        int[] none = {-1, -1, -1, -1};
        check(PolicyStore.decode(app(2, none, 5)) != PolicyStore.EMPTY, "a version 2 app with a place");
        check(PolicyStore.decode(app(2, none, 0)) != PolicyStore.EMPTY, "a version 2 app without a place");
        check(PolicyStore.decode(app(2, none, -1)) == PolicyStore.EMPTY, "a place of -1 is refused");
        check(PolicyStore.decode(app(2, new int[] {-1, -1, -1, 9}, 0)) == PolicyStore.EMPTY, "an unknown location value is refused");
        check(PolicyStore.decode(app(2, new int[] {-1, -1, -1, 2}, 0)) != PolicyStore.EMPTY, "a blocked location is valid");
        check(PolicyStore.decode(app(2, new int[] {-1, -1, -1, 1}, 0)) != PolicyStore.EMPTY, "a substituted location is valid");
        check(PolicyStore.decode(app(2, new int[] {-1, 1, -1, -1}, 0)) == PolicyStore.EMPTY, "a substituted loopback is refused in version 2");
        check(PolicyStore.decode(app(2, new int[] {-1, -1, -1, -1, -1}, 0)) == PolicyStore.EMPTY, "a fifth class is refused");
        check(PolicyStore.decode(app(2, new int[] {-1}, 0)) != PolicyStore.EMPTY, "a shorter class list is accepted in version 2");
        check(PolicyStore.decode(app(3, none, 0)) == PolicyStore.EMPTY, "version 3 is refused");
        check(PolicyStore.decode(app(0, none, 0)) == PolicyStore.EMPTY, "version 0 is refused");
        for (int version : new int[] {0, 3, 9, 127, -1, -128}) {  // a version 2 layout under a version that is not 2
            byte[] other = app(2, none, 0);
            other[4] = (byte) version;
            check(PolicyStore.decode(other) == PolicyStore.EMPTY, "version " + version + " is refused");
        }
        check(PolicyStore.decode(app(1, new int[] {-1, -1, -1}, 0)) != PolicyStore.EMPTY, "a version 1 app with three classes");
        check(PolicyStore.decode(app(1, none, 0)) == PolicyStore.EMPTY, "a version 1 file with a fourth class byte is refused");

        // the defaults carry no place, in the file as in the API
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x434D5031);
        out.writeByte(2);
        out.writeByte(Profile.ISOLATED.ordinal());
        out.writeByte(4);
        out.write(new byte[] {-1, -1, -1, -1});
        out.writeInt(5);
        out.writeInt(0);
        check(PolicyStore.decode(bytes.toByteArray()) == PolicyStore.EMPTY, "defaults with a place are refused");
    }

    private static final String PLACES = "1\tAmsterdam\tNL\t52.37\t4.89\tEurope/Amsterdam\t204\n"
            + "2\tMaastricht\tNL\t50.85\t5.69\tEurope/Amsterdam\t204\n"
            + "3\tGroningen\tNL\t53.55\t6.57\tEurope/Amsterdam\t204\n"
            + "4\tBerlin\tDE\t52.52\t13.40\tEurope/Berlin\t262\n";

    private static boolean far(int place) {
        return place == 2 || place == 3;
    }

    private static void personaMirrors() throws Exception {
        Places places = Places.parse(PLACES);
        PolicyStore s = PolicyStore.EMPTY
                .withApp("com.a.app", ISO)
                .withApp("com.b.app", ISO.withPlace(1))
                .withApp("com.c.app", AppPolicy.PROTECTED)
                .withApp("com.d.app", AppPolicy.PROTECTED.withValue(Cls.LOCATION, Value.SUBSTITUTE))
                .withApp("com.e.app", ISO.withValue(Cls.LOCATION, Value.BLOCKED))
                .withApp("com.f.app", AppPolicy.PROTECTED.withProfile(Profile.TRUSTED))
                .withApp("com.gone.app", ISO)
                .withApp("com.sys.app", ISO)
                .withApp("com.g.app", ISO.withPlace(99));
        Map<String, Integer> uids = Map.of("com.a.app", 10100, "com.b.app", 10200, "com.c.app", 10300, "com.d.app", 10400,
                "com.e.app", 10500, "com.f.app", 10600, "com.sys.app", 1000, "com.g.app", 10700);
        long salt = 0xABCDEF0123456789L;
        String file = MirrorPlan.personaFile(s, pkg -> uids.getOrDefault(pkg, -1), salt, places, "NL", 52.37, 4.89);
        PersonaFile pf = PersonaFile.parse(file);
        check(pf.salt() == salt, "the persona file carries the salt");
        check(far(pf.placeOf(10100)), "an Automatic place is far from the real position: " + pf.placeOf(10100));
        check(pf.placeOf(10200) == 1, "the place the user chose is kept, even close to the real position");
        check(pf.placeOf(10300) == 0, "a Protected app has no persona");
        check(far(pf.placeOf(10400)), "a Protected app that substitutes its location has a persona");
        check(pf.placeOf(10500) == 0, "a blocked location has no persona");
        check(pf.placeOf(10600) == 0, "a Trusted app has no persona");
        check(pf.placeOf(10700) == 0, "an unknown place is left out");
        check(pf.hasPersona(10700), "an app whose place is unknown has a persona without a place");
        check(pf.hasPersona(10100) && pf.hasPersona(10200) && pf.hasPersona(10400), "the apps with a place have a persona");
        check(!pf.hasPersona(10300) && !pf.hasPersona(10500) && !pf.hasPersona(10600) && !pf.hasPersona(1000),
                "a Protected, a blocked, a Trusted and a system app have none");
        check(file.split("\n").length == 5, "one salt line, three apps with a place and one without: " + file);
        check(pf.placeOf(110100) == pf.placeOf(10100), "the persona is the app's in every user");
        check(MirrorPlan.personaFile(s, pkg -> uids.getOrDefault(pkg, -1), salt, places, "NL", 52.37, 4.89).equals(file),
                "the choice is stable");
        check(file.equals(MirrorPlan.personaFile(s, pkg -> uids.getOrDefault(pkg, -1) + (uids.containsKey(pkg) ? 100000 : 0), salt,
                places, "NL", 52.37, 4.89)), "the file holds app ids, not uids");

        // the Automatic choice is salt- and app-seeded: both far places are used, and a place is never a near one
        boolean two = false;
        boolean three = false;
        for (int id = 10000; id < 10200; id++) {
            final int appId = id;
            int p = PersonaFile.parse(MirrorPlan.personaFile(PolicyStore.EMPTY.withApp("com.a.app", ISO), pkg -> appId, salt, places,
                    "NL", 52.37, 4.89)).placeOf(appId);
            check(far(p), "far place for app " + appId);
            two |= p == 2;
            three |= p == 3;
        }
        check(two && three, "the apps are spread over the far places");
        check(MirrorPlan.personaSeed(salt, 10100) != MirrorPlan.personaSeed(salt, 10101), "the seed depends on the app");
        check(MirrorPlan.personaSeed(salt, 10100) != MirrorPlan.personaSeed(salt + 1, 10100), "the seed depends on the phone");
        check(MirrorPlan.personaSeed(salt, 10100) == MirrorPlan.personaSeed(salt, 10100), "the seed is stable");

        // another real country gives another place; no country (and no place data) gives no Automatic persona
        PolicyStore auto = PolicyStore.EMPTY.withApp("com.a.app", ISO);
        check(PersonaFile.parse(MirrorPlan.personaFile(auto, pkg -> 10100, salt, places, "DE", 48.1, 11.5)).placeOf(10100) == 4,
                "the place is in the real country");
        // an Isolated app that gets no place keeps a persona without one: its scans, networks and cells are still hidden
        PersonaFile france = PersonaFile.parse(MirrorPlan.personaFile(auto, pkg -> 10100, salt, places, "FR", 48.1, 2.3));
        check(france.placeOf(10100) == 0 && france.hasPersona(10100), "a country without a place: a persona without a place");
        PersonaFile nowhere = PersonaFile.parse(MirrorPlan.personaFile(auto, pkg -> 10100, salt, places, null, 0, 0));
        check(nowhere.placeOf(10100) == 0 && nowhere.hasPersona(10100), "no country, no Automatic place, still a persona");
        PersonaFile withoutData = PersonaFile.parse(MirrorPlan.personaFile(auto, pkg -> 10100, salt, null, "NL", 52.37, 4.89));
        check(withoutData.placeOf(10100) == 0 && withoutData.hasPersona(10100), "no place data, no place, still a persona");
        PersonaFile noDataChosen = PersonaFile.parse(MirrorPlan.personaFile(PolicyStore.EMPTY.withApp("com.b.app", ISO.withPlace(1)),
                pkg -> 10200, salt, null, "NL", 52.37, 4.89));
        check(noDataChosen.placeOf(10200) == 0 && noDataChosen.hasPersona(10200), "no place data, no chosen place either, still a persona");
        check(!PersonaFile.parse(MirrorPlan.personaFile(PolicyStore.EMPTY.withApp("com.c.app", AppPolicy.PROTECTED), pkg -> 10300, salt,
                null, "NL", 52.37, 4.89)).hasPersona(10300), "a Protected app has no persona, with or without place data");
        check(MirrorPlan.personaFile(PolicyStore.EMPTY, pkg -> -1, 1L, places, "NL", 0, 0).equals("s 0000000000000001\n"),
                "an empty store has a file with the salt only");

        // two packages with one app id: the lower place wins, and a substituted package gives the id its persona
        PolicyStore shared = PolicyStore.EMPTY
                .withApp("com.x.app", ISO.withPlace(3))
                .withApp("com.y.app", ISO.withPlace(2))
                .withApp("com.z.app", AppPolicy.PROTECTED);
        check(PersonaFile.parse(MirrorPlan.personaFile(shared, pkg -> 10800, salt, places, "NL", 52.37, 4.89)).placeOf(10800) == 2,
                "a shared app id gets the lower place");
        // one package of a shared app id has a place and another has none: the app has a persona with the place, on one line
        PolicyStore sharedOne = PolicyStore.EMPTY
                .withApp("com.x.app", ISO.withPlace(2))
                .withApp("com.y.app", ISO.withPlace(99));
        PersonaFile sharedFile = PersonaFile.parse(MirrorPlan.personaFile(sharedOne, pkg -> 10800, salt, places, "NL", 52.37, 4.89));
        check(sharedFile.placeOf(10800) == 2 && sharedFile.hasPersona(10800), "a shared app id with a place and an unresolved package");
        PolicyStore sharedNone = PolicyStore.EMPTY
                .withApp("com.x.app", ISO.withPlace(98))
                .withApp("com.y.app", ISO.withPlace(99));
        PersonaFile sharedNoneFile = PersonaFile.parse(MirrorPlan.personaFile(sharedNone, pkg -> 10800, salt, places, "NL", 52.37, 4.89));
        check(sharedNoneFile.placeOf(10800) == 0 && sharedNoneFile.hasPersona(10800), "a shared app id with two unresolved packages is one n line");
        PolicyStore sharedReverse = PolicyStore.EMPTY
                .withApp("com.x.app", ISO.withPlace(2))
                .withApp("com.y.app", ISO.withPlace(3));
        check(PersonaFile.parse(MirrorPlan.personaFile(sharedReverse, pkg -> 10800, salt, places, "NL", 52.37, 4.89)).placeOf(10800) == 2,
                "a shared app id gets the lower place, whichever package comes first");

        // locationBlocked: configured packages whose location is blocked; unresolved: substituted but without a persona
        check(MirrorPlan.locationBlocked(s).toString().equals("[com.e.app]"), "blocked location: " + MirrorPlan.locationBlocked(s));
        check(MirrorPlan.locationBlocked(PolicyStore.EMPTY).isEmpty(), "none blocked");
        java.util.Set<String> unresolved = MirrorPlan.locationUnresolved(s, pkg -> uids.getOrDefault(pkg, -1), salt, places, "NL",
                52.37, 4.89);
        check(unresolved.toString().equals("[com.g.app]"), "only the unknown place has no persona: " + unresolved);
        java.util.Set<String> noData = MirrorPlan.locationUnresolved(s, pkg -> uids.getOrDefault(pkg, -1), salt, null, "NL", 52.37, 4.89);
        check(noData.toString().equals("[com.a.app, com.b.app, com.d.app, com.g.app]"), "no place data, every substituted app: " + noData);
        java.util.Set<String> noCountry = MirrorPlan.locationUnresolved(s, pkg -> uids.getOrDefault(pkg, -1), salt, places, "", 0, 0);
        check(noCountry.toString().equals("[com.a.app, com.d.app, com.g.app]"), "no country, every Automatic app: " + noCountry);
    }

    /** P4: the app ids that get no position at delivery, whatever permission an app holds (blocked, or substituted without a persona). */
    private static void withheldAppIds() throws Exception {
        Places places = Places.parse(PLACES);
        long salt = 0xABCDEF0123456789L;
        PolicyStore s = PolicyStore.EMPTY
                .withApp("com.blocked.app", ISO.withValue(Cls.LOCATION, Value.BLOCKED))
                .withApp("com.nope.app", ISO.withPlace(99))        // a place that is not in the data
                .withApp("com.ok.app", ISO.withPlace(2))
                .withApp("com.real.app", AppPolicy.PROTECTED)
                .withApp("com.sys.app", ISO.withValue(Cls.LOCATION, Value.BLOCKED));
        Map<String, Integer> uids = Map.of("com.blocked.app", 10100, "com.nope.app", 10200, "com.ok.app", 10300,
                "com.real.app", 10400, "com.sys.app", 1000);
        MirrorPlan.UidResolver r = pkg -> uids.getOrDefault(pkg, -1);
        java.util.Set<Integer> w = MirrorPlan.locationWithheldAppIds(s, r, salt, places, "NL", 52.37, 4.89, null, null);
        check(w.toString().equals("[10100, 10200]"), "blocked and unresolved are withheld, nothing else: " + w);
        check(MirrorPlan.locationWithheldAppIds(s, r, salt, null, "NL", 52.37, 4.89, null, null).toString().equals("[10100, 10200, 10300]"),
                "without place data every substituted app is withheld");
        check(MirrorPlan.locationWithheldAppIds(PolicyStore.EMPTY, r, salt, places, "NL", 52.37, 4.89, null, null).isEmpty(),
                "no entries, nothing withheld");
        // two packages with one app id: one withheld package withholds the id (the permission is per uid)
        PolicyStore shared = PolicyStore.EMPTY.withApp("com.x.app", ISO.withPlace(2))
                .withApp("com.y.app", ISO.withValue(Cls.LOCATION, Value.BLOCKED));
        check(MirrorPlan.locationWithheldAppIds(shared, pkg -> 10500, salt, places, "NL", 52.37, 4.89, null, null).contains(10500),
                "a shared app id is withheld when one package is");
        // a kept Automatic place is a resolved place
        PolicyStore auto = PolicyStore.EMPTY.withApp("com.a.app", ISO);
        PersonaFile first = PersonaFile.parse(MirrorPlan.personaFile(auto, pkg -> 10600, salt, places, "NL", 52.37, 4.89));
        check(MirrorPlan.locationWithheldAppIds(auto, pkg -> 10600, salt, places, "", Double.NaN, Double.NaN, null, null).contains(10600),
                "Automatic without a country or a kept place is withheld");
        check(MirrorPlan.locationWithheldAppIds(auto, pkg -> 10600, salt, places, "", Double.NaN, Double.NaN, auto, first).isEmpty(),
                "Automatic with a kept place is not");
    }

    /** D5: under Isolated defaults an app without an entry gets a persona of its own, like an app with one. */
    private static void isolatedDefaults() throws Exception {
        Places places = Places.parse(PLACES);
        long salt = 0xABCDEF0123456789L;
        Map<String, Integer> uids = Map.of("com.set.app", 10100, "com.free.app", 10200, "com.sys.app", 1000, "com.gone.app", -1);
        java.util.List<String> installed = java.util.List.of("com.set.app", "com.free.app", "com.sys.app", "com.gone.app");
        MirrorPlan.UidResolver r = new MirrorPlan.UidResolver() {
            @Override
            public int uidOf(String pkg) {
                return uids.getOrDefault(pkg, -1);
            }

            @Override
            public java.util.Collection<String> installedPackages() {
                return installed;
            }
        };
        PolicyStore iso = PolicyStore.EMPTY.withDefaults(ISO).withApp("com.set.app", ISO);
        PersonaFile t = PersonaFile.parse(MirrorPlan.personaFile(iso, r, salt, places, "NL", 52.37, 4.89));
        check(far(t.placeOf(10100)), "an app with an entry has a persona");
        check(far(t.placeOf(10200)), "an app without an entry has one under Isolated defaults");
        check(t.placeOf(1000) == 0, "a platform uid has none");
        check(MirrorPlan.locationUnresolved(iso, r, salt, places, "", Double.NaN, Double.NaN).equals(
                java.util.Set.of("com.set.app", "com.free.app")), "without a country both are unresolved, the platform uid and the missing package are not");
        check(MirrorPlan.locationWithheldAppIds(iso, r, salt, places, "", Double.NaN, Double.NaN, null, null).toString().equals("[10100, 10200]"),
                "and both are withheld");
        check(MirrorPlan.personaFile(iso, r, salt, null, "NL", 52.37, 4.89).equals(PersonaFile.format(salt, Map.of(), java.util.Set.of(10100, 10200))),
                "without place data nobody has a place, and every Isolated app has a persona without one");
        PersonaFile bare = PersonaFile.parse(MirrorPlan.personaFile(PolicyStore.EMPTY.withDefaults(AppPolicy.PROTECTED).withApp("com.set.app", ISO),
                r, salt, null, "NL", 52.37, 4.89));
        check(bare.hasPersona(10100) && !bare.hasPersona(10200) && !bare.hasPersona(1000),
                "under Protected defaults only the app with an Isolated entry has a persona without a place");
        check(MirrorPlan.locationUnresolved(iso, r, salt, null, "NL", 52.37, 4.89).equals(java.util.Set.of("com.set.app")),
                "and only the app with an entry is unresolved: the permissions of the apps without one are kept until place data is installed");
        check(MirrorPlan.locationWithheldAppIds(iso, r, salt, null, "NL", 52.37, 4.89, null, null).toString().equals("[10100, 10200]"),
                "but the apps without an entry are withheld the position at delivery, which the store reports as substituted");
        check(MirrorPlan.locationWithheldAppIds(PolicyStore.EMPTY.withDefaults(ISO).withApp("com.set.app", AppPolicy.PROTECTED), r, salt, null, "NL", 52.37, 4.89, null, null).toString().equals("[10200]"),
                "an app with a Protected entry is not withheld by the rule for apps without one");
        check(MirrorPlan.locationWithheldAppIds(iso, r, salt, places, "NL", 52.37, 4.89, null, null).isEmpty(),
                "with place data and a country nobody is withheld: every app has a persona");
        check(MirrorPlan.locationWithheldAppIds(PolicyStore.EMPTY.withDefaults(AppPolicy.PROTECTED).withApp("com.set.app", ISO), r, salt, null, "NL", 52.37, 4.89, null, null).toString().equals("[10100]"),
                "Protected defaults withhold only the app with an entry");
        PolicyStore prot = PolicyStore.EMPTY.withDefaults(AppPolicy.PROTECTED).withApp("com.set.app", ISO);
        PersonaFile p = PersonaFile.parse(MirrorPlan.personaFile(prot, r, salt, places, "NL", 52.37, 4.89));
        check(far(p.placeOf(10100)) && p.placeOf(10200) == 0, "Protected defaults give the app without an entry no persona");
        check(MirrorPlan.locationPackages(iso, r, places).size() == 4 && MirrorPlan.locationPackages(prot, r, places).equals(java.util.Set.of("com.set.app")),
                "the packages the plan covers");
    }

    /** A package signed with the platform key is the system: the defaults give it no persona and real sensors; an entry counts. */
    private static void platformPackagesFollowNoDefaults() throws Exception {
        Places places = Places.parse(PLACES);
        long salt = 0xABCDEF0123456789L;
        Map<String, Integer> uids = Map.of("com.free.app", 10200, "com.plat.app", 10300, "com.plat.set", 10400, "com.shared.entry", 10300);
        java.util.List<String> installed = java.util.List.of("com.free.app", "com.plat.app", "com.plat.set");
        MirrorPlan.UidResolver r = new MirrorPlan.UidResolver() {
            @Override
            public int uidOf(String pkg) {
                return uids.getOrDefault(pkg, -1);
            }

            @Override
            public java.util.Collection<String> installedPackages() {
                return installed;
            }

            @Override
            public java.util.Collection<String> platformPackages() {
                return java.util.Set.of("com.plat.app", "com.plat.set");
            }
        };
        PolicyStore iso = PolicyStore.EMPTY.withDefaults(ISO).withApp("com.plat.set", ISO);
        PersonaFile t = PersonaFile.parse(MirrorPlan.personaFile(iso, r, salt, places, "NL", 52.37, 4.89));
        check(far(t.placeOf(10200)), "an app without an entry has a persona under Isolated defaults");
        check(!t.hasPersona(10300), "a platform package without an entry has none");
        check(far(t.placeOf(10400)), "a platform package with an entry has one");
        check(MirrorPlan.locationPackages(iso, r, places).equals(java.util.Set.of("com.free.app", "com.plat.set")), "the packages the plan covers");
        check(MirrorPlan.personaFile(iso, r, salt, null, "NL", 52.37, 4.89).equals(PersonaFile.format(salt, Map.of(), java.util.Set.of(10200, 10400))),
                "without place data the platform package has no persona either");
        check(MirrorPlan.locationWithheldAppIds(iso, r, salt, null, "NL", 52.37, 4.89, null, null).toString().equals("[10200, 10400]"),
                "and is not withheld the position");

        // the sensors of a platform package are those of the Protected baseline (real motion, bias folded), never RAW
        check(MirrorPlan.sensorModeFile(iso, r, salt).equals("s abcdef0123456789\nd SUBSTITUTE\na 10300 FOLDED\n"),
                "a platform package without an entry has the baseline sensors under Isolated defaults");
        check(MirrorPlan.sensorModeFile(PolicyStore.EMPTY, r, salt).equals("s abcdef0123456789\nd FOLDED\n"),
                "and under Protected defaults it needs no line");
        PolicyStore blocked = PolicyStore.EMPTY.withDefaults(ISO).withApp("com.plat.set", ISO.withValue(Cls.SENSORS, Value.BLOCKED));
        check(MirrorPlan.sensorModeFile(blocked, r, salt).equals("s abcdef0123456789\nd SUBSTITUTE\na 10300 FOLDED\na 10400 BLOCKED\n"),
                "an entry of a platform package is honoured");
        PolicyStore shared = PolicyStore.EMPTY.withDefaults(ISO).withApp("com.shared.entry", ISO.withValue(Cls.SENSORS, Value.BLOCKED));
        check(MirrorPlan.sensorModeFile(shared, r, salt).equals("s abcdef0123456789\nd SUBSTITUTE\na 10300 BLOCKED\na 10400 FOLDED\n"),
                "an entry for a package that shares the app id wins over the exemption");
    }

    /** G6: the largest legal store fits the limit that the service reads, and reads back. */
    private static void largestStore() {
        PolicyStore full = PolicyStore.EMPTY;
        String pad = "a".repeat(240);
        for (int i = 0; i < PolicyStore.MAX_APPS; i++) {
            full = full.withApp(String.format("p%04d.%s", i, pad), ISO.withPlace(2147483647).withValue(Cls.LOCATION, Value.BLOCKED));
        }
        byte[] data = full.encode();
        check(data.length <= PolicyStore.MAX_ENCODED_BYTES, "the largest store is " + data.length);
        check(data.length > PolicyStore.MAX_ENCODED_BYTES - PolicyStore.MAX_APPS * 20, "and the limit is not far above it: " + data.length);
        check(PolicyStore.decode(data).configured().size() == PolicyStore.MAX_APPS, "and reads back");
        check(PolicyStore.MAX_ENCODED_BYTES > 1024 * 1024, "the old limit of 1 MiB was below the largest store");
    }

    private static void keptPlacesAndRestarts() throws Exception {
        Places places = Places.parse(PLACES);
        long salt = 0xABCDEF0123456789L;
        PolicyStore auto = PolicyStore.EMPTY.withApp("com.a.app", ISO);
        MirrorPlan.UidResolver uid = pkg -> 10100;
        PersonaFile first = PersonaFile.parse(MirrorPlan.personaFile(auto, uid, salt, places, "NL", 52.37, 4.89));
        int place = first.placeOf(10100);
        check(far(place), "the first place of an Automatic app is far: " + place);

        // a boot without a network: the app keeps the place of the last table, and keeps its location permissions
        check(PersonaFile.parse(MirrorPlan.personaFile(auto, uid, salt, places, "", Double.NaN, Double.NaN)).placeOf(10100) == 0,
                "without a country and without a kept place there is no persona");
        PersonaFile again = PersonaFile.parse(MirrorPlan.personaFile(auto, uid, salt, places, "", Double.NaN, Double.NaN, auto, first));
        check(again.placeOf(10100) == place, "an Automatic app keeps its place when the country is not known");
        check(MirrorPlan.locationUnresolved(auto, uid, salt, places, "", Double.NaN, Double.NaN).contains("com.a.app"),
                "unresolved without a kept place");
        check(MirrorPlan.locationUnresolved(auto, uid, salt, places, "", Double.NaN, Double.NaN, auto, first).isEmpty(),
                "a kept place is a resolved place");
        check(PersonaFile.parse(MirrorPlan.personaFile(auto, uid, salt, places, "DE", 48.1, 11.5, auto, first)).placeOf(10100) == place,
                "the place is chosen once: another real country does not move it");

        // nothing is kept when the app was not Automatic and substituted before, or the place left the data
        PolicyStore chosen = PolicyStore.EMPTY.withApp("com.a.app", ISO.withPlace(2));
        check(PersonaFile.parse(MirrorPlan.personaFile(auto, uid, salt, places, "", Double.NaN, Double.NaN, chosen, first)).placeOf(10100)
                == 0, "a place that the user chose is not an Automatic place to keep");
        PolicyStore real = PolicyStore.EMPTY.withApp("com.a.app", ISO.withValue(Cls.LOCATION, Value.REAL));
        check(PersonaFile.parse(MirrorPlan.personaFile(auto, uid, salt, places, "", Double.NaN, Double.NaN, real, first)).placeOf(10100)
                == 0, "a place from before the location was real is not kept");
        PersonaFile gone = PersonaFile.parse(PersonaFile.format(salt, Map.of(10100, 99)));
        check(PersonaFile.parse(MirrorPlan.personaFile(auto, uid, salt, places, "", Double.NaN, Double.NaN, auto, gone)).placeOf(10100)
                == 0, "a kept place that is not in the data is dropped");
        check(PersonaFile.parse(MirrorPlan.personaFile(auto, uid, salt, null, "NL", 52.37, 4.89, auto, first)).placeOf(10100) == 0,
                "no place data, nothing kept");
        int other = place == 2 ? 3 : 2;  // a place that is not the kept one
        PolicyStore userChoice = PolicyStore.EMPTY.withApp("com.a.app", ISO.withPlace(other));
        check(PersonaFile.parse(MirrorPlan.personaFile(userChoice, uid, salt, places, "NL", 52.37, 4.89, auto, first)).placeOf(10100)
                == other, "a chosen place wins over a kept one");
        check(MirrorPlan.personaFile(auto, uid, salt, places, "NL", 52.37, 4.89, auto, null)
                .equals(MirrorPlan.personaFile(auto, uid, salt, places, "NL", 52.37, 4.89)), "without a table nothing is kept");

        // restartNeeded
        PolicyStore prot = PolicyStore.EMPTY.withApp("com.a.app", AppPolicy.PROTECTED).withApp("com.b.app", AppPolicy.PROTECTED);
        check(MirrorPlan.restartNeeded(prot, prot).isEmpty(), "no change, no restart");
        check(MirrorPlan.restartNeeded(prot, prot.withApp("com.a.app", ISO)).toString().equals("[com.a.app]"),
                "a new profile restarts that app only");
        check(MirrorPlan.restartNeeded(prot, prot.withApp("com.a.app", AppPolicy.PROTECTED.withProfile(Profile.TRUSTED)))
                .toString().equals("[com.a.app]"), "a profile that changes only the sensor mode restarts");
        check(MirrorPlan.restartNeeded(prot, prot.withApp("com.a.app", AppPolicy.PROTECTED.withValue(Cls.SENSORS, Value.BLOCKED)))
                .toString().equals("[com.a.app]"), "a sensor value restarts");
        check(MirrorPlan.restartNeeded(prot, prot.withApp("com.b.app", AppPolicy.PROTECTED.withValue(Cls.LOCATION, Value.BLOCKED)))
                .toString().equals("[com.b.app]"), "a location value restarts");
        check(MirrorPlan.restartNeeded(prot, prot.withApp("com.a.app", AppPolicy.PROTECTED.withValue(Cls.LOOPBACK, Value.REAL))).isEmpty(),
                "a loopback value restarts nothing (the permission change does it)");
        check(MirrorPlan.restartNeeded(userChoice, userChoice.withApp("com.a.app", ISO.withPlace(4))).toString().equals("[com.a.app]"),
                "another place restarts");
        check(MirrorPlan.restartNeeded(prot, prot.without("com.a.app")).isEmpty(), "an entry that changes nothing restarts nothing");
        check(MirrorPlan.restartNeeded(PolicyStore.EMPTY, prot.withApp("com.c.app", ISO)).toString().equals("[com.c.app]"),
                "an app that gets its first entry restarts");
        check(MirrorPlan.restartNeeded(prot.withApp("com.c.app", ISO), prot).toString().equals("[com.c.app]"),
                "an app whose entry is cleared restarts");

        // the wait before the mirrors are written again
        check(MirrorPlan.nextRetryMs(0) == 10_000, "first retry after 10 s");
        check(MirrorPlan.nextRetryMs(10_000) == 20_000, "the wait doubles");
        check(MirrorPlan.nextRetryMs(160_000) == 300_000, "the wait stops at 5 minutes");
        check(MirrorPlan.nextRetryMs(300_000) == 300_000, "and stays there");
        check(MirrorPlan.nextRetryMs(-5) == 10_000, "a negative wait starts again");
        check(MirrorPlan.nextRetryMs(Integer.MAX_VALUE) == 300_000, "no overflow");
    }
}
