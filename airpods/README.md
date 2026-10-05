# AirPods (SovereignOS platform integration)

Platform side of the AirPods compatibility feature, per
`docs/superpowers/specs/2026-09-28-airpods-compatibility-and-finding-design.md`
and `docs/superpowers/specs/2026-09-29-airpods-settings-ui-design.md` in
`sovereignos-foundation`. Domain logic is vendored from `sovereignos-services`
(`airpods-core`, see `SOURCE.md`) and unmodified here.

## Layout

- `core/` — vendored `airpods-core.jar`. Provenance in `SOURCE.md`.
- `app/` — `AirPodsApp`, the first-party priv-app (`org.sovereignos.airpods`):
  the real accessory-protocol session in a foreground service, the native
  Settings page provider (config and per-item settings), the ear detection and
  conversation awareness controllers, the stores (ear detection, volume choice,
  last case battery), and the pure UI logic under `app/src/.../ui/` that the host
  checks run (`tools/run-host-checks.sh`). Own pages and the connection sheet are Plan 7B.
- `probe/` — debug-only capture tool (`org.sovereignos.airpods.probe`), see `SOURCE.md`.
- `tools/check-no-hardcoded-text.sh` — fails on user-visible text that is not a string resource.
- `tools/check-devicesettings-vendoring.sh` — fails when the vendored DeviceSettings contract (`app/src/.../devicesettings/`) differs from the build tree.
- `debug-harness/` — userdebug-only validation apps (ADR-120), never in `user` builds.
- `patches/` — AOSP source patches (Bluetooth module: identity, client wrapper,
  metadata setter, audio route, music duck). Provenance and apply instructions in `SOURCE.md`.

## Signing and privilege posture (ADR-118)

`AirPodsApp` is not platform-signed and not `platform_apis`. It is a
privileged app compiled against `system_current`. It holds one privileged
permission, `org.sovereignos.permission.MANAGE_AIRPODS_IDENTITY`, plus
`BLUETOOTH_CONNECT`, the foreground-service permissions and `RECEIVE_BOOT_COMPLETED`
(normal; added in Plan 7A-2 for the start after a reboot), and it is
named in `hidden-api-whitelisted-app-*.xml` so that only this package may use
the hidden `BluetoothSocket` constructor. It does not hold
`BLUETOOTH_PRIVILEGED` (ADR-117), `MODIFY_AUDIO_ROUTING` or
`MODIFY_AUDIO_SETTINGS_PRIVILEGED` (ADR-125, ADR-127): moving the audio to the phone and
lowering the music volume are two narrow `@SystemApi` calls in the Bluetooth module behind
`MANAGE_AIRPODS_IDENTITY` (patches `0007` to `0010`).

## Built and run

The package is built into the `lynx` images (`PRODUCT_PACKAGES` wiring is a
`vendor/adevtool` patch, see `SOURCE.md`) and runs on the owner's device. Device evidence,
with the image each row ran on, is in `docs/quality/validation-matrix.md` (rows `AIR-111`
to `AIR-192`) in `sovereignos-foundation`; the open rows are in its `docs/product/status.md`.
An app-only change cannot be installed with `adb install` (a system app update needs an
fs-verity signature), so every change is an image build. Plan 9 is the last plan of this
package: the control rows, Play sound, the alias call (patch `0012`) and the connection sheet.
