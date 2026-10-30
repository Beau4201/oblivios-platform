/*
 * FreeholdOS (ADR-208): invented motion data for an app on the Isolated profile, and the single place that applies a
 * SensorMode to an event.
 *
 * Why this diverges from upstream: an app that demands the motion sensors must keep working and must learn nothing true. The
 * stream is plausible for a held phone, a walking person or a vehicle, differs per app, is the same for one app on one
 * phone, and carries no bias estimate. It is a function of (seed, event time, activity), so it needs no state in the
 * sensor service and cannot disagree with itself between two connections of one app.
 */
#ifndef ANDROID_SUBSTITUTE_STREAM_H
#define ANDROID_SUBSTITUTE_STREAM_H

#include "SensorModes.h"

#include <cmath>
#include <cstddef>
#include <cstdint>

namespace android {

// The activity the invented stream looks like. The first version always uses STILL; the location persona couples it to the
// speed of the translated track (ADR-208, spec section 4.4).
enum class Activity : uint8_t { STILL = 0, WALKING = 1, DRIVING = 2 };

namespace substitute_detail {

constexpr float kPi = 3.14159265358979f;
constexpr float kGravity = 9.80665f;

// A float in [-1, 1) from (seed, event time, channel), the same every time.
inline float noise(uint64_t seed, int64_t t, uint32_t channel) {
    const uint64_t h = SensorModeTable::mix(seed ^ static_cast<uint64_t>(t) ^ (static_cast<uint64_t>(channel) << 56));
    return static_cast<float>(static_cast<double>(h >> 11) / 4503599627370496.0 - 1.0);
}

// A float in [lo, hi) fixed for a seed.
inline float perSeed(uint64_t seed, uint32_t slot, float lo, float hi) {
    const float u = noise(seed, 0, 0xF0 + slot) * 0.5f + 0.5f;
    return lo + (hi - lo) * u;
}

}  // namespace substitute_detail

// The type of events that substituteEvent writes.
inline bool substitutes(int32_t type) {
    return isMotionType(type);
}

// Writes the first three values of an event of a motion type (and zero as the bias of an uncalibrated one); the rest of the
// event is left as it is. Returns false, touching nothing, for any other type.
inline bool substituteEvent(sensors_event_t* e, uint64_t seed, Activity a) {
    using namespace substitute_detail;
    if (!substitutes(e->type)) return false;
    const float t = static_cast<float>(static_cast<double>(e->timestamp) * 1e-9);
    const float walk = (a == Activity::WALKING) ? 1.0f : 0.0f;
    const float drive = (a == Activity::DRIVING) ? 1.0f : 0.0f;
    float v[3];
    switch (e->type) {
        case SENSOR_TYPE_ACCELEROMETER:
        case SENSOR_TYPE_ACCELEROMETER_UNCALIBRATED: {
            const float tx = perSeed(seed, 0, -0.08f, 0.08f);   // tilt of a held phone, radians
            const float ty = perSeed(seed, 1, -0.08f, 0.08f);
            const float sigma = 0.035f + 0.4f * drive + 0.6f * walk;
            const float step = 2.5f * std::sin(2.0f * kPi * 1.8f * t) + 1.0f * std::sin(2.0f * kPi * 3.6f * t);
            const float sway = 0.8f * std::sin(2.0f * kPi * 0.25f * t);
            v[0] = kGravity * std::sin(ty) + sigma * noise(seed, e->timestamp, 1) + drive * sway;
            v[1] = -kGravity * std::sin(tx) * std::cos(ty) + sigma * noise(seed, e->timestamp, 2);
            v[2] = kGravity * std::cos(tx) * std::cos(ty) + sigma * noise(seed, e->timestamp, 3) + walk * step;
            break;
        }
        case SENSOR_TYPE_GYROSCOPE:
        case SENSOR_TYPE_GYROSCOPE_UNCALIBRATED: {
            const float sigma = 0.002f + 0.03f * drive + 0.05f * walk;
            const float swing = 0.4f * std::sin(2.0f * kPi * 1.8f * t + perSeed(seed, 2, 0.0f, 6.28f));
            v[0] = sigma * noise(seed, e->timestamp, 4) + walk * swing;
            v[1] = sigma * noise(seed, e->timestamp, 5) + walk * 0.5f * swing;
            v[2] = sigma * noise(seed, e->timestamp, 6);
            break;
        }
        default: {  // magnetic field, calibrated and uncalibrated
            const float magnitude = perSeed(seed, 3, 32.0f, 52.0f);          // microtesla
            const float inclination = perSeed(seed, 4, 0.70f, 1.10f);        // radians
            const float azimuth = perSeed(seed, 5, 0.0f, 6.28f);
            const float sigma = 0.15f + 0.2f * drive + 0.1f * walk;
            v[0] = magnitude * std::cos(inclination) * std::cos(azimuth) + sigma * noise(seed, e->timestamp, 7);
            v[1] = magnitude * std::cos(inclination) * std::sin(azimuth) + sigma * noise(seed, e->timestamp, 8);
            v[2] = -magnitude * std::sin(inclination) + sigma * noise(seed, e->timestamp, 9);
            break;
        }
    }
    e->data[0] = v[0];
    e->data[1] = v[1];
    e->data[2] = v[2];
    if (eventCarriesBias(e->type)) {
        e->data[3] = e->data[4] = e->data[5] = 0.0f;
    }
    return true;
}

// Applies a mode to one event in place. Returns false when the event is not to be delivered.
inline bool applyMode(sensors_event_t* e, SensorMode m, uint64_t seed, Activity a) {
    switch (m) {
        case SensorMode::RAW:
            return true;
        case SensorMode::FOLDED:
            if (eventCarriesBias(e->type)) foldBiasIntoReadings(e);
            return true;
        case SensorMode::SUBSTITUTE:
            substituteEvent(e, seed, a);
            return true;
        case SensorMode::BLOCKED:
            return !isMotionType(e->type);
    }
    return true;
}

// Whether a mode changes or drops an event of this type (so that a shared buffer must be copied first).
inline bool modeTouches(SensorMode m, int32_t type) {
    switch (m) {
        case SensorMode::RAW: return false;
        case SensorMode::FOLDED: return eventCarriesBias(type);
        default: return isMotionType(type);
    }
}

// Copies n events from in to out, applies the mode to each copy and leaves out the events that are not to be delivered.
// in is never written: it is shared with the other connections. Returns the number of events written to out.
inline size_t copyEventsApplyingMode(const sensors_event_t* in, size_t n, sensors_event_t* out, SensorMode m,
                                     uint64_t seed, Activity a) {
    size_t count = 0;
    for (size_t i = 0; i < n; i++) {
        out[count] = in[i];
        if (applyMode(&out[count], m, seed, a)) count++;
    }
    return count;
}

}  // namespace android

#endif  // ANDROID_SUBSTITUTE_STREAM_H
