# identity-surface

Three patches that stop apps from reading device-specific values (ADR-167). The design is
`docs/superpowers/specs/2026-10-14-identity-surface-hardening-design.md` in `sovereignos-foundation`.

| Patch | Project | Effect |
| --- | --- | --- |
| `patches/system-sepolicy/0001` | `system/sepolicy` | Five `ro.boot.*` properties get the type `unit_boot_prop`; every domain except `appdomain` and the two app zygotes (which run app code) may read it. `adb shell` is in `appdomain`, so it no longer lists them. |
| `patches/frameworks-native/0001` | `frameworks/native` | `SensorEventConnection::sendEvents` folds the bias of the uncalibrated accelerometer, gyroscope and magnetometer into the readings for every app uid (`uid % AID_USER_OFFSET >= AID_APP_START`); native daemons and platform uids keep the event. |
| `patches/frameworks-native/0002` | `frameworks/native` | `SensorDirectConnection::configureChannel` refuses a direct channel for those three types to app uids. |

`test/` holds the host test of the sensor arithmetic and a mutation check:

    AOSP_ROOT=<tree or copy with hardware/ and system/> test/run.sh <dir with SensorBiasFold.h>
    AOSP_ROOT=... test/mutate.py <dir with SensorBiasFold.h>
    python3 test/check_sepolicy_patch.py

The header is part of `frameworks-native/0001`; extract it with `git apply --include='*SensorBiasFold.h'` into an empty repository.
