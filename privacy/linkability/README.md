# linkability

Six patches that close channels through which two apps of one user link themselves to one device or fingerprint it
(ADR-174). The design is `docs/superpowers/specs/2026-10-18-linkability-kills-design.md` and the plan
`docs/superpowers/plans/2026-10-18-linkability-batches-3-6.md` in `freeholdos-foundation`. Authored and host-checked;
not landed and not built. `SOURCE.md` holds the base revisions, the reason for each patch and the checks.

| Patch | Project | Effect |
| --- | --- | --- |
| `patches/packages-modules-connectivity/0001` | `packages/modules/Connectivity` | The BPF loopback check also refuses traffic between two apps of one user unless either side holds `USE_LOOPBACK_INTERFACE`; `freeholdos_loopback_open` = 1 switches it off. |
| `patches/frameworks-base/0001` | `frameworks/base` | `USE_LOOPBACK_INTERFACE` becomes a runtime permission. |
| `patches/frameworks-base/0002` | `frameworks/base` | The clipboard is readable only through a paste; a denied caller learns the same for every clip. |
| `patches/frameworks-base/0003`, `0005` | `frameworks/base` | An ordinary app reads 15 settings keys; every other key reads as unset. |
| `patches/frameworks-base/0004` | `frameworks/base` | An app gets the vibrator resonant frequency to 10 Hz, the Q factor to 0.5 and no acceleration curve. |
| `patches/system-sepolicy/0001` | `system/sepolicy` | `ro.boot.hardware.color` and `.coo` are hidden from third-party apps. |

`test/` holds the host test of the settings allowlist and its mutation check:

    test/run-settings-test.sh <SettingsHardening.java>
    test/mutate-settings.py <SettingsHardening.java>

`SettingsHardening.java` is part of `frameworks-base/0003`; extract it with `git apply --include='*SettingsHardening.java'`
into an empty repository.
