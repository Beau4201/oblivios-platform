# exit

One patch for the per-app network exit of Strangers (ADR-178, proposed). The design is
`docs/superpowers/specs/2026-10-18-network-exit-design.md` and the plan
`docs/superpowers/plans/2026-10-18-network-exit-phase-1.md` in `freeholdos-foundation`. Landed on the build server
2026-10-18; no image carries it yet. `SOURCE.md` holds the base revision and the reason.

| Patch | Project | Effect |
| --- | --- | --- |
| `patches/packages-modules-connectivity/0001` | `packages/modules/Connectivity` | `setOemNetworkPreference` no longer refuses the production preferences on a device without `FEATURE_AUTOMOTIVE`; the `CONTROL_OEM_PAID_NETWORK_PREFERENCE` check stays. |
