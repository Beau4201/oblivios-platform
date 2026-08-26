# Source record

## Commits (verbatim from `git format-patch`)

### `frameworks/base`

- `b51bc3e0d4d9273164194eed5c97c1119ecc1710` — "aegisos: add
  USER_TYPE_FULL_AEGISOS_DECOY user type" (`patches/frameworks-base/0001`,
  real build-server history)
- `bc0f20e0166a8c75334dbf0e32e03d46b1a1ed71` — "aegisos: accept
  USER_TYPE_FULL_AEGISOS_DECOY into system-current.txt"
  (`patches/frameworks-base/0002`, real build-server history)
- `591c7e526e6ee9de5941245f0d765defd0fee930` — "aegisos: add dialog-free
  silent user switch for duress decoy" (`patches/frameworks-base/0003`) —
  authored under `AGENTS.md`'s new local-first discipline (ADR-054): edited
  in a disposable local git repo seeded from the real branch HEAD, not on
  the build server directly, then landed there with `git am --3way` (applied
  cleanly, no conflicts).
- Base commit: `e6e5746502a3ea1b8970210cb9ccbfac979e490d` (`credential/duress-unlock-dummy`'s own HEAD — this branch continues that lineage)

`0001`/`0002` were generated via `git format-patch` from real build-server
history, author identity rewritten to a generic `AegisOS Build
<build@aegisos.local>` identity before committing here, then verified to
reproduce the real branch tree byte-for-byte via `git am` against the base
commit in a disposable worktree before being trusted. `0003` was authored
directly under that same `AegisOS Build <build@aegisos.local>` identity in a
disposable local worktree (`aegisos-platform/.worktrees/duress-decoy-task2/`,
seeded read-only from the real branch HEAD `bc0f20e0166a`) and landed on the
real branch with `git am`, so no separate reproduction check was needed —
the patch committed here and the commit applied on the real branch are the
same object.

## Apply

Apply `duress-unlock-dummy`'s `frameworks-base` patches first (this
package's base commit assumes them already applied), then this package's:

```sh
cd frameworks/base && git am ../../credential/duress-unlock-dummy/patches/frameworks-base/*.patch
cd frameworks/base && git am ../../credential/duress-unlock-decoy/patches/frameworks-base/*.patch
```

## What the changes do

- `0001`: adds `USER_TYPE_FULL_AEGISOS_DECOY` to `UserManager.java` and a
  matching `UserTypeFactory.getDefaultTypeFullAegisosDecoy()` builder
  (`FLAG_FULL`, max 1, `allowStoppingUserWithDelayedLocking(true)`).
- `0002`: the matching `core/api/system-current.txt` entry (formal
  `@SystemApi` acceptance) — a one-line addition, confirmed by the commit's
  own message to add exactly that field and nothing else.
- `0003`: adds `UserController.switchToUserSilently`/
  `ActivityManagerInternal.switchToUserSilently`/`ActivityManagerService
  .LocalService.switchToUserSilently` — the dialog-free foreground-switch
  path Task 4's `DecoyProfileHelper` (not yet implemented) will call. No
  caller exists yet; this task only proves the mechanism exists and compiles
  (build not yet run — see Boundary in `README.md`).

## Verification trail

- ADR-042 (design approval, 2026-08-21) — architecture rationale (why
  `FLAG_FULL` not `USER_TYPE_PROFILE_PRIVATE`, why delayed locking must be
  explicitly opted in) lives there, not repeated here.
- ADR-053 (2026-08-26) — Task 1's citation, plus the router correction
  noting Task 1 of 9 landed.
- ADR-054 (2026-08-26) — Task 2's citation, plus adoption of the local-first
  patch-authoring discipline this and all future patches now follow.
