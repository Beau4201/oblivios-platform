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
(a new repository, branch `freeholdos-2026100600`, commits `bd9ed81` and `9d05bd1`, the latter adds `libs: ["framework-connectivity"]` after the first module build failed without it; `Android.bp`, `AndroidManifest.xml`, the allow-list
and `src/`, without `tests/` and `tools/`). `sha256sum` of the six deployed files equals the package, checked on
2026-10-20.

| Patch | Project and branch | Subject | Why it diverges from upstream |
| --- | --- | --- | --- |
| `vendor-adevtool/0001` | `vendor/adevtool` (`freeholdos-2026100600`, base `b3c9caf46a17`, landed as `fe4c53c7`) | exit: ship ExitProbe on userdebug images via device-common.mk | Adds `PRODUCT_PACKAGES_DEBUG += ExitProbe` to the file that already ships the other FreeholdOS modules to every Pixel target. `PRODUCT_PACKAGES_DEBUG` is installed for userdebug and eng only; `check-required-packages.sh` refuses `ExitProbe.apk` and its allow-list in a `user` build. |

The blob of `device-common.mk` after the patch is `e9b9850` on the build server and in the disposable commit (the base
blob `37fb8c9` equalled the server's before). Landing moves the `vendor/adevtool` pin of `lynx` and `stallion`.

Conflict surface: the last lines of `device-common.mk`.

## Checks

`NET-001a` (current image, test preference) passed; `NET-001b` (production preference on an image with this patch) is `NOT_RUN`. Module build: `build-exit-modules.sh` (`m -j6 -k com.android.tethering`) passed 2026-10-18 (`EXIT_MODULES_EXIT_CODE:0`, 03:39, no `FAILED:` line); `javap -c -p` of the built `service-connectivity-pre-jarjar.jar` shows no call to `enforceAutomotiveDevice`, and the permission checks of `setOemNetworkPreference` unchanged.

## Apply

On the build server, with no build running and the tree clean, after `privacy/linkability` is applied:

    cd packages/modules/Connectivity && git am <path>/privacy/exit/patches/packages-modules-connectivity/0001-*.patch
