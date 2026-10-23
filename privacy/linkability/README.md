# linkability

Fourteen patches that close channels through which two apps of one user link themselves to one device or fingerprint it
(ADR-174, amended by ADR-175 and ADR-179). The design is `docs/superpowers/specs/2026-10-18-linkability-kills-design.md` in `freeholdos-foundation`. Landed on the build server
and module-built (2026-10-18); the `lynx` image `2026101802` carries the first ten (`IMG-009`, `IMG-010`) and `2026102101` the four of ADR-179 (`IMG-011`, `IMG-012`). `SOURCE.md` holds the base revisions, the reason for each
patch and the checks.

| Patch | Project | Effect |
| --- | --- | --- |
| `patches/packages-modules-connectivity/0001` | `packages/modules/Connectivity` | The BPF loopback check also refuses traffic between two apps of one user unless either side holds `USE_LOOPBACK_INTERFACE`; `freeholdos_loopback_open` = 1 switches it off. |
| `patches/frameworks-base/0001` | `frameworks/base` | `USE_LOOPBACK_INTERFACE` becomes a runtime permission. |
| `patches/frameworks-base/0002` | `frameworks/base` | The clipboard is readable only through a paste; a denied caller learns the same for every clip. |
| `patches/frameworks-base/0003`, `0005`, `0006` | `frameworks/base` | An ordinary app reads 40 settings keys (the framework's in-process readers); every other key reads as unset. |
| `patches/frameworks-base/0007` | `frameworks/base` | A clipboard change reaches the app that set the clip and a paste-grant holder, not an app whose read would be denied. |
| `patches/packages-modules-connectivity/0002` | `packages/modules/Connectivity` | A broadcast or multicast datagram between two apps on the loopback path is refused too, and an app without a loopback permission does not receive a looped-back multicast or broadcast datagram (ADR-179). |
| `patches/frameworks-base/0008` | `frameworks/base` | The vibrator calibration is rounded for every app uid, SDK-sandbox and isolated processes included (ADR-179). |
| `patches/frameworks-base/0009` | `frameworks/base` | The rounded calibration keeps a valid frequency profile (usable range, flat curve) instead of an invalid one (ADR-179). |
| `patches/frameworks-base/0010` | `frameworks/base` | `accessibility_enabled` is readable by apps, and an app reads its own entry of the notification listener, accessibility service and input method lists (ADR-179). |
| `patches/packages-providers-mediaprovider/0001` | `packages/providers/MediaProvider` | `MediaStore.getVersion` returns the per-app hash to every caller, not only to apps that target API 36 or higher. |
| `patches/frameworks-base/0004` | `frameworks/base` | An app gets the vibrator resonant frequency to 10 Hz, the Q factor to 0.5 and no acceleration curve. |
| `patches/system-sepolicy/0001` | `system/sepolicy` | `ro.boot.hardware.color` and `.coo` are hidden from third-party apps. |

`test/` holds the host tests of the settings allowlist and of the vibrator rounding, each with its mutation check:

    test/run-settings-test.sh <SettingsHardening.java>
    test/mutate-settings.py <SettingsHardening.java>
    test/run-vibrator-test.sh <VibratorCoarse.java>
    test/mutate-vibrator.py <VibratorCoarse.java>
    test/run-loopback-test.sh <netd.c> <netd.h>
    test/mutate-loopback.py <netd.c> <netd.h>

`SettingsHardening.java` is created by `frameworks-base/0003` and changed by `0005` and `0006`; apply the three with
`git apply --include='*SettingsHardening.java'` in that order in an empty repository, or read it from the landed tree.
