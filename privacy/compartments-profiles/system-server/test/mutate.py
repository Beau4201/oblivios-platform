#!/usr/bin/env python3
"""Mutation check for the Compartments policy core: every mutant must make a test assertion fail.
Usage: mutate.py   (run from anywhere; reads the sources next to this file)"""
import pathlib, shutil, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
MUTANTS = [
    ("Profiles.java", "Isolated sensors are not substituted", "return p == Profile.ISOLATED ? Value.SUBSTITUTE : Value.REAL;", "return Value.REAL;"),
    ("Profiles.java", "Trusted loopback and settings are blocked", "return p == Profile.TRUSTED ? Value.REAL : Value.BLOCKED;", "return Value.BLOCKED;"),
    ("Profiles.java", "everything is valid", "return c == Cls.SENSORS || v != Value.SUBSTITUTE;", "return true;"),
    ("Profiles.java", "every real sensor is raw", "return p == Profile.TRUSTED ? SensorMode.RAW : SensorMode.FOLDED;", "return SensorMode.RAW;"),
    ("Profiles.java", "a Trusted app is folded", "return p == Profile.TRUSTED ? SensorMode.RAW : SensorMode.FOLDED;", "return SensorMode.FOLDED;"),
    ("Profiles.java", "blocked sensors are real", "case BLOCKED:\n                return SensorMode.BLOCKED;", "case BLOCKED:\n                return SensorMode.FOLDED;"),
    ("PolicyStore.java", "Trusted may be the default", "d.profile != Profile.TRUSTED\n                && ", ""),
    ("PolicyStore.java", "loopback may be opened by default", "&& d.value(Cls.LOOPBACK) == Profiles.defaultValue(d.profile, Cls.LOOPBACK)\n", "\n"),
    ("PolicyStore.java", "a new profile keeps the overrides", "return new AppPolicy(p, new Value[Cls.values().length]);", "return new AppPolicy(p, overrides);"),
    ("PolicyStore.java", "a value equal to the default is stored", "next[c.ordinal()] = v == Profiles.defaultValue(profile, c) ? null : v;", "next[c.ordinal()] = v;"),
    ("PolicyStore.java", "the number of apps is unbounded", "apps.size() >= MAX_APPS", "apps.size() > MAX_APPS + 1000000"),
    ("PolicyStore.java", "the file may hold too many apps", "if (n < 0 || n > MAX_APPS) {", "if (n < 0) {"),
    ("PolicyStore.java", "trailing bytes are accepted", "return in.read() == -1 ? new PolicyStore(d, apps) : EMPTY;", "return new PolicyStore(d, apps);"),
    ("PolicyStore.java", "a duplicate package is accepted", "|| apps.put(pkg, p) != null) {", "|| (apps.put(pkg, p) != null && false)) {"),
    ("PolicyStore.java", "a package name is not checked when read", "!validPackage(pkg) ||", ""),
    ("PolicyStore.java", "a longer class list is accepted", "|| classes > Cls.values().length", ""),
    ("PolicyStore.java", "an invalid value is accepted when read", "|| !Profiles.isValid(Cls.values()[i], Value.values()[v])", ""),
    ("PolicyStore.java", "the version is not checked", "|| in.readByte() != VERSION", "|| in.readByte() < 0"),
    ("PolicyStore.java", "a Trusted default is accepted when read", "if (d == null || !validDefaults(d)) {", "if (d == null) {"),
    ("MirrorPlan.java", "the settings list holds every app", "if (s.value(pkg, Cls.SETTINGS) == Value.REAL) {", "if (true) {"),
    ("MirrorPlan.java", "the settings list has no separator", 'out.append(out.length() == 0 ? "" : ",")', 'out.append("")'),
    ("MirrorPlan.java", "every configured app holds loopback", "if (s.value(pkg, Cls.LOOPBACK) == Value.REAL) {", "if (true) {"),
    ("MirrorPlan.java", "a line equal to the default is written", "if (e.getValue() != dflt) {", "if (true) {"),
    ("MirrorPlan.java", "a platform app id gets a line", "appId >= FIRST_APP_ID && appId <= LAST_APP_ID", "appId <= LAST_APP_ID"),
    ("MirrorPlan.java", "a sandbox app id gets a line", "appId >= FIRST_APP_ID && appId <= LAST_APP_ID", "appId >= FIRST_APP_ID"),
    ("MirrorPlan.java", "the first app id is left out", "appId >= FIRST_APP_ID && appId <= LAST_APP_ID", "appId > FIRST_APP_ID && appId <= LAST_APP_ID"),
    ("MirrorPlan.java", "the last app id is left out", "appId >= FIRST_APP_ID && appId <= LAST_APP_ID", "appId >= FIRST_APP_ID && appId < LAST_APP_ID"),
    ("MirrorPlan.java", "the line holds the uid of one user", "int appId = uid % USER_OFFSET;", "int appId = uid;"),
    ("MirrorPlan.java", "a later package with the same app id wins", "MirrorPlan::stricter", "(old, next) -> next"),
    ("MirrorPlan.java", "the looser of two modes wins", "return rank(a) >= rank(b) ? a : b;", "return rank(a) >= rank(b) ? b : a;"),
    ("MirrorPlan.java", "blocked is not the strictest", "            default:\n                return 3;", "            default:\n                return 1;"),
    ("MirrorPlan.java", "substitute is not stricter than folded", "            case RAW:\n                return 0;", "            case RAW:\n                return 5;"),
    ("MirrorPlan.java", "the default line ignores the defaults", "mode(s.defaults.profile, s.defaults.value(Cls.SENSORS))", "mode(Profiles.Profile.PROTECTED, Value.REAL)"),
]
survived = 0
for name, what, old, new in MUTANTS:
    with tempfile.TemporaryDirectory() as d:
        d = pathlib.Path(d)
        shutil.copytree(ROOT / "src" / "com" / "android" / "server" / "compartments", d / "src")
        shutil.copytree(ROOT / "test", d / "test", ignore=shutil.ignore_patterns("*.py", "*.sh"))
        f = d / "src" / name
        text = f.read_text()
        assert text.count(old) == 1, f"{name}: {what!r}: pattern found {text.count(old)} times"
        f.write_text(text.replace(old, new))
        c = subprocess.run(["javac", "-d", str(d / "out")] + [str(p) for p in (d / "src").glob("*.java")] + [str(p) for p in (d / "test").glob("*.java")], capture_output=True, text=True)
        if c.returncode != 0:
            print("INVALID MUTANT (does not compile):", name, what, c.stderr[:200])
            survived += 1
            continue
        r = subprocess.run(["java", "-ea", "-cp", str(d / "out"), "com.android.server.compartments.ProfilesTest"], capture_output=True, text=True)
        if r.returncode == 0:
            print("SURVIVED:", name, what)
            survived += 1
        else:
            print("killed:  ", name, what)
print(f"{len(MUTANTS) - survived} of {len(MUTANTS)} mutants killed")
sys.exit(1 if survived else 0)
