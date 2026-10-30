#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of build-persona-data.py: every mutant must fail test_build_persona_data.py. The unmutated builder must pass first."""
import importlib.util
import os
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("t", os.path.join(HERE, "test_build_persona_data.py"))
t = importlib.util.module_from_spec(spec)
spec.loader.exec_module(t)
SOURCE = open(os.path.join(HERE, "build-persona-data.py")).read()

MUTANTS = [
    ("the population threshold is 15001", "MIN_POPULATION = 15000", "MIN_POPULATION = 15001"),
    ("the population threshold is 14999", "MIN_POPULATION = 15000", "MIN_POPULATION = 14999"),
    ("the latitude limit is 81", "MAX_LATITUDE = 80.0", "MAX_LATITUDE = 81.0"),
    ("the latitude limit excludes 80", "abs(lat) > MAX_LATITUDE", "abs(lat) >= MAX_LATITUDE"),
    ("the ids do not follow the country", "cities.sort(key=lambda c: (c[0], c[1], c[2]))", "cities.sort(key=lambda c: (c[1], c[0], c[2]))"),
    ("the ids do not follow the name", "cities.sort(key=lambda c: (c[0], c[1], c[2]))", "cities.sort(key=lambda c: (c[0], c[2], c[1]))"),
    ("the ids follow the input order", "    cities.sort(key=lambda c: (c[0], c[1], c[2]))\n", ""),
    ("21 cells per place", "MAX_CELLS = 20", "MAX_CELLS = 21"),
    ("19 cells per place", "MAX_CELLS = 20", "MAX_CELLS = 19"),
    ("the farthest cells come first", "own.sort(key=lambda dc: (round(dc[0], 6), dc[1][5], dc[1][3], dc[1][4]))", "own.sort(key=lambda dc: (-round(dc[0], 6), dc[1][5], dc[1][3], dc[1][4]))"),
    ("a tie is not broken by the cell identity", "own.sort(key=lambda dc: (round(dc[0], 6), dc[1][5], dc[1][3], dc[1][4]))", "own.sort(key=lambda dc: (round(dc[0], 6),))"),
    ("a tie is broken by the highest cell identity", "own.sort(key=lambda dc: (round(dc[0], 6), dc[1][5], dc[1][3], dc[1][4]))", "own.sort(key=lambda dc: (round(dc[0], 6), -dc[1][5]))"),
    ("the cells are not sorted at all", "        own.sort(key=lambda dc: (round(dc[0], 6), dc[1][5], dc[1][3], dc[1][4]))\n", ""),
    ("mcc 199 counts", "not (200 <= mcc <= 799)", "not (199 <= mcc <= 799)"),
    ("mcc 800 counts", "not (200 <= mcc <= 799)", "not (200 <= mcc <= 800)"),
    ("mcc 100 counts", "not (200 <= mcc <= 799)", "not (100 <= mcc <= 799)"),
    ("a cell of another mcc stays with the place", "own = [(d, c) for d, c in found if c[2] == mcc]", "own = [(d, c) for d, c in found]"),
    ("a cell that is not LTE counts", 'f[0] != "LTE"', 'f[0] not in ("LTE", "GSM")'),
    ("tac 0 counts", "not (1 <= tac <= 65535)", "not (0 <= tac <= 65535)"),
    ("tac 70000 counts", "not (1 <= tac <= 65535)", "not (1 <= tac <= 99999)"),
    ("an identity beyond 28 bits counts", "not (1 <= cid <= 2 ** 28 - 1)", "not (1 <= cid <= 2 ** 29)"),
    ("a cell 130 km away counts", "RADIUS_KM = 25.0", "RADIUS_KM = 150.0"),
    ("the radius is zero", "RADIUS_KM = 25.0", "RADIUS_KM = 0.001"),
    ("a place without cells is dropped", "        mcc = country_mcc.get(city[0])\n        if mcc is None:\n            continue\n", "        mcc = country_mcc.get(city[0])\n        if mcc is None or not [c for d, c in found if c[2] == mcc]:\n            continue\n"),
    ("a place of a country without a cell is kept", "        if mcc is None:\n            continue\n", "        if mcc is None:\n            mcc = 999\n"),
    ("the country takes the lowest mcc", "key=lambda kv: (-kv[1], kv[0])", "key=lambda kv: (kv[0],)"),
    ("a control character stays in a name", "any(ord(c) < 0x20 or 0x7F <= ord(c) <= 0x9F for c in name)", "False"),
    ("a time zone with a space stays", "any(c not in ZONE_CHARS for c in zone)", "False"),
]
bad = 0
with tempfile.TemporaryDirectory() as d:
    def run(src):
        p = os.path.join(d, "b.py")
        with open(p, "w") as f:
            f.write(src)
        try:
            t.run_all(t.load(p))
            return True
        except Exception:
            return False

    if not run(SOURCE):
        print("BASELINE FAILS: no mutant counts")
        sys.exit(2)
    for name, old, new in MUTANTS:
        if SOURCE.count(old) != 1:
            print("INVALID MUTANT (pattern found %d times):" % SOURCE.count(old), name)
            bad += 1
            continue
        ok = run(SOURCE.replace(old, new))
        print("SURVIVED:" if ok else "killed:  ", name)
        bad += ok
print("%d of %d mutants killed" % (len(MUTANTS) - bad, len(MUTANTS)))
sys.exit(1 if bad else 0)
