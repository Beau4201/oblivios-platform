#!/usr/bin/env python3
"""Mutation check for SensorBiasFold.h: every mutant must make a test assertion fail.
Usage: AOSP_ROOT=... mutate.py <dir that holds SensorBiasFold.h>"""
import pathlib, subprocess, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent
SRC = (pathlib.Path(sys.argv[1]) / "SensorBiasFold.h").read_text()
FOLD = "if (eventCarriesBias(out[i].type)) foldBiasIntoReadings(&out[i]);"
MUTANTS = [
    ("bias is not zeroed", "e->data[i + 3] = 0.0f;", "(void)0;"),
    ("bias is added instead of subtracted", "e->data[i] -= bias;", "e->data[i] += bias;"),
    ("non-finite bias is subtracted", "if (std::isfinite(bias)) e->data[i] -= bias;", "e->data[i] -= bias;"),
    ("plain accelerometer counts as a bias event", "return type == SENSOR_TYPE_ACCELEROMETER_UNCALIBRATED ||",
     "return type == SENSOR_TYPE_ACCELEROMETER ||\n           type == SENSOR_TYPE_ACCELEROMETER_UNCALIBRATED ||"),
    ("magnetometer is forgotten", "||\n           type == SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED;", ";"),
    ("the copy writes the shared input", FOLD,
     "if (eventCarriesBias(out[i].type)) foldBiasIntoReadings(const_cast<sensors_event_t*>(&in[i]));"),
    ("the copy folds events without a bias", FOLD, "foldBiasIntoReadings(&out[i]);"),
]
survived = 0
for name, old, new in MUTANTS:
    assert SRC.count(old) == 1, f"mutant {name!r}: pattern found {SRC.count(old)} times"
    with tempfile.TemporaryDirectory() as d:
        (pathlib.Path(d) / "SensorBiasFold.h").write_text(SRC.replace(old, new))
        r = subprocess.run([str(HERE / "run.sh"), d], capture_output=True, text=True)
    # only a failing assertion counts; a mutant that does not compile proves nothing
    killed = r.returncode != 0 and "FAILED" in r.stdout
    print("killed   " if killed else "SURVIVED", name)
    survived += not killed
print(f"{len(MUTANTS) - survived} of {len(MUTANTS)} killed")
sys.exit(1 if survived else 0)
