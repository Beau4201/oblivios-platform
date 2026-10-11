#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of the classifier build: each control is removed in a copy of tools/build-classifier.py and
tests/run-builder-checks.sh must fail. usage: run-builder-mutations.py SRC_ROOT"""
import pathlib, shutil, subprocess, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent.parent
B = "build-classifier.py"
I = "import-companiesdb.py"
MUTATIONS = [   # (name, file in tools/, old text, new text, how often old occurs)
    ("licences are not checked", B, "if allowed is not None and r[2] not in allowed:", "if False:", 1),
    ("urls are not checked", B, 'if not r[3].startswith("https://"):', "if False:", 1),
    # the field count check of the build is not mutated: the device parser repeats it, so removing it changes only
    # the message of the refusal (an equivalent mutant)
    ("output is not sorted", B, "lines.sort(key=lambda x: (order[x[0]], x[1]))", "pass", 1),
    ("importer: a category that is not used is kept", I, "if cat is None:", "if False:", 1),
    ("importer: a company without a base gets an owner", I, "elif cid in bases and owner_id(cid) and not is_code(bases[cid][4]):", "elif owner_id(cid):", 1),
    ("importer: a curated service does not win", I, "if domain in curated_suffixes:", "if False:", 1),
    ("importer: an id longer than the parser allows", I, "return oid if 1 <= len(oid) <= MAX_ID else None", "return oid", 1),
    ("importer: two records of one company are two owners", I, "cid = canonical[bases[cid][1]]", "cid = cid", 1),
    ("importer: a Wikidata code is a legal name", I, "legal = name if is_code(b[2]) else clean(b[2])", "legal = clean(b[2])", 1),
    ("importer: a place that is only a code is used", I, "and not is_code(bases[cid][4]):\n            cid = canonical", ":\n            cid = canonical", 1),
    ("importer: the database licence is wrong", I, '"CC-BY-SA-4.0",', '"CC0-1.0",', 1),
]


def main():
    src = sys.argv[1]
    survivors = []
    for name, fname, old, new, count in MUTATIONS:
        tmp = pathlib.Path(tempfile.mkdtemp())
        try:
            shutil.copytree(HERE, tmp / "c")
            f = tmp / "c/tools" / fname
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
