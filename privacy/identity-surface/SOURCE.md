# Source record

## Base

GrapheneOS tag `2026100600` (ADR-166), read on the build server 2026-10-14 with the tree clean:

| Project | Revision | Files read | Blob ids |
| --- | --- | --- | --- |
| `frameworks/native` | `1c83ac6ae533` | `services/sensorservice/SensorEventConnection.cpp`, `services/sensorservice/SensorDirectConnection.cpp` | `05790a04e8f6`, `00daa9b15625` |
| `system/sepolicy` (branch `freeholdos-2026100600`) | `fd1b880f3315` | `private/property.te`, `private/property_contexts`, `private/domain.te` | `384cd3c06174`, `3839fa087dd7`, `4c49bef10fbe` |
| `system/sepolicy` (branch `freeholdos-2026100600`, for `0002`) | `e36d25873` | `private/property.te`, `private/property_contexts` | `a054d9d2112a`, `670a3746c3cb` |
| `packages/modules/Wifi` (tag `2026100600`, for `packages-modules-wifi/0001`) | `2d471d6036` | `service/java/com/android/server/wifi/ClientModeImpl.java` | `26d376950c7b` |

Authored in the disposable repositories `.worktrees/identity-surface-native` and `.worktrees/identity-surface-sepolicy`; `system-sepolicy/0002` and `packages-modules-wifi/0001` in `.worktrees/linkability-kills` (2026-10-18).

## Patches

| Patch | Subject | Why it diverges from upstream |
| --- | --- | --- |
| `system-sepolicy/0001` | keep apps from reading the unit-specific boot properties | `private/domain.te` lets every domain read every `ro.boot.*` property through `bootloader_prop`. Five of them identify a unit or a batch. The new type is readable by every domain except `appdomain` and `app_zygote` and `native_app_zygote`, which run app code (`AppZygotePreload`) and are not `appdomain` (`public/app_zygote.te`, `private/native_app_zygote.te`); no allow rule is added for an app. `shell` is in `appdomain` (`app_domain(shell)`), so `adb shell getprop` no longer lists the five names. |
| `frameworks-native/0001` | do not deliver the bias of uncalibrated sensors to apps | The bias estimate of the three uncalibrated sensors is stable per device and readable without a permission. It is folded into the readings for every app uid, `uid % AID_USER_OFFSET >= AID_APP_START` (`shouldFoldFor`), which includes isolated processes, SDK sandboxes and the apps of every user; native daemons (cameraserver and the like) and platform uids, also those of a secondary user, keep the event. The new header holds the arithmetic; the changes in `sendEvents` are two copies and one flag. |
| `frameworks-native/0002` | refuse direct sensor channels for the three bias-carrying types | A direct channel makes the HAL write events into shared memory and does not pass `sendEvents`. On the baseline image an ordinary app configured one for each of the three types (`IDS-006`). `configureChannel` returns `INVALID_OPERATION` for them to app uids (`shouldFoldFor`); stopping a channel stays allowed. That the bias is written into such a buffer is an assumption (Java cannot read a HardwareBuffer): the refusal is applied on the evidence that the channel is accepted. |
| `system-sepolicy/0002` | hide more unit and batch properties from apps | Nine more `ro.boot.*` names (`hardware.ddr`, `hardware.ufs`, `ddr_info`, `primary_panel_drv`, `warranty.sku`, `hw.soc.{rev,pkg_mode,sec-ar,nonsec-ar}`) move to `unit_boot_prop`. `persist.init.svc.last_fatal_reboot_epoch` gets its own type `init_fatal_reboot_epoch_prop` because `init_service_status_private_prop` is readable by `coredomain`, which includes every app; `init` may set any property type (`init.te:717`) and a `neverallow` keeps every other domain from setting it. The reader search of 2026-10-18 found only `perfetto` for the `ro.boot.*` names and `init/service.cpp` for the epoch. |
| `packages-modules-wifi/0001` | do not derive the link-local IPv6 address from the MAC | `ClientModeImpl` asked for EUI-64 link-local addresses when MAC randomisation is on, which embeds the random MAC. `LinkProperties` hands that address to every app with `ACCESS_NETWORK_STATE` while `WifiInfo` redacts the MAC. The call is removed, so the default of `ProvisioningConfiguration` (stable privacy) applies. P2P and Aware still ask for random-MAC EUI-64 and are not reachable as the default network. |

Conflict surface on a new upstream release: the end of the `ro.boot.` block of `property_contexts`, the line of `persist.init.svc.last_fatal_reboot_epoch`, the `system_internal_prop` list and the `init_service_status_private_prop` `neverallow` of `property.te`, the `isUsingMacRandomization` block of `ClientModeImpl.buildProvisioningConfiguration`, the `bootloader_prop` line of `domain.te`, the two copies in `sendEvents`, and the check before the rate adjustment in `configureChannel`.

## Landing

Landed with `git am` on 2026-10-14 on the build server with no build running and clean trees: `system/sepolicy` as `c7988b4c4` on `freeholdos-2026100600`, `frameworks/native` as `21fa95f288` and `bb680da774` on a new branch `freeholdos-2026100600` from `1c83ac6ae5`. The patch files here are the ones that were applied (SHA-256 equal on both sides). Blob ids on the server equal those of the authoring repositories: `SensorEventConnection.cpp` `451ac60261b2`, `SensorDirectConnection.cpp` `5e1fc1ae38d0`, `SensorBiasFold.h` `b0474dfce7a6`, `property.te` `c0617ab35a22`, `property_contexts` `74df868c85c4`, `domain.te` `9a3d9a52ceef`.

Landed with `git am` on 2026-10-18, no build running, clean trees: `system/sepolicy` as `fa4586a64` on `freeholdos-2026100600` (patch `0002`), `packages/modules/Wifi` as `f3d7430e8d` on a new branch `freeholdos-2026100600` from tag `2026100600` (`2d471d6036`). The patch files here were regenerated with `git format-patch` from the landed commits and equal the authored ones except for abbreviated blob ids and the git version line. Blob ids on the server equal those of the authoring repositories: `property_contexts` `046bd635cc82`, `property.te` `a38966397c5e`, `ClientModeImpl.java` `71a439775b81`.

## Apply

On the build server, with no build running and the trees clean:

    cd system/sepolicy && git am <path>/privacy/identity-surface/patches/system-sepolicy/0001-*.patch
    cd frameworks/native && git switch -c freeholdos-2026100600 && git am <path>/privacy/identity-surface/patches/frameworks-native/000*.patch
    cd system/sepolicy && git am <path>/privacy/identity-surface/patches/system-sepolicy/0002-*.patch
    cd packages/modules/Wifi && git switch -c freeholdos-2026100600 && git am <path>/privacy/identity-surface/patches/packages-modules-wifi/0001-*.patch

## Verification

- Host: the test and the mutation check of `test/` (11 of 11 mutants killed, 2026-10-14) and `test/check_sepolicy_patch.py` (the read rule excludes `appdomain`, `app_zygote` and `native_app_zygote`; the five names carry the type; the type is declared once).
- Reader search (source level only): `repo forall -c 'git grep -F'` over every project of the tree on 2026-10-14 for the five names and the prefix `ro.boot.hw.`. The only reader of an exact name is `PersistentDataBlockService` in `system_server` (not an app); the other hits are `ro.boot.vbmeta.device_state`, a different property that stays under the `ro.boot.` prefix. Prebuilt APKs, vendor binaries and names built by concatenation are not covered, so the device check `IDS-005` carries the rest.
- Module build, 2026-10-14: `m -j6 -k libsensorservice sensorservice selinux_policy` for `lynx-cur-userdebug` on `out-lynx` completed successfully (11:02), no `FAILED` or `error:` line; `SensorEventConnection.o` and `SensorDirectConnection.o` were recompiled and `libsensorservice.so` relinked. The compiled `plat_sepolicy.cil` declares `unit_boot_prop`, has exactly one allow rule for it, `(allow base_typeattr_432 unit_boot_prop (file (read getattr map open)))` with `base_typeattr_432` = `domain` minus `appdomain`, `app_zygote` and `native_app_zygote`, and the macro's `neverallow ... property_service set`; `plat_property_contexts` has the five labels. The `selinux_policy` neverallow and property checks passed. Script: `tools/build/build-identity-surface-modules.sh` in `freeholdos-foundation`.
- Not run: an image, a device check (`IDS-002` to `IDS-007` in the validation matrix of `freeholdos-foundation`).
- Module build, 2026-10-18 (`system-sepolicy/0002`, `packages-modules-wifi/0001`): `m -j6 -k selinux_policy service-wifi` for `lynx-cur-userdebug` on `out-lynx` with `OFFICIAL_BUILD=true` completed with exit 0 in 11 minutes and no `FAILED` line. The compiled `plat_sepolicy.cil` declares `init_fatal_reboot_epoch_prop` with its `neverallow` rules (no domain but `init` may set it, no domain may read or write its files), `plat_property_contexts` carries the epoch label and the nine `unit_boot_prop` labels, and the `service-wifi` jars were rebuilt. Script: `tools/build/build-linkability-modules.sh` in `freeholdos-foundation`. Reader search (source level): `repo forall -c 'git grep'` over every project on 2026-10-18; prebuilt APKs, vendor binaries and names built by concatenation are not covered, so the device check `LNK-003` to `LNK-005` carries the rest.
