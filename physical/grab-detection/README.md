# grab-detection

Five patches that lock the phone when it is pulled out of a hand in use and restart it after
60 seconds without a PIN (ADR-169). The design is
`docs/superpowers/specs/2026-10-15-grab-detection-design.md` in `sovereignos-foundation`.

| Patch | Project | Effect |
| --- | --- | --- |
| `patches/frameworks-base/0001` | `frameworks/base` | `GrabDetector`: the pure decision class. |
| `patches/frameworks-base/0002` | `frameworks/base` | The `Settings.Global` keys and the `ExtSettings` entries. |
| `patches/frameworks-base/0003` | `frameworks/base` | `GrabDetectionService`, the start in `SystemServer`, the hook in `KeyguardServiceDelegate`. |
| `patches/frameworks-base/0004` | `frameworks/base` | SystemUI: the message above the PIN entry after a grab lock. |
| `patches/packages-apps-Settings/0001` | `packages/apps/Settings` | The on/off switch on the exploit protection page. |

`test/` holds the host test of the decision class and a mutation check:

    test/run.sh <dir that holds com/android/server/grab/GrabDetector.java>
    test/mutate.py <the same dir>

The class is `frameworks-base/0001`; apply the patch to a copy of the baseline files and pass
`services/core/java`.
