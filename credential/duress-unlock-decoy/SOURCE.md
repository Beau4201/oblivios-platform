# Source record

## Fix 2026-11-15: the stage of an authorized switch, the decoy's look and theme colours (`frameworks-base/0037` to `0045`)

ADR-230 and ADR-231 of the foundation repository (measurements: foundation `duress-engineering-notes.md`, "Round of 2026-11-15";
rows `DUR-019`, `DUR-036`, `DUR-038`, `DUR-043`, `DUR-044`). Module-built and pushed to the phone, not built into an image; the
rows of the last build are not run. Nine commits of `oblivios-platform/.worktrees/duress-ram-timing` (branch `duress-v3`, from
`ae91efc3`), landed on the build tree with `git am` and exported with `git format-patch --start-number=37`:

- `0037` adds `AuthorizedSwitchStage` to SystemUI and shows it from the before-switch callback of an authorized switch (first form:
  an empty window that asks for the wallpaper). Replaced by `0042` and `0044`.
- `0038` writes the owner's Launcher3 look (themed icons, icon shape, labels hidden) to the decoy through the launcher's own
  `grid_control` provider (`DecoyLauncherLook`): read at the lock on a thread of its own, written after the decoy's storage is
  unlocked and before its home starts; the window manager holds the deferred home start while that is pending (at most 1.5 s).
  Removes the lock-time launcher warm start (`0021`). System_server cannot edit the launcher's prefs file (mode 660, owner is the
  app, no DAC override), which is why the provider is used.
- `0039` reads the shape row `is_default` as a string (the provider's `MatrixCursor` holds a Boolean, which arrives as "true";
  `getInt` of it is 0, so the shape would never have been written), gates the warnings to debuggable builds, and reads the owner's
  look off the lock settings handler. `DecoySettingsMirror#launcherLookParts` has 1 JVM test, the class 17 tests, 1 mutation fails it.
- `0040` replaces the two methods that `0038` had added to `ActivityManagerInternal` by a static, `DecoyLookGate` in `services.jar`.
  `ActivityManagerInternal` is in `framework.jar`, which a module build of `services` does not replace: the phone had the new
  `services.jar` against the old `framework.jar` and the duress PIN ended system_server with `NoSuchMethodError`.
- `0041` gives the decoy the outgoing user's wallpaper colours in `ThemeOverlayController#onUserChanged` when it has none: the
  service keeps the colours from the copied `wallpaper_info.xml`, so the engine's report equals what it has and is not sent on,
  `mCurrentColors[decoy]` stays empty and the theme was built from no colours (the default blue).
- `0042` adds `DecoyWallpaperHold` (static, services.jar): while an authorized switch is under way `WallpaperController#hideWallpapers`
  does not hide the wallpaper because a window that is not an activity went off screen (the stage was counted as not drawn after
  SystemUI redrew it, and the wallpaper was hidden until the home drew, 0.2 to 0.7 s of black); it ends when an activity window
  becomes the wallpaper target.
- `0043` keeps the stage window shown while WindowManager counts it as not drawn (`WindowStateAnimator#prepareSurfaceLocked`).
- `0044` makes the stage draw the wallpaper bitmap itself (`getBitmapAsUser` with `FLAG_SYSTEM`, the engine's own call; `FLAG_LOCK`
  with `returnDefault` returned the empty default image and the first try was black), full screen, no insets, at the layer of the
  additional status bar (above the status bar, below the shade); preloaded when the keyguard is shown; kept until the home has drawn.
- `0045` signals that moment by a broadcast to the SystemUI package, received by a receiver that is not exported. A persisted
  setting (the first form of `0044`) was refused by the security review: its presence in the secure settings proves a duress
  switch. Warnings of the stage are debuggable builds only.

Open: the pad over black while the wallpaper service rebinds at the switch (0.10 to 0.16 s) is covered by the stage but not
removed; the home draws about 0.9 to 1.0 s after the credential check, behind the stage.

## Fix 2026-11-14 (round of `lynx` `2026111401`): the decoy wallpaper and the early stop at the switch back (`frameworks-base/0035`, `0036`)

Measured on `2026111401` (`DUR-038`, `DUR-035`, `DUR-036`, `DUR-039`, `DUR-040`; foundation `duress-engineering-notes.md`, "Round of
2026-11-14").

- `0035` copies the owner's wallpaper as files (`wallpaper_orig`, `wallpaper`, the lock pair, `wallpaper_info.xml` last) to the
  decoy's system directory at the lock, instead of `WallpaperManager#setStream`: the wallpaper service finishes a static set in a file
  observer that exists only for the current and the unlocked user, so the decoy kept a source image, no crop and no colours, and its
  home and lock screen were black. New class `DecoyWallpaperFiles`; 8 JVM tests pass, 4 of 4 mutations fail them. Seen on the device:
  user 12 holds a crop of 265563 bytes and a `wallpaper_info.xml` of 8127 bytes (759 before), `dumpsys wallpaper` lists `mBindSource=SET_STATIC` for both users.
- `0036` does not kill the decoy's packages at the start of the switch back (`UserController#isEarlyPackageKillEnabledForUserSwitch`
  is false when the outgoing user is the decoy): `stopPreviousUserApps` killed the decoy launcher 0.18 s into the exit and the
  stage of the animation went with it. The stop of the switch still ends the decoy about 1.6 s after the return. One test in
  `UserControllerTest`, written, not run (it needs the Android test environment).

Authored in `oblivios-platform/.worktrees/duress-ram-timing` (branch `duress-v3`, base `83b9a7a2`, the `duress-v2` head), exported with
`git format-patch --start-number 35`, landed 2026-11-14 with `git am --3way` on `f26cefc13b00` as `f83ebf636926` and `de89c21728ad`;
the `git patch-id --stable` of each landed commit equals that of its file. Type-checked with `tools/build-vm/typecheck-duress-frameworks.sh`
(no error beyond the three of the base). Module-built (`services`, `SystemUI`, 14 min) and pushed to the phone as `services.jar`
with its `oat` files after `adb disable-verity` (owner, 2026-11-14); not part of an image yet.

## Fix 2026-11-14: the cold duress unlock at about 0.90 s (`frameworks-base/0022` to `0034`)

ADR-229 of the foundation repository (design: `docs/superpowers/specs/2026-11-12-duress-unlock-timing-design.md`, plan:
`docs/superpowers/plans/2026-11-12-duress-unlock-timing.md`). The cold unlock was five serial waits (measured on `lynx` `2026111201`);
the thirteen commits remove them and the differences a second person could see at the switch.

- `0022` copies `ui_night_mode` and the dark-theme times (the display configuration changed at the switch); `0024` copies the
  volumes, refresh rate, theme setting and lock-screen and status-bar values by an allowlist; `0025` copies the owner's wallpaper
  at the lock, on the handler; `0031` copies the owner's Auto confirm unlock when both PINs have the same known length.
- `0023` binds the decoy's `KeyChain` on its own thread (the bind blocked the shared SystemUI thread for 1.4 s).
- `0026` starts the decoy's home after the keyguard has gone away and holds the going-away until the window manager has applied
  the user change; `0027` requests the exit as soon as the switch is released and keeps the ordinary unlock animation; `0028`
  does not wait for the decoy's unlock to end and starts its home 150 ms after the going-away; `0029` adds the global setting
  `oblivios_duress_fast` (0 turns 0026 to 0028 off without a build); `0030` posts the deferred home start only when one waits.
- `0032` keeps the notification stack hidden at the end of the bouncer-to-gone transition during an authorized switch
  (the cause of the card that showed for a few frames and stuck: the transition ended before the held exit started).
- `0033` and `0034` are stage logs for the timing rows (`Exit held`, `Exit released after N ms`, `Home start deferred`), on
  debuggable builds only.

Authored in `oblivios-platform/.worktrees/duress-ram-timing` (branch `duress-v2`, base `291321d9`), exported with
`git format-patch --start-number 22`, checked on the build server with `git apply --cached` onto a temporary index (all thirteen
apply), landed 2026-11-14 with `git am` on `291321d970d3` (tree clean, no build running); head `f26cefc13b00`; the
`git patch-id --stable` of each landed commit equals that of its file. Type-checked with `tools/build-vm/typecheck-duress-frameworks.sh`
(Java and Kotlin against the jars of the previous build, a control on the base, planted errors found), 16 JVM tests of
`DecoySettingsMirror` pass, a mutation of the Auto confirm rule fails one. Built into `lynx` `2026111401`; SystemUI tests and the
module build are the first compile of the tests. What the device has to show: `DUR-019`, `DUR-035`, `DUR-036`, `DUR-038` to `DUR-040`.

## Spike 2026-11-12: the decoy launcher process while the decoy is locked (`frameworks-base/0021`, throwaway)

Design B of `docs/superpowers/specs/2026-11-12-duress-unlock-timing-design.md` in the foundation repository. The owner did not
accept the resident decoy key of `0017` (withdrawn, ADR-228). The spike series is `0018`, `0019`, `0020` and `0021`
(checked with `git am` onto the file of `026043e8`: the result is identical to the spike branch `duress-spike` in the scratch
repository, commits `dbd7105a`, `3d4eb67c`, `1dae54ec`, `e9ccee49`). `0021` requests the decoy's Launcher3 process through
`ActivityManagerInternal.startProcess` when the keyguard locks, with the decoy's CE key still evicted; it logs under `SpikeWarm`.
Verified in the source: the signature of `startProcess` (9 arguments), `IPackageManager.getApplicationInfo(String, long, int)`,
`AppGlobals.getPackageManager`, and that `ProcessList.startProcessLocked` has no locked-user check before the process
is requested. Landed 2026-11-12 with `git am -C1` on the build tree (`6cf16c743972`, `6b0fdd75fc8d`, `565288fd2c7b`,
`291321d970d3`, tree clean first, sha256 of the three touched files equal to the scratch repository), type-checked against the jars of
the previous build (0 errors, with a control of 1 error), compiled into `lynx` `2026111201` (build 16:13, markers `SpikeWarm` and
`DecoySettingsMirror` in `services.jar`), not signed, not flashed, not run. What the device has to show: the process exists after the lock
(`ps -A | grep u12_`), it is alive 30 s later, no `FATAL EXCEPTION` of `com.android.launcher3` for user 12 in `logcat`, the
decoy launcher's `Displayed` line after a duress credential is short, and the stage times of `DUR-019`.

## Fix 2026-11-12: the screen settings of the owner for the decoy (`frameworks-base/0018` to `0020`; `0017` withdrawn)

Measured on `lynx` `2026111101` (`DUR-019`, `docs/superpowers/specs/2026-11-12-duress-unlock-timing-design.md` in the
foundation repository): the duress unlock took 2.57 to 2.96 s after an owner unlock (3 s of black screen) against 0.79 s for the
ordinary one. vold evicts the decoy's CE key 0.2 s after every owner unlock because `0015` stops the decoy with
`stopUserWithCallback`, so the lock-time start stays `RUNNING_LOCKED` and the launcher starts after the duress credential.

- `0017` (withdrawn): `maybeStopUnusedWarmDecoy` would have stopped with `stopUserWithDelayedLocking` (verified in
  `ActivityManagerService.java:17424`), keeping the decoy's CE key resident so that the next warm start unlocks at once. The owner
  declined an open decoy storage while the device is locked (ADR-228); the file is removed, commit `2ef1f74` of this repository
  holds it. The spike `0021` takes its place.
- `0018`: `DecoySettingsMirror` (JVM-testable, `DecoySettingsMirrorTest`) copies the owner's brightness, screen timeout, rotation
  and lock-screen settings to the decoy; the owner's values are only read.
- `0019`: two findings of the automated security review of `0017` and `0018`. The quick-settings tile list holds
  `custom(package/service)` entries that name the owner's apps, so only built-in tiles are copied (a decoy must not learn the
  owner's apps); and the copy moved from before `switchToUserSilently` to the moment the keyguard locks (after the decoy is
  started), so it adds nothing to the duress unlock time. Residual gap: brightness changed from the lock screen's quick settings
  after the lock is not copied (measured in `DUR-037`).
- `0020`: a third review finding: a prefix check on `custom(` lets tile entries through by spelling (leading space, capitals,
  `intent(` forms, component names). The tile list is filtered by an allowlist: trimmed entries that are plain lower-case names.
  10 mirror tests, with the 24 existing decoy finger tests 34 of 34 pass on the JVM; mutations of the mirror (a key removed from
  the list, a write to the owner, a swallowed exception, a null written, the allowlist widened, the trim removed) were each
  killed by a test. The wiring in `DecoyProfileHelper` is not compiled and not run.

Authored in `oblivios-platform/.worktrees/duress-ram-timing` (branch `duress-timing`, `edad7c44`, `1b326ccf`, `112d9572`, `c7208946`), exported with
`git format-patch`, checked with `git am` onto the file of `026043e8` (result identical to the worktree file). `0018` to `0020`
were landed with `0021` (see the spike section); `0017` was never landed.

## Fix 2026-11-11: the API listing after the rename (`frameworks-base/0016`)

The first build after the rename (`lynx` `2026111101`, started 14:49) ran 2 h 18 min and stopped at 99 % with one failed target,
`check_current_api` of `system-api-stubs-docs-non-updatable`: the rename of `USER_TYPE_FULL_FREEHOLDOS_DECOY` to
`USER_TYPE_FULL_OBLIVIOS_DECOY` moves the constant behind `USER_TYPE_FULL_GUEST` in the generated listing, and
`core/api/system-current.txt` still had the old order (the only occurrence in any API file). `0016` swaps the two lines, no code
change. Authored in a scratch repository from the server's file, landed 2026-11-11 with `git am -C1` on `02bd4ccabea5` as
`a9cbbaac5325` (tree clean, no build running); the `git patch-id --stable` of the landed commit equals that of the file. The build was
started again at 17:11 and continues incrementally. Other renamed names that sort differently in a generated list are not
searched for; the build shows them.

## Fix 2026-11-05, exported 2026-11-11: choosing the finger and the owner check (`frameworks-base/0012`, `frameworks-base/0013`)

Two commits of the build tree that were built into `lynx` `2026110502` and never exported (found 2026-11-11 while landing the two
patches below, which had taken these numbers). Exported from the branch `oblivios-2026110200` as `ef65610d1f1b` (`0012`, choosing the
duress finger works for every biometric id: `DecoyFingerPolicy.isUsableFingerId`, a copy of the owner credential in
`DecoyFingerHelper.set`; row `DUR-027`) and `afc9ac07440e` (`0013`, the decoy administration accepts only the owner's own credential:
`LockSettingsService.isOwnerCredential`; the "DUR-032" of its message is the proof row of that time, since reassigned). Their
commit subjects on the branch still read `freeholdos:`; the files carry `oblivios:`, and no other text differs (the mapping of the
rename on the subject only). Proof that the files are the branch: on a temporary index of the server at `8b4fad9bb49b` (the head
before the two patches below) both applied in reverse and then forward again, and the tree equals `8b4fad9bb49b` again. Not
compiled by this export, and not run on a device beyond the build of `2026110502`.

## Fix 2026-11-10: decoy memory while locked and the FallbackHome route (`frameworks-base/0014`, `frameworks-base/0015`)

Numbered `0012` and `0013` until 2026-11-11, when the two patches above were found missing from the package and took those numbers
(the files were renamed and their `[PATCH 12/13]` marks reset; the commits on the branch are unchanged).

Owner requirement (2026-11-10): the decoy may not hold much memory, and the duress unlock must take as long as an
ordinary one. Measured on `lynx` `2026110502` the same day (logcat and `dumpsys meminfo`, charging, battery 3-4%, so
diagnostic and not a `DUR-019` result): with the device locked the decoy ran 38 processes, 2.0 GB PSS; about 30 of them
came from its `BOOT_COMPLETED`, one every 0.45 s for 14 s after the lock.

- `frameworks-base/0014`: `UserController.finishUserUnlockedCompleted` holds `BOOT_COMPLETED` back for a decoy-type user
  that is neither current nor the switch target (`UserState.bootCompletedDeferred`); `finishUserSwitch` sends it once the
  decoy is in the foreground and its home screen went idle. A stop discards the mark with its `UserState`.
- `frameworks-base/0015`: `DecoyProfileHelper` stops the warm decoy whenever the keyguard reports unlocked in the owner's
  own session with no switch pending (`getCurrentAndTargetUserIds`), so a finger unlock no longer leaves it running; and
  `awaitDecoyUnlocked` starts the decoy in the background and waits (bounded, 5 s) until it is unlocked before the
  switch, before OK for a typed credential and on the handler for the finger. Without it a switch to a decoy that is not
  yet unlocked resolves Settings' `FallbackHome` and unlocks the decoy only when that placeholder goes idle.
- Base: the public GrapheneOS `platform_frameworks_base` tag `2026110200` (`ad133394d443`) with the 83 tracked
  `frameworks-base` patches of all packages applied in author-date order with `git am -C1`, as `rebase-branches.sh`
  does; the build server was not reachable (no VPN) when the files were authored, so they were not compared with the live branch
  then; on 2026-11-11 they applied to its `oblivios-2026110200` without change (see the landing below). Authored in `.worktrees/duress-ram-timing`; the exported series re-applied from the tag gives
  the authored tree (`d2169c091944`).
- Landed 2026-11-11 on the build server, branch `oblivios-2026110200` of `/srv/oblivios/platform-primary/frameworks/base`
  (tree clean, no build running, `git apply --check` clean first, then `git am -C1`): `0014` as `788c1f1fdd6c` and `0015` as
  `02bd4ccabea5` on top of `8b4fad9bb49b`; the `git patch-id --stable` of both landed commits equals that of the files in
  `patches/frameworks-base/`, so the files and the live branch agree. Not compiled (a parse-only pass over the three files reports no
  error; it is not a type-check), no unit test (no `UserControllerTest` case reaches `BOOT_COMPLETED`), not run on a device. Checks on the
  device: `Deferring BOOT_COMPLETED of background user` in logcat and the decoy's process count and PSS while locked;
  no `FallbackHome` in a duress unlock; `am get-started-user-state` of the decoy after a finger unlock.

## Fix 2026-11-04: review of the finger work (`frameworks-base/0009`, `frameworks-base/0010`)

Found by the independent review of `docs/reviews/2026-11-unreviewed-code-review.md` (B18) and by the automated review of the commit.

- `frameworks-base/0009` (`9a05f2351c7a`): `DecoyFingerHelper.onOwnerSyntheticPasswordUnlocked` runs inside the real owner's credential check
  and now catches a `RuntimeException`, leaves the cache empty and logs, so a failure of the storage read or the key derivation can never fail
  the owner's own unlock.
- `frameworks-base/0010` (`176cd70ae139`): `AuthenticationClient` no longer calls `resetLockoutTimeBound` for a match routed to the decoy.
- `frameworks-base/0011` (review finding B8, landed 2026-11-05 on branch `freeholdos-2026110200` as `eab40ba20392`, on top of the rebased series; not on the old branch): removing the linked finger ends the link. `FingerprintUtils.removeBiometricForUser` (the one place `RemovalClient` and the enumerate clean-up pass through) calls the new `LockSettingsInternal.onFingerprintRemoved`, which deletes the link and the cached credential when the removed finger is the linked one of the real user (`DecoyFingerPolicy.shouldClearOnRemoval`, pure); a failure never fails the removal. Authored test first in a disposable repository: four new `DecoyFingerPolicyTest` cases failed to compile before the change, 24 of 24 tests pass after, nine mutants of the rule all killed. Landed with `git am` in a work tree of the branch (no build running); `git diff --stat` of the commit shows the six files of the patch only. Not compiled with the framework: `services` is built by the next module or image build. Not run on a device: `DUR-031`.
- Landed 2026-11-04 on `ce630046aa54`, branch `freeholdos-2026100600`, tree clean, no build running, authored in a disposable repository
  from the touched files; the blobs of `DecoyFingerHelper.java` and `AuthenticationClient.java` equal the disposable repository's. Not
  compiled, not module-built. Not done from the same review: B8 (the link is not cleared when the enrolled finger is removed; needs a hook
  in `FingerprintService`), B19 to B23 (design questions).

## Feature 2026-11-04: one enrolled finger opens the decoy (`frameworks-base/0008`, `packages-apps-settings/0004`)

The owner asked for a second way into the decoy: a finger of the other hand, chosen in Settings, in place of
the decoy PIN. Design: the internal record "duress unlock finger design" (not published); plan: the internal
record "duress unlock finger" (not published).

- `frameworks-base/0008`: `AuthenticationClient.onAuthenticated` asks `LockSettingsInternal.routeDecoyFinger`
  about every fingerprint match before any token reaches the keystore. `DecoyFingerPolicy` (pure, JVM tests,
  16 mutations killed together with the codec) decides: not the duress finger is the ordinary path; the duress
  finger at the keyguard with a registered decoy, a cached credential and biometrics allowed routes; the
  duress finger anywhere else (a `BiometricPrompt`) or without those is refused as an unrecognised finger and
  never opens the real session. A routed match skips `addAuthToken` and reaches the keyguard as a success for
  `UserHandle.USER_NULL`, which `KeyguardUpdateMonitor.handleFingerprintAuthenticated` ignores, so the real
  lock screen is neither dismissed nor trusted `[verified-in-source]`. `DecoyFingerHelper` stores the finger's
  biometric id and the decoy credential wrapped (`SyntheticPasswordCrypto`) under a key derived from the real
  user's synthetic password (`SyntheticPassword.deriveDecoyFingerKey`), and keeps the unwrapped credential in
  system_server memory from the real credential's verification until reboot. `DecoyProfileHelper` shares the
  mint and switch with the typed credential (`mintAndSwitch`) and gains `routeToDecoyWithCredential`; the mint
  stays in that class. `setDecoyProfile` for another user and `clearDecoyProfile` clear the link.
  `ILockSettings` gains `setDecoyFinger`, `clearDecoyFinger`, `getDecoyFingerId`, appended last, write
  permission.
- Rejected: an escrow token for the decoy (`activateEscrowTokens` calls `destroyEscrowData` for an unmanaged
  user at its first credential verification) and a keystore-bound credential (using the key needs the real
  user's hardware token in the keystore).
- `packages-apps-settings/0004`: `DuressUnlockFingerFlow` (pure rules, 10 JUnit tests, 10 mutations killed),
  `DuressUnlockFingerOps`, `DuressUnlockFingerActivity` (the chooser: enrolled fingers, then the second
  PIN or password), the Choose and Remove entries on the main screen, the relink after Change in
  `DuressUnlockSetupActivity`, strings in `strings_freeholdos.xml`, the manifest entry.
- Base: `frameworks/base` `freeholdos-2026100600` at `a32e09ce65a6`; `packages/apps/Settings`
  `freeholdos-2026100600` at `1fc2d361130a`. Authored in disposable local worktrees seeded from the build
  server (file checksums equal on both sides); the exported patches re-apply with `git am` onto the seeded
  base and give the tree the worktree has.
- Landed on the build server 2026-11-04: `git am` on `freeholdos-2026100600`, `frameworks/base` commit
  `ce630046aa54`, `packages/apps/Settings` commit `f9920122fdf`, both trees clean afterwards, every touched file's
  sha256 equal to the worktree. Compiled: no (the services module and Settings have no host-side type-check
  here; the files parse and the pure classes run on the JVM). Run on a device: no (rows `DUR-020` onward).

## Fix 2026-11-03: no user switching on a locked device (`frameworks-base/0007`)

Decision of the owner (2026-11-03, after `GRB-017` failed on `lynx` `2026110203`): a locked screen never
offers a user switch, with or without a decoy. Cause `[measured]`: after a grab lock the owner switched
from the lock screen to a second user without a secure lock screen; the log shows
`sendContinueUserSwitchLU-0-to-13` at 15:59:11 and `init: auto_reboot: on_device_unlocked: stopped timer`
at 15:59:12, so the 60 second restart of the grab lock never came. The earlier rule
(`frameworks-base/0002`) hid the switcher on a locked device only when a decoy existed, on purpose, so a
device without one stayed as stock.

- `frameworks-base/0007`: `UserManagerService.isUserSwitcherEnabled` returns false while the keyguard
  shows, whatever users exist; the helper `hasDecoyUser()` that only served the old condition is removed.
  The six surfaces of ADR-067 route through this method `[assumption]`: they were not audited again, the
  device run of `GRB-017` is the check.
- The method stays display-only: `UserController.switchUser` does not consult it, so a caller that may
  switch users still can. Enforcement at `switchUser` is not built; add it if a locked-screen path that
  skips this method is found.
- Tests in `UserManagerServiceTest`: a locked device without a decoy now gives false (it gave true), and
  an unlocked one without a decoy gives the real value. Host-side only a build runs them.
- Base: `frameworks/base` `freeholdos-2026100600` at `dac38e875e45`. Authored in a disposable local
  repository seeded from the build server (both files' sha256 equal to the tree before the edit); the
  patch applies with `git am` to that baseline and gives the identical diff.
- Landed on the build server 2026-11-03: `git am` on `freeholdos-2026100600`, commit `10000dab31e8` on top of
  `dac38e875e45`, tree clean afterwards, both files' sha256 equal to the disposable repository. Compiled:
  no (the first module build is the check). Run on a device: no (`GRB-017` re-run, then the lock-screen
  surfaces by eye).

## Feature 2026-11-02: a password as the second credential (`packages-apps-settings/0003`)

The owner asked for a password as the Duress Unlock second credential (ADR-145 had made it PIN-only
"until a password is built"). The second credential has the type of the screen lock: a PIN under a PIN
lock, a password under a password lock. Reason `[verified-in-source]`: `SyntheticPasswordManager.java`
(`Credential type mismatch`, around line 1729) rejects a typed credential whose type differs from the
stored one, and the keyguard types the lock's own type. `frameworks/base` needs no change: the routing
(`DecoyProfileHelper.maybeRouteToDecoy`) and the wipe check (`DuressPasswordHelper.isDuressCredential`)
already handle both types.

- `packages-apps-settings/0003`: the main and set-up screens follow the type of the owner credential
  (input type, wording through one `%1$s` noun, `createPassword` or `createPin`, "Passwords don't match");
  the Auto confirm unlock length rule applies to a PIN only. When the lock type changes after the
  set-up the old decoy never matches, so the main screen says so and Change types the current value as
  the type the decoy still has (`getCredentialTypeForUser`).
- Real branch: `packages/apps/Settings` `freeholdos-2026100600`, commit `dcb63695a6f` on top of
  `363826216c8`. Authored in a disposable local repository seeded from the build server and landed
  with `git am`.
- Compiled: no. Settings has no host-side type-check here; the first module build is the check.
  Run on a device: no (rows `DUR-014` to `DUR-018` of the validation matrix).

## Correction 2026-10-15: `system-current.txt` sort order after the FreeholdOS rename (`frameworks-base/0006`)

The rename of `USER_TYPE_FULL_SOVEREIGNOS_DECOY` to `USER_TYPE_FULL_FREEHOLDOS_DECOY` (ADR-170) left the line where
the old name sorted (after `..._SECONDARY`), so `check_current_api` failed at 96% of the first `lynx` build of the
renamed tree (`2026101407`, the only failed step of 10 732). The same defect had happened at the earlier rename (see
"Correction 2026-09-26" below). `frameworks-base/0006` moves the line before `..._GUEST`; one line, nothing else.
Verified: `core/api/system-current.txt` after the patch is byte-identical to the API file the build generated
(`diff` empty, blob `131a3e7ab661`), the build server's `freeholdos-2026100600` carries it as `e9f0c1e67143`. The
rename tool does not re-sort API files; any further renamed name in a `*-current.txt` needs the same check.

## Patch series squashed again (2026-10-13) — read this before using any patch number

The twelve `frameworks-base` and four `packages-apps-settings` patches were regrouped into five and
two. Only neighbouring patches were joined, so the order is unchanged. On the bases below the new
series gives the same tree as the old series applied in order (equal tree hash after `git am` of
each in a disposable repository seeded from the build server), so nothing in the end state
changed. Every number in this file and in the records that cite it is a number of the series as
it stood before this squash (the 2026-10-09 export, or older where the text says so); the table
maps those to the exported patches. `build-release/0001` and `packages-apps-multiuser/0001` are
unchanged.

| Exported patch | Former patches |
| --- | --- |
| `frameworks-base/0001` add the decoy user type, routing and keyguard hook, and quiesce the real user | `0001`, `0002` |
| `frameworks-base/0002` hide the real user from the decoy session's UI and conceal multi-user existence | `0003` to `0006` |
| `frameworks-base/0003` authorize a single-credential decoy switch and the return path, remove the swipe | `0007` to `0009` |
| `frameworks-base/0004` report real quiesce failures; ignore a registration whose user no longer exists | `0010`, `0011` |
| `frameworks-base/0005` let Settings ask whether a PIN is the duress wipe PIN | `0012` |
| `packages-apps-settings/0001` hide the real user from Settings and present the decoy as the owner | `0001`, `0002` |
| `packages-apps-settings/0002` the Duress Unlock set-up flow, screens and the Device unlock row | `0003`, `0004` |

Bases: `frameworks/base` `40ef3305fb73` (the second `duress-unlock-dummy` patch as landed),
`packages/apps/Settings` `4ed80d47c0f`. The Settings base sits before the AirPods Settings patch
that was landed between the two Settings pairs; the patches touch different files, so the order
does not matter.

## Feature 2026-10-07: Duress Unlock Settings UI (`frameworks-base/0012`, `packages-apps-settings/0003`, `0004`)

A Settings screen to set up, change and remove the decoy, as a second row below
"Duress password" on the Device unlock page. Design:
the internal record "duress unlock settings ui design" (not published), plan:
the internal record "duress unlock settings ui" (not published).

- `frameworks-base/0012`: `ILockSettings#isDuressWipeCredential(ownerCredential,
  candidate)`, appended last. Settings cannot tell whether a decoy PIN equals the
  duress wipe PIN, and a decoy PIN equal to it would silently disable the wipe
  because decoy routing replaces the verification response before the wipe hook
  sees it. Needs the write permission and the owner credential; runs no wipe and
  no routing; zeroizes both credentials.
- `packages-apps-settings/0003`: `DuressUnlockFlow` (the order of set-up, change and
  remove, no Android types) and `DuressUnlockFlowTest` (14 JUnit tests; ten
  mutations of the flow each fail at least one).
- `packages-apps-settings/0004`: the row (`order 1010`, system user only), the main
  screen, the set-up and change screen, the Android adapter, strings in
  `strings_sovereignos.xml` (not in the upstream `strings_ext.xml`), layout and
  manifest entries.
- Real branches: `frameworks/base` `sovereignos-2026091900-r2`, commit
  `887d69072797` on top of `d0e538f28aac`; `packages/apps/Settings`
  `sovereignos-2026091900`, commits `9e55d98fe45` and `54e69e46662` on top of
  `d5b871d8aa3`. Authored in disposable local worktrees and landed with `git am`;
  these patches are exported fresh from the landed commits and re-applied onto a
  pristine copy of the base files (identical tree hashes).
- Review: one independent reviewer, no blocking defect; its important findings
  (Auto confirm unlock PIN length, the process kept foreground while the work runs,
  rotation, zeroizing in system_server, a decoy left without a PIN, two surviving
  test mutants) are fixed in these patches. The plan records each finding.
- Compiled: yes, into the `lynx` `userdebug` image `2026100226` (built 2026-10-07 on the
  tree at the commits above; `DUR-002`). Run on a device: yes, on `lynx` on
  2026-10-07; rows `DUR-003` to `DUR-013` of the validation matrix have evidence, with
  the parts that were not run stated in each row.

## Fix 2026-09-29: wrong PIN crashed SystemUI when the decoy user had been removed (`frameworks-base/0011`)

Reported by the owner on `stallion` and `lynx`: after a wrong PIN the screen
turns off, and re-entering the same PIN shows "Already tried that PIN".

**Root cause, from logcat and the device's `locksettings.db` on `stallion`.**
`decoy_profile_state` was `0:10` while user 10 no longer existed: removing the
decoy user (`pm remove-user`) does not clear the registration. On every first
wrong PIN, `DecoyProfileHelper.maybeRouteToDecoy` called
`doVerifyCredentialInner` for user 10. A removed user has no protector, so its
`LskfIdentifier` reads as a special credential, and
`SyntheticPasswordManager.getSpecialUserPersistentData` threw
`IllegalArgumentException: Unknown special user id 10` out of
`SoftwareRateLimiter.writeStats`. That crossed Binder into
`LockPatternUtils.checkCredential` in SystemUI, which crashed and restarted
(`am_crash`, pid change), which is the screen going off. A repeated identical
guess is a duplicate wrong guess: it skips the hardware write, so it does not
crash, and the bouncer shows the upstream "Already tried that PIN" message.
That message is unmodified upstream behaviour and is deliberately untouched.

**Fix.** A registration whose user is missing or `partial` (being removed) is
treated as no decoy, including the equivalent-cost dummy check, so timing
matches a device with no decoy. One file, +16/-1.

- Real branch: `frameworks/base` `sovereignos-2026091900-r2`, commit
  `c013345b2161` on top of `8b9b021f5ff1`, landed with `git am` (authored in a
  disposable local worktree, then `git am`'d). This patch is exported fresh
  from that landed commit. Not yet on `sovereignos-duress-decoy-switch`.
- Compiled in: `javap` on the built class shows
  `private static boolean decoyUserExists(int)`.
- Hardware, `stallion`, `user` build `2026092802` (unlocked, before relock):
  - The three core Duress scenarios still pass (owner to decoy; decoy to owner
    with one owner-PIN entry; reboot then only the decoy PIN: user 0 CE
    storage was unlocked only by the later owner PIN, no second prompt).
  - Reproduction state (decoy removed, registration left): five
    verifications of user 0 and none of user 10, no `am_crash`, no
    `Unknown special user`, no SystemUI restart, and "Already tried that PIN"
    on the repeated guess.
- Limits: no unit test (no test file exists for `DecoyProfileHelper`). On a
  `user` build `locksettings.db` cannot be read, so the "registration is still
  stale" premise rests on the same procedure leaving `0:10` in place earlier
  the same day. Not tested on `lynx`. Follow-up not done: clear the
  registration when the decoy user is removed.

Apply after the series above:

```sh
cd frameworks/base && git am \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/0011-*.patch
```

## Correction 2026-09-26: `system-current.txt` sort order after the rename

`frameworks-base/0001` added `USER_TYPE_FULL_SOVEREIGNOS_DECOY` to
`core/api/system-current.txt` in the position the old `..._AEGISOS_DECOY`
name sorted to (before `..._GUEST`). The API file is checked in sorted order,
so `check_current_api` failed at 99% of the first `2026091900` `lynx` build.
The hunk now places the line after `..._SECONDARY`, before `..._SYSTEM`; nothing
else in the patch changed. Verified with a fresh `git am` of the whole
`frameworks-base` series (10 patches, after `branding/boot` and
`duress-unlock-dummy`): old base `2026071500` tree `c243494ada68`, new base
`2026091900` tree `e0b2c4bf0727`; on both, only `system-current.txt` differs
from the previous series. The tree hashes in the squash section below predate
this correction.

## Patch series squashed (2026-09-25) — read this before using any old patch number

The decoy package's patches were consolidated from 107 fine-grained patches
into 13: `frameworks-base` 93 → 10, `packages-apps-settings` 10 → 2,
`packages-apps-multiuser` 4 → 1. `build-release` (already one squashed
patch) is unchanged. Every "patch `NNNN`" reference elsewhere in this file,
in `README.md`, and in the decision log (ADR-053 through ADR-096) uses the
**pre-squash** numbering; the table below maps each old number to its new
patch. Each new patch's commit message also lists the old subjects it
absorbed.

Scope: package files only. The real build-server branch
`sovereignos-duress-decoy-switch` was **not** rewritten and still carries
the fine-grained history (and its original SHAs) in `frameworks/base`,
`packages/apps/Settings` and `packages/apps/Multiuser`. The two forms are
tree-equivalent, not SHA-equivalent.

Equivalence evidence (all three re-run from a clean `git am` of the
exported files, not taken from the squash step):

- Seeded a disposable local repo from the real base at the build server
  (read-only `git archive`), applied the old series, and compared every
  touched file against the branch tip on the build server: 48/48
  `frameworks/base`, 14/14 Settings, 2/2 Multiuser identical apart from the
  `AegisOS`→`SovereignOS` rename applied to this package by ADR-093.
- Squashed by cherry-pick per group; final tree identical to the unsquashed
  tree before export. One group reorders `0028` behind `0029`-`0035` (new
  `frameworks-base/0005`); the tree comparison covers that.
- Fresh `git am` of the exported patches onto the same base reproduces
  these tree hashes:
  - `frameworks/base` (after the two `duress-unlock-dummy` patches):
    `2f1399b82be7513fbc852746732bd16f0b200887`
  - `packages/apps/Settings`: `3bb5dc8f7e35a78ef7e4e6d6adedbf3d4752b58b`
  - `packages/apps/Multiuser`: `c86730033a917895b001a31d2c2d97c96cf251bb`
- Bases: `frameworks/base` `7d7001f9c4e4` (GrapheneOS `2026071500` plus the
  boot-identity commit) plus `credential/duress-unlock-dummy`;
  `packages/apps/Settings` the parent of the ten former patches on the
  build-server branch (tag `2026072900` lineage); `packages/apps/Multiuser` `44f89da`.
- Not rebuilt: this is a source-tree-identity result, no `m` build or
  device run was done for the squashed form.

### Old → new patch map

| New patch | Former patches | Subject |
| --- | --- | --- |
| `frameworks-base/0001` | 0001-0007 | sovereignos: add the duress decoy user type, routing and keyguard hook |
| `frameworks-base/0002` | 0008-0016 | sovereignos: quiesce the real user during a decoy session and add the return path |
| `frameworks-base/0003` | 0017-0027 | sovereignos: hide real-user identity from the decoy session's UI surfaces |
| `frameworks-base/0004` | 0029-0035 | sovereignos: make the decoy session's UI match a single-owner device |
| `frameworks-base/0005` | 0028, 0036-0038 | sovereignos: use the actual target user for CE-storage checks in backup and storage |
| `frameworks-base/0006` | 0039-0042 | sovereignos: conceal multi-user existence on pre-unlock surfaces |
| `frameworks-base/0007` | 0043-0057 | sovereignos: authorize a single-credential decoy switch and warm-start the target |
| `frameworks-base/0008` | 0058-0070 | sovereignos: skip the re-armed bouncer and active-dismiss on an authorized switch |
| `frameworks-base/0009` | 0071-0084 | sovereignos: authorize the return path and remove the unlock swipe and delay |
| `frameworks-base/0010` | 0085-0093 | sovereignos: report real quiesce failures and re-evaluate the user-switcher state |
| `packages-apps-settings/0001` | 0001-0007 | sovereignos: hide real-user identity from Settings for the decoy session |
| `packages-apps-settings/0002` | 0008-0010 | sovereignos: make Settings present the decoy as the device owner |
| `packages-apps-multiuser/0001` | 0001-0004 | sovereignos: hide real-user identity from the Multiuser widget for the decoy session |


## Commits (verbatim from `git format-patch`)

### `frameworks/base`

- `b51bc3e0d4d9273164194eed5c97c1119ecc1710` — "aegisos: add
  USER_TYPE_FULL_AEGISOS_DECOY user type" (`patches/frameworks-base/0001`,
  real build-server history)
- `bc0f20e0166a8c75334dbf0e32e03d46b1a1ed71` — "aegisos: accept
  USER_TYPE_FULL_AEGISOS_DECOY into system-current.txt"
  (`patches/frameworks-base/0002`, real build-server history)
- `c05c6b25fae8` — "aegisos: add dialog-free silent user switch for duress
  decoy" (`patches/frameworks-base/0003`, **real build-server SHA** — the
  patch file itself was generated from local commit `591c7e526e6ee9de5941245f0d765defd0fee930`
  in a disposable worktree; `git am` reconstructs the commit against the
  real tree, so it gets a genuinely different SHA even for byte-identical
  content. Verify the real SHA with `git log` on the branch after `git am`
  — never assume the local worktree's own commit SHA carries over) —
  authored under `CONTRIBUTING.md`'s new local-first discipline (ADR-054): edited
  in a disposable local git repo seeded from the real branch HEAD, not on
  the build server directly, then landed there with `git am --3way` (applied
  cleanly, no conflicts).
- `204a46b432f3` — "aegisos: add decoy profile routing state storage"
  (`patches/frameworks-base/0004`, real build-server SHA) — authored
  under ADR-054's local-first discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-decoy-task3/`, seeded read-only from
  the real branch HEAD `c05c6b25fae8`) and landed on the real branch with
  `git am --3way` (applied cleanly, no conflicts).
- `10f858743765` — "aegisos: add DecoyProfileHelper keyguard hook and
  freeze/switch orchestration" (`patches/frameworks-base/0005`, real
  build-server SHA) — new `DecoyProfileHelper.java` plus
  `LockSettingsService.java`/`ILockSettings.aidl`/`LockPatternUtils.java`
  changes. Diverges from the plan's own literal sample code in two
  places, both found by checking real source rather than transcribing the
  brief: added `checkWritePermission()` to `setDecoyProfile`/
  `clearDecoyProfile` (every sibling `@Override` in the real
  `LockSettingsService.java` has this call; the plan's sample omitted it —
  a real gap in the plan, not a style choice), and added a guard rejecting
  `decoyUserId == UserHandle.USER_SYSTEM` in `setDecoyProfile` (closes an
  unbounded-recursion path the design's own recursion-prevention
  — "userId == decoyUserId != USER_SYSTEM" — silently assumed rather than
  enforced).
- `ed256ec87fb1` — "aegisos: wire decoy routing into
  doVerifyCredentialInner" (`patches/frameworks-base/0006`, real
  build-server SHA) — the `doVerifyCredentialInner` not-matched branch now
  calls `decoyProfileHelper.maybeRouteToDecoy`, matching the plan exactly.
- `41aeb66e0893` — "aegisos: add set-decoy-profile/clear-decoy-profile
  shell commands" (`patches/frameworks-base/0007`, real build-server SHA)
  — `LockSettingsShellCommand.java` dispatch + two handler methods. An
  earlier draft of this patch placed the file at the wrong path (repo root
  instead of `services/core/java/com/android/server/locksettings/`), which
  would have broken `git am` against the real tree — caught in review
  before landing, corrected before this SHA was produced.
- Base commit: `e6e5746502a3ea1b8970210cb9ccbfac979e490d` (`credential/duress-unlock-dummy`'s own HEAD — this branch continues that lineage)

**Build verification: all of Tasks 1-6 (`0001`-`0007`) compile together.**
Two real builds confirm this: Task 2's own build (`m systemimage`,
1h51m36s) covered `0001`-`0003` (the only files it could have, since its
ninja graph was generated before `0004` existed); a second, consolidated
build launched after `0005`-`0007` landed (1h29m45s) covered all of
`0001`-`0007` together, `#### build completed successfully ####` both
times, confirmed via the real build server's own log output, not assumed
from `git am`/exit codes.

`0001`/`0002` were generated via `git format-patch` from real build-server
history, author identity rewritten to a generic `AegisOS Build
<build@aegisos.local>` identity before committing here, then verified to
reproduce the real branch tree byte-for-byte via `git am` against the base
commit in a disposable worktree before being trusted. `0003`/`0004` were
authored directly under that same `AegisOS Build <build@aegisos.local>`
identity in a disposable local worktree
(`aegisos-platform/.worktrees/duress-decoy-task2/` and `-task3/`
respectively, each seeded read-only from the real branch HEAD at the time)
and landed on the real branch with `git am`. The commit SHA cited above for
each is the **real post-`git am` branch SHA**, confirmed via `git log` on
the remote — not the disposable worktree's own local commit SHA, which
differs (different parent/committer timestamp) even when the patch content
is byte-identical. An earlier version of this file incorrectly cited
`0003`'s local worktree SHA as if it were the real branch commit; corrected
here after discovering the mismatch while investigating Task 3's build.

## Apply

Apply `duress-unlock-dummy`'s `frameworks-base` patches first (this
package's base commit assumes them already applied), then this package's:

```sh
cd frameworks/base && git am ../../credential/duress-unlock-dummy/patches/frameworks-base/*.patch
cd frameworks/base && git am ../../credential/duress-unlock-decoy/patches/frameworks-base/*.patch
```

`build/release` has no prerequisite package — apply directly onto its real
HEAD (`8976d78feac337469081409228886065a4dfefab`, manifest tag
`2026071500`), on a branch (not detached HEAD):

```sh
cd build/release && git checkout -b aegisos-duress-decoy-switch && \
  git am ../../credential/duress-unlock-decoy/patches/build-release/*.patch
```

## What the changes do

- `0001`: adds `USER_TYPE_FULL_AEGISOS_DECOY` to `UserManager.java` and a
  matching `UserTypeFactory.getDefaultTypeFullAegisosDecoy()` builder
  (`FLAG_FULL`, max 1, `allowStoppingUserWithDelayedLocking(true)`).
- `0002`: the matching `core/api/system-current.txt` entry (formal
  `@SystemApi` acceptance) — a one-line addition, confirmed by the commit's
  own message to add exactly that field and nothing else.
- `0003`: adds `UserController.switchToUserSilently`/
  `ActivityManagerInternal.switchToUserSilently`/`ActivityManagerService
  .LocalService.switchToUserSilently` — the dialog-free foreground-switch
  path Task 4's `DecoyProfileHelper` will call. No caller exists yet.
- `0004`: adds `DecoyProfileState` — off/registered routing state storage
  (int decoyUserId) using `LockSettingsStorage` with fail-closed parsing.
  Provides `maybeGet()`, `create()`, `save()`, `delete()` matching the
  `DuressCredentials` pattern. Task 4 consumes all these methods directly.
- `0005`: adds `DecoyProfileHelper` — the keyguard-hook orchestration class
  (decoy credential check at symmetric cost, freeze the real user, silent
  switch to the decoy), plus the `ILockSettings`/`LockPatternUtils` plumbing
  Task 6's shell command needs. No caller in `doVerifyCredentialInner` yet
  (that's `0006`).
- `0006`: wires `decoyProfileHelper.maybeRouteToDecoy` into
  `doVerifyCredentialInner`'s not-matched path, scoped to
  `UserHandle.USER_SYSTEM`/`LockDomain.Primary` only — the actual real,
  end-to-end mechanism is live after this patch.
- `0007`: `set-decoy-profile`/`clear-decoy-profile` shell subcommands — the
  real setup-flow backend (not test scaffolding), mirroring
  `set-duress-credentials`'s existing dispatch pattern exactly.

## Verification trail

- ADR-042 (design approval, 2026-08-21) — architecture rationale (why
  `FLAG_FULL` not `USER_TYPE_PROFILE_PRIVATE`, why delayed locking must be
  explicitly opted in) lives there, not repeated here.
- ADR-053 (2026-08-26) — Task 1's citation, plus the router correction
  noting Task 1 of 9 landed.
- ADR-054 (2026-08-26) — Task 2's citation, plus adoption of the local-first
  patch-authoring discipline this and all future patches now follow.
- ADR-055 (2026-08-26) — Task 3's citation, plus two process-defect
  findings (a wrong SHA citation; a patch landed mid-build) folded into
  `CONTRIBUTING.md`.
- ADR-056 (2026-08-27) — Tasks 4-6's citation, plus both real builds'
  results (Task 2's and the consolidated one) confirming `0001`-`0007`
  compile together. Tasks 7-9 (emulator validation, physical hardware
  validation, findings) remain not started.
- ADR-058 (2026-08-27) — the second plan's Tasks 1-8 (see below): Task 7 of
  the first plan found the freeze mechanism above (`stopUserWithDelayedLocking`)
  can never succeed for user 0 — a universal AOSP limitation, not a bug in
  this project's own code — and this second plan replaces it with app-level
  quiescing plus a new return path, then validates the whole mechanism on a
  real emulator.

## Landing history

The per-plan landing narratives (second plan, third plan, the swipe, the quiesce and cache fixes, the
closure tasks and their `atest` blockers) are in `HISTORY.md`. They describe the series as it stood
before the squashes and use its patch numbers.
