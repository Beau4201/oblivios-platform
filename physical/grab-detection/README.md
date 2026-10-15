# grab-detection

Eight patches that lock the phone when it is pulled out of a hand in use and restart it after
60 seconds without a PIN (ADR-169). The design is
`docs/superpowers/specs/2026-10-15-grab-detection-design.md` in `sovereignos-foundation`.

| Patch | Project | Effect |
| --- | --- | --- |
| `patches/frameworks-base/0001` | `frameworks/base` | `GrabDetector`: the pure decision class. |
| `patches/frameworks-base/0002` | `frameworks/base` | The `Settings.Global` keys and the `ExtSettings` entries. |
| `patches/frameworks-base/0003` | `frameworks/base` | `GrabDetectionService`, the start in `SystemServer`, the hook in `KeyguardServiceDelegate`. |
| `patches/frameworks-base/0004` | `frameworks/base` | SystemUI: the message above the PIN entry after a grab lock. |
| `patches/frameworks-base/0005` | `frameworks/base` | `GrabDetector`: a new touch voids the release before it; a peak without touch is reported (marked untouched) for the shadow log. |
| `patches/frameworks-base/0006` | `frameworks/base` | `GrabDetectionService`: no exception leaves the handler thread, the shadow log line for an untouched peak, the timer is cancelled when the keyguard does not show after the lock. |
| `patches/frameworks-base/0007` | `frameworks/base` | The `@hide` tags of the keys on their own line; one message text for every credential type. |
| `patches/packages-apps-Settings/0001` | `packages/apps/Settings` | The on/off switch on the exploit protection page. |

`test/` holds the host test of the decision class and a mutation check:

    test/run.sh <dir that holds com/android/server/grab/GrabDetector.java>
    test/mutate.py <the same dir>

The class is `frameworks-base/0001` plus `0005`; apply the patches to a copy of the baseline files and
pass `services/core/java`.
