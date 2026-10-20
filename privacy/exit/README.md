# exit

The per-app network exit of Compartments (ADR-178, ADR-184). The design is
`docs/superpowers/specs/2026-10-18-network-exit-design.md` in `freeholdos-foundation`. One platform patch landed on
the build server 2026-10-18; no image carries it yet. `SOURCE.md` holds the base revisions and the reasons.

| Part | Project | Effect |
| --- | --- | --- |
| `patches/packages-modules-connectivity/0001` | `packages/modules/Connectivity` | `setOemNetworkPreference` no longer refuses the production preferences on a device without `FEATURE_AUTOMOTIVE`; the `CONTROL_OEM_PAID_NETWORK_PREFERENCE` check stays. |
| `probe-app/` | `vendor/freeholdos/exit` | `ExitProbe`, the userdebug-only, throwaway, platform-signed probe of `NET-001b`: it registers a network with `NET_CAPABILITY_OEM_PRIVATE` and selects it for one package with the production preference. Never in a `user` build. |
| `patches/vendor-adevtool/0001` | `vendor/adevtool` | `PRODUCT_PACKAGES_DEBUG += ExitProbe` in `device-common.mk`. |

`probe-app/tools/run-host-checks.sh` runs the host checks of the pure Java (argument parser, packet summary) and
`probe-app/tools/typecheck-on-server.sh` type-checks the whole app against the module-lib stubs on the build server.
