// Host test for SensorBiasFold.h: g++ -std=c++17 with the real hardware/sensors.h.
#include "SensorBiasFold.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <limits>

using namespace android;

static int failures = 0;
#define CHECK(cond)                                                            \
    do {                                                                       \
        if (!(cond)) {                                                         \
            printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);             \
            failures++;                                                        \
        }                                                                      \
    } while (0)

static sensors_event_t make(int32_t type, const float (&v)[6]) {
    sensors_event_t e;
    memset(&e, 0, sizeof(e));
    e.version = sizeof(e);
    e.sensor = 7;
    e.type = type;
    e.timestamp = 123456789;
    for (int i = 0; i < 6; i++) e.data[i] = v[i];
    e.data[6] = 42.0f;  // beyond the sensor payload: must stay as it is
    return e;
}

int main() {
    const int32_t types[] = {SENSOR_TYPE_ACCELEROMETER_UNCALIBRATED,
                             SENSOR_TYPE_GYROSCOPE_UNCALIBRATED,
                             SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED};
    for (int32_t t : types) {
        CHECK(eventCarriesBias(t));
        sensors_event_t e = make(t, {0.402f, -0.476f, 9.79f, -0.002f, 0.075f, 0.017f});
        foldBiasIntoReadings(&e);
        CHECK(e.data[0] == 0.402f - -0.002f);
        CHECK(e.data[1] == -0.476f - 0.075f);
        CHECK(e.data[2] == 9.79f - 0.017f);
        CHECK(e.data[3] == 0.0f && e.data[4] == 0.0f && e.data[5] == 0.0f);
        CHECK(e.data[6] == 42.0f);
        CHECK(e.timestamp == 123456789 && e.sensor == 7 && e.type == t);
    }

    // the calibrated and plain variants never carry a bias
    CHECK(!eventCarriesBias(SENSOR_TYPE_ACCELEROMETER));
    CHECK(!eventCarriesBias(SENSOR_TYPE_GYROSCOPE));
    CHECK(!eventCarriesBias(SENSOR_TYPE_MAGNETIC_FIELD));
    CHECK(!eventCarriesBias(SENSOR_TYPE_PRESSURE));
    CHECK(!eventCarriesBias(SENSOR_TYPE_META_DATA));

    // a bias that is not finite must not turn the readings into NaN
    const float nan = std::numeric_limits<float>::quiet_NaN();
    const float inf = std::numeric_limits<float>::infinity();
    sensors_event_t bad = make(SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, {1.f, 2.f, 3.f, nan, inf, -inf});
    foldBiasIntoReadings(&bad);
    CHECK(bad.data[0] == 1.f && bad.data[1] == 2.f && bad.data[2] == 3.f);
    CHECK(bad.data[3] == 0.f && bad.data[4] == 0.f && bad.data[5] == 0.f);

    // the copy leaves the shared input alone and changes only events that carry a bias
    sensors_event_t in[3] = {
        make(SENSOR_TYPE_ACCELEROMETER_UNCALIBRATED, {1.f, 1.f, 1.f, 0.5f, 0.5f, 0.5f}),
        make(SENSOR_TYPE_PRESSURE, {1013.f, 0.f, 0.f, 9.f, 9.f, 9.f}),
        make(SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED, {10.f, 20.f, 30.f, 1.f, 2.f, 3.f})};
    sensors_event_t before[3];
    memcpy(before, in, sizeof(in));
    sensors_event_t out[3];
    CHECK(copyEventsFoldingBias(in, 3, out) == 3);
    CHECK(memcmp(before, in, sizeof(in)) == 0);
    CHECK(out[0].data[0] == 0.5f && out[0].data[3] == 0.f);
    CHECK(out[1].data[0] == 1013.f && out[1].data[3] == 9.f);  // not a bias event: untouched
    CHECK(out[2].data[0] == 9.f && out[2].data[1] == 18.f && out[2].data[2] == 27.f);
    CHECK(out[2].data[3] == 0.f);

    // only apps get the fold: native daemons, the system and platform uids keep the event
    CHECK(!shouldFoldFor(AID_ROOT));
    CHECK(!shouldFoldFor(AID_SYSTEM));
    CHECK(!shouldFoldFor(AID_RADIO));
    CHECK(!shouldFoldFor(AID_CAMERASERVER));
    CHECK(!shouldFoldFor(AID_SHELL));
    CHECK(!shouldFoldFor(AID_APP_START - 1));
    CHECK(!shouldFoldFor(AID_USER_OFFSET * 10 + AID_SYSTEM));  // system uid of a secondary user
    CHECK(shouldFoldFor(AID_APP_START));
    CHECK(shouldFoldFor(AID_APP_START + 234));
    CHECK(shouldFoldFor(AID_USER_OFFSET * 10 + AID_APP_START + 234));  // app of a secondary user
    CHECK(shouldFoldFor(AID_ISOLATED_START));
    CHECK(shouldFoldFor(AID_USER_OFFSET * 10 + AID_ISOLATED_START + 5));

    printf(failures ? "FAILED %d\n" : "ok\n", failures);
    return failures ? 1 : 0;
}
