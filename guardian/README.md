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
