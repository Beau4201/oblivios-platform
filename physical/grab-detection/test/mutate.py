#!/usr/bin/env python3
"""Mutation check for GrabDetector.java: every mutant must make a test assertion fail.
Usage: mutate.py <dir that holds com/android/server/grab/GrabDetector.java>
The test and run.sh are the ones next to this script."""
import pathlib, subprocess, sys, tempfile, shutil

HERE = pathlib.Path(__file__).resolve().parent
SRC_DIR = pathlib.Path(sys.argv[1])
REL = pathlib.Path("com/android/server/grab/GrabDetector.java")
SRC = (SRC_DIR / REL).read_text()
MUTANTS = [
    ("peak comparison is strict", "dev >= mParams.peak &&", "dev > mParams.peak &&"),
    ("touch is not required", "dev >= mParams.peak && touchedRecently(tMs)", "dev >= mParams.peak"),
    ("held contact is not touching", "return mInContact\n                ||", "return false\n                ||"),
    ("touch window is exclusive", "tMs - mLastContactMs <= mParams.touchWindowMs",
     "tMs - mLastContactMs < mParams.touchWindowMs"),
    ("touch history is not guarded", "mLastContactMs != NEVER && tMs", "tMs"),
    ("contact time is not recorded", "mInContact = true;\n            mLastContactMs = tMs;",
     "mInContact = true;"),
    ("contact flag is not set", "mInContact = true;\n            mLastContactMs", "mLastContactMs"),
    ("release without contact counts", "if (!mInContact) {\n            return;\n        }", ""),
    ("release interval after the peak is exclusive", "Math.abs(tMs - mPeakMs) <= mParams.touchLossMs",
     "Math.abs(tMs - mPeakMs) < mParams.touchLossMs"),
    ("release interval before the peak is exclusive", "tMs - mLastReleaseMs <= mParams.touchLossMs",
     "tMs - mLastReleaseMs < mParams.touchLossMs"),
    ("release delta has the wrong sign", "mReleaseDeltaMs = tMs - mPeakMs;", "mReleaseDeltaMs = mPeakMs - tMs;"),
    ("early release delta has the wrong sign", "mLastReleaseMs - tMs : 0", "tMs - mLastReleaseMs : 0"),
    ("release is not required", "mReleased && energy >=", "energy >="),
    ("energy comparison is strict", "energy >= mParams.sustainEnergy", "energy > mParams.sustainEnergy"),
    ("settle time is measured", "} else if (since < mParams.settleMs + mParams.sustainMs) {",
     "} else if (since < mParams.settleMs + mParams.sustainMs || true) {"),
    ("settle sample leaks into the window", "if (since < mParams.settleMs) {", "if (since <= mParams.settleMs) {"),
    ("window end is inclusive", "if (since < mParams.settleMs + mParams.sustainMs) {\n            return null;",
     "if (since <= mParams.settleMs + mParams.sustainMs) {\n            return null;"),
    ("cool-down is not set", "mIgnoreUntilMs = tMs + mParams.cooldownMs;", "mIgnoreUntilMs = NEVER;"),
    ("deviation is signed", "Math.abs(\n                Math.sqrt", "(\n                Math.sqrt"),
    ("one axis only", "Math.sqrt((double) x * x + (double) y * y + (double) z * z)", "Math.abs((double) z)"),
    ("peak is the last sample", "mPeakDev = Math.max(mPeakDev, dev);", "mPeakDev = dev;"),
    ("reset keeps the touch history", "mLastContactMs = NEVER;\n        mLastReleaseMs = NEVER;\n        mPending",
     "mLastReleaseMs = NEVER;\n        mPending"),
]
survived = 0
for name, old, new in MUTANTS:
    assert SRC.count(old) == 1, f"mutant {name!r}: pattern found {SRC.count(old)} times"
    with tempfile.TemporaryDirectory() as d:
        dst = pathlib.Path(d) / REL
        dst.parent.mkdir(parents=True)
        dst.write_text(SRC.replace(old, new))
        r = subprocess.run([str(HERE / "run.sh"), d], capture_output=True, text=True)
    # only a failing assertion counts; a mutant that does not compile proves nothing
    killed = r.returncode != 0 and "FAILED" in r.stdout
    print("killed   " if killed else "SURVIVED", name)
    if not killed and "error:" in r.stderr:
        print("         (did not compile)", r.stderr.splitlines()[0])
    survived += not killed
print(f"{len(MUTANTS) - survived} of {len(MUTANTS)} killed")
sys.exit(1 if survived else 0)
