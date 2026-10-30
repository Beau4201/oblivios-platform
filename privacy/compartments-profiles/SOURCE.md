# Source record: Compartments profiles (ADR-208)

Design: `docs/superpowers/specs/2026-10-30-compartments-profiles-design.md` in `freeholdos-foundation`; plan `docs/superpowers/plans/2026-10-30-compartments-profiles.md`; source facts `docs/research/compartments-profiles-source-facts.md`.

Every patch was authored in the disposable repository `.worktrees/compartments-profiles/<project>/` from a read-only copy of the touched files, exported with `git format-patch`, checked with `git apply --check` and landed on the build server with `git am`.

## Base

| Project | Branch and base revision | Touched files read from the tree |
| --- | --- | --- |
| `frameworks/native` | `freeholdos-2026100600`, `994e9e02e9` (after `privacy/identity-surface` `frameworks-native/0001` and `0002`) | `services/sensorservice/SensorEventConnection.cpp` (sha256 `3f1c9572...`), `services/sensorservice/SensorDirectConnection.cpp` (sha256 `5805ae44...`) |
| `system/sepolicy` | `freeholdos-2026100600`, `143d4d7c6` | `private/service.te`, `private/service_contexts`, `private/file_contexts`, `private/traceur_app.te`, `private/shell.te`, `build/soong/service_fuzzer_bindings.go` |
| `frameworks/base` | `freeholdos-2026100600`, `a0741cb37cff` | `core/res/AndroidManifest.xml`, `core/res/res/values/strings.xml`, `services/core/java/com/android/server/EventLogTags.logtags`, `services/java/com/android/server/SystemServer.java` |

## Patches

| Patch | Landed as | Subject | Why it diverges from upstream |
| --- | --- | --- | --- |
| `frameworks-base/0001` | `f3ec5199a710` | compartments: add the Binder interface and the two permissions | New AIDL files in `com.android.internal.compartments` and two `signature\|privileged` permissions, `@hide` as the Data Map permission (`datamap/patches/frameworks-base/0008`). Nothing upstream is changed beyond two inserted blocks. |
| `frameworks-base/0002` | `ac361f077d31` | compartments: add the policy service of Compartments (ADR-208) | A new system service `compartments`, event tag 51710 and its start in `SystemServer`. The pure core (`Profiles`, `PolicyStore`, `MirrorPlan`) is a copy of `system-server/src/` of this package; the host tests run on that copy. |
| `frameworks-native/0003` | `8ce5dad6e5` | sensorservice: give each app the sensor mode of its profile | The sensor service applies, per app uid, the folded events of ADR-167, the raw events, invented events or no motion events, from a file of the system server, and refuses a direct channel for the sensors that the mode changes. The three headers and `SensorModeProduction.h` are new files; the two `.cpp` files change in the block of `sendEvents` at `foldBias` and in `configureChannel`. |
| `system-sepolicy/0001` | not landed | sepolicy: add the Compartments policy service and its data file type (ADR-208) | New service type, `compartments_data_file` (created by `system_server` through a name-based `type_transition`), `exit_app` and `system_app` may find the service, `neverallow` for every other app domain and for writers other than `system_server`; `-compartments_service` added to the broad service lists of `traceur_app` and `shell` as for the Data Map service. The Settings app runs in `system_app`, not in a domain of its own. Landing waits for the module build of `selinux_policy`: a `neverallow` conflict cannot be seen without it. |

Conflict surface on a new upstream release: the `foldBias` block of `SensorEventConnection::sendEvents` and the direct-channel condition of `SensorDirectConnection::configureChannel` (both already carry the patches of ADR-167), the `DataMapService` block of `SystemServer.startOtherServices` (the new block follows it), the permission block after `READ_DATA_MAP` in `core/res/AndroidManifest.xml`, the last lines of `EventLogTags.logtags`.

## Checks

- The Java core: `system-server/test/run.sh` (150 and 70 checks) and `mutate.py` (53 of 53 mutants killed), 2026-10-30.
- The sensor headers against the real `hardware/sensors.h` of the tree: `sensor/test/run.sh` ok and `mutate.py` 39 of 39 killed, 2026-10-30.
- `aidl --lang=java` of the tree's host tool on both interface files: exit 0, `ICompartmentsPolicy.java` generated.
- Type check of the service on the build server with `javac` (jdk21 of the tree) against `framework-minus-apex/combined/framework.jar` and `services.core.unboosted.jar`: no error; a control with an unknown method of `Slog` gives one `cannot find symbol` error. `EventLogTags` was a stand-in with the one new method, because the jar holds the generated class of the previous tags. `SystemServer.java` was not compiled (two inserted lines).
- `frameworks-native/0003`: `clang++ -fsyntax-only` with the tree's own compiler (`clang-r584948b`) and the include, define and standard flags of the `libsensorservice` object rule in `out-lynx/soong/build.lynx.7.ninja`, on the two changed files. The original files and the patched files give the same six diagnostics (three headers that the tree builds with warning flags that the extraction left out); the patched files add none, and a control with an unknown function adds one `undeclared identifier` error. Warnings from the changed files and the four headers: none.
- Not built: no module build has run with these patches.
