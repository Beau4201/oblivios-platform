#!/usr/bin/env python3
"""Mutation check for PersonaProcess: every mutant must make a scenario of PersonaProcessTest fail.
Usage: mutate_process.py   (run from anywhere; reads the sources next to this file)"""
import pathlib, shutil, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCENARIOS = ("none", "null", "full", "badzone", "partial", "twice")
MUTANTS = [
    ("the zone property is not the persona zone", 'return zone;', 'return null;'),
    ("the operator property is the country", 'case "gsm.operator.numeric":\n                return sOperator;', 'case "gsm.operator.numeric":\n                return sCountry;'),
    ("the country property is the operator", 'case "gsm.operator.iso-country":\n                return sCountry;', 'case "gsm.operator.iso-country":\n                return sOperator;'),
    ("the operator name is not emptied", 'case "gsm.operator.alpha":\n                return "";', 'case "gsm.operator.alpha":\n                return null;'),
    ("roaming is not false", 'return "false";', 'return "true";'),
    ("the SIM operator is touched", 'case "gsm.operator.isroaming":', 'case "gsm.sim.operator.numeric":\n                return sOperator;\n            case "gsm.operator.isroaming":'),
    ("a process without a persona answers", 'if (zone == null || key == null) {', 'if (key == null) {'),
    ("a second call is accepted", "if (sSet) {", "if (false) {"),
    ("a bundle with one key is a persona", "if (timeZone == null || country == null || operator == null) {", "if (timeZone == null && country == null && operator == null) {"),
    ("the time zone is not stored", "sTimeZone = timeZone;", ""),
    ("the country is not stored", "sCountry = country;", ""),
    ("the operator is not stored", "sOperator = operator;", ""),
    ("an unknown time zone is applied", "if (known.contains(id)) {", "if (true) {"),
    ("the time zone is never applied", "if (known.contains(id)) {", "if (false) {"),
    # no mutant for the `id == null` early return of applyTimeZone: without it a null id is simply not in the set (a saved
    # allocation, not a behaviour)
    ("the country key is read as the operator", "String operator = coreSettings == null ? null : coreSettings.getString(KEY_OPERATOR);", "String operator = coreSettings == null ? null : coreSettings.getString(KEY_COUNTRY);"),
    ("a null bundle is not survived", "String timeZone = coreSettings == null ? null :", "String timeZone ="),
]


def run(d):
    out = d / "out"
    c = subprocess.run(["javac", "-d", str(out), str(d / "PersonaProcess.java"), str(d / "PersonaProcessTest.java"), str(d / "Bundle.java")], capture_output=True, text=True)
    if c.returncode != 0:
        return c
    runs = [subprocess.run(["java", "-ea", "-cp", str(out), "com.android.internal.compartments.PersonaProcessTest", s], capture_output=True, text=True) for s in SCENARIOS]
    return max(runs, key=lambda x: x.returncode)


def fresh(d):
    shutil.copy(ROOT / "framework" / "PersonaProcess.java", d / "PersonaProcess.java")
    shutil.copy(ROOT / "test" / "PersonaProcessTest.java", d / "PersonaProcessTest.java")
    shutil.copy(ROOT / "test" / "stubs" / "android" / "os" / "Bundle.java", d / "Bundle.java")


# The unmutated tree must pass first: with a failing baseline every mutant would count as killed and prove nothing.
with tempfile.TemporaryDirectory() as d:
    d = pathlib.Path(d)
    fresh(d)
    base = run(d)
    if base.returncode != 0:
        print("BASELINE FAILS, no mutant counts:", (base.stdout + base.stderr)[:400])
        sys.exit(2)
survived = 0
for what, old, new in MUTANTS:
    with tempfile.TemporaryDirectory() as d:
        d = pathlib.Path(d)
        fresh(d)
        f = d / "PersonaProcess.java"
        text = f.read_text()
        assert text.count(old) == 1, f"{what!r}: pattern found {text.count(old)} times"
        f.write_text(text.replace(old, new))
        r = run(d)
        if r.returncode == 0:
            print("SURVIVED:", what)
            survived += 1
        elif "error:" in r.stderr and "AssertionError" not in r.stderr:
            print("INVALID MUTANT (does not compile):", what, r.stderr[:200])
            survived += 1
        else:
            print("killed:  ", what)
print(f"{len(MUTANTS) - survived} of {len(MUTANTS)} mutants killed")
sys.exit(1 if survived else 0)
