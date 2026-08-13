# Guardian app tests

## `test_revoke_background_location.sh`

On-device end-to-end check of the real detect -> protect -> verify -> undo
pipeline. Run against a booted emulator or the physical `lynx` unit.

## `kotlin/GuardianScreenModelsTest.kt`

Unit tests for `src/org/aegisos/guardian/ui/GuardianScreenModels.kt`, the
mapping layer between guardian-core's records and the reference screens'
UI-state types.

**There is no in-tree runner.** `guardian/app` is an `android_app` Soong
module with no test target, and its UI depends on Jetpack Compose, so these
cannot run under `java_test_host` either. They are written for, and were run
against, a throwaway JVM harness built on `aegisos-services`' own Compose
Desktop Gradle setup:

1. Build `guardian-core.jar` from the `aegisos-services` branch this app's
   sources actually target (currently `guardian-residuals-hardening` — see
   the stale-jar note in `guardian/SOURCE.md`):
   `./gradlew :guardian-core:jar`
2. Create a Kotlin/JVM Gradle project with the same plugin versions as
   `aegisos-services`' root `build.gradle.kts`, depending on that jar plus
   `compose.desktop.currentOs`, `compose.material3`, `kotlin("test")` and
   JUnit 5.
3. Copy in `src/org/aegisos/guardianui/**`, `GuardianScreenModels.kt`, and
   `guardian/core/GuardianUi{Controller,State}.kt`. Two files cannot compile
   off Android and need stand-ins for the harness only:
   - `guardianui/components/GuardianMark.kt` — use the reference module's
     Compose Desktop version instead of the `painterResource` one.
   - `ui/GuardianTapjackingGuard.kt` — needs `android.view.MotionEvent`;
     stub `Modifier.filterTouchesWhenObscured()` as a no-op if you also want
     to type-check `GuardianApp.kt`.
4. `./gradlew test`.

Copying `aegisos-services`' own
`guardian-ui-reference/src/test/kotlin/org/aegisos/guardianui/**` tests into
the same harness is worth doing at the same time: they exercise the ported
screens directly and will catch a port deviation that changed their
behaviour.
