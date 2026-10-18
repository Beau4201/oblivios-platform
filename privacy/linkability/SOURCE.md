# Source record: linkability closures (ADR-173, batches 3 to 6)

Design: `docs/superpowers/specs/2026-10-18-linkability-kills-design.md` and plan `docs/superpowers/plans/2026-10-18-linkability-batches-3-6.md` in `freeholdos-foundation`. Authored 2026-10-18 in the disposable repositories under `.worktrees/linkability-2`; nothing is landed on the build server and nothing is built.

## Base

Files read read-only from the build server on 2026-10-18, trees clean:

| Project | Revision | Files | Blob ids (before, after) |
| --- | --- | --- | --- |
| `packages/modules/Connectivity` (detached at the tag) | `6ab5be47d4` | `bpf/progs/netd.c`, `service/src/com/android/server/BpfNetMaps.java` | `de72e5403190` to `03125ea0316b`; `426a356526bb` to `2ac530ebc250` |
| `frameworks/base` (branch `freeholdos-2026100600`) | `e9f0c1e67143` | `core/res/AndroidManifest.xml`, `core/res/res/values/config_ext.xml`, `core/java/android/os/VibratorInfo.java`, the clipboard service and access classes, `VibratorManagerService.java`, `SettingsProvider.java` and the new `SettingsHardening.java` | `0401d67967bb` to `5a88775335b3`; `bd323af7a017` to `f4455547f3be`; `8e10dd750652` to `acf7bd8a8929`; `e98788442b79` to `0d5353fa32e2` and `f89f3b16d97d` to `1eec110f5d98`; `3ac6a03f6a77` to `caf32300391f`; `1fbd8bccb698` to `07aa020df254`; new file `c848deda3ff7` |
| `system/sepolicy` (branch `freeholdos-2026100600`, after `privacy/identity-surface` `0002`) | `fa4586a64` | `private/property.te`, `private/property_contexts`, `private/domain.te` | `a38966397c5e` to `279ee5887df0`; `046bd635cc82` to `6da70c6697bb`; `a44c6df6f433` to `ee2d5a3dd043` |

## Patches

| Patch | Subject | Why it diverges from upstream |
| --- | --- | --- |
| `packages-modules-connectivity/0001` | bpf: refuse loopback traffic between two apps of one user | The loopback check judged only traffic between profiles, so two apps of one user could link themselves through the loopback interface without any permission. Two different app ids of one user now pass only if either side holds `USE_LOOPBACK_INTERFACE`; system uids and `FORCE_USE_LOOPBACK_INTERFACE` pass as before. The global setting `freeholdos_loopback_open` = 1 switches the check off, read once when `BpfNetMaps` initialises. |
| `frameworks-base/0001` | Permissions: make `USE_LOOPBACK_INTERFACE` a runtime permission | A normal permission is granted at install, so two colluding apps both declare it. As a dangerous permission in the undefined group (the pattern of `ACCESS_LOCAL_NETWORK`) the user grants it per app. Only an app that declares the permission can be granted it; for any other app the global setting is the only way out until the policy store exists. |
| `frameworks-base/0002` | Clipboard: read only through a paste, and answer a denied caller the same for every clip | The default of the existing per-app read switch becomes off. A denied caller gets a fixed description, constant `hasPrimaryClip` and `hasClipboardText`, and no change notification; each of the label, time, MIME types, presence and the moment of a change was a way for the app that set a clip to pass a message. |
| `frameworks-base/0003`, `0005` | `SettingsProvider`: an ordinary app reads only the settings it needs | 262 settings are readable by every app and are together a fingerprint, with `boot_count` and `device_name` among them. An app that is not a system app, platform-signed or test-only sees 15 keys and every other key reads as unset. `freeholdos_settings_open` = 1 switches it off; `freeholdos_settings_real_packages` names packages that read the real values. |
| `frameworks-base/0004` | Vibrator: give apps a rounded calibration of the motor | The resonant frequency, the Q factor and the acceleration curve are measured per unit. An app gets 10 Hz and 0.5 steps and no curve; the system gets the measured values. The curve-less `FrequencyProfile` logs `Invalid frequency profile received from HAL` once per app call (the constructor treats missing arrays as invalid); the resonant frequency is kept. |
| `system-sepolicy/0001` | hide the device colour and the country of origin from third-party apps | `ro.boot.hardware.color` and `.coo` get the type `batch_trait_prop`, readable by every domain except the apps and the app zygotes, and by `platform_app` (SystemUI), `priv_app` (launcher) and `system_app` (Settings). Apply after `privacy/identity-surface` `system-sepolicy/0002`. |

Conflict surface on a new upstream release: the `same_profile` branch of `uids_have_loopback_permissions`; the `USE_LOOPBACK_INTERFACE` block of `core/res/AndroidManifest.xml`; `setting_default_allow_clipboard_read`; `getPrimaryClipDescriptionLocked`, `hasClipboardTextLocked` and `sendClipChangedBroadcast`; the getters and bulk loops of `SettingsProvider`; `getVibratorInfo`; the `unit_boot_prop` lines of `domain.te` and `property.te`.

## Apply

On the build server, with no build running and the trees clean, after `privacy/identity-surface` is applied:

    cd packages/modules/Connectivity && git switch -c freeholdos-2026100600 && git am <path>/privacy/linkability/patches/packages-modules-connectivity/0001-*.patch
    cd frameworks/base && git am <path>/privacy/linkability/patches/frameworks-base/000*.patch
    cd system/sepolicy && git am <path>/privacy/linkability/patches/system-sepolicy/0001-*.patch

## Verification so far (host only)

- `test/run-settings-test.sh` and `test/mutate-settings.py` against `SettingsHardening.java`: `ok`, and 10 of 10 mutants killed.
- Type check of the Java files against the classes of the `out-lynx` build with `javac -XDshould-stop.ifError=FLOW` (the class path lacks some generated classes, so the baseline has errors too): the errors of the patched files equal the errors of the baseline files, and in each file a deliberately broken symbol is reported (controls: `VibratorInfo`, `ClipboardAccess`, `VibratorManagerService`, `SettingsProvider`, `BpfNetMaps`).
- `netd.c` compiled with the clang of the tree and the flags of the Soong rule (`--target=bpf -O2 -Wall -Werror -Wextra`), plain and with `-DBPF_USE_FUNCS` (the `netd@funcs.o` module, a symlink to the same file): exit 0 for baseline and patched, exit 1 for a mutant.
- `AndroidManifest.xml` and `config_ext.xml` parse as XML.
- Not run: the sepolicy compile, the module build, the BPF load on a kernel, and every device behaviour.
