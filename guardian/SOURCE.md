# Guardian — Source Provenance

## guardian-core.jar

- Source: `aegisos-services` commit `3d66a461b7aa9b61f2e6f2db9e4f96cbf380cf43`.
- Build command: `./gradlew :guardian-core:jar` (Java 21 toolchain, zero
  runtime dependencies — confirmed via `guardian-core/build.gradle.kts`).
- SHA-256: `6a9517f3ebe783ce0190ef3e1ea56e58c7328b86b040fb5ca9d044707c0e63f6`.
- Built: 2026-08-05.

## GuardianUiController.kt / GuardianUiState.kt

- Copied verbatim from the same commit above,
  `guardian-ui-reference/src/main/kotlin/org/aegisos/guardian/ui/`.
- Reused as-is: both files are plain Kotlin with no Compose Desktop or
  desktop-windowing dependency.

### Deviation: GuardianUiController.kt secondary constructor removed

- The source file included a secondary `constructor(scenario: GuardianReferenceScenario)`
  and its import (`org.aegisos.guardian.reference.GuardianReferenceScenario`).
- `GuardianReferenceScenario` is CLI-only reference scaffolding in the
  `guardian-reference` module of `aegisos-services`, not part of `guardian-core`,
  and was not vendored into this repository. The import cannot resolve,
  causing a hard Kotlin compile error.
- The secondary constructor is demo-only code (hardcoding fake package names for
  test scenarios) with no role in real privileged Android integration.
- **Fix:** the secondary constructor and its import were removed. All other
  content in both files — the primary constructor, all four methods (`start`,
  `protect`, `undo`, `overview`), and GuardianUiState.kt in its entirety —
  remains an unmodified copy of the source.

Re-vendor by repeating Task 1 of
`docs/superpowers/plans/2026-08-05-guardian-platform-integration.md` against
a newer `aegisos-services` commit; do not hand-edit any file in this
directory.
