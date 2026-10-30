#!/usr/bin/env python3
"""Mutation check for SensorModes.h and SubstituteStream.h: every mutant must make a test assertion fail.
Usage: AOSP_ROOT=... mutate.py <dir that holds SensorModes.h, SubstituteStream.h and SensorBiasFold.h>"""
import os, pathlib, shutil, subprocess, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent
SRC = pathlib.Path(sys.argv[1]).resolve()
ROOT = os.environ["AOSP_ROOT"]
INCLUDES = [f"-I{ROOT}/hardware/libhardware/include", f"-I{ROOT}/system/core/libcutils/include",
            f"-I{ROOT}/system/core/include", f"-I{ROOT}/system/logging/liblog/include"]
M, S, SRCH = "SensorModes.h", "SubstituteStream.h", "SensorModeSource.h"
# The size guard of SensorModeSource bounds the allocation, not the outcome (the 8192-line limit of the parser refuses a larger file
# anyway), so it has no mutant.
MUTANTS = [
    (M, "platform uids get the default", "if (!shouldFoldFor(uid)) return SensorMode::RAW;", ""),
    (M, "the seed ignores the app", "(static_cast<uint64_t>(hostAppId(uid)) * 0x9E3779B97F4A7C15ull)", "(0 * static_cast<uint64_t>(hostAppId(uid)))"),
    (M, "the seed ignores the salt", "mix(mSalt ^ (", "mix(0 ^ ("),
    (M, "a platform app id line is accepted", "appId < kFirstAppId ||", ""),
    (M, "a sandbox app id line is accepted", "appId > kLastAppId ||", ""),
    (M, "a repeated app id is accepted", "!perApp.emplace(static_cast<uint32_t>(appId), m).second", "(perApp.emplace(static_cast<uint32_t>(appId), m), false)"),
    (M, "a sandbox does not follow its host app", "? appId - (kFirstSandboxId - kFirstAppId) : appId;", "? appId : appId;"),
    (M, "the sandbox range ends early", "appId <= kLastSandboxId)", "appId < kLastSandboxId)"),
    (M, "the sandbox range starts late", "appId >= kFirstSandboxId &&", "appId > kFirstSandboxId &&"),
    (M, "the line holds a uid of one user", "const uint32_t appId = uid % AID_USER_OFFSET;", "const uint32_t appId = uid;"),
    (M, "the seed ignores the host app", "static_cast<uint64_t>(hostAppId(uid))", "static_cast<uint64_t>(uid)"),
    (M, "the number of lines is unbounded", "++lines > kMaxLines", "++lines > 100000000"),
    (M, "a short salt is accepted", "if (s.size() != 16) return false;", ""),
    (M, "letters beyond f are hex digits", "else if (c >= 'a' && c <= 'f') d = c - 'a' + 10;", "else if (c >= 'a' && c <= 'z') d = c - 'a' + 10;"),
    (M, "the default line may come first", "line[0] == 'd' && line[1] == ' ' && haveSalt &&", "line[0] == 'd' && line[1] == ' ' &&"),
    (M, "a refused file keeps the old table", "void reset() { mSalt = 0; mDefault = SensorMode::FOLDED; mActivity = Activity::STILL; mPerApp.clear(); }", "void reset() {}"),
    (M, "a refused file keeps the old activity", "mActivity = Activity::STILL; mPerApp.clear(); }", "mPerApp.clear(); }"),
    (M, "an activity line before the default is accepted", "line[0] == 't' && line[1] == ' ' && haveDefault &&", "line[0] == 't' && line[1] == ' ' &&"),
    (M, "a repeated activity line is accepted", "haveDefault && !haveActivity && perApp.empty()", "haveDefault && perApp.empty()"),
    (M, "an activity line after an app line is accepted", "haveDefault && !haveActivity && perApp.empty()", "haveDefault && !haveActivity"),
    (M, "an unknown activity is accepted", 'else if (s == "DRIVING") *out = Activity::DRIVING;\n        else return false;', 'else if (s == "DRIVING") *out = Activity::DRIVING;\n        else *out = Activity::STILL;'),
    (M, "walking is read as still", 'else if (s == "WALKING") *out = Activity::WALKING;', 'else if (s == "WALKING") *out = Activity::STILL;'),
    (M, "driving is read as walking", 'else if (s == "DRIVING") *out = Activity::DRIVING;', 'else if (s == "DRIVING") *out = Activity::WALKING;'),
    (M, "still is not accepted", 'if (s == "STILL") *out = Activity::STILL;\n        else if', 'if (false) *out = Activity::STILL;\n        else if'),
    (M, "the activity is not kept", "mActivity = activity;", ""),
    (M, "the activity is always still", "Activity activity() const { return mActivity; }", "Activity activity() const { return Activity::STILL; }"),
    (M, "a file without the line keeps the old activity", "Activity activity = Activity::STILL;\n        bool haveSalt", "Activity activity = mActivity;\n        bool haveSalt"),
    (M, "a substituted app keeps its direct channel", "case SensorMode::BLOCKED: return isMotionType(type) || isDerivedMotionType(type);", "case SensorMode::BLOCKED: return false;"),
    (M, "a folded app loses every motion direct channel", "case SensorMode::FOLDED: return eventCarriesBias(type);", "case SensorMode::FOLDED: return isMotionType(type);"),
    (M, "a raw app loses its direct channels", "default: return false;\n    }\n}", "default: return true;\n    }\n}"),
    (M, "an unknown mode name is accepted", 'else return false;\n        return true;\n    }\n\n    static bool parseDecimal', 'else *out = SensorMode::RAW;\n        return true;\n    }\n\n    static bool parseDecimal'),
    (SRCH, "the file is read at every call", "if (now >= mNextCheck) {", "if (true) {"),
    (SRCH, "the file is never read again", "mNextCheck = now + std::chrono::seconds(1);", "mNextCheck = now + std::chrono::hours(100000);"),
    (SRCH, "the owner is not checked", "st.st_uid != mOwner ||", ""),
    (SRCH, "a group-writable file is read", "(st.st_mode & (S_IWGRP | S_IWOTH)) != 0 ||", "(st.st_mode & S_IWOTH) != 0 ||"),
    (SRCH, "a world-writable file is read", "(st.st_mode & (S_IWGRP | S_IWOTH)) != 0 ||", "(st.st_mode & S_IWGRP) != 0 ||"),
    (SRCH, "a symbolic link is followed", "O_RDONLY | O_CLOEXEC | O_NOFOLLOW", "O_RDONLY | O_CLOEXEC"),
    (SRCH, "a refused file puts every app back on the folded events", "if (got != st.st_size || !next->parse(text)) {\n            return;", "if (got != st.st_size || !next->parse(text)) {\n            mTable = std::make_shared<const SensorModeTable>();\n            return;"),
    (SRCH, "an unusable file puts every app back on the folded events", "if (fd >= 0) close(fd);\n            return;  // the table of the last good read stays", "if (fd >= 0) close(fd);\n            mTable = std::make_shared<const SensorModeTable>();\n            return;"),
    (SRCH, "an unchanged file is parsed again", "if (mHaveSignature && st.st_mtim.tv_sec == mSec && st.st_mtim.tv_nsec == mNsec && st.st_size == mSize) {", "if (false) {"),
    (S, "gravity is wrong", "constexpr float kGravity = 9.80665f;", "constexpr float kGravity = 9.0f;"),
    (S, "the bias of an invented event is not zeroed", "e->data[3] = e->data[4] = e->data[5] = 0.0f;", "(void)0;"),
    (S, "a non-motion type is overwritten", "if (!substitutes(e->type)) return false;", ""),
    (S, "walking has no steps", "const float step = 2.5f * std::sin(2.0f * kPi * 1.8f * t) + 1.0f * std::sin(2.0f * kPi * 3.6f * t);", "const float step = 0.0f;"),
    (S, "driving has no sway", "+ drive * sway;", ";"),
    (S, "the noise ignores the event time", "seed ^ static_cast<uint64_t>(t) ^", "seed ^"),
    (S, "the noise ignores the seed", "SensorModeTable::mix(seed ^ static_cast<uint64_t>(t) ^", "SensorModeTable::mix(static_cast<uint64_t>(t) ^"),
    (S, "a blocked app gets motion events", "return !isMotionType(e->type) && !isDerivedMotionType(e->type);", "return true;"),
    (S, "a folded app is not folded", "if (eventCarriesBias(e->type)) foldBiasIntoReadings(e);", ""),
    (S, "the copy writes the shared input", "out[count] = in[i];\n        if (applyMode(&out[count], m, seed, a)) count++;",
     "if (applyMode(const_cast<sensors_event_t*>(&in[i]), m, seed, a)) out[count++] = in[i];"),
    (S, "a raw mode touches events", "case SensorMode::RAW: return false;", "case SensorMode::RAW: return true;"),
]
MUTANTS += [
    (M, "a substituted or blocked app keeps a derived direct channel", "case SensorMode::BLOCKED: return isMotionType(type) || isDerivedMotionType(type);", "case SensorMode::BLOCKED: return isMotionType(type);"),
    (S, "a substituted app gets derived events", "if (isDerivedMotionType(e->type)) return false;\n            substituteEvent(e, seed, a);", "substituteEvent(e, seed, a);"),
    (S, "a blocked app gets derived events", "return !isMotionType(e->type) && !isDerivedMotionType(e->type);", "return !isMotionType(e->type);"),
    (S, "a derived event is delivered from the shared buffer", "default: return isMotionType(type) || isDerivedMotionType(type);", "default: return isMotionType(type);"),
]
# one mutant per derived type: the type is no longer in the list
MUTANTS += [(M, f"{name.lower()} is not a derived motion type", f"type == SENSOR_TYPE_{name}", "false") for name in (
    "GRAVITY", "LINEAR_ACCELERATION", "ROTATION_VECTOR", "GAME_ROTATION_VECTOR", "GEOMAGNETIC_ROTATION_VECTOR", "ORIENTATION",
    "DEVICE_ORIENTATION", "HEADING", "STEP_COUNTER", "STEP_DETECTOR", "SIGNIFICANT_MOTION", "STATIONARY_DETECT", "MOTION_DETECT")]
# The unmutated headers must pass first: with a failing baseline every mutant would count as killed and prove nothing.
with tempfile.TemporaryDirectory() as d:
    d = pathlib.Path(d)
    for h in (M, S, SRCH, "SensorBiasFold.h"):
        shutil.copy(SRC / h, d / h)
    c = subprocess.run(["g++", "-std=c++17", "-Wall", f"-I{d}"] + INCLUDES + [str(HERE / "SensorProfile_test.cpp"), "-o", str(d / "t")],
                       capture_output=True, text=True)
    if c.returncode != 0 or subprocess.run([str(d / "t")], capture_output=True, text=True, timeout=60).returncode != 0:
        print("BASELINE FAILS, no mutant counts:", c.stderr[:300])
        sys.exit(2)
survived = 0
for name, what, old, new in MUTANTS:
    with tempfile.TemporaryDirectory() as d:
        d = pathlib.Path(d)
        for h in (M, S, SRCH, "SensorBiasFold.h"):
            shutil.copy(SRC / h, d / h)
        text = (d / name).read_text()
        assert text.count(old) == 1, f"{name}: {what!r}: pattern found {text.count(old)} times"
        (d / name).write_text(text.replace(old, new))
        c = subprocess.run(["g++", "-std=c++17", "-Wall", f"-I{d}"] + INCLUDES + [str(HERE / "SensorProfile_test.cpp"), "-o", str(d / "t")],
                           capture_output=True, text=True)
        if c.returncode != 0:
            print("INVALID MUTANT (does not compile):", name, what, c.stderr[:300])
            survived += 1
            continue
        r = subprocess.run([str(d / "t")], capture_output=True, text=True, timeout=60)
        if r.returncode == 0:
            print("SURVIVED:", name, what)
            survived += 1
        else:
            print("killed:  ", name, what)
print(f"{len(MUTANTS) - survived} of {len(MUTANTS)} mutants killed")
sys.exit(1 if survived else 0)
