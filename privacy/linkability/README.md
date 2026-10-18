# linkability

Ten patches that close channels through which two apps of one user link themselves to one device or fingerprint it
(ADR-174, amended by ADR-175). The design is `docs/superpowers/specs/2026-10-18-linkability-kills-design.md` and the plan
`docs/superpowers/plans/2026-10-18-linkability-batches-3-6.md` in `freeholdos-foundation`. Landed on the build server
and module-built (2026-10-18); no image carries them yet. `SOURCE.md` holds the base revisions, the reason for each
patch and the checks.

| Patch | Project | Effect |
| --- | --- | --- |
| `patches/packages-modules-connectivity/0001` | `packages/modules/Connectivity` | The BPF loopback check also refuses traffic between two apps of one user unless either side holds `USE_LOOPBACK_INTERFACE`; `freeholdos_loopback_open` = 1 switches it off. |
| `patches/frameworks-base/0001` | `frameworks/base` | `USE_LOOPBACK_INTERFACE` becomes a runtime permission. |
| `patches/frameworks-base/0002` | `frameworks/base` | The clipboard is readable only through a paste; a denied caller learns the same for every clip. |
| `patches/frameworks-base/0003`, `0005`, `0006` | `frameworks/base` | An ordinary app reads 40 settings keys (the framework's in-process readers); every other key reads as unset. |
| `patches/frameworks-base/0007` | `frameworks/base` | A clipboard change reaches the app that set the clip and a paste-grant holder, not an app whose read would be denied. |
| `patches/packages-providers-mediaprovider/0001` | `packages/providers/MediaProvider` | `MediaStore.getVersion` returns the per-app hash to every caller, not only to apps that target API 36 or higher. |
| `patches/frameworks-base/0004` | `frameworks/base` | An app gets the vibrator resonant frequency to 10 Hz, the Q factor to 0.5 and no acceleration curve. |
| `patches/system-sepolicy/0001` | `system/sepolicy` | `ro.boot.hardware.color` and `.coo` are hidden from third-party apps. |

`test/` holds the host test of the settings allowlist and its mutation check:

    test/run-settings-test.sh <SettingsHardening.java>
    test/mutate-settings.py <SettingsHardening.java>

`SettingsHardening.java` is created by `frameworks-base/0003` and changed by `0005` and `0006`; apply the three with
`git apply --include='*SettingsHardening.java'` in that order in an empty repository, or read it from the landed tree.
