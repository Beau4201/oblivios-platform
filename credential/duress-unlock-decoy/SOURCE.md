# Source record

## Commits (verbatim from `git format-patch`)

### `frameworks/base`

- `b51bc3e0d4d9273164194eed5c97c1119ecc1710` — "aegisos: add
  USER_TYPE_FULL_AEGISOS_DECOY user type" (`patches/frameworks-base/0001`,
  real build-server history)
- `bc0f20e0166a8c75334dbf0e32e03d46b1a1ed71` — "aegisos: accept
  USER_TYPE_FULL_AEGISOS_DECOY into system-current.txt"
  (`patches/frameworks-base/0002`, real build-server history)
- `c05c6b25fae8` — "aegisos: add dialog-free silent user switch for duress
  decoy" (`patches/frameworks-base/0003`, **real build-server SHA** — the
  patch file itself was generated from local commit `591c7e526e6ee9de5941245f0d765defd0fee930`
  in a disposable worktree; `git am` reconstructs the commit against the
  real tree, so it gets a genuinely different SHA even for byte-identical
  content. Verify the real SHA with `git log` on the branch after `git am`
  — never assume the local worktree's own commit SHA carries over) —
  authored under `AGENTS.md`'s new local-first discipline (ADR-054): edited
  in a disposable local git repo seeded from the real branch HEAD, not on
  the build server directly, then landed there with `git am --3way` (applied
  cleanly, no conflicts).
- `204a46b432f3` — "aegisos: add decoy profile routing state storage"
  (`patches/frameworks-base/0004`, real build-server SHA) — authored
  under ADR-054's local-first discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-decoy-task3/`, seeded read-only from
  the real branch HEAD `c05c6b25fae8`) and landed on the real branch with
  `git am --3way` (applied cleanly, no conflicts).
- `10f858743765` — "aegisos: add DecoyProfileHelper keyguard hook and
  freeze/switch orchestration" (`patches/frameworks-base/0005`, real
  build-server SHA) — new `DecoyProfileHelper.java` plus
  `LockSettingsService.java`/`ILockSettings.aidl`/`LockPatternUtils.java`
  changes. Diverges from the plan's own literal sample code in two
  places, both found by checking real source rather than transcribing the
  brief: added `checkWritePermission()` to `setDecoyProfile`/
  `clearDecoyProfile` (every sibling `@Override` in the real
  `LockSettingsService.java` has this call; the plan's sample omitted it —
  a real gap in the plan, not a style choice), and added a guard rejecting
  `decoyUserId == UserHandle.USER_SYSTEM` in `setDecoyProfile` (closes an
  unbounded-recursion path the design's own recursion-prevention
  — "userId == decoyUserId != USER_SYSTEM" — silently assumed rather than
  enforced).
- `ed256ec87fb1` — "aegisos: wire decoy routing into
  doVerifyCredentialInner" (`patches/frameworks-base/0006`, real
  build-server SHA) — the `doVerifyCredentialInner` not-matched branch now
  calls `decoyProfileHelper.maybeRouteToDecoy`, matching the plan exactly.
- `41aeb66e0893` — "aegisos: add set-decoy-profile/clear-decoy-profile
  shell commands" (`patches/frameworks-base/0007`, real build-server SHA)
  — `LockSettingsShellCommand.java` dispatch + two handler methods. An
  earlier draft of this patch placed the file at the wrong path (repo root
  instead of `services/core/java/com/android/server/locksettings/`), which
  would have broken `git am` against the real tree — caught in review
  before landing, corrected before this SHA was produced.
- Base commit: `e6e5746502a3ea1b8970210cb9ccbfac979e490d` (`credential/duress-unlock-dummy`'s own HEAD — this branch continues that lineage)

**Build verification: all of Tasks 1-6 (`0001`-`0007`) compile together.**
Two real builds confirm this: Task 2's own build (`m systemimage`,
1h51m36s) covered `0001`-`0003` (the only files it could have, since its
ninja graph was generated before `0004` existed); a second, consolidated
build launched after `0005`-`0007` landed (1h29m45s) covered all of
`0001`-`0007` together, `#### build completed successfully ####` both
times, confirmed via the real build server's own log output, not assumed
from `git am`/exit codes.

`0001`/`0002` were generated via `git format-patch` from real build-server
history, author identity rewritten to a generic `AegisOS Build
<build@aegisos.local>` identity before committing here, then verified to
reproduce the real branch tree byte-for-byte via `git am` against the base
commit in a disposable worktree before being trusted. `0003`/`0004` were
authored directly under that same `AegisOS Build <build@aegisos.local>`
identity in a disposable local worktree
(`aegisos-platform/.worktrees/duress-decoy-task2/` and `-task3/`
respectively, each seeded read-only from the real branch HEAD at the time)
and landed on the real branch with `git am`. The commit SHA cited above for
each is the **real post-`git am` branch SHA**, confirmed via `git log` on
the remote — not the disposable worktree's own local commit SHA, which
differs (different parent/committer timestamp) even when the patch content
is byte-identical. An earlier version of this file incorrectly cited
`0003`'s local worktree SHA as if it were the real branch commit; corrected
here after discovering the mismatch while investigating Task 3's build.

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
  path Task 4's `DecoyProfileHelper` will call. No caller exists yet.
- `0004`: adds `DecoyProfileState` — off/registered routing state storage
  (int decoyUserId) using `LockSettingsStorage` with fail-closed parsing.
  Provides `maybeGet()`, `create()`, `save()`, `delete()` matching the
  `DuressCredentials` pattern. Task 4 consumes all these methods directly.
- `0005`: adds `DecoyProfileHelper` — the keyguard-hook orchestration class
  (decoy credential check at symmetric cost, freeze the real user, silent
  switch to the decoy), plus the `ILockSettings`/`LockPatternUtils` plumbing
  Task 6's shell command needs. No caller in `doVerifyCredentialInner` yet
  (that's `0006`).
- `0006`: wires `decoyProfileHelper.maybeRouteToDecoy` into
  `doVerifyCredentialInner`'s not-matched path, scoped to
  `UserHandle.USER_SYSTEM`/`LockDomain.Primary` only — the actual real,
  end-to-end mechanism is live after this patch.
- `0007`: `set-decoy-profile`/`clear-decoy-profile` shell subcommands — the
  real setup-flow backend (not test scaffolding), mirroring
  `set-duress-credentials`'s existing dispatch pattern exactly.

## Verification trail

- ADR-042 (design approval, 2026-08-21) — architecture rationale (why
  `FLAG_FULL` not `USER_TYPE_PROFILE_PRIVATE`, why delayed locking must be
  explicitly opted in) lives there, not repeated here.
- ADR-053 (2026-08-26) — Task 1's citation, plus the router correction
  noting Task 1 of 9 landed.
- ADR-054 (2026-08-26) — Task 2's citation, plus adoption of the local-first
  patch-authoring discipline this and all future patches now follow.
- ADR-055 (2026-08-26) — Task 3's citation, plus two process-defect
  findings (a wrong SHA citation; a patch landed mid-build) folded into
  `AGENTS.md`.
- ADR-056 (2026-08-27) — Tasks 4-6's citation, plus both real builds'
  results (Task 2's and the consolidated one) confirming `0001`-`0007`
  compile together. Tasks 7-9 (emulator validation, physical hardware
  validation, findings) remain not started.
