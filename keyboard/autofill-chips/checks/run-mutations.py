#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of AutofillChipPolicy (model: freeholdos-platform/passwords/app/tools/run-mutations.py).

Applies ONE mutation at a time (an exact, unique text replaced by another) to a copy of the policy, compiles it with the
host check and runs the check. A mutation is killed when the check fails. One that does not compile proves nothing and
is not counted as killed. A text not found exactly once is an error of this list, not a surviving mutant.

    LATINIME_JAVA=<LatinIME java dir> run-mutations.py [--check-applies] [NAME_FILTER]       (JDK 17 or newer on the PATH)

Left out on purpose: the value of NONE (any value below the first token behaves the same).
"""
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
# the policy as it sits in a LatinIME tree: JAVA_DIR/src/com/android/inputmethod/latin/suggestions/AutofillChipPolicy.java
SRC = os.path.join(os.environ.get("LATINIME_JAVA", ""), "src", "com", "android", "inputmethod", "latin", "suggestions", "AutofillChipPolicy.java")
CHECK = os.path.join(HERE, "AutofillChipPolicyCheck.java")
CLASS = "com.android.inputmethod.latin.suggestions.AutofillChipPolicyCheck"
BIN = os.path.join(os.environ["JAVA_HOME"], "bin") + os.sep if os.environ.get("JAVA_HOME") else ""

# (name, old, new)
MUTATIONS = [
    ("cap: six chips", "MAX_CHIPS = 5;", "MAX_CHIPS = 6;"),
    ("cap: four chips", "MAX_CHIPS = 5;", "MAX_CHIPS = 4;"),
    ("visibleCount: no cap", "return Math.min(Math.max(count, 0), MAX_CHIPS);", "return Math.max(count, 0);"),
    ("visibleCount: a negative count passes", "return Math.min(Math.max(count, 0), MAX_CHIPS);", "return Math.min(count, MAX_CHIPS);"),
    ("visibleCount: always the cap", "return Math.min(Math.max(count, 0), MAX_CHIPS);", "return MAX_CHIPS;"),
    ("alone: also none", "return shown == 1;", "return shown <= 1;"),
    ("alone: also two", "return shown == 1;", "return shown <= 2;"),
    ("alone: never", "return shown == 1;", "return false;"),
    ("request: typing is not forgotten", "    void onNewRequest() {\n        mTyped = false;\n", "    void onNewRequest() {\n"),
    ("request: nothing in flight is dropped", "    void onNewRequest() {\n        mTyped = false;\n        mGeneration++;\n", "    void onNewRequest() {\n        mTyped = false;\n"),
    ("typed: not remembered", "    void onTyped() {\n        mTyped = true;\n", "    void onTyped() {\n"),
    ("typed: in-flight chips stay", "    void onTyped() {\n        mTyped = true;\n        mGeneration++;\n", "    void onTyped() {\n        mTyped = true;\n"),
    ("field empty: typing is not forgotten", "    void onFieldEmpty() {\n        mTyped = false;\n", "    void onFieldEmpty() {\n"),
    ("field empty: counts as typing", "    void onFieldEmpty() {\n        mTyped = false;\n", "    void onFieldEmpty() {\n        mTyped = true;\n"),
    ("field empty: in-flight chips are dropped", "    void onFieldEmpty() {\n        mTyped = false;\n", "    void onFieldEmpty() {\n        mTyped = false;\n        mGeneration++;\n"),
    ("cleared: in-flight chips stay", "    void onCleared() {\n        mGeneration++;\n", "    void onCleared() {\n"),
    ("cleared: counts as typing", "    void onCleared() {\n        mGeneration++;\n", "    void onCleared() {\n        mTyped = true;\n        mGeneration++;\n"),
    ("begin: a newer response does not replace", "        mGeneration++;\n        return (mTyped", "        return (mTyped"),
    ("begin: typing is ignored", "return (mTyped || shown <= 0) ? NONE : mGeneration;", "return shown <= 0 ? NONE : mGeneration;"),
    ("begin: an empty response has a token", "return (mTyped || shown <= 0) ? NONE : mGeneration;", "return mTyped ? NONE : mGeneration;"),
    ("begin: a negative count has a token", "return (mTyped || shown <= 0) ? NONE : mGeneration;", "return (mTyped || shown == 0) ? NONE : mGeneration;"),
    ("begin: always none", "return (mTyped || shown <= 0) ? NONE : mGeneration;", "return NONE;"),
    ("current: an older token counts", "return token == mGeneration;", "return token <= mGeneration;"),
    ("current: always", "return token == mGeneration;", "return true;"),
    ("typed(): always false", "    boolean typed() {\n        return mTyped;", "    boolean typed() {\n        return false;"),
]


def run(workdir, java_src):
    out = os.path.join(workdir, "out")
    os.makedirs(out, exist_ok=True)
    c = subprocess.run([BIN + "javac", "-Xlint:all", "-Werror", "-d", out, java_src, CHECK], capture_output=True, text=True)
    if c.returncode != 0:
        return "no-compile", c.stdout + c.stderr
    r = subprocess.run([BIN + "java", "-ea", "-cp", out, CLASS], capture_output=True, text=True)
    return ("passed" if r.returncode == 0 else "failed"), r.stdout + r.stderr


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    only_applies = "--check-applies" in sys.argv
    flt = args[0] if args else ""
    if not os.path.isfile(SRC):
        print("set LATINIME_JAVA to the java directory of a LatinIME tree that holds the policy")
        return 2
    original = open(SRC, encoding="utf-8").read()
    bad = 0
    survivors = []
    killed = 0
    for name, old, new in MUTATIONS:
        if flt and flt not in name:
            continue
        if original.count(old) != 1:
            print(f"ERROR   {name}: the text is found {original.count(old)} times")
            bad += 1
            continue
        if only_applies:
            print(f"applies {name}")
            continue
        with tempfile.TemporaryDirectory() as w:
            d = os.path.join(w, "src")
            os.makedirs(d)
            f = os.path.join(d, "AutofillChipPolicy.java")
            open(f, "w", encoding="utf-8").write(original.replace(old, new))
            state, text = run(w, f)
        if state == "failed":
            killed += 1
            print(f"killed  {name}")
        elif state == "no-compile":
            bad += 1
            print(f"NO COMPILE {name}\n{text[:300]}")
        else:
            survivors.append(name)
            print(f"SURVIVED {name}")
    if only_applies:
        return 1 if bad else 0
    print(f"{killed} killed, {len(survivors)} survived, {bad} errors")
    return 0 if not survivors and not bad else 1


if __name__ == "__main__":
    sys.exit(main())
