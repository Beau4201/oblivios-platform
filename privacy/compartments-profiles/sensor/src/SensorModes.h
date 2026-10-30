/*
 * FreeholdOS (ADR-208): what the sensor service does for an app, as set by the Compartments profile of the app.
 *
 * Why this diverges from upstream: ADR-167 folds the bias of the uncalibrated sensors into the readings for every app. A profile
 * lets the user keep an app raw (Trusted), give it invented data (Isolated) or no motion events (Blocked). The system server
 * writes the table to a file that this service reads; a file that is not exactly the format below leaves every app on the
 * folded events of ADR-167, the behaviour of an image without profiles.
 *
 * File format, every line ends with a newline: "s <16 hex digits>" (salt of the invented streams), "d <MODE>" (every app
 * without a line of its own), then optionally "t <ACTIVITY>" (STILL, WALKING or DRIVING; without it STILL), then any number of
 * "a <app id> <MODE>" with an app id of an ordinary app (10000 to 19999). MODE is FOLDED, RAW, SUBSTITUTE or BLOCKED. A line holds an app id, so it reaches the app in every user, and the reader maps the
 * SDK sandbox of an app (app id 20000 to 29999) back to its host app, so that the sandbox cannot read what the app may not.
 * An isolated process has no host app that the sensor service can see and follows the default.
 */
#ifndef ANDROID_SENSOR_MODES_H
#define ANDROID_SENSOR_MODES_H

#include "SensorBiasFold.h"

#include <hardware/sensors.h>
#include <private/android_filesystem_config.h>

#include <sys/types.h>

#include <cstdint>
#include <map>
#include <string>

namespace android {

enum class SensorMode : uint8_t { FOLDED = 0, RAW = 1, SUBSTITUTE = 2, BLOCKED = 3 };

// What the phone is doing, from the speed of its real location fixes: the character of the invented streams.
enum class Activity : uint8_t { STILL = 0, WALKING = 1, DRIVING = 2 };

constexpr uint32_t kFirstAppId = 10000;
constexpr uint32_t kLastAppId = 19999;
constexpr uint32_t kFirstSandboxId = 20000;
constexpr uint32_t kLastSandboxId = 29999;

// The app id that a uid answers to: its id without the user, and for the SDK sandbox of an app the id of the host app.
inline uint32_t hostAppId(uid_t uid) {
    const uint32_t appId = uid % AID_USER_OFFSET;
    return (appId >= kFirstSandboxId && appId <= kLastSandboxId) ? appId - (kFirstSandboxId - kFirstAppId) : appId;
}

// The types that SUBSTITUTE replaces and BLOCKED drops: the three motion sensors and their uncalibrated variants.
inline bool isMotionType(int32_t type) {
    return type == SENSOR_TYPE_ACCELEROMETER || type == SENSOR_TYPE_GYROSCOPE ||
           type == SENSOR_TYPE_MAGNETIC_FIELD || type == SENSOR_TYPE_ACCELEROMETER_UNCALIBRATED ||
           type == SENSOR_TYPE_GYROSCOPE_UNCALIBRATED || type == SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED;
}

// The types that the HAL derives from the same real motion (gravity, linear acceleration, the rotation vectors, the old
// orientation, the steps and the detectors): the stream that SUBSTITUTE invents has no counterpart for them, and the real values
// would contradict it, so SUBSTITUTE and BLOCKED both deliver none of them.
inline bool isDerivedMotionType(int32_t type) {
    return type == SENSOR_TYPE_GRAVITY || type == SENSOR_TYPE_LINEAR_ACCELERATION || type == SENSOR_TYPE_ROTATION_VECTOR ||
           type == SENSOR_TYPE_GAME_ROTATION_VECTOR || type == SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR ||
           type == SENSOR_TYPE_ORIENTATION || type == SENSOR_TYPE_DEVICE_ORIENTATION || type == SENSOR_TYPE_HEADING ||
           type == SENSOR_TYPE_STEP_COUNTER || type == SENSOR_TYPE_STEP_DETECTOR || type == SENSOR_TYPE_SIGNIFICANT_MOTION ||
           type == SENSOR_TYPE_STATIONARY_DETECT || type == SENSOR_TYPE_MOTION_DETECT;
}

// A direct channel hands the HAL's own events to the app, so it must not exist where the app is not meant to see them:
// for a folded app the uncalibrated types (ADR-167), for a substituted or blocked app every motion type.
inline bool directChannelRefused(SensorMode m, int32_t type) {
    switch (m) {
        case SensorMode::FOLDED: return eventCarriesBias(type);
        case SensorMode::SUBSTITUTE:
        case SensorMode::BLOCKED: return isMotionType(type) || isDerivedMotionType(type);
        default: return false;
    }
}

class SensorModeTable {
public:
    // Replaces the table with what text holds. Any text that is not exactly a file of the format above (a missing or repeated
    // "s" or "d" line, an unknown mode, an app id outside 10000 to 19999, a line without a newline, more than 8192 lines) leaves
    // the status quo and returns false.
    bool parse(const std::string& text) {
        uint64_t salt = 0;
        SensorMode dflt = SensorMode::FOLDED;
        Activity activity = Activity::STILL;
        bool haveSalt = false, haveDefault = false, haveActivity = false;
        std::map<uint32_t, SensorMode> perApp;
        size_t pos = 0, lines = 0;
        while (pos < text.size()) {
            const size_t nl = text.find('\n', pos);
            if (nl == std::string::npos || ++lines > kMaxLines) { reset(); return false; }
            const std::string line = text.substr(pos, nl - pos);
            pos = nl + 1;
            if (line.size() >= 3 && line[0] == 's' && line[1] == ' ' && !haveSalt && !haveDefault && perApp.empty()) {
                if (!parseHex(line.substr(2), &salt)) { reset(); return false; }
                haveSalt = true;
            } else if (line.size() >= 3 && line[0] == 'd' && line[1] == ' ' && haveSalt && !haveDefault && perApp.empty()) {
                if (!parseMode(line.substr(2), &dflt)) { reset(); return false; }
                haveDefault = true;
            } else if (line.size() >= 3 && line[0] == 't' && line[1] == ' ' && haveDefault && !haveActivity && perApp.empty()) {
                if (!parseActivity(line.substr(2), &activity)) { reset(); return false; }
                haveActivity = true;
            } else if (line.size() >= 3 && line[0] == 'a' && line[1] == ' ' && haveDefault) {
                const size_t sp = line.find(' ', 2);
                uint64_t appId = 0;
                SensorMode m;
                if (sp == std::string::npos || !parseDecimal(line.substr(2, sp - 2), &appId) ||
                    appId < kFirstAppId || appId > kLastAppId ||
                    !parseMode(line.substr(sp + 1), &m) ||
                    !perApp.emplace(static_cast<uint32_t>(appId), m).second) {
                    reset();
                    return false;
                }
            } else {
                reset();
                return false;
            }
        }
        if (!haveDefault) { reset(); return false; }
        mSalt = salt;
        mDefault = dflt;
        mActivity = activity;
        mPerApp = std::move(perApp);
        return true;
    }

    // Native daemons and platform uids keep the unchanged event; an app, in any user, and its SDK sandbox follow the line of the
    // app or the default.
    SensorMode modeFor(uid_t uid) const {
        if (!shouldFoldFor(uid)) return SensorMode::RAW;
        auto it = mPerApp.find(hostAppId(uid));
        return it != mPerApp.end() ? it->second : mDefault;
    }

    // The seed of the invented streams of one app: the same for the app, its sandbox and its copies in other users on this
    // phone, different between apps and between phones.
    uint64_t seedFor(uid_t uid) const { return mix(mSalt ^ (static_cast<uint64_t>(hostAppId(uid)) * 0x9E3779B97F4A7C15ull)); }

    // What the phone is doing now, the same for every app: STILL when the file has no "t" line or cannot be used.
    Activity activity() const { return mActivity; }

    static uint64_t mix(uint64_t z) {
        z += 0x9E3779B97F4A7C15ull;
        z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ull;
        z = (z ^ (z >> 27)) * 0x94D049BB133111EBull;
        return z ^ (z >> 31);
    }

private:
    static constexpr size_t kMaxLines = 8192;

    void reset() { mSalt = 0; mDefault = SensorMode::FOLDED; mActivity = Activity::STILL; mPerApp.clear(); }

    static bool parseActivity(const std::string& s, Activity* out) {
        if (s == "STILL") *out = Activity::STILL;
        else if (s == "WALKING") *out = Activity::WALKING;
        else if (s == "DRIVING") *out = Activity::DRIVING;
        else return false;
        return true;
    }

    static bool parseMode(const std::string& s, SensorMode* out) {
        if (s == "FOLDED") *out = SensorMode::FOLDED;
        else if (s == "RAW") *out = SensorMode::RAW;
        else if (s == "SUBSTITUTE") *out = SensorMode::SUBSTITUTE;
        else if (s == "BLOCKED") *out = SensorMode::BLOCKED;
        else return false;
        return true;
    }

    static bool parseDecimal(const std::string& s, uint64_t* out) {
        if (s.empty() || s.size() > 10) return false;
        uint64_t v = 0;
        for (char c : s) {
            if (c < '0' || c > '9') return false;
            v = v * 10 + static_cast<uint64_t>(c - '0');
        }
        *out = v;
        return true;
    }

    static bool parseHex(const std::string& s, uint64_t* out) {
        if (s.size() != 16) return false;
        uint64_t v = 0;
        for (char c : s) {
            int d;
            if (c >= '0' && c <= '9') d = c - '0';
            else if (c >= 'a' && c <= 'f') d = c - 'a' + 10;
            else return false;
            v = (v << 4) | static_cast<uint64_t>(d);
        }
        *out = v;
        return true;
    }

    uint64_t mSalt = 0;
    SensorMode mDefault = SensorMode::FOLDED;
    Activity mActivity = Activity::STILL;
    std::map<uint32_t, SensorMode> mPerApp;
};

}  // namespace android

#endif  // ANDROID_SENSOR_MODES_H
