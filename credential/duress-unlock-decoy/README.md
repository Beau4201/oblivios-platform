# Duress Unlock — decoy mechanism, emulator-validated

Implements the full Duress Unlock decoy mechanism across two plans:
Tasks 1-6 of `docs/superpowers/plans/2026-08-21-duress-decoy-user-switch.md`
(a new AegisOS-defined Android user type,
`USER_TYPE_FULL_AEGISOS_DECOY`; a dialog-free silent user switch; decoy
profile routing state storage; the `DecoyProfileHelper` keyguard-hook
orchestration; its wiring into `doVerifyCredentialInner`; and the
`set-decoy-profile`/`clear-decoy-profile` shell command backend), and all 8
tasks of `docs/superpowers/plans/2026-08-27-duress-real-user-quiescing-and-return.md`
(replacing the first plan's own freeze mechanism — found to always fail for
user 0 during that plan's own Task 7 — with app-level quiescing
(`RealUserQuiescer`: the existing cached-app freezer plus notification
suppression, including a call/media-notification-exemption fix found during
code review), and adding a previously-unspecified return-to-real-user path
(`ReturnToRealUserHelper`)). Per the owner-approved architecture in ADR-042,
`docs/superpowers/specs/2026-08-21-duress-decoy-user-switch-design.md`, and
`docs/superpowers/specs/2026-08-27-duress-real-user-quiescing-and-return-design.md`
in `aegisos-foundation`. This is the full real, end-to-end mechanism: a
registered decoy credential typed at the primary keyguard silently switches
to the decoy user (real user quiesced, notifications suppressed), and the
real owner's credential typed at the decoy's own keyguard silently switches
back (real user unquiesced, notifications resume).

## Boundary

Do not apply this package to a Phase 1 (or later) baseline tree without a
corresponding ADR naming this exact patch set
(`aegisos-platform/AGENTS.md`'s hard rule). That ADR is **ADR-053** for Task 1
(2026-08-26, amending ADR-042), **ADR-054** for Task 2 (2026-08-26, also
adopting `AGENTS.md`'s new local-first patch-authoring discipline),
**ADR-055** (2026-08-26) for Task 3, **ADR-056** (2026-08-27) for
Tasks 4-6, and **ADR-058** (2026-08-27) for the second plan's Tasks 1-8
(the quiescing/return-path replacement, patches `0008`-`0017`).

## What this is not

This package is **build-verified** (three real `m systemimage` builds, all
clean; see `SOURCE.md`) and **emulator-validated with real evidence** (ADR-058:
decoy match, real-user freeze, notification suppression, the return path,
and wrong-PIN/wipe-PIN non-confusion all confirmed via direct `dumpsys`/
`logcat` evidence, not assumption). Two items are explicitly **not** confirmed
and remain open: a dynamic trigger of the call/media-notification-exemption
fix against the specific backgrounded-user-0 scenario (static/logical
confirmation only — no tooling in the emulator environment could construct
one), and the post-wipe emulator reboot timing (time-boxed and left
genuinely inconclusive, assessed as a likely shared-server environment
artifact rather than a regression). Physical `lynx` hardware validation and
latency/battery measurement, and recording findings in
`docs/research/duress-unlock-api-verification.md`, are Tasks 8-9 of the
*first* plan — **not started, not part of this package.**

## Relationship to `credential/duress-unlock-dummy/`

This package's base commit is `duress-unlock-dummy`'s own HEAD
(`e6e5746502a3`) — this is a continuation of the same `frameworks/base`
lineage, not an independent branch point. Future Stage 2 tasks should be
appended here as they land, the same way `duress-unlock-dummy` itself grew
from 1 to 2 `frameworks/base` commits across its own Task 1 and Task 2.

Provenance: `SOURCE.md`.
