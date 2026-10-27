# Source record: network exit (ADR-178)

Design: `docs/superpowers/specs/2026-10-18-network-exit-design.md` in `freeholdos-foundation`. The patch was authored 2026-10-18 in the disposable repository `.worktrees/exit-oem-pref` from a read-only copy of the file and landed on the build server with `git am` (branch `freeholdos-2026100600` in `packages/modules/Connectivity`, commit `35b54c20e0`).

## Base

| Project | Revision | File | Blob ids (before, after) |
| --- | --- | --- | --- |
| `packages/modules/Connectivity` (branch `freeholdos-2026100600`, after `privacy/linkability` `packages-modules-connectivity/0001`) | `ffb9bb27dd` | `service/src/com/android/server/ConnectivityService.java` | see `git show 35b54c20e0` on the server |

## Patches

| Patch | Subject | Why it diverges from upstream |
| --- | --- | --- |
| `packages-modules-connectivity/0001` | Connectivity: allow the OEM network preference on phones | Upstream restricts the production values of `OemNetworkPreferences` to automotive devices (`enforceAutomotiveDevice()`), so the per-uid default-network selection cannot be used on a phone. The design routes each app to the Tor network or to the normal default network through that selection. The gate is removed; `enforceOemNetworkPreferencesPermission()` and the validation stay, so only a component with `CONTROL_OEM_PAID_NETWORK_PREFERENCE` can set it. `enforceAutomotiveDevice()` stays defined and unused. |

Conflict surface on a new upstream release: `setOemNetworkPreference` (the `else` branch of the test-preference check).

## Probe app and image wiring (NET-001b, ADR-184)

The app is new code, not a patch: `probe-app/` is deployed to `vendor/freeholdos/exit/probe-app/` on the build server
(a new repository, branch `freeholdos-2026100600`; commits `bd9ed81`, `9d05bd1` and `dddefdf`; the last two fix the first module build, which failed with 46 `cannot find symbol` errors because `module_current` alone does not put `android.net.*` on the class path, and then because Soong refuses a direct dependency on the `java_sdk_library`: `libs` names `framework-connectivity.stubs.module_lib`; `Android.bp`, `AndroidManifest.xml`, the allow-list
and `src/`, without `tests/` and `tools/`). `sha256sum` of the six deployed files equals the package, checked on
2026-10-20 (the first commit; `Android.bp` of the last one equals the package, `sha256sum` checked 2026-10-21).

| Patch | Project and branch | Subject | Why it diverges from upstream |
| --- | --- | --- | --- |
| `vendor-adevtool/0001` | `vendor/adevtool` (`freeholdos-2026100600`, base `b3c9caf46a17`, landed as `fe4c53c7`) | exit: ship ExitProbe on userdebug images via device-common.mk | Adds `PRODUCT_PACKAGES_DEBUG += ExitProbe` to the file that already ships the other FreeholdOS modules to every Pixel target. `PRODUCT_PACKAGES_DEBUG` is installed for userdebug and eng only; `check-required-packages.sh` refuses `ExitProbe.apk` and its allow-list in a `user` build. |

The blob of `device-common.mk` after the patch is `e9b9850` on the build server and in the disposable commit (the base
blob `37fb8c9` equalled the server's before). Landing moves the `vendor/adevtool` pin of `lynx` and `stallion`.

Conflict surface: the last lines of `device-common.mk`.

## Checks

`ExitProbe` module build 2026-10-21 (`build-exit-probe-module.sh`, `m -j6 -k ExitProbe`, `OFFICIAL_BUILD=true`): `build completed successfully (07:42 (mm:ss))`, `EXIT_PROBE_MODULE_EXIT_CODE:0`; `unzip -l` of `out-lynx/target/product/lynx/system/priv-app/ExitProbe/ExitProbe.apk` (24799 bytes) shows `classes.dex` and `AndroidManifest.xml`, and `aapt2 dump permissions` lists the seven declared permissions (`NETWORK_FACTORY`, `CONTROL_OEM_PAID_NETWORK_PREFERENCE`, `MANAGE_TEST_NETWORKS`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`); `privapp-permissions-org.freeholdos.exitprobe.xml` is in `system/etc/permissions`. The pins of `lynx` and `stallion` name `vendor/adevtool` `fe4c53c7` (the previous files are kept as `adevtool-version-check.mk.pre-exit-probe`).

`NET-001a` (current image, test preference) passed; `NET-001b` (production preference on an image with this patch) passed on `lynx` image `2026102101` on 2026-10-21 with `transport wifi` (a `TRANSPORT_TEST` network cannot carry the production preference); the evidence is the `NET-001b` row of the validation matrix in `freeholdos-foundation`. Module build: `build-exit-modules.sh` (`m -j6 -k com.android.tethering`) passed 2026-10-18 (`EXIT_MODULES_EXIT_CODE:0`, 03:39, no `FAILED:` line); `javap -c -p` of the built `service-connectivity-pre-jarjar.jar` shows no call to `enforceAutomotiveDevice`, and the permission checks of `setOemNetworkPreference` unchanged.

## Apply

On the build server, with no build running and the tree clean, after `privacy/linkability` is applied:

    cd packages/modules/Connectivity && git am <path>/privacy/exit/patches/packages-modules-connectivity/0001-*.patch

## Exit service (ADR-184, ADR-186)

The daemon is built from `freeholdos-services/exit-daemon` (`daemon/SOURCE.md` holds the commit, the toolchain and the hashes); the app and the
policy are new code and patches. They were authored in disposable repositories (`.worktrees/exit-sepolicy`,
`.worktrees/exit-adevtool2`) from read-only copies of the touched files and landed on the build server with `git am` on
2026-10-21; `vendor/freeholdos/exit` got `daemon/` and `app/` in one commit.

| Project | Base | Landed as | Subject |
| --- | --- | --- | --- |
| `system/sepolicy` (`freeholdos-2026100600`) | `9344ac271` | `4d297ef48` | sepolicy: add the domains of the Compartments exit service (`private/exitd.te`, `exitd_route.te`, `exit_app.te`, contexts; the blobs of the three context files equalled the server's before) |
| `system/sepolicy` | `4d297ef48` | `38f19ca1f` | sepolicy: make the neverallows of the exit service hold in the combined policy |
| `vendor/adevtool` (`freeholdos-2026100600`) | `fe4c53c7` (blob of `device-common.mk` `e9b98507`) | `e4aca756` (blob `fe456150`) | exit: ship the exit service on every Pixel target (`PRODUCT_PACKAGES += exitd ExitApp`) |
| `vendor/freeholdos/exit` (`freeholdos-2026100600`) | `cb444ef` | `569e0c8` | the daemon prebuilt, its init definition and mask list, and the exit app |
| `vendor/freeholdos/exit` | `569e0c8` | `06be330` | the speed guard, the never-hidden stand, the fixes of the review of 2026-10-22 and the `exitd` of `daemon/SOURCE.md` (2026-10-23: the files of this package were copied with `rsync -c` and compared again, 0 differences; the tree had held the previous package uncommitted and is clean now). Not module-built since. |
| `vendor/freeholdos/exit` | `06be330` | `29dd125` | the `exitd` of `daemon/SOURCE.md` (`eee939b2...`, 19 374 568 bytes) replaces `0ce3c5a4...`; landed 2026-10-23 on the owner's word, tree clean, no build running; a dry-run `rsync -c` of the 42 tracked files showed this one file differing, the hash on the server equals `SOURCE.md`, and the tree is clean after the commit. Module-built the same day (`NET-010`, `tools/build-vm/build-exit-service-modules.sh`, exit code 0, 9 minutes 17 seconds): the installed `exitd` and mask list equal the hashes of `daemon/SOURCE.md`, `check-sepolicy.py` passes on the fresh `vendor/etc/selinux/precompiled_sepolicy`, and `check-neverallows.sh` (now also for the Data Map service) holds. |
| `vendor/freeholdos/exit` | `29dd125` | `42db4f4` | the five files of the exit app changed by the second review of Compartments (`ExitController`, `Notifier`, `Policy`, `Presenter`, `UidRanges`; platform `56eb402`), landed 2026-10-23 with the image of `lynx` `2026102301` in mind; the dry-run `rsync -c` of the 42 tracked files showed these five files only, the tree is clean after the commit. Module-built the same day (`build-passwords-modules.sh`, exit code 0). |
| `system/sepolicy` | `4ecc94a1b` | `d8e045d2f` | `system-sepolicy/0003`: `init` may `listen` on the two service domains, and the two services may `write` to `kmsg_debug_device` (userdebug and eng). Authored 2026-10-24 in the disposable repository `.worktrees/exit-fixes-2301` from read-only copies of `private/exitd.te` and `private/exitd_route.te` (blobs `fe66f797` and `5c0b930d` equal the server's); `git apply --check` passed, landed with `git am`, the tree was clean and no build ran; the blobs after are `7335159b` and `e0ed035d`. Not built. Why: `NET-009` on image `2026102301` showed `init` denied `listen` on both sockets and the route helper denied the write to `/dev/kmsg_debug`. |
| `vendor/freeholdos/exit` | `42db4f4` | `819e626` | `ExitRuntime.java` (the user-event receiver is registered for all users, `IMG-014`) and `daemon/exit.rc` (no `memcg.limit_in_bytes`: `init` refuses a memory control without `ro.config.per_app_memcg`, `NET-009`); platform commit of the same files; both files equal the package by `sha256sum` (`95a8519b`, `9c48912b`) and were equal to the previous package commit on the server before; the tree is clean after the commit. Not built. |
| `vendor/freeholdos/exit` | `819e626` | `98da765` | `daemon/exit.rc`: `exitd` runs with the group `inet` (3003) as well as its own. Authored in this package and landed 2026-10-27 with a copy; the server tree was clean at `819e626`, no build ran, the file differed from the package in these three lines only, and the `sha256` of `daemon/exit.rc` is `3031e9f2...` on both sides. Not built. Why: on `lynx` image `2026102302` the self-check of `exitd` ended with `Os { code: 13 }` at `bind` on 127.0.0.1, no SELinux denial; a debug build run as uid and gid 2961 failed without a supplementary group and passed with only 3003 (`NET-009`). |
| `system/sepolicy` | `d8e045d2f` | `5c67042e2` | `system-sepolicy/0004`: the sockets `exitd_socket` and `exitd_route_socket` are `mlstrustedobject` and the domains `exitd` and `exitd_route` are `mlstrustedsubject`. Authored 2026-10-27 in the disposable repository `.worktrees/exit-mls-trust` from read-only copies of `private/exitd.te` and `private/exitd_route.te` (blobs `7335159b` and `e0ed035d` equal the server's); `git apply --check` passed, landed with `git am`, the tree was clean and no build ran; the blobs after are `c820f819` and `61fff3b9`, equal to the worktree's. The patch file carries the build author, the server commit the committer's. Not built, so the policy is not compiled and `check-sepolicy.py` has not run on it. Why: on `lynx` image `2026102302` `exit_app` (`s0:c202,c256,c512,c768`, `levelFrom=all`) was denied `write` on `exitd_route_socket` (`s0`) by the MLS constraint on `sock_file` write (`plat_sepolicy.cil`, equal levels or a trusted subject or object) and `connectto` is constrained the same way on the target domain. `levelFrom=none` was not chosen: with `exit_app` at `s0` every other app dominates its level, so `open` and `search` on its data (`dom l1 l2`) would pass MLS and only DAC would be left, for other users too; that is a regression of upstream MCS isolation. Conflict surface: the two `typeattribute` lines in each file. |

Landing the adevtool patch moved its pin: `vendor/google_devices/{lynx,stallion}/adevtool-version-check.mk` name `e4aca7568bc7…`
now (the previous files are kept as `adevtool-version-check.mk.pre-exit-service`).

The Settings entry: the category key of "Network &amp; internet" is `com.android.settings.category.ia.wireless`
`[verified-in-source: frameworks/base/packages/SettingsLib/src/com/android/settingslib/drawer/CategoryKey.java:27]`, not the
`ia.network` that the plan named; the meta-data names are those of `TileUtils.java` (`com.android.settings.title`, `.summary`,
`.icon`, `.order`) and the action is `com.android.settings.action.IA_SETTINGS`.

Conflict surface on a new upstream release: the end of `private/seapp_contexts`, `private/file_contexts` and
`contexts/file_contexts_test_data` (one or two lines each); the last lines of `device-common.mk`.
