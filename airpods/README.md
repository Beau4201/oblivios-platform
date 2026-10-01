# AirPods (SovereignOS platform integration)

Platform side of the AirPods compatibility feature, per
`docs/superpowers/specs/2026-09-28-airpods-compatibility-and-finding-design.md`
and `docs/superpowers/specs/2026-09-29-airpods-settings-ui-design.md` in
`sovereignos-foundation`. Domain logic is vendored from `sovereignos-services`
(`airpods-core`, see `SOURCE.md`) and unmodified here.

## Layout

- `core/` — vendored `airpods-core.jar`. Provenance in `SOURCE.md`.
- `app/` — `AirPodsApp`, the first-party priv-app (`org.sovereignos.airpods`).
  Currently a scaffold: manifest, build file, privapp allow-list, the
  single-package hidden-API sysconfig entry, and one string resource. The
  services, providers and pages are Plan 6.
- `tools/check-devicesettings-vendoring.sh` — fails when the vendored DeviceSettings contract (`app/src/.../devicesettings/`) differs from the build tree.
- `debug-harness/` — userdebug-only validation apps (ADR-120), never in `user` builds.
- `patches/` — AOSP source patches (Bluetooth module identity and client
  wrapper). Provenance and apply instructions in `SOURCE.md`.

## Signing and privilege posture (ADR-118)

`AirPodsApp` is not platform-signed and not `platform_apis`. It is a
privileged app compiled against `system_current`. It holds one privileged
permission, `org.sovereignos.permission.MANAGE_AIRPODS_IDENTITY`, and it is
named in `hidden-api-whitelisted-app-*.xml` so that only this package may use
the hidden `BluetoothSocket` constructor. It does not hold
`BLUETOOTH_PRIVILEGED` (ADR-117).

## Not built or run yet

Nothing in this package has been compiled. Per the owner's directive there is
one AOSP rebuild after all planned AirPods work; until then only static checks
run. `PRODUCT_PACKAGES` wiring is a separate patch (Plan 3 Task 6).
