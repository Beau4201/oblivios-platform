# Duress Unlock — decoy user type + silent switch (Stage 2, Tasks 1-2 of 9)

Implements Tasks 1-2 of AegisOS's Duress Unlock Stage 2: a new AegisOS-defined
Android user type, `USER_TYPE_FULL_AEGISOS_DECOY` (`FLAG_FULL`, delayed
locking explicitly opted in), and a dialog-free silent user switch
(`ActivityManagerInternal`/`UserController.switchToUserSilently`), per the
owner-approved decoy-profile architecture in ADR-042 and
`docs/superpowers/specs/2026-08-21-duress-decoy-user-switch-design.md` in
`aegisos-foundation`.

## Boundary

Do not apply this package to a Phase 1 (or later) baseline tree without a
corresponding ADR naming this exact patch set
(`aegisos-platform/AGENTS.md`'s hard rule). That ADR is **ADR-053** for Task 1
(2026-08-26, amending ADR-042) and **ADR-054** for Task 2 (2026-08-26, also
adopting `AGENTS.md`'s new local-first patch-authoring discipline).

## What this is not

This is only Tasks 1-2 of 9 from
`docs/superpowers/plans/2026-08-21-duress-decoy-user-switch.md`. Task 2's own
Step 4 (a real `m systemimage` build proving this compiles) has not yet run —
treat this package as landed-on-branch, not build-verified. Routing-state
storage, the `DecoyProfileHelper` keyguard hook, wiring into
`doVerifyCredentialInner`, the setup-flow shell command, and
emulator/physical-hardware validation are Tasks 3-9 — **not started, not
part of this package.**

## Relationship to `credential/duress-unlock-dummy/`

This package's base commit is `duress-unlock-dummy`'s own HEAD
(`e6e5746502a3`) — this is a continuation of the same `frameworks/base`
lineage, not an independent branch point. Future Stage 2 tasks should be
appended here as they land, the same way `duress-unlock-dummy` itself grew
from 1 to 2 `frameworks/base` commits across its own Task 1 and Task 2.

Provenance: `SOURCE.md`.
