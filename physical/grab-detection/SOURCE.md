# Source record

## Base

GrapheneOS tag `2026100600` (ADR-166), read on the build server 2026-10-15 with the trees clean:

| Project | Revision | Files read |
| --- | --- | --- |
| `frameworks/base` (branch `sovereignos-2026100600`) | `7320e4e97e5d` | the seven files of the plan, Task 1 |
| `packages/apps/Settings` (branch `sovereignos-2026100600`) | `7358c527cb6c` | `res/xml/exploit_protection_settings.xml`, `res/values/strings_ext.xml` |

Authored in the disposable repositories `.worktrees/grab-detection-fwb` and `.worktrees/grab-detection-settings`.

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
| `packages-apps-Settings/0001` | switch for grab detection | The mode needs an owner-visible control. |

Conflict surface on a new upstream release: the `Settings.Global` block after `AUTO_REBOOT_TIMEOUT`, `ExtSettings` after `AUTO_REBOOT_TIMEOUT`, the `StartSensorNotification` block of `SystemServer`, `KeyguardServiceDelegate.onShowingStateChanged`, the user-lockdown branch of `BouncerMessageInteractor`, and the first `Preference` after `auto_reboot` in `exploit_protection_settings.xml`.

## Apply

On the build server, with no build running and the trees clean:

    cd frameworks/base && git am <path>/physical/grab-detection/patches/frameworks-base/000*.patch
    cd packages/apps/Settings && git am <path>/physical/grab-detection/patches/packages-apps-Settings/0001-*.patch

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
| `packages-apps-Settings/0001` | `f70f1e9fb78` |

The blob id of each of the 12 touched files (9 in `frameworks/base`, 3 in `packages/apps/Settings`) equals the blob id in the worktree at its tip.

## Verification

- Host: `test/run.sh` and `test/mutate.py`, 26 of 26 mutants killed (2026-10-15).
- Replay: the seven `frameworks-base` patches and the `Settings` patch applied to the baseline reproduce the worktree trees (2026-10-15).
- Not run: a device check (`GRB-003` to `GRB-017`).
- Build: `m -j6 -k services SystemUI Settings` for `lynx-cur-userdebug` passed on the landed trees (2026-10-15, exit 0, no `FAILED:` or `error:` line; `GRB-002`). Module outputs only, no image.
- Known break: `tests/utils/.../BouncerMessageInteractorKosmos.kt` builds `BouncerMessageInteractor` with named arguments and lacks `globalSettings` (`Kosmos.fakeGlobalSettings` exists). The SystemUI tests do not build until that line is added; the product build does not compile `tests/utils`.
- Depends on the flag `revamped_bouncer_messages` being on in the release configuration of the build; with the legacy bouncer messages the grab text does not show.
- The review dispositions and the rulings taken during execution are in the plan's execution record (not committed), summarised in the session report.
