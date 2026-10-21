# Source record: linkability closures (ADR-174 and ADR-175, batches 3 to 6)

Design: `docs/superpowers/specs/2026-10-18-linkability-kills-design.md` in `freeholdos-foundation`. Patches 0001 to 0005 of `frameworks-base`, `packages-modules-connectivity/0001` and `system-sepolicy/0001` were authored 2026-10-18 in the disposable repositories under `.worktrees/linkability-2`; `frameworks-base/0006`, `0007` and `packages-providers-mediaprovider/0001` after the whole-branch review, under `.worktrees/linkability-3` and `linkability-3-mp`. All ten are landed on the build server (branch `freeholdos-2026100600` in each project) and module-built (`LNK-011`); no image carries them.

## Base

Files read read-only from the build server on 2026-10-18, trees clean:

| Project | Revision | Files | Blob ids (before, after) |
| --- | --- | --- | --- |
| `packages/modules/Connectivity` (detached at the tag) | `6ab5be47d4` | `bpf/progs/netd.c`, `service/src/com/android/server/BpfNetMaps.java` | `de72e5403190` to `03125ea0316b`; `426a356526bb` to `2ac530ebc250` |
| `frameworks/base` (branch `freeholdos-2026100600`) | `e9f0c1e67143` | `core/res/AndroidManifest.xml`, `core/res/res/values/config_ext.xml`, `core/java/android/os/VibratorInfo.java`, the clipboard service and access classes, `VibratorManagerService.java`, `SettingsProvider.java` and the new `SettingsHardening.java` | `0401d67967bb` to `5a88775335b3`; `bd323af7a017` to `f4455547f3be`; `8e10dd750652` to `acf7bd8a8929`; `e98788442b79` to `0d5353fa32e2` and `f89f3b16d97d` to `1eec110f5d98`; `3ac6a03f6a77` to `caf32300391f`; `1fbd8bccb698` to `07aa020df254`; new file `c848deda3ff7` |
| `system/sepolicy` (branch `freeholdos-2026100600`, after `privacy/identity-surface` `0002`) | `fa4586a64` | `private/property.te`, `private/property_contexts`, `private/domain.te` | `a38966397c5e` to `279ee5887df0`; `046bd635cc82` to `6da70c6697bb`; `a44c6df6f433` to `ee2d5a3dd043` |
| `frameworks/base`, review fixes (after the five commits above, `f6e5f89482b9`) | `f6e5f89482b9` | `SettingsHardening.java`, `ClipboardService.java` | `c848deda3ff7` to `66a2d00650cc`; `1eec110f5d98` to `b35778abb95d` |
| `packages/providers/MediaProvider` (detached at the tag, new branch) | `db0779586e` | `src/com/android/providers/media/MediaProvider.java` | `f259f9c572ce` to `24f5db481012` |

## Patches

| Patch | Subject | Why it diverges from upstream |
| --- | --- | --- |
| `packages-modules-connectivity/0001` | bpf: refuse loopback traffic between two apps of one user | The loopback check judged only traffic between profiles, so two apps of one user could link themselves through the loopback interface without any permission. Two different app ids of one user now pass only if either side holds `USE_LOOPBACK_INTERFACE`; system uids and `FORCE_USE_LOOPBACK_INTERFACE` pass as before. The global setting `freeholdos_loopback_open` = 1 switches the check off, read once when `BpfNetMaps` initialises. |
| `frameworks-base/0001` | Permissions: make `USE_LOOPBACK_INTERFACE` a runtime permission | A normal permission is granted at install, so two colluding apps both declare it. As a dangerous permission in the undefined group (the pattern of `ACCESS_LOCAL_NETWORK`) the user grants it per app. Only an app that declares the permission can be granted it; for any other app the global setting is the only way out until the policy store exists. |
| `frameworks-base/0002` | Clipboard: read only through a paste, and answer a denied caller the same for every clip | The default of the existing per-app read switch becomes off. A denied caller gets a fixed description, constant `hasPrimaryClip` and `hasClipboardText`, and no change notification; each of the label, time, MIME types, presence and the moment of a change was a way for the app that set a clip to pass a message. |
| `frameworks-base/0003`, `0005` | `SettingsProvider`: an ordinary app reads only the settings it needs | 262 settings are readable by every app and are together a fingerprint, with `boot_count` and `device_name` among them. An app that is not a system app, platform-signed or test-only sees 15 keys and every other key reads as unset. `freeholdos_settings_open` = 1 switches it off; `freeholdos_settings_real_packages` names packages that read the real values. |
| `frameworks-base/0004` | Vibrator: give apps a rounded calibration of the motor | The resonant frequency, the Q factor and the acceleration curve are measured per unit. An app gets 10 Hz and 0.5 steps and no curve; the system gets the measured values. The curve-less `FrequencyProfile` logs `Invalid frequency profile received from HAL` once per app call (the constructor treats missing arrays as invalid); the resonant frequency is kept. |
| `system-sepolicy/0001` | hide the device colour and the country of origin from third-party apps | `ro.boot.hardware.color` and `.coo` get the type `batch_trait_prop`, readable by every domain except the apps and the app zygotes, and by `platform_app` (SystemUI), `priv_app` (launcher) and `system_app` (Settings). Apply after `privacy/identity-surface` `system-sepolicy/0002`. |
| `frameworks-base/0006` | `SettingsProvider`: let apps read the settings the framework reads in-process | The first reader search missed `device_provisioned`, `user_setup_complete`, the speech engine keys, typing aids and caption style keys that the framework reads in the app process; hidden, every app lost Share and the smart-selection actions and ignored the speech engine of the user. 25 keys added (40 in all, ADR-175). |
| `frameworks-base/0007` | Clipboard: let the owner and a paste-grant holder hear a clip change | The listener condition of `0002` used the persistent read switch alone, off by default, so the app that set a clip and an app with a paste grant stopped hearing changes. A listener now hears a change unless `getPayloadReadAccessLocked` is `DENIED`. |
| `packages-modules-connectivity/0002` | bpf: refuse broadcast and multicast datagrams between apps on the loopback path | The lookup of the receiving socket finds one socket; a broadcast or multicast datagram reaches every matching socket, so the check let two apps talk (measured on `lynx` `2026101802`, `LNK-013`). A non-unicast destination on the loopback interface is judged by the sender alone, and an ingress rule drops a looped-back (`PACKET_LOOPBACK`) datagram for an app without a loopback permission. Base `35b54c20e0`. |
| `frameworks-base/0008` | Vibrator: round the calibration for every app uid | `UserHandle.isApp` covers app ids 10000 to 19999 only; SDK-sandbox and isolated uids read the measured calibration. Base `3b0677c8531a`. |
| `frameworks-base/0009` | Vibrator: keep a valid frequency profile in the rounded calibration | The profile built without a curve was invalid: error log per call, `getFrequencyProfile()` null, envelope effects reported unsupported. The usable range stays (10 Hz steps), the curve becomes flat at the maximum acceleration to 0.5 G. `VibratorCoarse` holds the rounding for the host test. |
| `frameworks-base/0010` | SettingsProvider: let an app read accessibility_enabled and its own entry of the list settings | `ConfirmationPrompt` read `accessibility_enabled` in the app process and went ahead when it was hidden; apps and libraries read the list settings to see whether they were enabled. |
| `packages-providers-mediaprovider/0001` | MediaProvider: give every app the hashed MediaStore version | The lockdown that replaces the raw database uuid by a per-app hash was a compat change enabled from API 36, so an app that targets an older API read a device-wide identifier. It is on for every caller. |

Conflict surface on a new upstream release: `shouldLockdownMediaStoreVersion` and the `sendClipChangedBroadcast` listener condition (clipboard); the `same_profile` branch of `uids_have_loopback_permissions`; the `USE_LOOPBACK_INTERFACE` block of `core/res/AndroidManifest.xml`; `setting_default_allow_clipboard_read`; `getPrimaryClipDescriptionLocked`, `hasClipboardTextLocked` and `sendClipChangedBroadcast`; the getters and bulk loops of `SettingsProvider`; `getVibratorInfo`; the `unit_boot_prop` lines of `domain.te` and `property.te`.

## Apply

On the build server, with no build running and the trees clean, after `privacy/identity-surface` is applied:

    cd packages/modules/Connectivity && git switch -c freeholdos-2026100600 && git am <path>/privacy/linkability/patches/packages-modules-connectivity/0001-*.patch
    cd frameworks/base && git am <path>/privacy/linkability/patches/frameworks-base/000*.patch
    cd packages/providers/MediaProvider && git switch -c freeholdos-2026100600 && git am <path>/privacy/linkability/patches/packages-providers-mediaprovider/0001-*.patch
    cd system/sepolicy && git am <path>/privacy/linkability/patches/system-sepolicy/0001-*.patch

## Verification

- `test/run-settings-test.sh` and `test/mutate-settings.py` against `SettingsHardening.java`: `ok`, and 15 of 15 mutants killed.
- Type check of the Java files against the classes of the `out-lynx` build with `javac -XDshould-stop.ifError=FLOW` (the class path lacks some generated classes, so the baseline has errors too): the errors of the patched files equal the errors of the baseline files, and in each file a deliberately broken symbol is reported (controls: `VibratorInfo`, `ClipboardAccess`, `VibratorManagerService`, `SettingsProvider`, `BpfNetMaps`).
- `netd.c` compiled with the clang of the tree and the flags of the Soong rule (`--target=bpf -O2 -Wall -Werror -Wextra`), plain and with `-DBPF_USE_FUNCS` (the `netd@funcs.o` module, a symlink to the same file): exit 0 for baseline and patched, exit 1 for a mutant.
- `AndroidManifest.xml` and `config_ext.xml` parse as XML.
- Done after these host checks: the sepolicy compile and the module build (`LNK-011`), the image `2026101802` (`IMG-009`) and its device rows (`LNK-006` to `LNK-012`, `IMG-010`); the four patches of ADR-179 are in the image `2026102101` (`IMG-011`, `IMG-012`), and their device rows (`LNK-013`, `LNK-014`) are `NOT_RUN`; the matrix says what was not shown.
