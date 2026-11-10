# Guardian (ObliviOS platform integration)

Real Android integration of Guardian's `REVOKE_BACKGROUND_LOCATION`
remediation, per
the internal record "guardian platform integration design" (not published). Domain logic is vendored from `oblivios-services`
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

- **ADR-070** (2026-08-30): `BackgroundLocationWatcher`'s
  AppOps callbacks were not scoped to this process's own Android user, so on
  any multi-user ObliviOS device this Guardian instance would ingest another
  user's own app activity into its own overview store. Found incidentally
  during an exhaustive Duress Unlock decoy identity-leak audit, but the
  defect itself is general to any multi-user pairing. See `SOURCE.md`'s
  "GuardianApp (app module)" section for the fix detail.
