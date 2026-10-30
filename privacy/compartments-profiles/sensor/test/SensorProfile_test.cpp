// Host test for SensorModes.h and SubstituteStream.h: g++ -std=c++17 with the real hardware/sensors.h.
#include "SensorModeSource.h"
#include "SubstituteStream.h"

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

#include <sys/stat.h>
#include <unistd.h>

using namespace android;

static int failures = 0;
#define CHECK(cond)                                                            \
    do {                                                                       \
        if (!(cond)) {                                                         \
            printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);             \
            failures++;                                                        \
        }                                                                      \
    } while (0)

static const int32_t kMotion[] = {SENSOR_TYPE_ACCELEROMETER, SENSOR_TYPE_GYROSCOPE, SENSOR_TYPE_MAGNETIC_FIELD,
                                  SENSOR_TYPE_ACCELEROMETER_UNCALIBRATED, SENSOR_TYPE_GYROSCOPE_UNCALIBRATED,
                                  SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED};
static const int32_t kDerived[] = {SENSOR_TYPE_GRAVITY, SENSOR_TYPE_LINEAR_ACCELERATION, SENSOR_TYPE_ROTATION_VECTOR,
                                   SENSOR_TYPE_GAME_ROTATION_VECTOR, SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR,
                                   SENSOR_TYPE_ORIENTATION, SENSOR_TYPE_DEVICE_ORIENTATION, SENSOR_TYPE_HEADING,
                                   SENSOR_TYPE_STEP_COUNTER, SENSOR_TYPE_STEP_DETECTOR, SENSOR_TYPE_SIGNIFICANT_MOTION,
                                   SENSOR_TYPE_STATIONARY_DETECT, SENSOR_TYPE_MOTION_DETECT};

static sensors_event_t make(int32_t type, int64_t ts, float fill = 1.0f) {
    sensors_event_t e;
    memset(&e, 0, sizeof(e));
    e.version = sizeof(e);
    e.sensor = 7;
    e.type = type;
    e.timestamp = ts;
    for (int i = 0; i < 6; i++) e.data[i] = fill + i;
    e.data[6] = 42.0f;
    return e;
}

static float magnitude(const sensors_event_t& e) {
    return std::sqrt(e.data[0] * e.data[0] + e.data[1] * e.data[1] + e.data[2] * e.data[2]);
}

static const char* kGood = "s 00112233445566ff\nd SUBSTITUTE\na 10100 RAW\na 10200 BLOCKED\na 10300 FOLDED\n";

static void table() {
    SensorModeTable t;
    CHECK(t.parse(kGood));
    CHECK(t.modeFor(10100) == SensorMode::RAW);
    CHECK(t.modeFor(1010200) == SensorMode::BLOCKED);  // the same app in user 10: a line holds an app id
    CHECK(t.modeFor(110200) == SensorMode::BLOCKED);   // and in user 1
    CHECK(t.modeFor(10300) == SensorMode::FOLDED);
    CHECK(t.modeFor(10400) == SensorMode::SUBSTITUTE);       // no line: the default
    CHECK(t.modeFor(110400) == SensorMode::SUBSTITUTE);      // an app of user 1, no line: the default
    // the SDK sandbox of an app answers to its host app, in every user
    CHECK(t.modeFor(20100) == SensorMode::RAW);
    CHECK(t.modeFor(120100) == SensorMode::RAW);
    CHECK(t.modeFor(20200) == SensorMode::BLOCKED);
    CHECK(t.modeFor(1020200) == SensorMode::BLOCKED);
    CHECK(t.modeFor(20400) == SensorMode::SUBSTITUTE);       // the sandbox of an app without a line: the default
    CHECK(t.modeFor(29999) == SensorMode::SUBSTITUTE);
    // ids beyond the sandbox range and isolated uids have no host app: the default
    CHECK(t.modeFor(30100) == SensorMode::SUBSTITUTE);
    CHECK(t.modeFor(90001) == SensorMode::SUBSTITUTE);
    CHECK(t.modeFor(99999) == SensorMode::SUBSTITUTE);
    // platform uids and native daemons are never touched, whatever the default is
    CHECK(t.modeFor(0) == SensorMode::RAW);
    CHECK(t.modeFor(1000) == SensorMode::RAW);
    CHECK(t.modeFor(9999) == SensorMode::RAW);
    CHECK(t.modeFor(101000) == SensorMode::RAW);  // the system uid of user 1
    SensorModeTable blocked;
    CHECK(blocked.parse("s 0000000000000001\nd BLOCKED\n"));
    CHECK(blocked.modeFor(1000) == SensorMode::RAW);
    CHECK(blocked.modeFor(10000) == SensorMode::BLOCKED);

    // the edges of the app id range are valid
    SensorModeTable edges;
    CHECK(edges.parse("s 0000000000000001\nd FOLDED\na 10000 BLOCKED\na 19999 BLOCKED\n"));
    CHECK(edges.modeFor(10000) == SensorMode::BLOCKED && edges.modeFor(19999) == SensorMode::BLOCKED);
    CHECK(edges.modeFor(20000) == SensorMode::BLOCKED && edges.modeFor(29999) == SensorMode::BLOCKED);  // sandboxes of both

    // the seed is stable for an app, differs between apps and between phones
    CHECK(t.seedFor(10100) == t.seedFor(10100));
    CHECK(t.seedFor(10100) != t.seedFor(10400));
    CHECK(t.seedFor(10100) == t.seedFor(110100));  // one app, one stream, in every user
    CHECK(t.seedFor(10100) == t.seedFor(20100));   // and in its sandbox
    CHECK(t.seedFor(10100) != t.seedFor(20400));
    SensorModeTable other;
    CHECK(other.parse("s 00112233445566fe\nd SUBSTITUTE\n"));
    CHECK(other.seedFor(10100) != t.seedFor(10100));

    // the status quo before any file, and after a file that is refused
    SensorModeTable none;
    CHECK(none.modeFor(10100) == SensorMode::FOLDED);
    CHECK(t.parse("garbage\n") == false);
    CHECK(t.modeFor(10100) == SensorMode::FOLDED);
    CHECK(t.modeFor(10400) == SensorMode::FOLDED);

    const char* bad[] = {
        "",
        "s 0000000000000001\n",                                  // no default
        "d FOLDED\n",                                            // no salt
        "d FOLDED\ns 0000000000000001\n",                        // wrong order
        "s 0000000000000001\ns 0000000000000002\nd FOLDED\n",    // repeated salt
        "s 0000000000000001\nd FOLDED\nd RAW\n",                 // repeated default
        "s 0000000000000001\nd folded\n",                        // unknown mode
        "s 0000000000000001\nd FOLDED\na 1000 RAW\n",            // a platform id
        "s 0000000000000001\nd FOLDED\na 9999 RAW\n",
        "s 0000000000000001\nd FOLDED\na 20000 RAW\n",           // a sandbox id: the reader maps those, the file holds hosts
        "s 0000000000000001\nd FOLDED\na 20100 RAW\n",
        "s 0000000000000001\nd FOLDED\na 90000 RAW\n",           // an isolated uid
        "s 0000000000000001\nd FOLDED\na 110100 RAW\n",          // a uid, not an app id
        "s 0000000000000001\nd FOLDED\na 10100 RAW\na 10100 BLOCKED\n",  // repeated app id
        "s 0000000000000001\nd FOLDED\nu 10100 RAW\n",           // the old line type
        "s 0000000000000001\nd FOLDED\na x RAW\n",
        "s 0000000000000001\nd FOLDED\na 99999999999 RAW\n",
        "s 0000000000000001\nd FOLDED\na -5 RAW\n",
        "s 0000000000000001\nd FOLDED\na 10100 RAW x\n",
        "s 0000000000000001\nd FOLDED\na 10100\n",
        "s 0000000000000001\nd FOLDED",                          // no newline
        "s 0000000000000001\r\nd FOLDED\r\n",
        "s 000000000000001\nd FOLDED\n",                         // 15 digits
        "s 000000000000000G\nd FOLDED\n",
        "s 000000000000000A\nd FOLDED\n",                        // upper case
        "s 000000000000000g\nd FOLDED\n",
        "a 10100 RAW\ns 0000000000000001\nd FOLDED\n",           // an app line before the header
    };
    for (const char* text : bad) {
        SensorModeTable x;
        CHECK(!x.parse(text));
        CHECK(x.modeFor(10100) == SensorMode::FOLDED);
    }
    std::string many = "s 0000000000000001\nd FOLDED\n";
    for (int i = 0; i < 8190; i++) many += "a " + std::to_string(10000 + i) + " RAW\n";
    SensorModeTable full;
    CHECK(full.parse(many));                       // 8192 lines
    many += "a 19999 RAW\n";
    CHECK(!full.parse(many));                      // 8193 lines
    CHECK(!full.parse(std::string(1u << 20, 'x') + "\n"));

    // direct channels
    CHECK(directChannelRefused(SensorMode::FOLDED, SENSOR_TYPE_GYROSCOPE_UNCALIBRATED));
    CHECK(!directChannelRefused(SensorMode::FOLDED, SENSOR_TYPE_GYROSCOPE));
    CHECK(!directChannelRefused(SensorMode::RAW, SENSOR_TYPE_GYROSCOPE_UNCALIBRATED));
    for (int32_t type : kMotion) {
        CHECK(directChannelRefused(SensorMode::SUBSTITUTE, type));
        CHECK(directChannelRefused(SensorMode::BLOCKED, type));
    }
    CHECK(!directChannelRefused(SensorMode::SUBSTITUTE, SENSOR_TYPE_PRESSURE));
    for (int32_t type : kDerived) {
        CHECK(directChannelRefused(SensorMode::SUBSTITUTE, type));
        CHECK(directChannelRefused(SensorMode::BLOCKED, type));
        CHECK(!directChannelRefused(SensorMode::FOLDED, type));
        CHECK(!directChannelRefused(SensorMode::RAW, type));
    }
}

static void write(const std::string& path, const std::string& text, mode_t mode = 0600) {
    unlink(path.c_str());
    std::ofstream(path) << text;
    chmod(path.c_str(), mode);
}

// The optional "t" line of the file: what the phone is doing, from the real speed, for the invented streams (ADR-208).
static void activityLine() {
    SensorModeTable t;
    CHECK(t.activity() == Activity::STILL);  // before any file
    CHECK(t.parse(kGood));
    CHECK(t.activity() == Activity::STILL);  // a file without the line keeps STILL
    CHECK(t.parse("s 0000000000000001\nd FOLDED\nt STILL\n"));
    CHECK(t.activity() == Activity::STILL);
    CHECK(t.parse("s 0000000000000001\nd FOLDED\nt WALKING\n"));
    CHECK(t.activity() == Activity::WALKING);
    CHECK(t.parse("s 0000000000000001\nd SUBSTITUTE\nt DRIVING\na 10100 RAW\na 10200 BLOCKED\n"));
    CHECK(t.activity() == Activity::DRIVING);
    CHECK(t.modeFor(10100) == SensorMode::RAW && t.modeFor(10200) == SensorMode::BLOCKED && t.modeFor(10400) == SensorMode::SUBSTITUTE);
    SensorModeTable noLine;
    CHECK(noLine.parse("s 0000000000000001\nd SUBSTITUTE\na 10100 RAW\na 10200 BLOCKED\n"));
    CHECK(t.seedFor(10100) == noLine.seedFor(10100));  // the activity is not part of the seed
    CHECK(t.parse(kGood));
    CHECK(t.activity() == Activity::STILL);  // a file without the line puts it back

    const char* bad[] = {
        "s 0000000000000001\nt WALKING\nd FOLDED\n",                    // before the default
        "t WALKING\ns 0000000000000001\nd FOLDED\n",                    // before the salt
        "s 0000000000000001\nd FOLDED\nt WALKING\nt DRIVING\n",         // repeated
        "s 0000000000000001\nd FOLDED\na 10100 RAW\nt WALKING\n",       // after an app line
        "s 0000000000000001\nd FOLDED\nt walking\n",                    // not a name
        "s 0000000000000001\nd FOLDED\nt RUNNING\n",
        "s 0000000000000001\nd FOLDED\nt FOLDED\n",                     // a mode is not an activity
        "s 0000000000000001\nd FOLDED\nt WALKING x\n",
        "s 0000000000000001\nd FOLDED\nt \n",
        "s 0000000000000001\nd FOLDED\nt\n",
        "s 0000000000000001\nd FOLDED\nt WALKING",                      // no newline
    };
    for (const char* text : bad) {
        SensorModeTable x;
        CHECK(x.parse("s 0000000000000001\nd FOLDED\nt DRIVING\n"));
        CHECK(!x.parse(text));
        CHECK(x.activity() == Activity::STILL);                         // a refused file leaves the status quo
        CHECK(x.modeFor(10100) == SensorMode::FOLDED);
    }

    // through the source, and a change of the line alone reaches the reader
    char dir[] = "/tmp/sensor-activity-XXXXXX";
    CHECK(mkdtemp(dir) != nullptr);
    const std::string path = std::string(dir) + "/modes";
    auto now = std::chrono::steady_clock::time_point{} + std::chrono::hours(1);
    SensorModeSource src(path, getuid(), [&now] { return now; });
    write(path, "s 0000000000000001\nd FOLDED\nt WALKING\n");
    CHECK(src.table()->activity() == Activity::WALKING);
    write(path, "s 0000000000000001\nd FOLDED\nt DRIVING\n");  // same size, a new modification time
    now += std::chrono::seconds(2);
    CHECK(src.table()->activity() == Activity::DRIVING);
    unlink(path.c_str());
    rmdir(dir);
}

static void stream() {
    const uint64_t seedA = SensorModeTable::mix(1), seedB = SensorModeTable::mix(2);
    for (int32_t type : kMotion) {
        for (Activity a : {Activity::STILL, Activity::WALKING, Activity::DRIVING}) {
            sensors_event_t e = make(type, 1234567890LL);
            CHECK(substituteEvent(&e, seedA, a));
            CHECK(std::isfinite(e.data[0]) && std::isfinite(e.data[1]) && std::isfinite(e.data[2]));
            CHECK(e.data[6] == 42.0f);
            CHECK(e.timestamp == 1234567890LL && e.sensor == 7 && e.type == type && e.version == (int32_t)sizeof(e));
            if (eventCarriesBias(type)) {
                CHECK(e.data[3] == 0.0f && e.data[4] == 0.0f && e.data[5] == 0.0f);
            } else {
                CHECK(e.data[3] == 4.0f && e.data[4] == 5.0f && e.data[5] == 6.0f);  // the rest of the payload is left alone
            }
            sensors_event_t again = make(type, 1234567890LL);
            substituteEvent(&again, seedA, a);
            CHECK(memcmp(&e, &again, sizeof(e)) == 0);  // deterministic
            sensors_event_t b = make(type, 1234567890LL);
            substituteEvent(&b, seedB, a);
            CHECK(memcmp(&e.data[0], &b.data[0], 3 * sizeof(float)) != 0);  // differs between apps
        }
    }

    // plausible values for a held phone
    for (int i = 0; i < 200; i++) {
        const int64_t ts = 1000000000LL + i * 20000000LL;
        sensors_event_t acc = make(SENSOR_TYPE_ACCELEROMETER, ts);
        sensors_event_t gyr = make(SENSOR_TYPE_GYROSCOPE, ts);
        sensors_event_t mag = make(SENSOR_TYPE_MAGNETIC_FIELD, ts);
        substituteEvent(&acc, seedA, Activity::STILL);
        substituteEvent(&gyr, seedA, Activity::STILL);
        substituteEvent(&mag, seedA, Activity::STILL);
        CHECK(magnitude(acc) > 9.6f && magnitude(acc) < 10.0f);
        CHECK(magnitude(gyr) < 0.02f);
        CHECK(magnitude(mag) > 30.0f && magnitude(mag) < 54.0f);
    }

    // the noise moves with the event time
    sensors_event_t t1 = make(SENSOR_TYPE_ACCELEROMETER, 1000000000LL), t2 = make(SENSOR_TYPE_ACCELEROMETER, 1020000000LL);
    substituteEvent(&t1, seedA, Activity::STILL);
    substituteEvent(&t2, seedA, Activity::STILL);
    CHECK(t1.data[0] != t2.data[0] && t1.data[2] != t2.data[2]);

    // the character follows the activity
    auto spread = [&](Activity a) {
        double sum = 0, sq = 0;
        const int n = 400;
        for (int i = 0; i < n; i++) {
            sensors_event_t e = make(SENSOR_TYPE_ACCELEROMETER, 1000000000LL + i * 20000000LL);
            substituteEvent(&e, seedA, a);
            sum += e.data[2];
            sq += static_cast<double>(e.data[2]) * e.data[2];
        }
        const double mean = sum / n;
        return std::sqrt(sq / n - mean * mean);
    };
    CHECK(spread(Activity::STILL) < 0.05);
    CHECK(spread(Activity::WALKING) > 1.0);
    auto sway = [&](Activity a) {
        double sum = 0, sq = 0;
        const int n = 400;
        for (int i = 0; i < n; i++) {
            sensors_event_t e = make(SENSOR_TYPE_ACCELEROMETER, 1000000000LL + i * 20000000LL);
            substituteEvent(&e, seedA, a);
            sum += e.data[0];
            sq += static_cast<double>(e.data[0]) * e.data[0];
        }
        const double mean = sum / n;
        return std::sqrt(sq / n - mean * mean);
    };
    CHECK(sway(Activity::DRIVING) > 0.3);
    CHECK(sway(Activity::STILL) < 0.05);

    // other types are not touched
    sensors_event_t p = make(SENSOR_TYPE_PRESSURE, 5);
    sensors_event_t before = p;
    CHECK(!substituteEvent(&p, seedA, Activity::STILL));
    CHECK(memcmp(&p, &before, sizeof(p)) == 0);
}

static void modes() {
    const uint64_t seed = SensorModeTable::mix(3);
    sensors_event_t raw = make(SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 9);
    sensors_event_t keep = raw;
    CHECK(applyMode(&raw, SensorMode::RAW, seed, Activity::STILL));
    CHECK(memcmp(&raw, &keep, sizeof(raw)) == 0);

    sensors_event_t folded = make(SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 9);
    CHECK(applyMode(&folded, SensorMode::FOLDED, seed, Activity::STILL));
    CHECK(folded.data[0] == 1.0f - 4.0f && folded.data[3] == 0.0f);
    sensors_event_t calibrated = make(SENSOR_TYPE_GYROSCOPE, 9);
    sensors_event_t calibratedBefore = calibrated;
    CHECK(applyMode(&calibrated, SensorMode::FOLDED, seed, Activity::STILL));
    CHECK(memcmp(&calibrated, &calibratedBefore, sizeof(calibrated)) == 0);

    sensors_event_t sub = make(SENSOR_TYPE_ACCELEROMETER, 9);
    CHECK(applyMode(&sub, SensorMode::SUBSTITUTE, seed, Activity::STILL));
    CHECK(magnitude(sub) > 9.6f && magnitude(sub) < 10.0f);
    sensors_event_t pressure = make(SENSOR_TYPE_PRESSURE, 9);
    sensors_event_t pressureBefore = pressure;
    CHECK(applyMode(&pressure, SensorMode::SUBSTITUTE, seed, Activity::STILL));
    CHECK(memcmp(&pressure, &pressureBefore, sizeof(pressure)) == 0);

    sensors_event_t blockedMotion = make(SENSOR_TYPE_MAGNETIC_FIELD, 9);
    CHECK(!applyMode(&blockedMotion, SensorMode::BLOCKED, seed, Activity::STILL));
    sensors_event_t blockedPressure = make(SENSOR_TYPE_PRESSURE, 9);
    CHECK(applyMode(&blockedPressure, SensorMode::BLOCKED, seed, Activity::STILL));
    sensors_event_t meta = make(SENSOR_TYPE_META_DATA, 9);
    CHECK(applyMode(&meta, SensorMode::BLOCKED, seed, Activity::STILL));

    for (int32_t type : kMotion) {
        CHECK(!modeTouches(SensorMode::RAW, type));
        CHECK(modeTouches(SensorMode::SUBSTITUTE, type));
        CHECK(modeTouches(SensorMode::BLOCKED, type));
        CHECK(modeTouches(SensorMode::FOLDED, type) == eventCarriesBias(type));
    }
    CHECK(!modeTouches(SensorMode::BLOCKED, SENSOR_TYPE_PRESSURE));
    CHECK(!modeTouches(SensorMode::SUBSTITUTE, SENSOR_TYPE_META_DATA));

    // the types that the HAL derives from the real motion are delivered in neither SUBSTITUTE nor BLOCKED; FOLDED and RAW pass them
    for (int32_t type : kDerived) {
        sensors_event_t d = make(type, 9);
        CHECK(!applyMode(&d, SensorMode::SUBSTITUTE, seed, Activity::STILL));
        sensors_event_t e = make(type, 9);
        CHECK(!applyMode(&e, SensorMode::BLOCKED, seed, Activity::STILL));
        sensors_event_t f = make(type, 9);
        sensors_event_t fBefore = f;
        CHECK(applyMode(&f, SensorMode::FOLDED, seed, Activity::STILL) && memcmp(&f, &fBefore, sizeof(f)) == 0);
        sensors_event_t g = make(type, 9);
        CHECK(applyMode(&g, SensorMode::RAW, seed, Activity::STILL));
        CHECK(modeTouches(SensorMode::SUBSTITUTE, type));
        CHECK(modeTouches(SensorMode::BLOCKED, type));
        CHECK(!modeTouches(SensorMode::FOLDED, type));
        CHECK(!modeTouches(SensorMode::RAW, type));
    }
    sensors_event_t stepIn[2] = {make(SENSOR_TYPE_STEP_COUNTER, 1), make(SENSOR_TYPE_PRESSURE, 2)};
    sensors_event_t stepOut[2];
    CHECK(copyEventsApplyingMode(stepIn, 2, stepOut, SensorMode::SUBSTITUTE, seed, Activity::STILL) == 1 &&
          stepOut[0].type == SENSOR_TYPE_PRESSURE);

    // the copy leaves the shared input alone, drops what is blocked and keeps the order
    sensors_event_t in[4] = {make(SENSOR_TYPE_ACCELEROMETER, 1), make(SENSOR_TYPE_PRESSURE, 2),
                             make(SENSOR_TYPE_META_DATA, 3), make(SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 4)};
    sensors_event_t inCopy[4];
    memcpy(inCopy, in, sizeof(in));
    sensors_event_t out[4];
    const size_t n = copyEventsApplyingMode(in, 4, out, SensorMode::BLOCKED, seed, Activity::STILL);
    CHECK(n == 2);
    CHECK(out[0].type == SENSOR_TYPE_PRESSURE && out[1].type == SENSOR_TYPE_META_DATA);
    CHECK(memcmp(in, inCopy, sizeof(in)) == 0);
    const size_t m = copyEventsApplyingMode(in, 4, out, SensorMode::SUBSTITUTE, seed, Activity::STILL);
    CHECK(m == 4);
    CHECK(memcmp(in, inCopy, sizeof(in)) == 0);
    CHECK(out[0].data[2] != in[0].data[2]);
    CHECK(memcmp(&out[1], &in[1], sizeof(out[1])) == 0);
}

static void source() {
    char dir[] = "/tmp/sensor-source-XXXXXX";
    CHECK(mkdtemp(dir) != nullptr);
    const std::string path = std::string(dir) + "/modes";
    auto now = std::chrono::steady_clock::time_point{} + std::chrono::hours(1);
    SensorModeSource src(path, getuid(), [&now] { return now; });

    // no file: the status quo
    CHECK(src.table()->modeFor(10100) == SensorMode::FOLDED);

    write(path, "s 0000000000000001\nd BLOCKED\n");
    CHECK(src.table()->modeFor(10100) == SensorMode::FOLDED);  // not looked at again within the second
    now += std::chrono::milliseconds(1100);
    CHECK(src.table()->modeFor(10100) == SensorMode::BLOCKED);

    write(path, "s 0000000000000001\nd SUBSTITUTE\n");
    CHECK(src.table()->modeFor(10100) == SensorMode::BLOCKED);
    now += std::chrono::milliseconds(1100);
    CHECK(src.table()->modeFor(10100) == SensorMode::SUBSTITUTE);

    // the same table is kept when the file did not change
    auto held = src.table();
    now += std::chrono::seconds(5);
    CHECK(src.table() == held);

    // an unusable file leaves the table that was read last (never a fall back to real motion data for an app that was
    // substituted or blocked); the folded events remain the answer only while no table has been read
    write(path, "garbage\n");
    now += std::chrono::seconds(2);
    CHECK(src.table()->modeFor(10100) == SensorMode::SUBSTITUTE);

    write(path, "s 0000000000000001\nd BLOCKED\n", 0620);  // writable by the group
    now += std::chrono::seconds(2);
    CHECK(src.table()->modeFor(10100) == SensorMode::SUBSTITUTE);
    write(path, "s 0000000000000001\nd BLOCKED\n", 0602);  // writable by others
    now += std::chrono::seconds(2);
    CHECK(src.table()->modeFor(10100) == SensorMode::SUBSTITUTE);
    SensorModeSource neverRead(path, getuid(), [&now] { return now; });
    CHECK(neverRead.table()->modeFor(10100) == SensorMode::FOLDED);  // no table before: the status quo

    write(path, "s 0000000000000001\nd BLOCKED\n");
    now += std::chrono::seconds(2);
    CHECK(src.table()->modeFor(10100) == SensorMode::BLOCKED);
    SensorModeSource wrongOwner(path, getuid() + 1, [&now] { return now; });
    CHECK(wrongOwner.table()->modeFor(10100) == SensorMode::FOLDED);

    // a symbolic link is not followed, a file over 256 KiB is not read
    const std::string link = std::string(dir) + "/link";
    unlink(link.c_str());
    CHECK(symlink(path.c_str(), link.c_str()) == 0);
    SensorModeSource viaLink(link, getuid(), [&now] { return now; });
    CHECK(viaLink.table()->modeFor(10100) == SensorMode::FOLDED);
    write(path, "s 0000000000000001\nd SUBSTITUTE\n" + std::string(300 * 1024, '\n'));
    now += std::chrono::seconds(2);
    CHECK(src.table()->modeFor(10100) == SensorMode::BLOCKED);  // the last table stays
    SensorModeSource huge(path, getuid(), [&now] { return now; });
    CHECK(huge.table()->modeFor(10100) == SensorMode::FOLDED);

    // the file is gone for a moment: the last table stays
    unlink(path.c_str());
    now += std::chrono::seconds(2);
    CHECK(src.table()->modeFor(10100) == SensorMode::BLOCKED);
    write(path, "s 0000000000000001\nd SUBSTITUTE\n");
    now += std::chrono::seconds(2);
    CHECK(src.table()->modeFor(10100) == SensorMode::SUBSTITUTE);

    unlink(link.c_str());
    unlink(path.c_str());
    rmdir(dir);
}

int main() {
    table();
    activityLine();
    stream();
    modes();
    source();
    if (failures) {
        printf("%d failure(s)\n", failures);
        return 1;
    }
    printf("ok\n");
    return 0;
}
