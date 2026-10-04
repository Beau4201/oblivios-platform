# AirPods — Source Provenance

## airpods-core.jar

- Source: `sovereignos-services` commit `1f46416` (`master`, "airpods: decode the device information, encode the adaptive strength, press and hold and rename frames"). Re-vendored 2026-10-04 with `DeviceInformation` and the three new encoders.
- Build command: `./gradlew :airpods-core:clean :airpods-core:test :airpods-core:jar --offline`
  (Java 21 toolchain, zero runtime dependencies: `airpods-core/build.gradle.kts`
  lists only test dependencies). Output `airpods-core-0.1.0-SNAPSHOT.jar`,
  vendored under the name `airpods-core.jar`.
- Tests at that commit: 49 tests, 0 failures (`AapCodecPlan7bTest` added: frames, bounds and a fuzz of the device information decoder); the new code was mutation-checked on the committed source (Task 2 step 8 of the Plan 7B plan).
- SHA-256: `07249a4430f3aea2742201839ada3a904a032b179731e2b9c7e6412b8c9465fc`.
  Rebuilt from clean a second time with the same command: same hash.
- 28 classes in `org/sovereignos/airpods/{contracts,platform,state,validation}`;
  class-file major version 65, the same as the vendored `guardian-core.jar`.
- Replaces the jar of 2026-09-29 (`324091f9...`), whose parser decoded an invented format that no AirPods sends.
  Vendored 2026-10-02. Re-vendor whenever `airpods-core` changes, and record the new commit and hash here.

## AirPodsApp (`app/`)

Scaffold only (Plan 3 Task 5, ADR-118). Nothing here has been compiled: the
single AOSP rebuild follows all planned AirPods work.

- `Android.bp`: `android_app` `AirPodsApp`, `privileged`, `sdk_version:
  "system_current"`, no `certificate:` line (release key at signing, never the
  platform key), no `platform_apis`, `dex_preopt.enabled: false` (temporary: the
  scaffold declares no components, so R8 removes all code, no `classes.dex` exists
  and dex2oat fails; remove it with the first manifest component in Plan 6). Two `prebuilt_etc` modules:
  `privapp-permissions-org.sovereignos.airpods.xml` (into `permissions`) and
  `hidden-api-whitelisted-app-org.sovereignos.airpods.xml` (into `sysconfig`).
- `AndroidManifest.xml`: `BLUETOOTH_CONNECT` and
  `org.sovereignos.permission.MANAGE_AIRPODS_IDENTITY` only.
- Privapp allow-list: `MANAGE_AIRPODS_IDENTITY` only (`BLUETOOTH_CONNECT` is a
  runtime permission and needs no allow-list entry).
- Sysconfig: `<hidden-api-whitelisted-app package="org.sovereignos.airpods"/>`,
  one package, read from `/system/etc/sysconfig`.
- Open before it can work on a device: the runtime grant of `BLUETOOTH_CONNECT`
  (no default-grant policy exists for it yet).
- `PRODUCT_PACKAGES` wiring is `patches/vendor-adevtool/0001` (table below). The
  build resolves `AirPodsApp` only if this package is present on the build
  server at `vendor/sovereignos/airpods` (its own git repo, like
  `vendor/sovereignos/guardian`). Deployed 2026-09-29 (commit `c61330f` there):
  a byte-for-byte copy of `app/`, `core/`, `README.md`, `SOURCE.md`, 9 of 9
  files verified by sha256. Any later change here must be copied over the same
  way and re-verified before a build.

## AirPodsApp real session (Plan 7A, ADR-124)

`app/src/org/sovereignos/airpods/app/`: `AirPodsSocketOpener` (reflection on the hidden `BluetoothDevice#createL2capSocket(int)`, PSM `0x1001`, under the single-package
hidden-API allow-list), `AirPodsSession` (one reader thread, opening frames 200 ms apart, the notification request repeated until battery and listening mode arrived, nine malformed
frames in a row end it), `AirPodsSessionHost` (main-thread owner of the session, retry with backoff, the wait for the page request), `AirPodsSessionService` (foreground service of type
`connectedDevice`), `SessionCommandSink` (typed commands; Off first enables the AirPods' "Allow Off" option; the comfort settings show the written value), `EarDetectionStore` (the value the
phone last wrote, app-private, because this pair never reports it), `AirPodsStateStore.transform` (atomic read-modify-write). Pure logic in `app/src/.../ui/` (`SessionReducer`,
`AirPodsRecognition`, `Backoff`), host checks `tools/run-host-checks.sh` and `tools/run-store-stress.sh` (needs `BUILD_HOST`; runs once with the lock and once without, which must fail).
New permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, and the privileged `START_FOREGROUND_SERVICES_FROM_BACKGROUND` (named in the privapp allow-list).
Built into images `2026100202` to `2026100204` (`2026100204` not flashed at the time of writing). Threat review: `docs/architecture/airpods-session-threat-review.md` in
`sovereignos-foundation`. The codec it uses is `core/airpods-core.jar` (see its section above; frames are documented in `docs/research/airpods-aap-protocol-facts.md`).

## Probe (`probe/`, Plan 7A task 3)

A debug tool, never in an image: `m AirPodsProbe` builds it, `adb install` installs it on a userdebug device. It opens the accessory channel on the owner's pair and logs frame kinds
(hex, up to 64 bytes, for every opcode except 0x1D, 0x2E, 0x31, 0x0E, 0x10 and 0x11, which carry serial numbers, addresses or keys and are logged as their length only). It reaches the hidden method only while the owner has set
`hidden_api_blacklist_exemptions` to `Landroid/bluetooth/BluetoothDevice;->createL2capSocket` and the probe process was started afterwards. `cmds` accepts `mode:1..3`, `ca:0|1`, `ear:0|1`
and `raw:<hex>` (a frame as hex without spaces). Used on 2026-10-02 for `AIR-121` to `AIR-123`.

## AirPodsApp native device page provider (Plan 6B, ADR-122)

`app/src/org/sovereignos/airpods/ui/` (pure Java, host-tested with `app/test/.../UiLogicCheck.java`: `AirPodsUiState`,
`MetadataEncoder`, `ListeningModeToggles`, `NativePageLayout`, `DebugStateParser`) and
`app/src/org/sovereignos/airpods/app/` (Kotlin: state store, command sink, runtime and receiver, debug observer,
metadata writer, icon provider, settings factory, the two bound services). Resources: strings, 12 row icons
generated from the approved design (`tools/design/svg-icons-to-vector-drawables.py` in `sovereignos-foundation`), three
ring artwork PNGs rendered once (`render-airpods-artwork.sh`, `assets/icons/`). Checks: `UiLogicCheck` (ten
mutations killed), `tools/check-no-hardcoded-text.sh`, `tools/check-devicesettings-vendoring.sh`.
Built (`AirPodsApp` compiled by Soong, images `2026100102` to `2026100104`) and seen on a `lynx` with a debug state:
see `AIR-111` to `AIR-115` in the validation matrix. No real AirPods session yet (Plan 6D). The debug input is the
global setting `airpods_debug_state`, read only when `Build.TYPE` is not `user`.
Deploy like `app/`: rsync to `vendor/sovereignos/airpods` (without `patches/` and `.git`), commit there, build.

## Vendored DeviceSettings contract (`app/src/com/android/settingslib/bluetooth/devicesettings/`)

40 files copied byte-for-byte from `frameworks/base/packages/SettingsLib/src/com/android/settingslib/bluetooth/devicesettings/`
(top-level files only; the Settings-side `data/` and `shared/` packages are not needed), taken from the
tree after the Plan 6A patch (`frameworks/base` `f004903a08af`), so `DeviceSettingContract` carries
`TOGGLE_LABEL_IN_BUTTON`. Why vendored: `SettingsLib` is an `android_library` without `sdk_version`, and
Soong refuses it as a static dependency of the `system_current` `AirPodsApp`
(`build/soong/java/base.go`, `sdkLinkType` check). The AIDL descriptors and parcelable class names must
match Settings' own, so the files keep their package and content. Imports are limited to `android.*`,
`androidx.annotation` and Guava `ImmutableList` (`androidx.annotation_annotation` and `guava` in
`static_libs`). Compiled on the build server on 2026-10-01 (`AirPodsApp` only, 17 steps, exit 0; the
intermediate `javac` jar holds 75 `devicesettings` entries including the generated AIDL interfaces,
the `kotlin` jar 17; R8 removes them from the APK until a component uses them).
Drift check: `BUILD_HOST=<user@host> tools/check-devicesettings-vendoring.sh` (read-only; fails on any
changed, added or missing file; two mutations verified). Re-vendor and re-check after every rebase of
`frameworks/base` that touches that directory.

## Validation harness (`debug-harness/`)

Userdebug-only validation apps for `AIR-029` to `AIR-032` and `AIR-036` (ADR-120, plan
`docs/superpowers/plans/2026-10-01-airpods-debug-harness.md` in `sovereignos-foundation`).
Not part of the product: they are installed through `PRODUCT_PACKAGES_DEBUG`
(`patches/vendor-adevtool/0002`) and the package gate refuses them in a
`user` image.

- One source file (`HarnessProvider`) and one parser (`HexBytes`) built into three
  `privileged` apps: `AirPodsDebugHarness` (holds `MANAGE_AIRPODS_IDENTITY`),
  `AirPodsProbeNoPerm` (`BLUETOOTH_CONNECT` only), `AirPodsProbeBtPriv`
  (`BLUETOOTH_PRIVILEGED`, not ours). All three carry the same single-package
  hidden-API exemption mechanism as `AirPodsApp`.
- The provider answers only to uid shell or root and is guarded by `DUMP`.
- Checked on 2026-10-01 without a full build: `HexBytesCheck` (plain `javac`/`java`, three
  mutations detected); the Java compiled against the build's own
  `android_system_stubs_current.jar` (`javac -source 8 -target 8`, exit 0); `bpfmt -d`
  clean; the three manifests link with the build's `aapt2` against `framework-res`.
  **Not compiled by Soong and not run on a device.**
- Deployed 2026-10-01 like `app/`: a byte-for-byte copy to `vendor/sovereignos/airpods`, 19 of 19
  files sha256-identical, committed there as `b522ca9`.

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
| `packages/modules/Bluetooth` | `469799157dcd901470465b243f0ccc762de40d36` | `patches/packages-modules-bluetooth/0001` | ADR-109 (names this branch/commit literally: `sovereignos-airpods-identity`, `1102e506bdb774bf2a4ccb24d8057e3e370d6290`). **Must not ship alone**, see Corrections below. |
| `packages/modules/Bluetooth` | `469799157dcd901470465b243f0ccc762de40d36` | `patches/packages-modules-bluetooth/0002` | ADR-115: exact-match, bond-gated identity registry, header-only (`593ddedbad37e477737b83302f20b9825eb0c96f`) |
| `packages/modules/Bluetooth` | `469799157dcd901470465b243f0ccc762de40d36` | `patches/packages-modules-bluetooth/0003` | ADR-115: register through a dedicated btif/JNI call; clear on unbond (`98c91e16d3ba08fa269c76dedb664e1c1f9e0478`) |
| `packages/modules/Bluetooth` | `469799157dcd901470465b243f0ccc762de40d36` | `patches/packages-modules-bluetooth/0004` | ADR-115: non-mutating SDP vendor view; hooks gated on registry and bond; interop feature removed (`8a31c37d88e2536f5ea102e63d811f79453d73d5`) |
| `packages/modules/Bluetooth` | `469799157dcd901470465b243f0ccc762de40d36` | `patches/packages-modules-bluetooth/0005` | ADR-115: `BluetoothDevice#setAirPodsIdentity` client wrapper (`b86ac07d9f3a81d450cb1bd8ff4e68f41a7545b2`) |
| `packages/modules/Bluetooth` | `469799157dcd901470465b243f0ccc762de40d36` | `patches/packages-modules-bluetooth/0006` | ADR-119: `BluetoothDevice#setAirPodsMetadata`, the narrow metadata setter behind `MANAGE_AIRPODS_IDENTITY` (`e4b12de12490582d60b3c57832750a9c113ea9bd`; `git am` exit 0; the ten touched files sha256-identical to the authoring worktree) |
| `packages/modules/Bluetooth` | `469799157dcd901470465b243f0ccc762de40d36` | `patches/packages-modules-bluetooth/0007` | ADR-125: `BluetoothDevice#setAirPodsAudioRoute`, the narrow audio-route call behind `MANAGE_AIRPODS_IDENTITY` (`237cae40557f` on branch `sovereignos-airpods-identity` on top of `e4b12de124`; `git am` exit 0; the nine touched files sha256-identical to the authoring worktree; not yet compiled, the module build is the first compile) |
| `packages/modules/Bluetooth` | `469799157dcd901470465b243f0ccc762de40d36` | `patches/packages-modules-bluetooth/0008` | ADR-125: keep the caller's binder referenced in the route watch, so the death link survives garbage collection of the BinderProxy (`b8a00afbd2` on branch `sovereignos-airpods-identity` on top of `237cae4055`; `git am` exit 0; `AdapterService.java` sha256-identical to the authoring worktree; found on the device, `AIR-137`: force-stop while routed away did not restore the route) |
| `packages/modules/Bluetooth` | `469799157dcd901470465b243f0ccc762de40d36` | `patches/packages-modules-bluetooth/0009` | ADR-127: `BluetoothDevice#setAirPodsMusicDuck`, the narrow music-volume call behind `MANAGE_AIRPODS_IDENTITY` (`cca0669ee0` on branch `sovereignos-airpods-identity` on top of `b8a00afbd2`; `git am` exit 0; the nine touched files sha256-identical to the authoring worktree; compiled only after patch `0010`) |
| `packages/modules/Bluetooth` | `469799157dcd901470465b243f0ccc762de40d36` | `patches/packages-modules-bluetooth/0010` | ADR-127: rename the nested duck record that Error Prone rejected (`JavaLangClash`), found by the first module build of `0009` (`620cbdeef1`; `git am` exit 0; file sha256-identical; module build `Build Succeeded: 40 steps`, 0 failed) |
| `packages/modules/Bluetooth` | `469799157dcd901470465b243f0ccc762de40d36` | `patches/packages-modules-bluetooth/0011` | ADR-129: `setAirPodsMusicDuck` accepts an integer percent from 10 to 90 instead of 20, 35, 50 or 70, for the continuous volume slider of Plan 7B (`97ef1dd24a` on branch `sovereignos-airpods-identity` on top of `620cbdeef1`; `git am` exit 0; the six files sha256-identical to the worktree commit; `run-duck-tracker-check.sh` 14 tests OK and four range mutations killed; module build in Task 9). |
| `vendor/adevtool` | `6ebe8024291a5c003f0615a896574afbb9cbf29b` | `patches/vendor-adevtool/0001` | ADR-118: `AirPodsApp` and its two sysconfig modules in `common/device-common.mk` (`4438b3d305f765315672db8cc94e85923b692f9d`, branch `sovereignos-airpods-identity`, created from `sovereignos-2026091900`; authored from a fetch-only local copy of the one touched file, `git am` exit 0, resulting file sha256 equal to the worktree's) |
| `vendor/adevtool` | `4438b3d305f765315672db8cc94e85923b692f9d` | `patches/vendor-adevtool/0002` | ADR-120: `PRODUCT_PACKAGES_DEBUG += AirPodsDebugHarness AirPodsProbeNoPerm AirPodsProbeBtPriv` in `common/device-common.mk`, so the userdebug-only validation harness never reaches a `user` image (`9b0e6172643f76714f76b1be8c6ad986ae4dde35`, branch `sovereignos-airpods-identity`; authored from a fetch-only local copy of the one touched file; `git am` exit 0; resulting file sha256 `3451d24107b6de4e534d337036c90756911b83b25e5c1272a2953786c33fd336`, equal to the authoring worktree's; the lynx version pin was moved to this commit, backup `adevtool-version-check.mk.pre-harness`) |
| `frameworks/base` | `c013345b2161` | `patches/frameworks-base/0001` | ADR-121: opt-in `DeviceSettingContract.TOGGLE_LABEL_IN_BUTTON`, `ToggleModel.labelInsideButton`, `SegmentedButtonPreference.setLabelInsideButton` (`f004903a08af679cf5118e9a3946017150c716bb`, branch `sovereignos-2026091900-r2`; authored from a fetch-only local copy of the five touched files; `git am` exit 0; the five files sha256-identical to the authoring worktree; paths are relative to the `frameworks/base` repository root, first attempt with tree-root paths failed and was aborted cleanly) |
| `frameworks/base` | `f004903a08af` | `patches/frameworks-base/0002` | ADR-121: horizontal padding of 8 dp for a button in `labelInsideButton` mode, found on the device (the default 24 dp cut `Transparency` to `Transpare...`) (`bae295d23b6899567dcf7eec67d9ee9353164403`, branch `sovereignos-2026091900-r2`; one file, sha256-identical to the authoring worktree; `git am` exit 0) |
| `frameworks/base` | `bae295d23b68` | `patches/frameworks-base/0003` | ADR-123: a label in `labelInsideButton` mode may use two lines, hyphenates a word wider than the button and the row shares the tallest height; the label was cut at font scale 1.3 (`Transparen...`) and 2.0 (`Transp...`) (`d0e538f28aac`, branch `sovereignos-2026091900-r2`; one file, sha256-identical to the authoring worktree; `git am` exit 0; regenerated from the server history) |
| `packages/apps/Settings` | `c19f800e81f8` | `patches/packages-apps-settings/0001` | ADR-121: pass the opt-in to `SegmentedButtonPreference` in `BluetoothDetailsConfigurableFragment` (`d5b871d8aa32fb7873346a7c27c003407c710011`, branch `sovereignos-2026091900`; one file, sha256-identical to the authoring worktree; `git am` exit 0) |

Apply from the tree's own repository root:

```sh
cd packages/modules/Bluetooth && git am ../../airpods/patches/packages-modules-bluetooth/*.patch
```

### What patch 0006 does (ADR-117, ADR-119)

Adds `BluetoothDevice#setAirPodsMetadata(int key, byte[] value)` (`@SystemApi`),
backed by `IBluetooth#setAirPodsMetadata`. `AdapterServiceBinder` enforces
`org.sovereignos.permission.MANAGE_AIRPODS_IDENTITY` (never
`BLUETOOTH_PRIVILEGED`). `AdapterService#setAirPodsMetadata` then:

- accepts only 18 keys (`AirPodsMetadataPolicy`): the untethered and main icon,
  battery, charging and low-battery-threshold keys, `IS_UNTETHERED_HEADSET`, and
  `FAST_PAIR_CUSTOMIZED_FIELDS`. `ENHANCED_SETTINGS_UI_URI`, `COMPANION_APP`,
  `DEVICE_TYPE` and the manufacturer, model and version keys are refused;
- accepts only a value of at most `METADATA_MAX_LENGTH` bytes;
- runs only for a device that is bonded and was registered through
  `setAirPodsIdentity` (`AirPodsIdentityTracker`, one lock shared with
  `setAirPodsIdentity` and the unbond hook, held across the storage write);
- reuses `setMetadata` for the write and returns true when the stored value
  already equals the request, so a false result means refused or failed.

New files: `AirPodsMetadataPolicy.java`, `AirPodsIdentityTracker.java`, their
host-runnable tests, and three binder tests in `AdapterServiceBinderTest.kt`.
Evidence: 17 host tests pass against the two new classes (fetched from the
server tree), and three mutation runs fail the tests as intended. Not
compiled: the Bluetooth module, the Kotlin tests, the API file. The
`setAirPodsMetadata` declaration was compiled on its own with the tree's `aidl`.

### What patch 0007 does (ADR-125)

Adds `BluetoothDevice#setAirPodsAudioRoute(boolean away)` (`@SystemApi`), backed by
`IBluetooth#setAirPodsAudioRoute`, so the AirPods app can take the AirPods out of the
active A2DP/HFP slots while both pods are out of the ears, without `MODIFY_AUDIO_ROUTING`.
Three guarantees:

- only a registered device: `AdapterServiceBinder` enforces
  `org.sovereignos.permission.MANAGE_AIRPODS_IDENTITY`, and `AdapterService` runs the change
  only for a bonded device registered through `setAirPodsIdentity`
  (`AirPodsIdentityTracker.runIfRegistered`);
- only what it removed: `AirPodsRouteTracker` records the slots (A2DP, HFP) on which the
  device was active when removed; the restore touches only those, and only where the slot is
  still free (no other classic device, LE Audio device or hearing aid took it). It never selects
  another device;
- restored on every exit: the record is dropped on A2DP/HFP disconnect, unbond and adapter off
  (`ActiveDeviceManager.resetState`), and a binder death recipient restores the route when the
  calling process dies.

New files: `AirPodsRouteTracker.java` and its host-runnable test (12 tests, four mutations
fail them as intended, one of them caught by a neighbouring test, see the plan record), plus
three binder tests in `AdapterServiceBinderTest.kt`. Not compiled: the Bluetooth module, the
Kotlin tests, the API file.

### What patch 0008 does (ADR-125)

Fixes patch 0007: `AdapterService` linked a death recipient to the caller's binder but kept only the
recipient. The link lives on the `BinderProxy`, so it was lost when the proxy was garbage collected and
the route was never restored when the AirPods app died. The watch entry now keeps the token and unlinks
from it. Found on the device (`AIR-137`), not by a host check: the death semantics need a real binder.
Not compiled until the next module build.

### What patches 0009 and 0010 do (ADR-127)

`BluetoothDevice#setAirPodsMusicDuck(boolean duck, int percent)` (`@SystemApi`), backed by
`IBluetooth#setAirPodsMusicDuck`. The audio hardening of this OS ignores an app's own volume calls
(`AIR-153`), so the AirPods app cannot lower the music while the owner speaks; the Bluetooth process is
exempt by uid. Guarantees: only a bonded device registered through `setAirPodsIdentity` and active as
A2DP; `percent` is one of 20, 35, 50, 70 in `0009` and any integer from 10 to 90 since `0011` (`AirPodsDuckTracker.isValidPercent`); the volume is only
lowered (target below the current volume, never below the stream minimum), one index at a time under
its own lock; it is given back only if the volume is still the value this call set (an owner change
wins); also on caller death (the death watch is shared with `0007`/`0008` and keeps the token), when the
AirPods are routed away (restore first), on A2DP disconnect, unbond and adapter off. Host-run tests
(13) and eight mutations cover `AirPodsDuckTracker`; the fade and the binder path are covered by the
module build and by `AIR-154` on the device. Patch `0010` only renames a nested class. Patch `0011` (ADR-129) widens the allowed percent from four constants to the integers 10 to 90; every other limit stays.

### What patch 0001 does (its registration and SDP mechanism is superseded, see Corrections)

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

### Corrections to 0001 (patches 0002-0005, ADR-115)

A pre-build review of `0001` against the live pinned tree (2026-09-29, read
only, nothing built) found five defects. Patches `0002`-`0005` are additive on
top of `0001` (no history rewrite of `sovereignos-airpods-identity`); `0001`
alone must not be shipped.

- **D1** `sdp_dynamic_change_airpods_vendor` wrote the Apple vendor ID into the
  shared SDP record with no restore, so after one AirPods query every peer saw
  Apple (a device-wide spoof). Now the vendor attribute is served from a
  per-response copy (`sdpu_airpods_vendor_view`); the shared record is never
  written.
- **D2** `AdapterService#setAirPodsIdentity` passed length 6 to
  `interop_database_add_addr`, which asserts `length < sizeof(RawAddress)` with a
  fatal log: the first registration would abort the Bluetooth process. Registration
  no longer uses the interop database.
- **D3** interop dynamic entries persist to
  `/data/misc/bluedroid/interop_database_dynamic.conf` and were never removed on
  unbond. The registry is now in memory only and cleared on `BOND_NONE`.
- **D4** `interop_match_addr_or_name` also matched by device name. Removed.
- **D5** no client path existed for `AirPodsApp`. Added
  `BluetoothDevice#setAirPodsIdentity(boolean)` (`@Hide @SystemApi`, modeled on
  `setMetadata`, with its `framework/api/system-current.txt` line).

Mechanism now: `system/device/include/airpods_identity.h` holds at most 16 exact
6-byte peers; `airpods_identity_applies()` is true only for a registered peer
that is also currently bonded (`BTM_IsBonded`, injected from `btif`, fails closed
when absent). Both native hooks (SDP, L2CAP) consult only that predicate.

**Not compiled yet.** Per the owner's directive (2026-09-29) there is one
rebuild after all planned AirPods work is applied. `0002`-`0005` are verified
only by standalone `g++ -Wall -Wextra -Werror` checks of the header and the pure
SDP view function, a gtest run of the registry tests against a stand-in
`RawAddress`, a `javac` parse-only gate on the edited Java files, and per-file
sha256 comparison between the worktree and the landed branch. The build plan
starts with a host compile of `net_test_device` and `net_test_stack_sdp`.
Validation on hardware: `AIR-021`, `AIR-029` to `AIR-032`.

### Real defect found and fixed before landing (patch 0001)

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
