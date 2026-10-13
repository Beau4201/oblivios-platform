# Guardian — Source Provenance

## guardian-core.jar

- Source: `sovereignos-services` commit `c9ac0dfc28454824e8c049d9d5222ee79fce28b5`
  (`master`, after merging `guardian-residuals-hardening` — merge commit
  message: "merge: guardian-core persistent stores, coarse-location
  detection, audit hardening"). Supersedes the 2026-08-05 vendoring below.
- Build command: `./gradlew :guardian-core:jar` (Java 21 toolchain, zero
  runtime dependencies — confirmed via `guardian-core/build.gradle.kts`).
  Reproduced via `:guardian-core:clean :guardian-core:jar --offline` to rule
  out a stale Gradle cache; hash matched both times.
- SHA-256 (current, after the `org.aegisos` -> `org.sovereignos` package
  rename, re-vendored 2026-09-25 in `c3e1684`):
  `f35ba45f394ee96beb3b7579a6c1b9e30684c93030996a0b3c834b7f5fc2ffac`.
  Independently reproduced 2026-09-28 with `./gradlew :guardian-core:clean
  :guardian-core:jar --offline` at `sovereignos-services` `92d29b7`: same
  hash. Same 84 entries as the pre-rename jar, only the package prefix
  differs.
- SHA-256 of the pre-rename 2026-08-13 build (superseded):
  `a96a00e21ac4a13d5750f3dd00d4fcf86efd1c99c18e0f2da3d443a8f28eac58`.
- Built: 2026-08-13; rebuilt after the rename 2026-09-25.
- Verified present via `unzip -l`/`javap` before use: `FileGuardianAuditStore`,
  `FileGuardianOverviewStore`, `ActiveProtection#auditId()`,
  `FileGuardianAuditStore#retentionPeriod()` — the four APIs the "Known
  staleness" section below previously reported missing.

<details>
<summary>Previous vendoring (2026-08-05, superseded)</summary>

- Source: `sovereignos-services` commit `3d66a461b7aa9b61f2e6f2db9e4f96cbf380cf43`.
- SHA-256: `6a9517f3ebe783ce0190ef3e1ea56e58c7328b86b040fb5ca9d044707c0e63f6`.

</details>

## GuardianUiController.kt / GuardianUiState.kt

- Copied verbatim from the 2026-08-05 commit above (`3d66a461b`),
  `guardian-ui-reference/src/main/kotlin/org/sovereignos/guardian/ui/`.
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
  and its import (`org.sovereignos.guardian.reference.GuardianReferenceScenario`).
- `GuardianReferenceScenario` is CLI-only reference scaffolding in the
  `guardian-reference` module of `sovereignos-services`, not part of `guardian-core`,
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
- Task 6 wires `GuardianActivity` (package `org.sovereignos.guardian`, not
  `org.sovereignos.guardian.ui`) to implement `GuardianUiScenario` with an
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
the internal record "guardian platform integration" (not published) against
a newer `sovereignos-services` commit; do not hand-edit any file in this
directory.

### Resolved staleness (was: guardian-core.jar predates this app's sources)

Historical record — this section described a real, now-fixed blocker; kept
for context on why the 2026-08-13 re-vendor happened.

Four APIs this app's sources called did not exist in the 2026-08-05 vendored
jar. All four were added on `sovereignos-services`' `guardian-residuals-hardening`
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

## Reference UI screens (app/src/org/sovereignos/guardianui/)

- Source: `sovereignos-services` `master` at commit `7171dbd47e93`
  (2026-08-14 re-vendor, superseding the earlier `bf13c97`; carries three
  upstream commits: the sliding-indicator nav bar (dropping Home's settings
  gear), a dash-free copy pass plus a card-width fix, and moving the
  "Keep history for" retention control from Trust to Choices while adding a
  soft radial screen tint to every screen — see those commits' messages for
  the itemized lists),
  `guardian-ui-reference/src/main/kotlin/org/sovereignos/guardianui/`.
- Copied files: `theme/GuardianColor.kt`, `theme/GuardianType.kt`,
  `theme/GuardianTheme.kt`, `icons/GuardianIcons.kt`,
  `components/GuardianMark.kt`, `components/GuardianUiComponents.kt`, and the
  five `screens/Guardian{FirstRun,Home,Trust,Choices,IncidentReview}Screen.kt`.
- `GuardianVisualIdentityDemo.kt` is deliberately **not** copied: it is desktop
  preview wiring, not a navigation shell. The real shell is
  `app/src/org/sovereignos/guardian/ui/GuardianApp.kt`.
- The package and directory layout are kept identical to the reference module
  so a future re-vendor is a diff rather than a rewrite. SovereignOS-specific glue
  (navigation, adapters, the tapjacking guard) lives in
  `org.sovereignos.guardian.ui` instead, alongside the vendored UI controller.
- `app/res/drawable-nodpi/guardian_mark.png` is a byte-identical copy of the
  reference module's `src/main/resources/guardian_mark.png`.

These files supersede the earlier `org.sovereignos.guardian.ui` screens (green,
Material 2, light theme), which were removed. See
the internal record "guardian visual identity design" (not published).

### Android port deviations

Every deviation is defaulted so the reference module's own call sites and its
Compose UI tests still pass unchanged against these copies. Confirmed by
inspection for this re-vendor (all 59 of `sovereignos-services`' own
`guardian-ui-reference` tests passed against the un-vendored source before
copying, and every default value and call-site signature was diffed by hand
against the previous vendored copy) — **not** re-run against these platform
copies through the throwaway JVM harness (`guardian/app/tests/README.md`)
this round; that step is still outstanding before this can be called
independently verified rather than hand-checked. The real compile-level check
happens at the next Soong build.

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
   that does not exist. This screen also gained
   `GuardianChoicesUiState.retentionChangeable` and `retentionEnforced`, both
   defaulting to true — moved here (2026-08-14) from `GuardianTrustScreen.kt`
   when the upstream reference module moved the "Keep history for" control
   itself from Trust to Choices; the deviation's own behaviour is unchanged.
   The real app sets both false. `retentionChangeable = false` renders the
   recorded period as a single static pill instead of four options, because
   shortening retention prunes the audit entry an active protection's Undo
   depends on — a real interaction that needs a design decision before it is
   exposed. `retentionEnforced = false` relabels the section and adds an
   explicit note that the period is a recorded setting Guardian does not act
   on, because nothing in this build ever calls `applyRetention()`; without
   it, the pill would imply a deletion schedule the device does not run, on
   the one screen that (since the move) carries this build's own claims about
   what is and is not a real, acted-on record.
4. `screens/GuardianHomeScreen.kt` — `GuardianRecentChange.key`, an opaque
   nullable row identity so a tap can be resolved back to a domain action.
5. `screens/GuardianIncidentReviewScreen.kt` — `GuardianIncidentTone` plus
   `GuardianIncidentUiState.tone`, and `primaryActionLabel` /
   `secondaryActionLabel` parameters. The real app resolves the whole
   Observe -> Explain -> Choose -> Enforce -> Verify -> Undo loop on this one
   screen (as the design spec asks), so its chip and its two actions have to
   express the applied, undone and failed outcomes as well as the initial one.
6. `screens/GuardianIncidentReviewScreen.kt` — `secondaryActionNote`,
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
7. `screens/GuardianIncidentReviewScreen.kt` — `FactRow`'s layout, fixed.
   Confirmed live on the emulator (2026-08-14): a real `Detected` incident's
   "Observed" row rendered with the key wrapped one character per line and
   the primary/secondary buttons pushed off-screen, unusable. Root cause:
   the value `Text` (the one that can be long) had no `weight`, so Row's
   unweighted-first measurement pass let it claim nearly the row's full
   width for a short sentence, leaving the weighted key `Text` a few pixels.
   Fixed by moving the weight from the key to the value and giving them a
   fixed `Spacer` instead of sharing weight, so the key keeps its natural
   width and the value wraps within what's left. This defect existed,
   byte-identical, in the upstream reference module's copy of `FactRow` too;
   it has since been fixed there as well (present as of `7171dbd`), so this
   is no longer a live divergence — kept here as a record of the fix's
   origin, not an active deviation.

## GuardianApp (app module)

The `app/` module (Android manifest, resources, permission allowlist, build
configuration, and later source code in `app/src/`) is **first-party SovereignOS
code**, not vendored. It is built and deployed as part of the SovereignOS platform.

Permission grants are justified in the internal record "guardian platform api verification" (not published):

- `android.permission.WATCH_APPOPS` — marked `signature|privileged` in AOSP.
  Granted by `privapp-permissions-org.sovereignos.guardian.xml` allowlist entry
  via `frameworks/base` auto-grant at installation.
- `android.permission.GET_APP_OPS_STATS` — marked `signature|privileged|development` in AOSP.
  Granted by `privapp-permissions-org.sovereignos.guardian.xml` allowlist entry
  via `frameworks/base` auto-grant at installation.
- `android.permission.REVOKE_RUNTIME_PERMISSIONS` — marked `signature|installer|verifier` in AOSP.
  Guardian's grant path is platform-signing the APK (signature match); the allowlist
  entry is deliberately omitted (would be a no-op).
- `android.permission.GRANT_RUNTIME_PERMISSIONS` — marked `signature|installer|verifier` in AOSP
  (`frameworks/base/core/res/AndroidManifest.xml:7154-7155`), the same protection level as
  `REVOKE_RUNTIME_PERMISSIONS` above. Guardian's grant path is likewise platform-signing the
  APK (signature match); the allowlist entry is deliberately omitted (would be a no-op).

### Fix: `BackgroundLocationWatcher` AppOps callbacks were not user-scoped (ADR-070)

Found incidentally during the exhaustive Duress Unlock
decoy identity-leak audit (the internal record "duress decoy remaining leaks audit" (not published),
item 13), but the defect itself is general to any multi-user SovereignOS
device, not decoy-specific: `AppOpsManager#startWatchingNoted`/
`startWatchingActive` are registered system-wide by this class, with no
uid/user filter passed to either call. `onAccess(op, uid, packageName)`
checked only `isBackground(uid)`, never whether `uid` belonged to this
process's own Android user. Confirmed this is a real, not theoretical,
cross-user leak: this Guardian process's own overview data model
(`GuardianOverviewEntry`/`GuardianActiveProtectionV1` in
`sovereignos-services`' `guardian-core`) carries no `userId` field
whatsoever, so any event ingested here has no way to be filtered back
out downstream.

- `9084da3...` — "guardian: scope BackgroundLocationWatcher's AppOps
  callbacks to this user" (real `vendor/aegisos/guardian` build-server
  SHA, `master` branch — this package is **first-party SovereignOS code**,
  not a patch against upstream, so there is no separate `patches/`
  entry for it; the fix lives directly in `app/src/`, kept in sync with
  the real build-server file). Adds a check comparing
  `UserHandle.getUserHandleForUid(uid)` against
  `Process.myUserHandle()` before doing anything else with the event,
  discarding it if they differ. Both APIs are genuine public SDK APIs
  (confirmed via source, neither `@hide`); `Process.myUserHandle()`
  matches the existing pattern already used by
  `GuardianComponents.java` to construct `SovereignOSPlatformGateway`.
  Authored under `CONTRIBUTING.md`'s local-first discipline in a disposable
  local git repo
  (`sovereignos-platform/.worktrees/guardian-appops-user-scope-fix/`, a
  fetch-only worktree seeded read-only from the real branch HEAD
  `91cea5c4aed476a83c24adc390106cbc878b77ae` — only the one touched
  file, its `sha256sum` confirmed byte-identical to the live remote file
  before editing) and landed on the real `master` branch with `git am`
  (applied cleanly, no conflicts, confirmed no build was already in
  flight beforehand). The patch file was regenerated fresh via `git
  format-patch` directly from the real post-`git am` branch history, not
  exported from the disposable worktree's own commit. This vendored
  `app/src/` copy was then synced to match the real post-`git am` file
  byte-for-byte.
- Base commit: `91cea5c4aed476a83c24adc390106cbc878b77ae` (the real
  `master` branch tip at fetch time).

**Build verification:** a fast `m GuardianApp` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side, confirmed no build was already in
flight beforehand: `ninja: 1m55.27s Build Succeeded: 21 steps`, `####
build completed successfully (02:01 (mm:ss)) ####`. No diagnostics
attached to the edited file. **This is compile-time verification
only** — it does not confirm the fix actually closes the cross-user
leak at runtime (would need two real users, one triggering a
background-location access, confirming it does not appear in the
other's Guardian Overview). Not yet done as of this entry.

### Sync: live tree renamed and moved to `vendor/sovereignos/guardian` (2026-09-28)

The package was renamed to `org.sovereignos` on 2026-09-23 (`e2829a1`), but
the live build-server tree at `vendor/aegisos/guardian` never was. adevtool's
`PRODUCT_PACKAGES` entry names `privapp-permissions-org.sovereignos.guardian.xml`,
while the live module was still `privapp-permissions-org.aegisos.guardian.xml`,
so the `lynx` build of 2026-09-26 shipped `GuardianApp` with no privapp
allowlist and the build did not fail. Found during `stallion` bring-up
(ADR-111 and
the internal record "stallion daily use personal stable" (not published)).

- Before syncing, the live tree (renamed mechanically in a scratch copy) was
  diffed against this package file by file. Drift ran both ways: this
  package was newer for the retention tests (moved from Trust to Choices)
  and `guardian-core.jar` (rebuilt after the rename). The live tree was newer
  for `testfixtures/locationfixture` (the coarse-location `FUSED_PROVIDER`
  request from the residuals-hardening Task 10, ADR-034/036), which this
  package had never received. Every other difference was a sed artifact.
- Result, landed with `git am` on the live `master`:
  `1ed9ff6197bf60ca4ae6d8c4f930d61524ca8c6d` ("guardian: rename
  org.aegisos to org.sovereignos, sync with the platform package"). The
  directory was then moved to `vendor/sovereignos/guardian`; nothing else in
  the tree referenced the old path. Authored in a disposable clone
  (`.worktrees/guardian-rename/`) of the live repo at `9084da3`.
- This package received the newer location fixture in the same change, so
  the live tree and this directory match file for file, `patches/` aside.
- Not yet build- or hardware-verified. The next `stallion`/`lynx` build must
  pass `tools/check-required-packages.sh` (in `sovereignos-docs`),
  which checks the privapp allowlist is actually installed.

## AOSP source patches (`patches/`)

Applied to individual AOSP component checkouts, not vendored app code —
see each tree's own row for the exact base commit and patch count.
Generated via `git format-patch`, author identity rewritten to a generic
`SovereignOS Build <build@sovereignos.local>` identity before committing here (same
convention as `credential/duress-unlock-dummy/SOURCE.md`). Real, dated
commits on the build server's `platform-primary` checkout.

| Tree | Base commit | Patches | Approving ADR |
| --- | --- | --- | --- |
| `vendor/adevtool` | `4c9a78e2e7428818291a8c1e620f1e07e33ef185` | `patches/vendor-adevtool/0001` | Squash of two landed commits. First: ADR-035 (names this branch/commit literally: `aegisos-guardian-lynx`, `43b73335`; carried onto the `2026091900` rebase branch `sovereignos-2026091900` as `5aa3fac8`). Second: ADR-111 — moves the Guardian `PRODUCT_PACKAGES` entry from `device/lynx/device.mk` to `common/device-common.mk` so every Pixel target ships it; landed as `6ebe8024291a5c003f0615a896574afbb9cbf29b` on `sovereignos-2026091900`, authored from a fetch-only local repo of the two touched files (the live repo is a partial clone, so a full clone fails). |
| `device/generic/goldfish` | `1facfe03b3519c3413a51d2ea56400bbade0b4c3` | `patches/device-generic-goldfish/0001` | ADR-051 (amends ADR-033); squash of three landed commits (base_phone product, generic_system path requirement, dynamic-partitions budget) |

The exported series were squashed on 2026-10-13: `vendor-adevtool` two landed commits into one
(the second removes the `device/lynx/device.mk` lines of the first, so only the
`common/device-common.mk` addition is left) and `device-generic-goldfish` three into one. On the
base above, the squashed patch gives the same tree as the landed commits applied in order, checked
in a disposable repository seeded from the build server. The commit hashes in the table are the
landed commits.

Apply from the tree's own repository root:

```sh
cd vendor/adevtool && git am ../../guardian/patches/vendor-adevtool/*.patch
```

```sh
cd device/generic/goldfish && git am ../../guardian/patches/device-generic-goldfish/*.patch
```
