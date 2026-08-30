# Guardian (AegisOS platform integration)

Real Android integration of Guardian's `REVOKE_BACKGROUND_LOCATION`
remediation, per
`docs/superpowers/specs/2026-08-05-guardian-platform-integration-design.md`
in `aegisos-foundation`. Domain logic is vendored from `aegisos-services`
(see `SOURCE.md`) and unmodified here.

## Layout

- `core/` — vendored `guardian-core.jar` and two reusable Kotlin UI-controller
  files. Provenance in `SOURCE.md`.
- `app/` — the Android app module: manifest, `Android.bp`, real gateway,
  event source, ported UI.
- `patches/` — AOSP source patches (not vendored app code) that integrate
  GuardianApp into a product build. Provenance and apply instructions in
  `SOURCE.md`.

## Fixes since initial integration

- **ADR-070** (2026-08-30, `aegisos-foundation`): `BackgroundLocationWatcher`'s
  AppOps callbacks were not scoped to this process's own Android user, so on
  any multi-user AegisOS device this Guardian instance would ingest another
  user's own app activity into its own overview store. Found incidentally
  during an exhaustive Duress Unlock decoy identity-leak audit, but the
  defect itself is general to any multi-user pairing. See `SOURCE.md`'s
  "GuardianApp (app module)" section for the fix detail.
