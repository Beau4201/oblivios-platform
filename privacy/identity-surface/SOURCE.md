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
| `system-sepolicy/0001` | keep apps from reading the unit-specific boot properties | `private/domain.te` lets every domain read every `ro.boot.*` property through `bootloader_prop`. Five of them identify a unit or a batch. The new type is readable by every domain except `appdomain` and `app_zygote` and `native_app_zygote`, which run app code (`AppZygotePreload`) and are not `appdomain` (`public/app_zygote.te`, `private/native_app_zygote.te`); no allow rule is added for an app. `shell` is in `appdomain` (`app_domain(shell)`), so `adb shell getprop` no longer lists the five names. |
| `frameworks-native/0001` | do not deliver the bias of uncalibrated sensors to apps | The bias estimate of the three uncalibrated sensors is stable per device and readable without a permission. It is folded into the readings for every app uid, `uid % AID_USER_OFFSET >= AID_APP_START` (`shouldFoldFor`), which includes isolated processes, SDK sandboxes and the apps of every user; native daemons (cameraserver and the like) and platform uids, also those of a secondary user, keep the event. The new header holds the arithmetic; the changes in `sendEvents` are two copies and one flag. |
| `frameworks-native/0002` | refuse direct sensor channels for the three bias-carrying types | A direct channel makes the HAL write events into shared memory and does not pass `sendEvents`. On the baseline image an ordinary app configured one for each of the three types (`IDS-006`). `configureChannel` returns `INVALID_OPERATION` for them to app uids (`shouldFoldFor`); stopping a channel stays allowed. That the bias is written into such a buffer is an assumption (Java cannot read a HardwareBuffer): the refusal is applied on the evidence that the channel is accepted. |

Conflict surface on a new upstream release: the end of the `ro.boot.` block of `property_contexts`, the `bootloader_prop` line of `domain.te`, the two copies in `sendEvents`, and the check before the rate adjustment in `configureChannel`.

## Apply

On the build server, with no build running and the trees clean:

    cd system/sepolicy && git am <path>/privacy/identity-surface/patches/system-sepolicy/0001-*.patch
    cd frameworks/native && git switch -c sovereignos-2026100600 && git am <path>/privacy/identity-surface/patches/frameworks-native/000*.patch

## Verification

- Host: the test and the mutation check of `test/` (11 of 11 mutants killed, 2026-10-14) and `test/check_sepolicy_patch.py` (the read rule excludes `appdomain`, `app_zygote` and `native_app_zygote`; the five names carry the type; the type is declared once).
- Reader search (source level only): `repo forall -c 'git grep -F'` over every project of the tree on 2026-10-14 for the five names and the prefix `ro.boot.hw.`. The only reader of an exact name is `PersistentDataBlockService` in `system_server` (not an app); the other hits are `ro.boot.vbmeta.device_state`, a different property that stays under the `ro.boot.` prefix. Prebuilt APKs, vendor binaries and names built by concatenation are not covered, so the device check `IDS-005` carries the rest.
- Not run: a module build of `libsensorservice` and `sepolicy`, a device check (`IDS-002` to `IDS-007` in the validation matrix of `sovereignos-foundation`).
