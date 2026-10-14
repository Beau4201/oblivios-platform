# identity-surface

Three patches that stop apps from reading device-specific values (ADR-167). The design is
`docs/superpowers/specs/2026-10-14-identity-surface-hardening-design.md` in `sovereignos-foundation`.

| Patch | Project | Effect |
| --- | --- | --- |
| `patches/system-sepolicy/0001` | `system/sepolicy` | Five `ro.boot.*` properties get the type `unit_boot_prop`; every domain except `appdomain` may read it. |
| `patches/frameworks-native/0001` | `frameworks/native` | `SensorEventConnection::sendEvents` folds the bias of the uncalibrated accelerometer, gyroscope and magnetometer into the readings for every uid but `AID_SYSTEM`. |
| `patches/frameworks-native/0002` | `frameworks/native` | `SensorDirectConnection::configureChannel` refuses a direct channel for those three types to every uid but `AID_SYSTEM`. |

`test/` holds the host test of the sensor arithmetic and a mutation check:

    AOSP_ROOT=<tree or copy with hardware/ and system/> test/run.sh <dir with SensorBiasFold.h>
    AOSP_ROOT=... test/mutate.py <dir with SensorBiasFold.h>

The header is part of `frameworks-native/0001`; extract it with `git apply --include='*SensorBiasFold.h'` into an empty repository.
