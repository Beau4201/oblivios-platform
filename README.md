# sovereignos-platform

Isolated SovereignOS framework and platform patches layered over pinned GrapheneOS
revisions. Android source trees, build output, vendor blobs, signing material
and device logs do not belong in this repository.

## Current packages

- [`branding/boot`](branding/boot/README.md) — the SovereignOS lighthouse boot
  picture: reproducible textures and three `frameworks/base` patches
  (ADR-152, superseding ADR-019).
- [`credential/duress-unlock-dummy`](credential/duress-unlock-dummy/README.md) —
  Duress Unlock Stage 1: the cost-symmetric dummy-credential-check
  primitive (ADR-040).
- [`credential/duress-unlock-decoy`](credential/duress-unlock-decoy/README.md) —
  Duress Unlock Stage 2: the full real decoy-switch mechanism (user type,
  silent switch, routing state, keyguard hook, wiring, shell command)
  (ADR-053/054/055/056), plus its real-user quiescing/notification
  suppression/return-path replacement for the freeze step that was found to
  always fail for user 0 (ADR-058). Emulator-validated with real evidence
  (decoy match, freeze, suppression, return path, wipe-PIN non-confusion);
  two items remain open (dynamic call/media test, post-wipe reboot timing).
  Physical `lynx` hardware validation and findings not started.
- [`guardian`](guardian/README.md) — Guardian's real Android integration
  (app + core) plus the AOSP-side patches that wire it into the emulator
  and `lynx` product builds (ADR-033/ADR-035/ADR-051).
- [`release/ota-server-url`](release/ota-server-url/README.md) — points
  the Updater app at SovereignOS's private lynx OTA server (ADR-052, amending
  ADR-045).

All packages are patches layered over the pinned GrapheneOS manifest tag
recorded in `sovereignos-manifest`; none are applied to a baseline tree without
the ADR their own README cites.
