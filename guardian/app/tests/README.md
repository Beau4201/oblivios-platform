# Guardian app tests

## `test_revoke_background_location.sh`

On-device end-to-end check of the real detect -> protect -> verify -> undo
pipeline. Run against a booted emulator or the physical `lynx` unit.

## `kotlin/GuardianScreenModelsTest.kt`

Unit tests for `src/org/aegisos/guardian/ui/GuardianScreenModels.kt`, the
mapping layer between guardian-core's records and the reference screens'
UI-state types.

## `kotlin/GuardianIncidentActionsTest.kt`

Unit tests for `incidentActions()` in `src/org/aegisos/guardian/ui/GuardianApp.kt`.
Two of its properties are security-relevant: a deferred detection must not be
dropped from `GuardianApplication`'s queue, and every secondary action that
reverses an applied change must be a `GuardianSecondaryAction.Undo`, because
that type is what the `filterTouchesWhenObscured()` tapjacking guard keys on
(ADR-034 residual 5). Needs the tapjacking stub from step 3 below, since it
pulls in `GuardianApp.kt`.

## `kotlin/GuardianPendingQueueTest.kt`

Tests for `resolvesPendingDetection()` — the single rule deciding whether an
open detection leaves `GuardianApplication`'s pending queue — plus end-to-end
tests that drive the real `GuardianApp` shell through each exit path, with a
real `GuardianUiController` over a fake `GuardianUiScenario`. Both directions
of that rule have been real defects here: an undecided detection destroyed
because of which button was tapped, and a resolved one left queued because the
owner used the back gesture. Needs the `BackHandler` stand-in from step 3.

## `kotlin/GuardianTrustRetentionTest.kt`

Compose UI tests for the Trust screen's retention section: the recorded period
must not be presented as an enforced deletion schedule, since nothing in this
build calls `applyRetention()`. Separate from the reference module's own
`GuardianTrustScreenTest`, which stays an unmodified copy.

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
   `guardian/core/GuardianUi{Controller,State}.kt`. Three things cannot
   compile off Android and need stand-ins for the harness only:
   - `guardianui/components/GuardianMark.kt` — use the reference module's
     Compose Desktop version instead of the `painterResource` one.
   - `ui/GuardianTapjackingGuard.kt` — needs `android.view.MotionEvent`;
     stub `Modifier.filterTouchesWhenObscured()` as a no-op if you also want
     to type-check `GuardianApp.kt`.
   - `androidx.activity.compose.BackHandler` — an Android artifact with no
     Compose Desktop equivalent. Declare a stand-in in that package. Make it
     record the registered handler rather than ignore it (e.g. a
     `HarnessBackDispatcher` object with `reset()`/`press()`), so
     `GuardianPendingQueueTest` can fire a back gesture. That verifies
     `GuardianApp`'s own side of the wiring only — whether Android's real
     dispatcher delivers a gesture to a registered handler is not verified
     here.
4. `./gradlew test`.

The composable tests are JUnit 4 (`createComposeRule`, run through the vintage
engine); the plain ones are JUnit 5. Both engines are on the test runtime
classpath, and a single file can hold classes of each kind.

Copying `aegisos-services`' own
`guardian-ui-reference/src/test/kotlin/org/aegisos/guardianui/**` tests into
the same harness is worth doing at the same time: they exercise the ported
screens directly and will catch a port deviation that changed their
behaviour.
