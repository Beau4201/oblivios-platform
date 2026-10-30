# compartments-profiles

Host-checked cores of the profiles of Compartments (ADR-208, `docs/superpowers/specs/2026-10-30-compartments-profiles-design.md`
in `freeholdos-foundation`): the policy store and its mirrors in Java, and the sensor mode table, its file source and the
invented motion streams in C++. State: the cores are written and checked on the host; `frameworks-base/0001` to `0004` and `frameworks-native/0003` and `0004` are landed on the build server and not module-built, the two sepolicy patches are exported and not landed, no image carries any of it (`SOURCE.md`). The AOSP-side patches (`frameworks-base`, `frameworks-native`, `system-sepolicy`, `packages-apps-Settings`) and the exit
app changes follow in `docs/superpowers/plans/2026-10-30-compartments-profiles.md`, which names the files below as its
inputs.

| Path | Content |
| --- | --- |
| `system-server/src/com/android/server/compartments/` | `Profiles` (profiles, classes, values, defaults), `PolicyStore` (immutable store and its codec; a store that cannot be read reads as Protected for every app), `MirrorPlan` (what the enforcement points are told), `Geo`, `PersonaTrack` (the real track moved to another place, continuous across a re-anchor) `ActivityEstimator` (still, walking or driving from the speed of the fixes), `Places` (the offline city list, strict parser, search, the automatic place), `CellSubset` (the cell towers of a place) and `PersonaFile` (the file of app ids and places that the telephony, Wi-Fi and Bluetooth code read). No `android.*` import. |
| `system-server/test/` | `ProfilesTest.java` (546 checks), `PersonaTest.java` (70 checks), `PlacesTest.java` (322 checks of `Places`, `CellSubset` and `PersonaFile`), `run.sh`, `mutate.py` (190 mutants over all the Java cores; the unmutated tree must pass first, and every mutant must fail a check). |
| `sensor/src/` | `SensorModes.h` (the table keyed by app id, the file format, the mapping of an SDK sandbox to its host app, the direct-channel rule), `SensorModeSource.h` (reads the file at most once per second, refuses a file of another owner or one writable by group or others), `SubstituteStream.h` (invented motion events, `applyMode`). They include `SensorBiasFold.h` of `privacy/identity-surface`. |
| `sensor/test/` | `SensorProfile_test.cpp`, `run.sh`, `mutate.py` (50 mutants; the unmutated headers must pass first). |

Run (the sensor tests need the real `hardware/sensors.h`; the tree of the build server has it):

    system-server/test/run.sh
    system-server/test/mutate.py
    AOSP_ROOT=<tree> sensor/test/run.sh <dir with the three headers and SensorBiasFold.h>
    AOSP_ROOT=<tree> sensor/test/mutate.py <the same dir>

The sensor tests were first run against a local stand-in of `hardware/sensors.h` and `private/android_filesystem_config.h`; the run against the real headers of the tree passed on 2026-10-30 (`ok`, 50 of 50 mutants killed). The real `sensors.h` is a symlink into `hardware/libhardware/include_all`, so a copy of the tree needs that directory too.
