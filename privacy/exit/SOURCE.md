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
