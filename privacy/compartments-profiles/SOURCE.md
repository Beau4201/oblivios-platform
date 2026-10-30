# Source record: Compartments profiles (ADR-208)

Design: `docs/superpowers/specs/2026-10-30-compartments-profiles-design.md` in `freeholdos-foundation`; plan `docs/superpowers/plans/2026-10-30-compartments-profiles.md`; source facts `docs/research/compartments-profiles-source-facts.md`.

Every patch was authored in the disposable repository `.worktrees/compartments-profiles/<project>/` from a read-only copy of the touched files, exported with `git format-patch`, checked with `git apply --check` and landed on the build server with `git am`.

## Base

| Project | Branch and base revision | Touched files read from the tree |
| --- | --- | --- |
| `frameworks/base` | `freeholdos-2026100600`, `a0741cb37cff` | `core/res/AndroidManifest.xml`, `core/res/res/values/strings.xml`, `services/core/java/com/android/server/EventLogTags.logtags`, `services/java/com/android/server/SystemServer.java` |

## Patches

| Patch | Landed as | Subject | Why it diverges from upstream |
| --- | --- | --- | --- |
| `frameworks-base/0001` | `f3ec5199a710` | compartments: add the Binder interface and the two permissions | New AIDL files in `com.android.internal.compartments` and two `signature\|privileged` permissions, `@hide` as the Data Map permission (`datamap/patches/frameworks-base/0008`). Nothing upstream is changed beyond two inserted blocks. |
| `frameworks-base/0002` | `ac361f077d31` | compartments: add the policy service of Compartments (ADR-208) | A new system service `compartments`, event tag 51710 and its start in `SystemServer`. The pure core (`Profiles`, `PolicyStore`, `MirrorPlan`) is a copy of `system-server/src/` of this package; the host tests run on that copy. |

Conflict surface on a new upstream release: the `DataMapService` block of `SystemServer.startOtherServices` (the new block follows it), the permission block after `READ_DATA_MAP` in `core/res/AndroidManifest.xml`, the last lines of `EventLogTags.logtags`.

## Checks

- The Java core: `system-server/test/run.sh` (150 and 70 checks) and `mutate.py` (53 of 53 mutants killed), 2026-10-30.
- The sensor headers against the real `hardware/sensors.h` of the tree: `sensor/test/run.sh` ok and `mutate.py` 39 of 39 killed, 2026-10-30.
- `aidl --lang=java` of the tree's host tool on both interface files: exit 0, `ICompartmentsPolicy.java` generated.
- Type check of the service on the build server with `javac` (jdk21 of the tree) against `framework-minus-apex/combined/framework.jar` and `services.core.unboosted.jar`: no error; a control with an unknown method of `Slog` gives one `cannot find symbol` error. `EventLogTags` was a stand-in with the one new method, because the jar holds the generated class of the previous tags. `SystemServer.java` was not compiled (two inserted lines).
- Not built: no module build has run with these patches.
