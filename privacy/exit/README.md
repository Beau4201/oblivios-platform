# exit

The per-app network exit of Compartments (ADR-178, ADR-184). The design is
`docs/superpowers/specs/2026-10-18-network-exit-design.md` in `freeholdos-foundation`. One platform patch landed on
the build server 2026-10-18; the `lynx` image `2026102101` carries it and the probe app (`IMG-011`, `IMG-012`), and `NET-001b` passed there. The exit service (daemon and route helper, exit app, SELinux domains, packaging) landed on the build server 2026-10-21 and its modules were built; no image carries it yet. `SOURCE.md` holds the base revisions and the reasons.

| Part | Project | Effect |
| --- | --- | --- |
| `patches/packages-modules-connectivity/0001` | `packages/modules/Connectivity` | `setOemNetworkPreference` no longer refuses the production preferences on a device without `FEATURE_AUTOMOTIVE`; the `CONTROL_OEM_PAID_NETWORK_PREFERENCE` check stays. |
| `daemon/` | `vendor/freeholdos/exit/daemon` | The prebuilt `exitd` (daemon and, with `--routes`, route helper; built from `freeholdos-services/exit-daemon`), its init definition `exit.rc` for the two services and the mask list `exit-mask-list.tsv`; `check-prebuilt.sh` compares them with `SOURCE.md`. |
| `app/` | `vendor/freeholdos/exit/app` | `ExitApp`: the privileged, persistent exit app that owns the policy, registers the Tor network, sets the network preferences and has the "Compartments" entry under Network &amp; internet, whose screens use the widgets and the theme of SettingsLib like the Updater. Pure Java core with host checks and mutations (`tools/run-host-checks.sh`, `tools/run-mutations.py`), `tools/typecheck-on-server.sh`, `tools/check-static.py`, `tools/check-resources.py`. |
| `patches/system-sepolicy/0001` to `0005` | `system/sepolicy` | The domains `exitd`, `exitd_route`, `exit_app`, their types and contexts, and the neverallows; the second patch makes the neverallows hold in the combined policy; the third lets `init` listen on the two control sockets and the services write to `kmsg_debug` (userdebug and eng); the fourth makes the sockets and the two domains MLS-trusted for the app; the fifth gives `exitd` `create` on its own `tun_socket` and `exitd_route` the message type `RTM_SETLINK`. |
| `tools/check-syscall-policy.py`, `tools/exit-needs.tsv` | | The gate of what the two services do: one row per operation read from `strace` of the real binaries on a phone (start, Tor bootstrap, route apply), checked against a compiled policy (`sesearch`) and the groups of `exit.rc`. Run it on the policy of a product output (or of `/sys/fs/selinux/policy` of a phone) before any exit image is signed; it must fail on a policy without the rule and pass on one with it. |
| `patches/packages-apps-Settings/0001` | `packages/apps/Settings` | The row "Compartments" on the info page of an app (under data usage); it opens the app's page of the exit app and reads nothing of it. |
| `patches/packages-apps-Settings/0003` | `packages/apps/Settings` | The summary of that row is the profile of the app (ADR-208), read through the Compartments policy service with `READ_COMPARTMENT_PROFILE`. Not landed: the Kotlin file is first compiled by the module build. |
| `patches/vendor-adevtool/0002` | `vendor/adevtool` | `PRODUCT_PACKAGES += exitd ExitApp` in `device-common.mk`. |
| `tools/check-sepolicy.py`, `tools/check-neverallows.sh` | | The policy gate (setools on the compiled policy) and the neverallow check (the build checks none: the Pixel board configurations set `SELINUX_IGNORE_NEVERALLOWS`). |
| `probe-app/` | `vendor/freeholdos/exit` | `ExitProbe`, the userdebug-only, throwaway, platform-signed probe of `NET-001b`: it registers a network with `NET_CAPABILITY_OEM_PRIVATE` and selects it for one package with the production preference. Never in a `user` build. |
| `patches/vendor-adevtool/0001` | `vendor/adevtool` | `PRODUCT_PACKAGES_DEBUG += ExitProbe` in `device-common.mk`. |

`probe-app/tools/run-host-checks.sh` runs the host checks of the pure Java (argument parser, packet summary) and
`probe-app/tools/typecheck-on-server.sh` type-checks the whole app against the module-lib stubs on the build server.
