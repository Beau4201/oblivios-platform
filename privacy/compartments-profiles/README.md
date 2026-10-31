# compartments-profiles

Host-checked cores of the profiles of Compartments (ADR-208, `docs/superpowers/specs/2026-10-30-compartments-profiles-design.md`
in `freeholdos-foundation`): the policy store and its mirrors in Java, and the sensor mode table, its file source and the
invented motion streams in C++. State: the cores are checked on the host and every patch of `patches/` is landed on the build server (`SOURCE.md`). The first module build found two failures that `system-sepolicy/0003` and `frameworks-base/0007` fix; the review of that build is in `frameworks-base/0008`, `frameworks-native/0005` and the three `0003` copies. The modules are not rebuilt since, and no image carries any of it. The plan is `docs/superpowers/plans/2026-10-30-compartments-profiles.md`.

| Path | Content |
| --- | --- |
| `system-server/src/com/android/server/compartments/` | `Profiles` (profiles, classes, values, defaults), `PolicyStore` (immutable store and its codec; a store that cannot be read reads as Protected for every app), `MirrorPlan` (what the enforcement points are told), `Geo`, `PersonaTrack` (the real track moved to another place, continuous across a re-anchor) `ActivityEstimator` (still, walking or driving from the speed of the fixes), `Places` (the offline city list, strict parser, search, the automatic place), `CellSubset` (the cell towers of a place) and `PersonaFile` (the file of app ids and places that the telephony, Wi-Fi and Bluetooth code read). No `android.*` import. |
| `system-server/test/` | `ProfilesTest.java` (584 checks), `PersonaTest.java` (80 checks), `PlacesTest.java` (334 checks of `Places`, `CellSubset` and `PersonaFile`), `run.sh`, `mutate.py` (229 mutants over all the Java cores; the unmutated tree must pass first, and every mutant must fail a check). |
| `persona-client/` | The code that the framework, Telephony, Wi-Fi and Bluetooth modules carry as copies: `PersonaReader` (reads the persona file, keeps the last good table when a reload fails), `PersonaCells` (the cell identity of a place), `PersonaProcess` (the persona of the calling process); `test/` holds their host checks and mutants. `tools/check-copies.py` fails when a copy in `patches/` differs from its source. |
| `data/`, `tools/` | `data/SOURCES.md` names the place and cell data that are not supplied yet; `tools/build-persona-data.py` builds the data files from them, with its tests and mutants. |
| `sensor/src/` | `SensorModes.h` (the table keyed by app id, the file format, the mapping of an SDK sandbox to its host app, the direct-channel rule), `SensorModeSource.h` (reads the file at most once per second, refuses a file of another owner or one writable by group or others), `SubstituteStream.h` (invented motion events, `applyMode`). They include `SensorBiasFold.h` of `privacy/identity-surface`. |
| `sensor/test/` | `SensorProfile_test.cpp`, `run.sh`, `mutate.py` (70 mutants; the unmutated headers must pass first). |

Run (the sensor tests need the real `hardware/sensors.h`; the tree of the build server has it):

    system-server/test/run.sh
    system-server/test/mutate.py
    AOSP_ROOT=<tree> sensor/test/run.sh <dir with the three headers and SensorBiasFold.h>
    AOSP_ROOT=<tree> sensor/test/mutate.py <the same dir>

The sensor tests were first run against a local stand-in of `hardware/sensors.h` and `private/android_filesystem_config.h`; the run against the real headers of the tree passed on 2026-10-30 (`ok`, 50 of 50 mutants killed). The real `sensors.h` is a symlink into `hardware/libhardware/include_all`, so a copy of the tree needs that directory too.
