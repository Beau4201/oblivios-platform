# Source record

## Base

GrapheneOS tag `2026100600` (ADR-166), read on the build server 2026-10-15 with the trees clean:

| Project | Revision | Files read |
| --- | --- | --- |
| `frameworks/base` (branch `sovereignos-2026100600`) | `7320e4e97e5d` | the seven files of the plan, Task 1 |
| `packages/apps/Settings` (branch `sovereignos-2026100600`) | `7358c527cb6c` | `res/xml/exploit_protection_settings.xml`, `res/values/strings_ext.xml` |

Authored in the disposable repositories `.worktrees/grab-detection-fwb` and `.worktrees/grab-detection-settings`.

## Patch series squashed (2026-11-15, ADR-234) — read this before using any `frameworks-base` patch number

The twelve `frameworks-base` patches were regrouped into two; only neighbouring patches were joined,
so the order is unchanged. The table under "Patches" and every record that cites it use the numbers
of the series before this squash. The `packages-apps-Settings` patches are unchanged.

| Exported patch | Former patches |
| --- | --- |
| `frameworks-base/0001` detect a phone pulled out of a hand in use, lock it and tell the owner | `0001` to `0008` |
| `frameworks-base/0002` the strictness of the detection, the set-down and the pulse of an impact | `0009` to `0012` |

Each exported commit message lists its former patches. Checked on 2026-11-15: the twelve old and the
two new files give the same tree (`b37bb2f0fac6dafc7ef61bd9e4428d9c63512420`) on `frameworks/base`
`3d302c8d` (the parent of the first landed grab commit on the build server branch
`oblivios-2026110200`); the two new files also apply, read from the build server, to the files of
`7320e4e97e5d` named under "Base"; the 9 files that only this series changes have the blob of the
build server branch tip (the tenth, a file other packages change too, is not compared); they apply with plain
`git am` on a tree that holds the other `frameworks-base` series. Not built, not run on a device.

## Patches

| Patch | Subject | Why it diverges from upstream |
| --- | --- | --- |
| `frameworks-base/0001` | decide whether the phone was pulled out of a hand in use | No upstream component detects a pull; the only trace is a comment in `KeyguardShowWhileAwakeInteractor.kt`. |
| `frameworks-base/0002` | settings keys of the grab detection | The keys are not `@Protected` so that adb can tune the shadow mode on a user build. |
| `frameworks-base/0003` | lock the phone and arm the restart timer when it is pulled out of a hand in use | The restart is the existing auto-reboot timer; `init` is unchanged. |
| `frameworks-base/0004` | tell the owner above the PIN entry why the phone locked | The stock lockdown message does not say that a restart follows. |
| `frameworks-base/0005` | a new touch voids the release before it, and report a peak without touch | Follows the whole-branch review: a finger that is down again at the peak was counted as pulled out; the shadow phase must be able to measure a pull without touch (design 4.2, 7). |
| `frameworks-base/0006` | keep exceptions off the handler thread, log untouched peaks, cancel the timer if the keyguard does not show | Follows the review: `lockNow()` only posts the lock, and an exception on the service thread would take `system_server` down. |
| `frameworks-base/0007` | `@hide` tags on their own line, one message text for every credential type | Matches the neighbouring keys; the stock lockdown text names the PIN only. |
| `frameworks-base/0008` | pass the settings to `BouncerMessageInteractor` in the SystemUI test fixture | `0004` added the `globalSettings` constructor argument; the Kosmos fixture builds the class with named arguments and no longer compiled. Test code only, not part of the product build. |
| `frameworks-base/0009` | lock at once on a hard impact, with or without touch | The pull rule needs touch, a release and continued movement; the owner's grabs without touch, or after which the thief stands still, never met it (ADR-176, `Proposed`). Adds the key `grab_impact_peak_dms2` and one route in `GrabDetector`. |
| `frameworks-base/0010` | an impact after a short free fall is a set-down and does not lock, with a strictness setting | The peak of an impact cannot separate a grab from a set-down of 3 cm (`GRB-018`, `GRB-019`); the free fall before it can. Adds the key `grab_impact_level` (0 strictest, 1 default, 2 relaxed), the free-fall tracking and the limit of 250 ms in `GrabDetector`, and the mapping of the level in `GrabDetectionService` (ADR-195, `Proposed`). |
| `frameworks-base/0011` | an impact with no free fall locks only after a pulse of 120 ms | The owner's pickup from a table locked the phone in active mode (2026-10-29): its first sample over 30 m/s^2 looks like a grab's. The recordings give the width: 171 to 310 ms above 15 m/s^2 for the 12 grabs, 4 to 13 ms for the spikes of pickups and bumps; ADR-204, `GRB-024`. Tests and 89 mutants in `test/`. |
| `frameworks-base/0012` | the pulse of an impact is measured in sensor time | `onSensorChanged` passed the time the callback ran; a stalled or batched delivery stretched or squeezed the pulse of `0011` (review B6). `GrabDetector.sensorUptimeMs` places a sample by its own timestamp, moved by the sleep; a missing timestamp falls back to the callback time. |
| `packages-apps-Settings/0001` | switch for grab detection | The mode needs an owner-visible control. |
| `packages-apps-Settings/0002` | list of the impact strictness | The owner asked for a setting from the strictest level to a relaxed one for running (2026-10-25); a radio list like the auto-reboot page, below the switch. |
| `packages-apps-Settings/0003` | one row on the exploit protection page, like Auto reboot | The owner (2026-10-27, after the first look at `lynx` `2026102302`): one row instead of two, no description on the exploit protection page and none per entry, the description only as the footer of the page the row opens. Replaces the switch and the strictness row by one list (Off, Strictest, Standard, Relaxed) that writes the mode and the level together; `GrabImpactLevelPrefController` is deleted. |
| `packages-apps-Settings/0004` | the row selects nothing in shadow mode, cuts a stray level, asks for the screen lock | Independent review of 2026-10-28 (C1 to C3, `docs/reviews/2026-10-post-2302-review.md` of `freeholdos-foundation`) and the owner's answer the same day (do what is best; ask for the PIN or fingerprint). In shadow mode, the default, the row read Off and the picker ignores a tap on the value it already shows, so Off could not be set; shadow now selects no entry. A level outside 0 to 2 is cut to the nearest one, as `GrabDetectionService.arm` does. `isCredentialConfirmationRequired` is true, as for Auto reboot. |
| `packages-apps-Settings/0005` | the description under the list and the levels in order of protection | The owner (2026-10-29, on `lynx` `2026102303`): the description belongs under the entries, the spacing against the first entry was wrong and the order Off, Strictest, Standard, Relaxed is not logical. The page now uses `addPrefsAfterList` (prior art: `ConnectivityChecksPrefController`) and lists Off, Relaxed, Standard, Strictest; the written values do not change. |

Conflict surface on a new upstream release: the `Settings.Global` block after `AUTO_REBOOT_TIMEOUT`, `ExtSettings` after `AUTO_REBOOT_TIMEOUT`, the `StartSensorNotification` block of `SystemServer`, `KeyguardServiceDelegate.onShowingStateChanged`, the user-lockdown branch of `BouncerMessageInteractor`, and the first `Preference` after `auto_reboot` in `exploit_protection_settings.xml`.

## Apply

On the build server, with no build running and the trees clean:

    cd frameworks/base && git am <path>/physical/grab-detection/patches/frameworks-base/000*.patch
    cd packages/apps/Settings && git am <path>/physical/grab-detection/patches/packages-apps-Settings/000*.patch

## Landed

On the build server 2026-10-15 with `git am`, trees clean and no build running, branch `sovereignos-2026100600`. `git am` produces other hashes than the worktree commits.

| Patch | Landed commit |
| --- | --- |
| `frameworks-base/0001` | `abf783cb1c44` |
| `frameworks-base/0002` | `1f5d7f670e0b` |
| `frameworks-base/0003` | `58bf873d670b` |
| `frameworks-base/0004` | `f11e72195d6a` |
| `frameworks-base/0005` | `c8ddeaa4cbc8` |
| `frameworks-base/0006` | `c5569e8b39d7` |
| `frameworks-base/0007` | `42a79c600313` |
| `frameworks-base/0008` | `2e4c21be56a4` (2026-10-15, on top of the rename commit `e6a271d588f8`, branch `freeholdos-2026100600`) |
| `frameworks-base/0009` | `3b0677c8531a` (2026-10-18, branch `freeholdos-2026100600`, trees clean, no build running; the blobs differ from the worktree because the server tree holds later commits in the same files) |
| `frameworks-base/0010` | `23ee105cd833` (2026-10-26, branch `freeholdos-2026100600`, trees clean, no build running, on `94e9f26848c4`; the blobs of the four touched files equal the worktree's) |
| `frameworks-base/0011` | `a0741cb37cff` (2026-10-30, on `23ee105cd833`, branch `freeholdos-2026100600`, tree clean, no build running; `git apply --check` passed first; the blobs of `GrabDetector.java` (`4c1075f3...`) and `GrabDetectionService.java` (`2598668a...`) equal the disposable worktree's, authored in `.worktrees/grab-pulse`; module-built with `services`, `SystemUI` and `Settings`: see `GRB-024`) |
| `packages-apps-Settings/0001` | `f70f1e9fb78` |
| `packages-apps-Settings/0002` | `d31f2300fb2` (2026-10-26, on `fe618d503111`) |
| `packages-apps-Settings/0003` | `0ea5154a33a` (2026-10-27, on `d31f2300fb2`, branch `freeholdos-2026100600`, tree clean, no build running; `git apply --check` passed first; the blobs of `strings_ext.xml`, `exploit_protection_settings.xml` and `GrabDetectionPrefController.java` equal the worktree's and the other controller is gone; not compiled or built) |
| `packages-apps-Settings/0004` | `290bc735639` (2026-10-28, on `0ea5154a33a`, branch `freeholdos-2026100600`, tree clean, no build running; `git apply --check` passed first; the blob of `GrabDetectionPrefController.java` equals the disposable worktree's, authored in `.worktrees/grab-settings-0004`; not compiled or built) |
| `packages-apps-Settings/0005` | `065a8bb9d72` (2026-10-29, on `fee9d093fdd`, branch `freeholdos-2026100600`, tree clean, no build running; `git apply --check` passed first; the blobs of `GrabDetectionPrefController.java` (`862e4817...`) and `strings_ext.xml` (`fc97bd23...`) equal the disposable worktree's, authored in `.worktrees/grab-settings-order`; module-built 2026-10-29 17:09 with `PasswordsApp`, exit code 0; `GRB-022`) |
| `frameworks-base/0012` | `ebdfdecba16b` (2026-11-04, on `ce630046aa54`, branch `freeholdos-2026100600`, tree clean, no build running; authored in a disposable repository from the touched files; the blobs of the four touched files equal the disposable repository's; `GrabDetectorTest` passes on the live `GrabDetector.java`, and two mutants of the helper are killed) | Not module-built. |

The blob id of each of the 12 touched files (9 in `frameworks/base`, 3 in `packages/apps/Settings`) equals the blob id in the worktree at its tip, before `packages-apps-Settings/0003`; that patch changes three of the Settings files and deletes one.

## Verification

- Host: `test/run.sh` and `test/mutate.py`, 26 of 26 mutants killed (2026-10-15).
- Host, with `0009` (worktree `.worktrees/grab-detection-fwb`, branch `impact-route`, tip `3a56f88`): `all passed`, 35 of 35 mutants killed (2026-10-18). The same test and mutants also pass on `GrabDetector.java` read from the build server after `git am` (2026-10-18). Module build `m -j6 -k services SystemUI Settings` for `lynx-cur-userdebug` with `0009` landed: exit 0, 42:30, no `FAILED:` or `error:` line (`GRB-002`); the patch is in the `lynx` image `2026102101` since 2026-10-21 (`IMG-011`, `IMG-012`), with no device check yet (`GRB-018`).
- Host, with `0010` (worktree `.worktrees/grab-freefall`): `all passed`, 59 of 59 mutants killed (2026-10-26). The real `GrabDetector` was also replayed over the recordings of `GRB-018`/`GRB-019` at 50 Hz in five phases: at level 1, 12 of 12 staged grabs lock (free fall 0), 0 of 15 to 17 set-downs of 3 cm and 0 of 13 hard set-downs lock (free fall 20 to 159 ms); at level 0, every one locks; at level 2 (60 m/s^2), 4 to 6 of the 12 grabs lock through this route (the pull rule decides the others) and no set-down does. The recordings stay on the phone and the workstation.
- Replay: the seven `frameworks-base` patches and the `Settings` patch applied to the baseline reproduce the worktree trees (2026-10-15).
- Not run: a device check (`GRB-003` to `GRB-017`).
- Build: `m -j6 -k services SystemUI Settings` for `lynx-cur-userdebug` passed on the landed trees (2026-10-15, exit 0, no `FAILED:` or `error:` line; `GRB-002`). Module outputs only, no image.
- Fixture `tests/utils/.../BouncerMessageInteractorKosmos.kt` lacked `globalSettings`; `0008` adds it from `Kosmos.fakeGlobalSettings` (same import as `BouncerRepositoryKosmos.kt`). Read against the constructor in the live source and the blob id equals the worktree's; the SystemUI tests were not compiled, so the fix is unproven by a build.
- Depends on the flag `revamped_bouncer_messages` being on in the release configuration of the build; with the legacy bouncer messages the grab text does not show.
- The review dispositions and the rulings taken during execution are in the plan's execution record (not committed), summarised in the session report.
