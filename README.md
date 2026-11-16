# oblivios-platform

Isolated ObliviOS framework and platform patches layered over pinned GrapheneOS
revisions. Android source trees, build output, vendor blobs, signing material
and device logs do not belong in this repository.

## Current packages

Each package has a `README.md` or `SOURCE.md` with its provenance, the patches and the decisions behind them.

- [`airpods`](airpods/README.md) — AirPods support: the app, the codec jar and the Bluetooth patches.
- [`branding/boot`](branding/boot/README.md) — the ObliviOS lighthouse boot picture: reproducible textures and
  three `frameworks/base` patches (ADR-152, superseding ADR-019).
- [`credential/duress-unlock-dummy`](credential/duress-unlock-dummy/README.md) — Duress Unlock stage 1: the
  cost-symmetric dummy-credential-check primitive (ADR-040).
- [`credential/duress-unlock-decoy`](credential/duress-unlock-decoy/README.md) — Duress Unlock stage 2: the decoy
  switch mechanism (user type, silent switch, routing state, keyguard hook, shell command; ADR-053 to ADR-058).
- [`datamap`](datamap/README.md) — the Live Data Map: a collector in `system_server`, the classifier, the address
  data and the app (ADR-157).
- [`keyboard/autofill-chips`](keyboard/autofill-chips/README.md) — logins of the Passwords app as chips in the
  suggestion strip of the on-screen keyboard.
- [`keyboard/restyle`](keyboard/restyle/README.md) — the restyle of the on-screen keyboard.
- [`launcher/app-drawer`](launcher/app-drawer/README.md) — the app drawer with themed icons and automatic category
  groups.
- [`memory/cached-process-cap`](memory/cached-process-cap/SOURCE.md), [`memory/decoy-stop-on-switch`](memory/decoy-stop-on-switch/SOURCE.md),
  [`memory/zram-size`](memory/zram-size/SOURCE.md) — memory settings of the image (records only).
- [`passwords`](passwords/SOURCE.md) — the Passwords app and the prebuilt Rust core of its vault (ADR-185).
- [`physical/grab-detection`](physical/grab-detection/README.md) — the phone locks when it is pulled out of a hand
  in use and restarts after 60 seconds without a PIN (ADR-169).
- [`privacy/compartments-profiles`](privacy/compartments-profiles/README.md) — the profiles of Compartments: policy
  store, sensor modes and persona data (ADR-208).
- [`privacy/exit`](privacy/exit/README.md) — the per-app network exit of Compartments (ADR-178, ADR-184).
- [`privacy/identity-surface`](privacy/identity-surface/README.md) — apps cannot read the unit-specific boot
  properties or the bias estimates of the uncalibrated sensors (ADR-167).
- [`privacy/linkability`](privacy/linkability/README.md) — loopback between apps, the clipboard, the settings tables,
  the vibrator calibration and the device colour are closed for ordinary apps (ADR-174).
- [`privacy/screenshot-detection`](privacy/screenshot-detection/README.md) — apps are not told when the user takes a
  screenshot (one `frameworks/base` patch).
- [`release/ota-server-url`](release/ota-server-url/README.md) — points the Updater app at the private OTA server of
  the owner (ADR-052, amending ADR-045).

All packages are patches layered over the pinned GrapheneOS manifest tag recorded in `oblivios-manifest`, or code that
belongs to such a patch; none is applied to a baseline tree without the ADR their own README cites. The status of each
package (what is built, what was tested on a device) is in its own record, not here.

## Unpublished records

A record cited by path in a README, a `SOURCE.md` or a patch (for example `docs/superpowers/...` or "the foundation
repository") may be in a private repository and not published. The decisions are in the decision log of `oblivios-docs`.
The project was named AegisOS, SovereignOS and FreeholdOS before ObliviOS (the rename is ADR-224); records written under
those names keep them.

## Stage

Source only. No builds, images or update service are offered, and no device is
supported. See `CONTRIBUTING.md` for how a package is laid out and the rules for
a patch. The pinned upstream baseline is in `oblivios-manifest`.

## Licence

Apache-2.0, see `LICENSE` and `NOTICE`. Each patch keeps the licence of the
upstream file it modifies. The name ObliviOS and its logo are not licensed.
