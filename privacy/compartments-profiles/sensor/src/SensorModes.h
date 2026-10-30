/*
 * FreeholdOS (ADR-208): what the sensor service does for an app, as set by the Compartments profile of the app.
 *
 * Why this diverges from upstream: ADR-167 folds the bias of the uncalibrated sensors into the readings for every app. A profile
 * lets the user keep an app raw (Trusted), give it invented data (Isolated) or no motion events (Blocked). The system server
 * writes the table to a file that this service reads; a file that is not exactly the format below leaves every app on the
 * folded events of ADR-167, the behaviour of an image without profiles.
 *
 * File format, every line ends with a newline: "s <16 hex digits>" (salt of the invented streams), "d <MODE>" (every app
 * uid without a line of its own), then any number of "u <uid> <MODE>" with an app uid. MODE is FOLDED, RAW, SUBSTITUTE or BLOCKED.
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

// The types that SUBSTITUTE replaces and BLOCKED drops: the three motion sensors and their uncalibrated variants.
inline bool isMotionType(int32_t type) {
    return type == SENSOR_TYPE_ACCELEROMETER || type == SENSOR_TYPE_GYROSCOPE ||
           type == SENSOR_TYPE_MAGNETIC_FIELD || type == SENSOR_TYPE_ACCELEROMETER_UNCALIBRATED ||
           type == SENSOR_TYPE_GYROSCOPE_UNCALIBRATED || type == SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED;
}

// A direct channel hands the HAL's own events to the app, so it must not exist where the app is not meant to see them:
// for a folded app the uncalibrated types (ADR-167), for a substituted or blocked app every motion type.
inline bool directChannelRefused(SensorMode m, int32_t type) {
    switch (m) {
        case SensorMode::FOLDED: return eventCarriesBias(type);
        case SensorMode::SUBSTITUTE:
        case SensorMode::BLOCKED: return isMotionType(type);
        default: return false;
    }
}

class SensorModeTable {
public:
    // Replaces the table with what text holds. Any text that is not exactly a file of the format above (a missing or repeated
    // "s" or "d" line, an unknown mode, a uid that is not an app uid, a line without a newline, more than 8192 lines) leaves
    // the status quo and returns false.
    bool parse(const std::string& text) {
        uint64_t salt = 0;
        SensorMode dflt = SensorMode::FOLDED;
        bool haveSalt = false, haveDefault = false;
        std::map<uid_t, SensorMode> perUid;
        size_t pos = 0, lines = 0;
        while (pos < text.size()) {
            const size_t nl = text.find('\n', pos);
            if (nl == std::string::npos || ++lines > kMaxLines) { reset(); return false; }
            const std::string line = text.substr(pos, nl - pos);
            pos = nl + 1;
            if (line.size() >= 3 && line[0] == 's' && line[1] == ' ' && !haveSalt && !haveDefault && perUid.empty()) {
                if (!parseHex(line.substr(2), &salt)) { reset(); return false; }
                haveSalt = true;
            } else if (line.size() >= 3 && line[0] == 'd' && line[1] == ' ' && haveSalt && !haveDefault && perUid.empty()) {
                if (!parseMode(line.substr(2), &dflt)) { reset(); return false; }
                haveDefault = true;
            } else if (line.size() >= 3 && line[0] == 'u' && line[1] == ' ' && haveDefault) {
                const size_t sp = line.find(' ', 2);
                uint64_t uid = 0;
                SensorMode m;
                if (sp == std::string::npos || !parseDecimal(line.substr(2, sp - 2), &uid) ||
                    uid > 0xFFFFFFFFull || !shouldFoldFor(static_cast<uid_t>(uid)) ||
                    !parseMode(line.substr(sp + 1), &m) ||
                    !perUid.emplace(static_cast<uid_t>(uid), m).second) {
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
        mPerUid = std::move(perUid);
        return true;
    }

    // Native daemons and platform uids keep the unchanged event; an app follows its own line or the default.
    SensorMode modeFor(uid_t uid) const {
        if (!shouldFoldFor(uid)) return SensorMode::RAW;
        auto it = mPerUid.find(uid);
        return it != mPerUid.end() ? it->second : mDefault;
    }

    // The seed of the invented streams of one app: stable for the app on this phone, different between apps.
    uint64_t seedFor(uid_t uid) const { return mix(mSalt ^ (static_cast<uint64_t>(uid) * 0x9E3779B97F4A7C15ull)); }

    static uint64_t mix(uint64_t z) {
        z += 0x9E3779B97F4A7C15ull;
        z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ull;
        z = (z ^ (z >> 27)) * 0x94D049BB133111EBull;
        return z ^ (z >> 31);
    }

private:
    static constexpr size_t kMaxLines = 8192;

    void reset() { mSalt = 0; mDefault = SensorMode::FOLDED; mPerUid.clear(); }

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
    std::map<uid_t, SensorMode> mPerUid;
};

}  // namespace android

#endif  // ANDROID_SENSOR_MODES_H
