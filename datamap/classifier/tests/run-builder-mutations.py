#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of the classifier build: each control is removed in a copy of tools/build-classifier.py and
tests/run-builder-checks.sh must fail. usage: run-builder-mutations.py SRC_ROOT"""
import pathlib, shutil, subprocess, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent.parent
MUTATIONS = [
    ("licences are not checked", "if allowed is not None and r[2] not in allowed:", "if False:", 1),
    ("urls are not checked", 'if not r[3].startswith("https://"):', "if False:", 1),
    # the field count check of the build is not mutated: the device parser repeats it, so removing it changes only
    # the message of the refusal (an equivalent mutant)
    ("output is not sorted", "lines.sort(key=lambda x: (order[x[0]], x[1]))", "pass", 1),
]


def main():
    src = sys.argv[1]
    survivors = []
    for name, old, new, count in MUTATIONS:
        tmp = pathlib.Path(tempfile.mkdtemp())
        try:
            shutil.copytree(HERE, tmp / "c")
            f = tmp / "c/tools/build-classifier.py"
            text = f.read_text(encoding="utf-8")
            if text.count(old) != count:
                print(f"BROKEN MUTATION {name}")
                sys.exit(2)
            f.write_text(text.replace(old, new), encoding="utf-8")
            r = subprocess.run([str(tmp / "c/tests/run-builder-checks.sh"), src], capture_output=True, text=True)
            killed = r.returncode != 0
            print(("KILLED   " if killed else "SURVIVED ") + name)
            if not killed:
                survivors.append(name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print(f"{len(MUTATIONS) - len(survivors)} of {len(MUTATIONS)} mutations killed")
    sys.exit(1 if survivors else 0)


main()
