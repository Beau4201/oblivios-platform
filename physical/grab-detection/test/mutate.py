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
    ("deviation is signed", "final double dev = Math.abs(mag - (double) GRAVITY);", "final double dev = (mag - (double) GRAVITY);"),
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
    ("the quiet period is not stored", "mImpactQuietUntilMs = NEVER;\n        mPulseWait", "mPulseWait"),
    ("the quiet period is exclusive", "tMs >= mImpactQuietUntilMs", "tMs > mImpactQuietUntilMs"),
    ("the free fall is not tracked", "trackFreeFall(tMs, mag);\n        if", "if"),
    ("the free fall is tracked after the cool-down", "trackFreeFall(tMs, mag);\n        if (mPulseWait) {\n            return pulseSample(tMs, mag);\n        }\n        if (mIgnoreUntilMs != NEVER && tMs < mIgnoreUntilMs) {\n            return null;\n        }",
     "if (mPulseWait) {\n            return pulseSample(tMs, mag);\n        }\n        if (mIgnoreUntilMs != NEVER && tMs < mIgnoreUntilMs) {\n            return null;\n        }\n        trackFreeFall(tMs, mag);"),
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
    # the minimum pulse (owner, 2026-10-29)
    ("the pulse rule is off", "if (fall == 0 && mParams.minPulseMs > 0) {", "if (false) {"),
    ("the pulse rule cannot be turned off", "if (fall == 0 && mParams.minPulseMs > 0) {", "if (fall == 0) {"),
    ("a real fall waits for the pulse", "if (fall == 0 && mParams.minPulseMs > 0) {", "if (mParams.minPulseMs > 0) {"),
    ("the pulse minimum is exclusive at the impact sample", "return mPulseSumMs >= mParams.minPulseMs ? pulseSample(tMs, mag) : null;",
     "return mPulseSumMs > mParams.minPulseMs ? pulseSample(tMs, mag) : null;"),
    ("the pulse minimum is exclusive", "final boolean longEnough = mPulseSumMs >= mParams.minPulseMs;",
     "final boolean longEnough = mPulseSumMs > mParams.minPulseMs;"),
    ("the impact sample never decides at once", "return mPulseSumMs >= mParams.minPulseMs ? pulseSample(tMs, mag) : null;", "return null;"),
    ("the rise does not count", "mPulseSumMs = mAboveRunMs;", "mPulseSumMs = 0;"),
    ("the rise counts from the whole recording", "mAboveRunMs = mag >= PULSE_MAG ? mAboveRunMs + dt : 0;",
     "mAboveRunMs = mAboveRunMs + dt;"),
    ("the rise is no run", "mAboveRunMs = mag >= PULSE_MAG ? mAboveRunMs + dt : 0;", "mAboveRunMs = mag >= PULSE_MAG ? dt : 0;"),
    ("the rise limit is exclusive", "mAboveRunMs = mag >= PULSE_MAG ?", "mAboveRunMs = mag > PULSE_MAG ?"),
    ("every sample adds to the pulse", "if (mag >= PULSE_MAG) {\n            mPulseSumMs += dt;", "if (true) {\n            mPulseSumMs += dt;"),
    ("no sample adds to the pulse", "if (mag >= PULSE_MAG) {\n            mPulseSumMs += dt;", "if (false) {\n            mPulseSumMs += dt;"),
    ("the pulse limit is exclusive", "if (mag >= PULSE_MAG) {\n            mPulseSumMs += dt;", "if (mag > PULSE_MAG) {\n            mPulseSumMs += dt;"),
    ("the window never ends", "if (!longEnough && tMs - mPulseStartMs < PULSE_WINDOW_MS) {", "if (!longEnough) {"),
    ("the window is one sample longer", "tMs - mPulseStartMs < PULSE_WINDOW_MS", "tMs - mPulseStartMs <= PULSE_WINDOW_MS"),
    ("the window starts at the beginning of time", "mPulseStartMs = tMs;", "mPulseStartMs = 0;"),
    ("the last pulse sample is not stored", "mPulseLastMs = tMs;\n                // the pulse began", "// the pulse began"),
    ("the pulse peak is the first sample", "mPulsePeak = Math.max(mPulsePeak, Math.abs(mag - (double) GRAVITY));", "mPulsePeak = mPulsePeak;"),
    ("the pulse is not reported", "0L, mPulseSumMs);", "0L, 0L);"),
    ("a refusal is a verdict for the pulse only", "return new Decision(longEnough, touchedRecently(tMs),", "return new Decision(true, touchedRecently(tMs),"),
    ("a long pulse is not a trigger", "return new Decision(longEnough, touchedRecently(tMs),", "return new Decision(false, touchedRecently(tMs),"),
    ("a long pulse does not start the cool-down", "if (longEnough) {\n            mPending = false;\n            mIgnoreUntilMs = tMs + mParams.cooldownMs;",
     "if (longEnough) {\n            mPending = false;"),
    ("a long pulse leaves the candidate pending", "if (longEnough) {\n            mPending = false;\n            mIgnoreUntilMs = tMs + mParams.cooldownMs;",
     "if (longEnough) {\n            mIgnoreUntilMs = tMs + mParams.cooldownMs;"),
    ("a refused pulse is a cool-down", "mImpactQuietUntilMs = tMs + IMPACT_QUIET_MS;\n            mNoTouchReportUntilMs = tMs + IMPACT_QUIET_MS;\n        }\n        return new Decision(longEnough",
     "mIgnoreUntilMs = tMs + mParams.cooldownMs;\n        }\n        return new Decision(longEnough"),
    ("a refused pulse is judged again", "mImpactQuietUntilMs = tMs + IMPACT_QUIET_MS;\n            mNoTouchReportUntilMs = tMs + IMPACT_QUIET_MS;\n        }\n        return new Decision(longEnough",
     "mNoTouchReportUntilMs = tMs + IMPACT_QUIET_MS;\n        }\n        return new Decision(longEnough"),
    ("the wait is not left", "        mPulseWait = false;\n        if (longEnough) {", "        if (longEnough) {"),
    ("the wait does not hold", "if (mPulseWait) {\n            return pulseSample(tMs, mag);\n        }", ""),
    ("the wait is not cleared by a reset", "mPulseWait = false;\n        mAboveRunMs = 0;\n        mLastSampleMs", "mAboveRunMs = 0;\n        mLastSampleMs"),
    ("the minimum pulse is not stored", "this.minPulseMs = minPulseMs;", "this.minPulseMs = 0L;"),
    ("the ten argument constructor starts with a minimum", "impactPeak, freeFallLongMs, 0L);", "impactPeak, freeFallLongMs, 120L);"),
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
