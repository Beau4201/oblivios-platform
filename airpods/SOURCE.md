# AirPods — Source Provenance

## AOSP source patches (`patches/`)

Applied to individual AOSP component checkouts, not vendored app code — see
this tree's row below for the exact base commit and patch count. Generated
via `git format-patch` directly from the real post-`git am` branch history
on the build server (not from the disposable worktree's own local commit,
whose SHA differs from what was actually built and verified — see
`docs/research/build-and-signing-engineering-notes.md`'s note on this in
`sovereignos-foundation`), author identity rewritten to the generic
`SovereignOS Build <build@sovereignos.local>` identity before committing
here (same convention as `guardian/SOURCE.md`). Real, dated commit on the
build server's `platform-primary` checkout.

| Tree | Base commit | Patches | Approving ADR |
| --- | --- | --- | --- |
| `packages/modules/Bluetooth` | `469799157dcd901470465b243f0ccc762de40d36` | `patches/packages-modules-bluetooth/0001` | ADR-109 (names this branch/commit literally: `sovereignos-airpods-identity`, `1102e506bdb774bf2a4ccb24d8057e3e370d6290`) |

Apply from the tree's own repository root:

```sh
cd packages/modules/Bluetooth && git am ../../airpods/patches/packages-modules-bluetooth/*.patch
```

### What the patch does

Adds a bonded-device-scoped identity mechanism (design §6.2 of
`docs/superpowers/specs/2026-09-28-airpods-compatibility-and-finding-design.md`
in `sovereignos-foundation`, corrected per ADR-107/ADR-108 before this patch
was authored — the original design named the wrong hook point for the
Device-ID half):

- A new `org.sovereignos.permission.MANAGE_AIRPODS_IDENTITY` `signature|privileged`
  permission (`android/app/AndroidManifest.xml`) and AIDL method
  `IBluetooth#setAirPodsIdentity` (`AdapterServiceBinder` enforces the
  permission at the binder boundary via `enforceCallingOrSelfPermission`,
  the same pattern as every other privileged method in this file).
- `AdapterService#setAirPodsIdentity` refuses to register a device that
  isn't currently bonded (`getBondState(device) != BOND_BONDED`) — a
  defense-in-depth check beyond the caller's own protocol validation.
- Registration forwards to the existing native interop-database mechanism
  (`AdapterNativeInterface#interopDatabaseAddRemoveAddr`, wrapping the
  already-JNI-wired but previously Java-unused
  `interopDatabaseAddRemoveAddrNative`) under a new `INTEROP_AIRPODS_IDENTITY`
  feature (`system/device/include/interop.h`/`interop.cc`) — reusing the
  existing enum/name-table/`btif_config` plumbing end to end, no new native
  storage mechanism.
- Two scoped hook patches consult this registration per-peer, never
  device-wide: `sdp_dynamic_change_airpods_vendor` in
  `system/stack/sdp/sdp_server.cc` (new function, modeled on the existing
  `sdp_dynamic_change_hfp_version` precedent, called from both
  `process_service_attr_req` and `process_service_search_attr_req`)
  overrides the outgoing PNP Information vendor attribute
  (`ATTR_ID_VENDOR_ID`) to Apple's registered Bluetooth SIG company ID
  (`LMP_COMPID_APPLE 0x004C`, added to `system/stack/include/hcidefs.h`)
  only in the SDP response to a registered peer; the `l2c_fcr_chk_chan_modes`
  extension in `system/stack/l2cap/l2c_fcr.cc` (modeled on the existing
  `l2c_should_skip_ertm` precedent) allows the same basic-mode-fallback
  path for a registered peer. The local Device-ID record
  `BTA_DmSetLocalDiRecord` sets is never touched, and no other Bluetooth
  peer or connection is affected.

### Real defect found and fixed before landing

The first patch attempt used an AIDL `@JavaPassthrough`/`RequiresPermission`
annotation embedding the new permission as an escaped string literal
(`\"org.sovereignos.permission.MANAGE_AIRPODS_IDENTITY\"`). This failed a
real preflight build with a genuine AIDL compiler error (`Found invalid
character '\' at index 92`) — confirmed by searching this entire file for
any existing precedent of a literal, non-`android.Manifest.permission`
string in this annotation shape: none exists among 95 other
`RequiresPermission` usages in `IBluetooth.aidl`. Fixed by referencing only
the real `BLUETOOTH_CONNECT` constant in the annotation (which is lint/doc
metadata only, with zero runtime effect) and adding a comment noting the
custom permission is enforced at runtime in `AdapterServiceBinder`, which
was correct and unaffected by this failure. Full detail: ADR-109.

### Build verification

A fully-detached preflight build (`m com.android.bt`,
`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`,
`USE_CCACHE=1`) on the build server, launched only after confirming the
server was idle (`uptime` load `0.00`, no build/emulator processes running).
First attempt failed on the AIDL error above; after the fix, the feature
branch was reset to the pinned baseline and the corrected patch re-applied
cleanly, and the rebuild succeeded: `ninja: 1h13m18.17s Build Succeeded:
15425 steps`, `AIRPODS_PREFLIGHT_BUILD_EXIT_CODE:0`. All warnings in the
build log are pre-existing `frameworks/base` telephony/API-lint warnings,
unrelated to this patch.

**This is compile-time verification only.** `AIR-021` (real-hardware,
two-peer validation — the scoped identity presentation observed only
toward the recognized AirPods BD_ADDR, confirmed on real hardware per
design §12) is **not yet done** and requires the owner's physical AirPods
and a flashed device — cannot be performed by an agent session. Do not
treat this patch as functionally validated until that evidence exists.

Full investigation and authoring trail:
`docs/research/airpods-bluetooth-stack-investigation.md`,
`docs/research/airpods-nonsdk-api-exemption-investigation.md`, and
ADR-107/ADR-108/ADR-109 in `docs/governance/decision-log.md`, all in
`sovereignos-foundation`.
