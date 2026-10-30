# compartments-profiles

Host-checked cores of the profiles of Compartments (ADR-208, `docs/superpowers/specs/2026-10-30-compartments-profiles-design.md`
in `freeholdos-foundation`): the policy store and its mirrors in Java, and the sensor mode table, its file source and the
invented motion streams in C++. State: written and checked on the host, not landed on the build server, no patch exported, in no
image. The AOSP-side patches (`frameworks-base`, `frameworks-native`, `system-sepolicy`, `packages-apps-Settings`) and the exit
app changes follow in `docs/superpowers/plans/2026-10-30-compartments-profiles.md`, which names the files below as its
inputs.

| Path | Content |
| --- | --- |
| `system-server/src/com/android/server/compartments/` | `Profiles` (profiles, classes, values, defaults), `PolicyStore` (immutable store and its codec; a store that cannot be read reads as Protected for every app), `MirrorPlan` (what the enforcement points are told), `Geo`, `PersonaTrack` (the real track moved to another place, continuous across a re-anchor) and `ActivityEstimator` (still, walking or driving from the speed of the fixes). No `android.*` import. |
| `system-server/test/` | `ProfilesTest.java` (140 checks), `PersonaTest.java` (70 checks), `run.sh`, `mutate.py` (50 mutants over all the Java cores, every one must fail a check). |
| `sensor/src/` | `SensorModes.h` (the table keyed by app id, the file format, the mapping of an SDK sandbox to its host app, the direct-channel rule), `SensorModeSource.h` (reads the file at most once per second, refuses a file of another owner or one writable by group or others), `SubstituteStream.h` (invented motion events, `applyMode`). They include `SensorBiasFold.h` of `privacy/identity-surface`. |
| `sensor/test/` | `SensorProfile_test.cpp`, `run.sh`, `mutate.py` (39 mutants). |

Run (the sensor tests need the real `hardware/sensors.h`; the tree of the build server has it):

    system-server/test/run.sh
    system-server/test/mutate.py
    AOSP_ROOT=<tree> sensor/test/run.sh <dir with the three headers and SensorBiasFold.h>
    AOSP_ROOT=<tree> sensor/test/mutate.py <the same dir>

The sensor tests were first run against a local stand-in of `hardware/sensors.h` and `private/android_filesystem_config.h`
(same field names and constants); the run against the real headers is a step of the plan.
