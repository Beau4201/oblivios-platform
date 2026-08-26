# Duress Unlock — decoy mechanism, code-complete (Stage 2, Tasks 1-6 of 9)

Implements Tasks 1-6 of AegisOS's Duress Unlock Stage 2: a new AegisOS-defined
Android user type, `USER_TYPE_FULL_AEGISOS_DECOY` (`FLAG_FULL`, delayed
locking explicitly opted in); a dialog-free silent user switch
(`ActivityManagerInternal`/`UserController.switchToUserSilently`); decoy
profile routing state storage (`DecoyProfileState`); the `DecoyProfileHelper`
keyguard-hook orchestration; its wiring into `doVerifyCredentialInner`; and
the `set-decoy-profile`/`clear-decoy-profile` shell command backend — per the
owner-approved decoy-profile architecture in ADR-042 and
`docs/superpowers/specs/2026-08-21-duress-decoy-user-switch-design.md` in
`aegisos-foundation`. This is the full real, end-to-end mechanism: a
registered decoy credential typed at the primary keyguard now silently
switches to the decoy user, at cost symmetric with the no-decoy case.

## Boundary

Do not apply this package to a Phase 1 (or later) baseline tree without a
corresponding ADR naming this exact patch set
(`aegisos-platform/AGENTS.md`'s hard rule). That ADR is **ADR-053** for Task 1
(2026-08-26, amending ADR-042), **ADR-054** for Task 2 (2026-08-26, also
adopting `AGENTS.md`'s new local-first patch-authoring discipline),
**ADR-055** (2026-08-26) for Task 3, and **ADR-056** (2026-08-27) for
Tasks 4-6.

## What this is not

This is Tasks 1-6 of 9 from
`docs/superpowers/plans/2026-08-21-duress-decoy-user-switch.md` —
**build-verified** (two real `m systemimage` builds, both clean; see
`SOURCE.md`) but **not yet functionally validated**: no emulator or physical
device has exercised the real match/decoy match/wrong PIN/wipe-PIN sequence
yet. Emulator validation, physical `lynx` hardware validation and latency
measurement, and recording findings are Tasks 7-9 — **not started, not part
of this package.**

## Relationship to `credential/duress-unlock-dummy/`

This package's base commit is `duress-unlock-dummy`'s own HEAD
(`e6e5746502a3`) — this is a continuation of the same `frameworks/base`
lineage, not an independent branch point. Future Stage 2 tasks should be
appended here as they land, the same way `duress-unlock-dummy` itself grew
from 1 to 2 `frameworks/base` commits across its own Task 1 and Task 2.

Provenance: `SOURCE.md`.
