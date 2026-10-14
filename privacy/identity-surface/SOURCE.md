# Source record

## Base

GrapheneOS tag `2026100600` (ADR-166), read on the build server 2026-10-14 with the tree clean:

| Project | Revision | Files read | Blob ids |
| --- | --- | --- | --- |
| `frameworks/native` | `1c83ac6ae533` | `services/sensorservice/SensorEventConnection.cpp`, `services/sensorservice/SensorDirectConnection.cpp` | `05790a04e8f6`, `00daa9b15625` |
| `system/sepolicy` (branch `sovereignos-2026100600`) | `fd1b880f3315` | `private/property.te`, `private/property_contexts`, `private/domain.te` | `384cd3c06174`, `3839fa087dd7`, `4c49bef10fbe` |

Authored in the disposable repositories `.worktrees/identity-surface-native` and `.worktrees/identity-surface-sepolicy`.

## Patches

| Patch | Subject | Why it diverges from upstream |
| --- | --- | --- |
| `system-sepolicy/0001` | keep apps from reading the unit-specific boot properties | `private/domain.te` lets every domain read every `ro.boot.*` property through `bootloader_prop`. Five of them identify a unit or a batch. The new type is readable by every domain except `appdomain`; no allow rule is added for an app. |
| `frameworks-native/0001` | do not deliver the bias of uncalibrated sensors to apps | The bias estimate of the three uncalibrated sensors is stable per device and readable without a permission. It is folded into the readings for every uid but `AID_SYSTEM`. The new header holds the arithmetic; the changes in `sendEvents` are two copies and one flag. |
| `frameworks-native/0002` | refuse direct sensor channels for the three bias-carrying types | A direct channel makes the HAL write events into shared memory and does not pass `sendEvents`. On the baseline image an ordinary app configured one for each of the three types (`IDS-006`). `configureChannel` returns `INVALID_OPERATION` for them to every uid but `AID_SYSTEM`; stopping a channel stays allowed. |

Conflict surface on a new upstream release: the end of the `ro.boot.` block of `property_contexts`, the `bootloader_prop` line of `domain.te`, the two copies in `sendEvents`, and the check before the rate adjustment in `configureChannel`.

## Apply

On the build server, with no build running and the trees clean:

    cd system/sepolicy && git am <path>/privacy/identity-surface/patches/system-sepolicy/0001-*.patch
    cd frameworks/native && git switch -c sovereignos-2026100600 && git am <path>/privacy/identity-surface/patches/frameworks-native/000*.patch

## Verification

- Host: the test and the mutation check of `test/` (7 of 7 mutants killed, 2026-10-14).
- Not run: a module build of `libsensorservice` and `sepolicy`, a device check (`IDS-002` to `IDS-007` in the validation matrix of `sovereignos-foundation`).
