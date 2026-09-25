# Duress Unlock — decoy mechanism, hardware-validated

Implements the full Duress Unlock decoy mechanism across three plans:
Tasks 1-6 of `docs/superpowers/plans/2026-08-21-duress-decoy-user-switch.md`
(a new SovereignOS-defined Android user type,
`USER_TYPE_FULL_SOVEREIGNOS_DECOY`; a dialog-free silent user switch; decoy
profile routing state storage; the `DecoyProfileHelper` keyguard-hook
orchestration; its wiring into `doVerifyCredentialInner`; and the
`set-decoy-profile`/`clear-decoy-profile` shell command backend), and all 8
tasks of `docs/superpowers/plans/2026-08-27-duress-real-user-quiescing-and-return.md`
(replacing the first plan's own freeze mechanism — found to always fail for
user 0 during that plan's own Task 7 — with app-level quiescing
(`RealUserQuiescer`: the existing cached-app freezer plus notification
suppression, including a call/media-notification-exemption fix found during
code review), and adding a previously-unspecified return-to-real-user path
(`ReturnToRealUserHelper`)); and the third plan
(`docs/superpowers/plans/2026-08-28-duress-decoy-session-indistinguishability.md`,
all 11 tasks) closing real, confirmed session-indistinguishability leaks
across `frameworks/base`, `build/release`, `packages/apps/Settings`, and
(new to this package) `packages/apps/Multiuser`. Per the owner-approved
architecture in ADR-042,
`docs/superpowers/specs/2026-08-21-duress-decoy-user-switch-design.md`,
`docs/superpowers/specs/2026-08-27-duress-real-user-quiescing-and-return-design.md`,
and
`docs/superpowers/specs/2026-08-28-duress-decoy-session-indistinguishability-design.md`
in `sovereignos-foundation`. This is the full real, end-to-end mechanism: a
registered decoy credential typed at the primary keyguard silently switches
to the decoy user (real user quiesced, notifications suppressed), the real
owner's credential typed at the decoy's own keyguard silently switches back
(real user unquiesced, notifications resume), and every UI surface that
lists device users is scoped so the decoy session never sees the real
owner's identity.

> **Patch numbering:** the series was squashed from 107 to 13 patches on
> 2026-09-25. Old patch numbers cited in this package and in ADR-053
> through ADR-096 are pre-squash; `SOURCE.md` has the old → new map.

## Boundary

Do not apply this package to a Phase 1 (or later) baseline tree without a
corresponding ADR naming this exact patch set
(`sovereignos-platform/AGENTS.md`'s hard rule). That ADR is **ADR-053** for Task 1
(2026-08-26, amending ADR-042), **ADR-054** for Task 2 (2026-08-26, also
adopting `AGENTS.md`'s new local-first patch-authoring discipline),
**ADR-055** (2026-08-26) for Task 3, **ADR-056** (2026-08-27) for
Tasks 4-6, **ADR-058** (2026-08-27) for the second plan's Tasks 1-8
(the quiescing/return-path replacement, patches `0008`-`0017`), **ADR-059**
(2026-08-28) for physical hardware validation of the first two plans,
**ADR-060** (2026-08-29) for the third plan's Tasks 1-9 (patches
`0018`-`0023`, `build-release` `0001`, `packages-apps-settings` `0001`-`0002`,
`packages-apps-multiuser` `0001`), **ADR-061** (2026-08-29) for Component
A's revert to inert (`build-release` `0002`-`0004`) after real validation
found it caused a device-wide keyguard-bypass regression, **ADR-062**/
**ADR-063** (2026-08-30) for physical `lynx` hardware validation of the
third plan's Components B/C/D, **ADR-064** (2026-08-30) for the closing
whole-branch review's own fixes (`frameworks-base` `0024`,
`packages-apps-settings` `0003`), **ADR-065** (2026-08-30) for the
Wallet QS tile decoy exemption (`frameworks-base` `0025`), a follow-up
product decision closing one of the items the third plan had left
open, **ADR-066** (2026-08-30) for a real, hardware-discovered
Critical identity-leak fix to the shared `getUsersForUiDisplay`
primitive (`frameworks-base` `0026`), **ADR-067** (2026-08-30)
commissioning an exhaustive, owner-requested audit for every remaining
leak surface (21 found;
`docs/superpowers/specs/2026-08-30-duress-decoy-remaining-leaks-audit.md`),
**ADR-068** (2026-08-30) for the first batch of fixes from that
audit — three Critical items (`frameworks-base` `0027`,
`packages-apps-settings` `0004`/`0005`) and four High items
(`frameworks-base` `0028`, `packages-apps-settings` `0006`), and
**ADR-069** (2026-08-30) for two more items — the dormant status-bar
multi-user icon (`frameworks-base` `0029`) and the Multiuser widget's
own data layer (`packages-apps-multiuser` `0002`), **ADR-071**
(2026-09-02) for resolving the audit's three low-confidence items (14
and 17 no-fix; 16 split into a moot citation and a real fix,
`packages-apps-settings` `0007`, closing three more call sites the
original audit missed), **ADR-072** (2026-09-03) for the consolidated
build plus the first real physical-hardware dynamic validation of any
of this audit's fixes, and **ADR-073** (2026-09-03) for a real
crash-to-reboot bug found during that validation, root-caused to a
six-year-old upstream AOSP wrong-context bug in
`UserBackupManagerService.java` (`frameworks-base` `0030`), fixed, and
hardware-confirmed by reproducing the exact original trigger — see
`SOURCE.md` for exactly what each patch does and why, and
`sovereignos-foundation`'s `docs/devices/lynx-duress-decoy-backup-context-crash-incident.md`
for the crash bug's full record.

## What this is not

This package is **build-verified** (multiple real `m systemimage` builds,
all clean; see `SOURCE.md`), **emulator-validated with real evidence**
(ADR-058: decoy match, real-user freeze, notification suppression, the
return path, and wrong-PIN/wipe-PIN non-confusion; ADR-060/061: Component
B/C identity-leak and setup-complete fixes confirmed working, Component A
confirmed broken and reverted), and **physical-hardware-validated with real
evidence** (ADR-059: first two plans' real match, decoy match, freeze,
return path, latency, and battery draw on a physical Pixel 7a; ADR-063:
third plan's Components B/C/D — identity-leak fixes and the setup-complete
write's causal before/after — confirmed on the same physical device;
ADR-064: the whole-branch-review fixes below independently re-confirmed on
physical hardware, including a real `IllegalArgumentException` proving the
decoy-type validation fix). Several items remain explicitly **not**
confirmed, not silently assumed: a dynamic trigger of the call/media-
notification-exemption fix against the specific backgrounded-user-0
scenario; the post-wipe emulator reboot timing; and, on physical hardware,
the lock-screen avatar switcher, the `com.android.multiuser` widget, the
clipboard overlay, the alarm icon, Material You theming, and now also the
Wallet QS tile exemption (`0025`, ADR-065) — all environment-limitation
UNCHECKED or simply not yet run, not fails — see ADR-063/064/065 for the
specific reasoning behind each. The Mobile-Data/Internet tile has no
per-user gate in this source tree at all (ADR-065), so it is not an open
item — there is nothing there to check.

**A real, Critical identity leak was found and fixed during that same
verification sweep, not merely a theoretical gap** (ADR-066,
`frameworks-base` `0026`): the QS footer's "Select user" dialog showed
the real Owner identity during a live, unlocked decoy session on
physical `lynx` hardware, directly contradicting ADR-063's claim that
this surface was leak-free. Root cause: `getUsersForUiDisplay`'s decoy
filter keyed off the Binder calling user, which is always user 0 for
SystemUI's floating windows regardless of which user is actually
current. Fixed in the shared primitive itself (keys off the device's
actual current user instead) so every present and future caller is
covered, not just this one call site. **Build-verified only as of this
entry** — dynamic (emulator or hardware) re-validation that the leak is
actually closed at runtime has not yet been done. Component A (the double-PIN-prompt fix)
ships as inert by deliberate design (ADR-061) — its own resource flip
(`0018`) is real platform content but harmless only in combination with
`build/release`'s own `DISABLED` flag override (`0002`-`0004`) *and*
(after `0024`) its own reverted-to-`false` resource default; a safe
redesign of the underlying bug is future, unscoped work.

**That dynamic re-validation has since happened, for real, on physical
hardware (ADR-071 through ADR-073).** The remaining-leaks audit's three
low-confidence items are resolved: 14 and 17 no-fix (confirmed
unreachable/safe by tracing the real caller chains), 16 split into a
moot citation and a real fix (`packages-apps-settings` `0007`, which
also closed three more real call sites — Compose "SPA" App Info
screens — the original 21-item audit missed). A physical `lynx`
candidate carrying all eleven fixes (the ten original plus `0007`) was
built, signed, and flashed by the owner, and the QS footer's "Select
user" dialog (this exact ADR-066 leak) and Settings ▸ Users are now
both **directly confirmed fixed on physical hardware** — screenshot
and full UI-text-dump evidence, not inference. Six narrower items
(Wi-Fi shared badge/dialog, uninstall dialogs, the status-bar icon,
the Multiuser widget, Guardian's cross-user AppOps check, and `0007`
itself) remain honestly UNCHECKED, not forced into a pass — no valid
trigger fixture existed for any of them during this validation round.

That same validation session also found, root-caused, fixed, and
hardware-confirmed a real crash-to-full-device-reboot bug unrelated to
any identity leak (ADR-073, `frameworks-base` `0030`): entering the
decoy PIN on a device where the real owner had never unlocked that
boot crashed `system_server` and rebooted the device, dropping the
decoy session. Root cause was a six-year-old upstream AOSP
wrong-context bug in `UserBackupManagerService.java`, latent on every
ordinary Android flow and exposed for what appears to be the first
time by Duress Unlock's own core security property. Full record:
`sovereignos-foundation`'s
`docs/devices/lynx-duress-decoy-backup-context-crash-incident.md`.

**The distinct, larger design question raised by the owner after that
round — hiding other users' identity from the decoy is not the same as
making the decoy session look like the device's one true primary
profile — has since been designed, implemented, and hardware-validated
(2026-09-03/04, `sovereignos-foundation` ADR-074 through ADR-079)**. A real
audit found only two genuinely cosmetic gaps behind this concern (the
QS footer's "End session" affordance, and two `packages/apps/Settings`
text/label choices) — everywhere else `isMain()`/`isAdminUser()` is
used it turned out to be a real, correctly-enforced capability
boundary and was deliberately left untouched. One new shared
display-only primitive, `UserManager.isMainForUiDisplay(int)`
(`frameworks-base` `0031`), closed both: the "End session" leak
(`0032`) and the two Settings text choices (`packages-apps-settings`
`0008`/`0009`). All four patches are hardware-confirmed working on
physical `lynx`; full record: `sovereignos-foundation`'s
`docs/devices/lynx-duress-decoy-visual-parity-findings.md`. ADR-065's
Wallet-tile exemption was the precedent this generalized from.

**A further, distinct instance of the same underlying problem was found
during that closure's own hardware validation and remains open**: the
device-wide `Settings.Global.USER_SWITCHER_ENABLED` flag — flipped on
permanently and automatically the moment any second user (including a
decoy) is ever created, read unfiltered by at least six real UI
surfaces (the QS icon above being only one) — is not covered by
`isMainForUiDisplay` at all, since it is a single global setting, not a
per-user list. Designed (`sovereignos-foundation`'s
`docs/superpowers/specs/2026-09-03-duress-decoy-user-switcher-visibility-design.md`)
and planned
(`docs/superpowers/plans/2026-09-03-duress-decoy-user-switcher-visibility.md`,
next patch `frameworks-base` `0033`) but **not yet implemented** as of
this writing.

## Relationship to `credential/duress-unlock-dummy/`

This package's base commit is `duress-unlock-dummy`'s own HEAD
(`e6e5746502a3`) — this is a continuation of the same `frameworks/base`
lineage, not an independent branch point. Future Stage 2 tasks should be
appended here as they land, the same way `duress-unlock-dummy` itself grew
from 1 to 2 `frameworks/base` commits across its own Task 1 and Task 2.

Provenance: `SOURCE.md`.
