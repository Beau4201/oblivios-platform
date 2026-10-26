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
    ("peak comparison is strict", "if (dev < mParams.peak) {", "if (dev <= mParams.peak) {"),
    ("touch is not required", "if (touchedRecently(tMs)) {", "if (true) {"),
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
    ("cool-down is not set", "mIgnoreUntilMs = tMs + mParams.cooldownMs;\n        final double energy",
     "mIgnoreUntilMs = NEVER;\n        final double energy"),
    ("deviation is signed", "Math.abs(mag - (double) GRAVITY)", "(mag - (double) GRAVITY)"),
    ("one axis only", "Math.sqrt((double) x * x + (double) y * y + (double) z * z)", "Math.abs((double) z)"),
    ("peak is the last sample", "mPeakDev = Math.max(mPeakDev, dev);", "mPeakDev = dev;"),
    ("a new touch does not void the release before it",
     "if (!mInContact) {\n                // a new touch voids the release before it: the finger is on the screen again\n"
     "                mLastReleaseMs = NEVER;\n            }\n            mInContact = true;",
     "mInContact = true;"),
    ("a peak without touch is not reported", "if (tMs >= mNoTouchReportUntilMs) {", "if (false) {"),
    ("a peak without touch is reported for every sample", "mNoTouchReportUntilMs = tMs + mParams.cooldownMs;",
     "mNoTouchReportUntilMs = NEVER;"),
    ("a candidate says it was untouched", "return new Decision(trigger, true,", "return new Decision(trigger, false,"),
    ("reset keeps the touch history", "mLastContactMs = NEVER;\n        mLastReleaseMs = NEVER;\n        mPending",
     "mLastReleaseMs = NEVER;\n        mPending"),
    ("impact comparison is strict", "dev >= mParams.impactPeak", "dev > mParams.impactPeak"),
    ("impact route cannot be turned off", "mParams.impactPeak > 0 && dev >=", "dev >="),
    ("impact does not start the cool-down",
     "mPending = false;\n                mIgnoreUntilMs = tMs + mParams.cooldownMs;\n            } else {",
     "mPending = false;\n            } else {"),
    ("impact leaves the candidate pending",
     "mPending = false;\n                mIgnoreUntilMs = tMs + mParams.cooldownMs;\n            } else {",
     "mIgnoreUntilMs = tMs + mParams.cooldownMs;\n            } else {"),
    ("impact does not say it is an impact", "(float) dev, false, 0, 0f, true, fall);", "(float) dev, false, 0, 0f, false, fall);"),
    ("impact does not report the free fall", "(float) dev, false, 0, 0f, true, fall);", "(float) dev, false, 0, 0f, true, 0L);"),
    ("impact always says it was touched", "new Decision(lock, touchedRecently(tMs),", "new Decision(lock, true,"),
    ("impact is not a trigger", "new Decision(lock, touchedRecently(tMs),", "new Decision(false, touchedRecently(tMs),"),
    ("every impact locks", "final boolean lock = fall == 0 || fall >= mParams.freeFallLongMs;", "final boolean lock = true;"),
    ("no impact locks after a fall", "final boolean lock = fall == 0 || fall >= mParams.freeFallLongMs;",
     "final boolean lock = fall == 0;"),
    ("an impact with no free fall does not lock", "final boolean lock = fall == 0 || fall >= mParams.freeFallLongMs;",
     "final boolean lock = fall >= mParams.freeFallLongMs;"),
    ("the long-fall limit is exclusive", "fall >= mParams.freeFallLongMs;", "fall > mParams.freeFallLongMs;"),
    ("a set-down starts the cool-down",
     "mImpactQuietUntilMs = tMs + IMPACT_QUIET_MS;\n                mNoTouchReportUntilMs",
     "mIgnoreUntilMs = tMs + mParams.cooldownMs;\n                mNoTouchReportUntilMs"),
    ("the pulse of a set-down is judged again",
     "mImpactQuietUntilMs = tMs + IMPACT_QUIET_MS;\n                mNoTouchReportUntilMs",
     "mNoTouchReportUntilMs"),
    ("the pulse of a set-down is reported without touch",
     "mNoTouchReportUntilMs = tMs + IMPACT_QUIET_MS;\n            }", "}"),
    ("the quiet period is not stored", "mImpactQuietUntilMs = NEVER;\n        mLastSampleMs", "mLastSampleMs"),
    ("the quiet period is exclusive", "tMs >= mImpactQuietUntilMs", "tMs > mImpactQuietUntilMs"),
    ("the free fall is not tracked", "trackFreeFall(tMs, mag);\n        if", "if"),
    ("the free fall is tracked after the cool-down", "trackFreeFall(tMs, mag);\n        if (mIgnoreUntilMs != NEVER && tMs < mIgnoreUntilMs) {\n            return null;\n        }",
     "if (mIgnoreUntilMs != NEVER && tMs < mIgnoreUntilMs) {\n            return null;\n        }\n        trackFreeFall(tMs, mag);"),
    ("the free fall limit is inclusive", "mag < FREE_FALL_MAX", "mag <= FREE_FALL_MAX"),
    ("the last interval is not counted", "mFallLenMs = tMs - mFallStartMs + dt;", "mFallLenMs = tMs - mFallStartMs;"),
    ("a run starts at every sample", "if (!mInFall) {\n                mInFall = true;\n                mFallStartMs = tMs;\n            }",
     "mInFall = true;\n            mFallStartMs = tMs;"),
    ("the run end is not stored", "mFallEndMs = tMs;\n            // a run", "// a run"),
    ("the latest run replaces the longest", "mFallLenMs >= mBestLenMs ||", "true ||"),
    ("an old run is never replaced", " || tMs - mBestEndMs > FREE_FALL_WINDOW_MS) {", ") {"),
    ("the window of the best run is exclusive", "tMs - mBestEndMs <= FREE_FALL_WINDOW_MS", "tMs - mBestEndMs < FREE_FALL_WINDOW_MS"),
    ("the running fall is ignored", "if (mInFall) {\n            // the impact sample", "if (false) {\n            // the impact sample"),
    ("the best run is ignored", "best = Math.max(best, mBestLenMs);", "best = best;"),
    ("reset keeps the free fall", "mBestEndMs = NEVER;\n        mBestLenMs = 0;\n    }", "}"),
    ("the long-fall limit is not stored", "this.freeFallLongMs = freeFallLongMs;", "this.freeFallLongMs = 0L;"),
    ("the eight argument constructor sets a limit", "impactPeak, 0L);", "impactPeak, 240L);"),
    ("the short constructor turns the impact route on", "cooldownMs,\n                    0f);",
     "cooldownMs,\n                    30f);"),
    ("the impact threshold is not stored", "this.impactPeak = impactPeak;", "this.impactPeak = 0f;"),
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
