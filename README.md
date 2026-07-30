# aegisos-platform

Isolated AegisOS framework and platform patches layered over pinned GrapheneOS
revisions. Android source trees, build output, vendor blobs, signing material
and device logs do not belong in this repository.

## Current packages

- [`branding/boot`](branding/boot/README.md) — approved AegisOS boot identity,
  reproducible assets and the minimal `frameworks/base` background patch.

The boot package is prepared and locally verified, but must not be applied to
the active Phase 1 unmodified baseline tree. Integration begins only after that
baseline build and boot review have been accepted.
