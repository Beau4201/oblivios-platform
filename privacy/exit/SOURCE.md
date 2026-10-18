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

## Checks

`NET-001a` (current image, test preference) passed; `NET-001b` (production preference on an image with this patch) is `NOT_RUN`. Module build: `build-exit-modules.sh` (`m -j6 -k com.android.tethering`) passed 2026-10-18 (`EXIT_MODULES_EXIT_CODE:0`, 03:39, no `FAILED:` line); `javap -c -p` of the built `service-connectivity-pre-jarjar.jar` shows no call to `enforceAutomotiveDevice`, and the permission checks of `setOemNetworkPreference` unchanged.

## Apply

On the build server, with no build running and the tree clean, after `privacy/linkability` is applied:

    cd packages/modules/Connectivity && git am <path>/privacy/exit/patches/packages-modules-connectivity/0001-*.patch
