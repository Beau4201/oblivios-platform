#!/usr/bin/env python3
"""Mutation check for VibratorCoarseTest: every mutant of VibratorCoarse.java must fail it.
Usage: mutate-vibrator.py <VibratorCoarse.java>"""
import os, subprocess, sys, tempfile

src = open(sys.argv[1]).read()
here = os.path.dirname(os.path.abspath(__file__))
mutants = {
    "no NaN guard": ("Float.isNaN(value) || Float.isInfinite(value)", "Float.isInfinite(value)"),
    "no infinity guard": ("Float.isNaN(value) || Float.isInfinite(value)", "Float.isNaN(value)"),
    "truncate": ("Math.round(value / step) * step", "(int) (value / step) * step"),
    "ignore step": ("Math.round(value / step) * step", "Math.round(value / 10f) * 10f"),
    "floor": ("Math.round(value / step) * step", "(float) Math.floor(value / step) * step"),
    "resonant step": ("coarse(resonantHz, 10f)", "coarse(resonantHz, 5f)"),
    "zero allowed": ("rounded > 0f ? rounded : Float.NaN", "rounded >= 0f ? rounded : Float.NaN"),
    "negative allowed": ("rounded > 0f ? rounded : Float.NaN", "rounded != 0f ? rounded : Float.NaN"),
    "invalid returns zero": ("rounded > 0f ? rounded : Float.NaN", "rounded > 0f ? rounded : 0f"),
}
survived = []
for name, (old, new) in mutants.items():
    assert src.count(old) == 1, (name, src.count(old))
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(d + "/m")
        open(d + "/m/VibratorCoarse.java", "w").write(src.replace(old, new))
        out = d + "/out"
        os.makedirs(out)
        c = subprocess.run(["javac", "-d", out, d + "/m/VibratorCoarse.java", here + "/VibratorCoarseTest.java"], capture_output=True)
        assert c.returncode == 0, (name, c.stderr.decode())
        r = subprocess.run(["java", "-cp", out, "android.os.VibratorCoarseTest"], capture_output=True)
        print(("killed  " if r.returncode != 0 else "SURVIVED"), name)
        if r.returncode == 0:
            survived.append(name)
print(f"{len(mutants) - len(survived)} of {len(mutants)} killed")
sys.exit(1 if survived else 0)
