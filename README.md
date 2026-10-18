# freeholdos-platform

Isolated FreeholdOS framework and platform patches layered over pinned GrapheneOS
revisions. Android source trees, build output, vendor blobs, signing material
and device logs do not belong in this repository.

## Current packages

- [`branding/boot`](branding/boot/README.md) — the FreeholdOS lighthouse boot
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
- [`privacy/screenshot-detection`](privacy/screenshot-detection/README.md) — apps are not
  told when the user takes a screenshot (one `frameworks/base` patch).
- [`privacy/identity-surface`](privacy/identity-surface/README.md) — apps cannot read the
  unit-specific boot properties or the bias estimates of the uncalibrated sensors (ADR-167).
- [`privacy/linkability`](privacy/linkability/README.md) — loopback between apps, the clipboard, the
  settings tables, the vibrator calibration and the device colour are closed for ordinary apps (ADR-174).
- [`physical/grab-detection`](physical/grab-detection/README.md) — the phone locks when it is pulled out
  of a hand in use and restarts after 60 seconds without a PIN (ADR-169).
- [`release/ota-server-url`](release/ota-server-url/README.md) — points
  the Updater app at FreeholdOS's private lynx OTA server (ADR-052, amending
  ADR-045).

All packages are patches layered over the pinned GrapheneOS manifest tag
recorded in `freeholdos-manifest`; none are applied to a baseline tree without
the ADR their own README cites.

## Stage

Source only. No builds, images or update service are offered, and no device is
supported. See `CONTRIBUTING.md` for how a package is laid out and the rules for
a patch. The pinned upstream baseline is in `freeholdos-manifest`.

## Licence

Apache-2.0, see `LICENSE` and `NOTICE`. Each patch keeps the licence of the
upstream file it modifies. The name FreeholdOS and its logo are not licensed.
