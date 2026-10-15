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
| `packages-apps-Settings/0001` | switch for grab detection | The mode needs an owner-visible control. |

Conflict surface on a new upstream release: the `Settings.Global` block after `AUTO_REBOOT_TIMEOUT`, `ExtSettings` after `AUTO_REBOOT_TIMEOUT`, the `StartSensorNotification` block of `SystemServer`, `KeyguardServiceDelegate.onShowingStateChanged`, the user-lockdown branch of `BouncerMessageInteractor`, and the first `Preference` after `auto_reboot` in `exploit_protection_settings.xml`.

## Apply

On the build server, with no build running and the trees clean:

    cd frameworks/base && git am <path>/physical/grab-detection/patches/frameworks-base/000*.patch
    cd packages/apps/Settings && git am <path>/physical/grab-detection/patches/packages-apps-Settings/0001-*.patch

## Verification

- Host: `test/run.sh` and `test/mutate.py`, 22 of 22 mutants killed (2026-10-15).
- Replay: both patch sets applied to the baseline reproduce the worktree trees (2026-10-15).
- Not run: a build (`GRB-002`), a device check (`GRB-003` to `GRB-014`).
- Not updated and not compiled: the SystemUI test fixtures that construct `BouncerMessageInteractor`; they gain the `globalSettings` argument when the SystemUI tests are built.
