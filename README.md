# aegisos-platform

Isolated AegisOS framework and platform patches layered over pinned GrapheneOS
revisions. Android source trees, build output, vendor blobs, signing material
and device logs do not belong in this repository.

## Current packages

- [`branding/boot`](branding/boot/README.md) — AegisOS boot identity,
  reproducible assets and the `frameworks/base` background/framerate
  patches (ADR-019).
- [`credential/duress-unlock-dummy`](credential/duress-unlock-dummy/README.md) —
  Duress Unlock Stage 1: the cost-symmetric dummy-credential-check
  primitive (ADR-040).
- [`credential/duress-unlock-decoy`](credential/duress-unlock-decoy/README.md) —
  Duress Unlock Stage 2, Tasks 1-3 of 9: the `USER_TYPE_FULL_AEGISOS_DECOY`
  user type (ADR-053, amending ADR-042), the dialog-free silent user
  switch (ADR-054), and decoy profile routing state storage (ADR-055 pending).
  Tasks 4-9 not started.
- [`guardian`](guardian/README.md) — Guardian's real Android integration
  (app + core) plus the AOSP-side patches that wire it into the emulator
  and `lynx` product builds (ADR-033/ADR-035/ADR-051).
- [`release/ota-server-url`](release/ota-server-url/README.md) — points
  the Updater app at AegisOS's private lynx OTA server (ADR-052, amending
  ADR-045).

All packages are patches layered over the pinned GrapheneOS manifest tag
recorded in `aegisos-manifest`; none are applied to a baseline tree without
the ADR their own README cites.
