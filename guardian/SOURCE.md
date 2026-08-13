# Guardian — Source Provenance

## guardian-core.jar

- Source: `aegisos-services` commit `c9ac0dfc28454824e8c049d9d5222ee79fce28b5`
  (`master`, after merging `guardian-residuals-hardening` — merge commit
  message: "merge: guardian-core persistent stores, coarse-location
  detection, audit hardening"). Supersedes the 2026-08-05 vendoring below.
- Build command: `./gradlew :guardian-core:jar` (Java 21 toolchain, zero
  runtime dependencies — confirmed via `guardian-core/build.gradle.kts`).
  Reproduced via `:guardian-core:clean :guardian-core:jar --offline` to rule
  out a stale Gradle cache; hash matched both times.
- SHA-256: `a96a00e21ac4a13d5750f3dd00d4fcf86efd1c99c18e0f2da3d443a8f28eac58`.
- Built: 2026-08-13.
- Verified present via `unzip -l`/`javap` before use: `FileGuardianAuditStore`,
  `FileGuardianOverviewStore`, `ActiveProtection#auditId()`,
  `FileGuardianAuditStore#retentionPeriod()` — the four APIs the "Known
  staleness" section below previously reported missing.

<details>
<summary>Previous vendoring (2026-08-05, superseded)</summary>

- Source: `aegisos-services` commit `3d66a461b7aa9b61f2e6f2db9e4f96cbf380cf43`.
- SHA-256: `6a9517f3ebe783ce0190ef3e1ea56e58c7328b86b040fb5ca9d044707c0e63f6`.

</details>

## GuardianUiController.kt / GuardianUiState.kt

- Copied verbatim from the 2026-08-05 commit above (`3d66a461b`),
  `guardian-ui-reference/src/main/kotlin/org/aegisos/guardian/ui/`.
- Reused as-is: both files are plain Kotlin with no Compose Desktop or
  desktop-windowing dependency.
- Re-diffed against the new 2026-08-13 source commit (`c9ac0dfc2`) before
  this re-vendor: `GuardianUiState.kt` is unchanged; `GuardianUiController.kt`
  gained one upstream method, `fun undo(explicitAuditId: String)` — already
  present, byte-identical, in this app's copy (added independently here in
  an earlier session, `35e8ef9`). No re-copy needed; the two files below
  remain current modulo the two already-documented deviations.

### Deviation: GuardianUiController.kt secondary constructor removed

- The source file included a secondary `constructor(scenario: GuardianReferenceScenario)`
  and its import (`org.aegisos.guardian.reference.GuardianReferenceScenario`).
- `GuardianReferenceScenario` is CLI-only reference scaffolding in the
  `guardian-reference` module of `aegisos-services`, not part of `guardian-core`,
  and was not vendored into this repository. The import cannot resolve,
  causing a hard Kotlin compile error.
- The secondary constructor is demo-only code (hardcoding fake package names for
  test scenarios) with no role in real privileged Android integration.
- **Fix:** the secondary constructor and its import were removed. All other
  content in both files — the primary constructor, all four methods (`start`,
  `protect`, `undo`, `overview`), and GuardianUiState.kt in its entirety —
  remains an unmodified copy of the source.

### Deviation: GuardianUiController.kt's GuardianUiScenario made public (Task 6)

- The source file declared `internal interface GuardianUiScenario` (line 7).
- Task 6 wires `GuardianActivity` (package `org.aegisos.guardian`, not
  `org.aegisos.guardian.ui`) to implement `GuardianUiScenario` with an
  anonymous object.
- **Fix:** removed the `internal` modifier only — the interface is now
  `interface GuardianUiScenario`. No other content in the file changed.
- Note: under this repository's actual Android.bp layout, `GuardianUiController.kt`
  and `GuardianUiState.kt` are compiled as `srcs` of the same `GuardianApp`
  `android_app` Soong module as everything under `app/src/**/*.kt` (see
  `app/Android.bp`), so they already form a single Kotlin compilation unit —
  `internal` visibility would likely have been satisfied across packages within
  that one module regardless. The change is made anyway per the task's explicit
  instruction: it is a harmless widening (public is a superset of internal) and
  keeps the interface usable if `core/` is ever split into a separately-compiled
  module later.

Re-vendor by repeating Task 1 of
`docs/superpowers/plans/2026-08-05-guardian-platform-integration.md` against
a newer `aegisos-services` commit; do not hand-edit any file in this
directory.

### Resolved staleness (was: guardian-core.jar predates this app's sources)

Historical record — this section described a real, now-fixed blocker; kept
for context on why the 2026-08-13 re-vendor happened.

Four APIs this app's sources called did not exist in the 2026-08-05 vendored
jar. All four were added on `aegisos-services`' `guardian-residuals-hardening`
branch, which was unmerged at the time. That branch's own tests were
re-verified green (`./gradlew :guardian-core:test --offline`, `BUILD
SUCCESSFUL`) before merging it to `master` (merge commit, no conflicts — the
two branches touched disjoint files), then the jar above was re-vendored from
the merged `master`. All four APIs are now confirmed present in the current
jar (see the SHA-256 entry above):

| API | Used by | Was |
| --- | --- | --- |
| `audit.FileGuardianAuditStore` (jar previously had only `InMemoryGuardianAuditStore`) | `app/src/.../GuardianComponents.java` | predated the UI rework |
| `overview.FileGuardianOverviewStore` | `app/src/.../GuardianComponents.java` | predated the UI rework |
| `overview.GuardianOverviewEntry.ActiveProtection#auditId()` | `app/src/.../ui/GuardianScreenModels.kt` | introduced by the UI rework |
| `audit.FileGuardianAuditStore#retentionPeriod()` | `app/src/.../GuardianComponents.java#retentionPeriod()` | introduced by the UI rework |

## Reference UI screens (app/src/org/aegisos/guardianui/)

- Source: `aegisos-services` `master` at commit `08455a0`,
  `guardian-ui-reference/src/main/kotlin/org/aegisos/guardianui/`.
- Copied files: `theme/GuardianColor.kt`, `theme/GuardianType.kt`,
  `theme/GuardianTheme.kt`, `icons/GuardianIcons.kt`,
  `components/GuardianMark.kt`, `components/GuardianUiComponents.kt`, and the
  five `screens/Guardian{FirstRun,Home,Trust,Choices,IncidentReview}Screen.kt`.
- `GuardianVisualIdentityDemo.kt` is deliberately **not** copied: it is desktop
  preview wiring, not a navigation shell. The real shell is
  `app/src/org/aegisos/guardian/ui/GuardianApp.kt`.
- The package and directory layout are kept identical to the reference module
  so a future re-vendor is a diff rather than a rewrite. AegisOS-specific glue
  (navigation, adapters, the tapjacking guard) lives in
  `org.aegisos.guardian.ui` instead, alongside the vendored UI controller.
- `app/res/drawable-nodpi/guardian_mark.png` is a byte-identical copy of the
  reference module's `src/main/resources/guardian_mark.png`.

These files supersede the earlier `org.aegisos.guardian.ui` screens (green,
Material 2, light theme), which were removed. See
`docs/superpowers/specs/2026-08-13-guardian-visual-identity-design.md` in
`aegisos-foundation`.

### Android port deviations

Every deviation is defaulted so the reference module's own call sites and its
Compose UI tests still pass unchanged against these copies — verified: all 40
reference screen/component/theme tests pass against the ported files.

1. `components/GuardianMark.kt` — rewritten. The reference decodes the PNG
   with `androidx.compose.ui.res.useResource` and `org.jetbrains.skia.Image`,
   both Compose Desktop-only. Android loads the same bytes from the
   `guardian_mark` drawable via `painterResource`.
2. `components/GuardianUiComponents.kt` — `GuardianSwitch` gained
   `enabled: Boolean = true`.
3. `screens/GuardianChoicesScreen.kt` — `GuardianWatchToggle.changeable` and
   `GuardianChoicesUiState.notifyImmediatelyChangeable`, both defaulting to
   true. The real app sets them false: every switch on that screen reports a
   fixed property of this build, and a live-looking switch would claim control
   that does not exist.
4. `screens/GuardianTrustScreen.kt` — `GuardianTrustUiState.retentionChangeable`
   and `retentionEnforced`, both defaulting to true. The real app sets both
   false. `retentionChangeable = false` renders the recorded period as a single
   static pill instead of four options, because shortening retention prunes the
   audit entry an active protection's Undo depends on — a real interaction that
   needs a design decision before it is exposed. `retentionEnforced = false`
   relabels the section and adds an explicit note that the period is a recorded
   setting Guardian does not act on, because nothing in this build ever calls
   `applyRetention()`; without it, the pill would imply a deletion schedule the
   device does not run, on the one screen that promises every claim points to a
   real record.
5. `screens/GuardianHomeScreen.kt` — `GuardianRecentChange.key`, an opaque
   nullable row identity so a tap can be resolved back to a domain action.
6. `screens/GuardianIncidentReviewScreen.kt` — `GuardianIncidentTone` plus
   `GuardianIncidentUiState.tone`, and `primaryActionLabel` /
   `secondaryActionLabel` parameters. The real app resolves the whole
   Observe -> Explain -> Choose -> Enforce -> Verify -> Undo loop on this one
   screen (as the design spec asks), so its chip and its two actions have to
   express the applied, undone and failed outcomes as well as the initial one.
7. `screens/GuardianIncidentReviewScreen.kt` — `secondaryActionNote`,
   defaulting to null: a caveat rendered under the secondary action, for the
   case where accepting that action promises the owner less than its label
   implies. The real app sets it on one path only. "Decide later" defers a
   detection into `GuardianApplication.pendingDetections`, which is an
   in-memory `ConcurrentLinkedDeque` that nothing persists — the deferral does
   not survive a process restart, and nothing re-raises it afterwards
   (`BackgroundLocationWatcher` fires only on a *new* AppOps event). The label
   and its note are wrapped in a `Column` so they read as one unit; with no
   note — the reference module's only case — the rendered layout is unchanged.
   That wrapper also carries the `clickable` and the `Role.Button`, which the
   label `Text` held in the reference, so the label and the note merge into a
   single semantics node and a screen reader announces the caveat together with
   the button it qualifies instead of as a separate element reached after it.
   The clickable has to move for that to work: Compose skips a merging
   descendant when it merges (`SemanticsNode.mergeConfig`) and `clickable` is
   one, so marking the wrapper `mergeDescendants` while the label stayed
   clickable would have merged the note *without* the label. With no note the
   merged node's bounds, touch target and announcement are unchanged —
   verified by the reference module's own "Not now" click and 48dp
   touch-target tests, which still pass unmodified.

## GuardianApp (app module)

The `app/` module (Android manifest, resources, permission allowlist, build
configuration, and later source code in `app/src/`) is **first-party AegisOS
code**, not vendored. It is built and deployed as part of the AegisOS platform.

Permission grants are justified in `docs/research/guardian-platform-api-verification.md`:

- `android.permission.WATCH_APPOPS` — marked `signature|privileged` in AOSP.
  Granted by `privapp-permissions-org.aegisos.guardian.xml` allowlist entry
  via `frameworks/base` auto-grant at installation.
- `android.permission.GET_APP_OPS_STATS` — marked `signature|privileged|development` in AOSP.
  Granted by `privapp-permissions-org.aegisos.guardian.xml` allowlist entry
  via `frameworks/base` auto-grant at installation.
- `android.permission.REVOKE_RUNTIME_PERMISSIONS` — marked `signature|installer|verifier` in AOSP.
  Guardian's grant path is platform-signing the APK (signature match); the allowlist
  entry is deliberately omitted (would be a no-op).
- `android.permission.GRANT_RUNTIME_PERMISSIONS` — marked `signature|installer|verifier` in AOSP
  (`frameworks/base/core/res/AndroidManifest.xml:7154-7155`), the same protection level as
  `REVOKE_RUNTIME_PERMISSIONS` above. Guardian's grant path is likewise platform-signing the
  APK (signature match); the allowlist entry is deliberately omitted (would be a no-op).
