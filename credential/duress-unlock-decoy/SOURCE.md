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
- ADR-058 (2026-08-27) — the second plan's Tasks 1-8 (see below): Task 7 of
  the first plan found the freeze mechanism above (`stopUserWithDelayedLocking`)
  can never succeed for user 0 — a universal AOSP limitation, not a bug in
  this project's own code — and this second plan replaces it with app-level
  quiescing plus a new return path, then validates the whole mechanism on a
  real emulator.

## Second plan: real-user quiescing and return path (`0008`-`0017`)

`docs/superpowers/plans/2026-08-27-duress-real-user-quiescing-and-return.md`
continues the same `frameworks/base` branch (`aegisos-duress-decoy-switch`)
from `0007`'s own HEAD (`41aeb66e0893`). All 10 commits below were authored
under the same local-first discipline (disposable worktrees under
`aegisos-platform/.worktrees/duress-quiesce-task*/`, landed with
`git am --3way`) and independently re-verified: patches `0008`-`0017` were
regenerated fresh via `git format-patch` directly from the real remote
branch history (rather than trusted from each task's own disposable
worktree) and confirmed to reproduce the real branch tree byte-for-byte —
applying all 10 in order onto a fresh worktree seeded at the real base
commit (`41aeb66e0893`) and diffing the result against the real tip
(`295f6f9cf610`) produced an empty diff.

- `6ef616c714af` — "aegisos: add RealUserQuiescer for duress decoy
  freeze/unfreeze" (`0008`) — new `RealUserQuiescer.java`
  (`com.android.server.am`): freezes/unfreezes a user's app processes via
  the existing `CachedAppOptimizer`, tracks which users are currently
  quiesced. No caller yet.
- `35dee8ebff74` — "aegisos: expose quiesceUserApps/unquiesceUserApps via
  ActivityManagerInternal" (`0009`) — three new abstract methods on
  `ActivityManagerInternal` (`quiesceUserApps`, `unquiesceUserApps`,
  `isUserQuiescedForDuress`), implemented in `ActivityManagerService
  .LocalService` delegating to a new `mRealUserQuiescer` field. The
  implementer caught a real bug in the plan's own sample code here: a field
  initializer for `mRealUserQuiescer` would have captured `mCachedAppOptimizer`
  as `null` (field initializers run before constructor bodies); fixed by
  assigning in the constructor body instead, after `mCachedAppOptimizer`
  itself is constructed.
- `15162045ce05` — "aegisos: suppress notifications for a duress-quiesced
  user" (`0010`) — an 8-line addition to `NotificationManagerService
  .checkDisqualifyingFeatures`, blocking notifications for a quiesced user.
  A task reviewer found this alone was insufficient (see `0017` below).
- `834412c4b589` — "aegisos: unquiesce a user automatically when it becomes
  current" (`0011`) — a one-line hook in `UserController`, immediately after
  `mCurrentUserId = userId` (outside the `synchronized (mLock)` block),
  calling `unquiesceUserApps`. Fires for every user that becomes current,
  not just a return from the decoy — self-healing even against a raw
  `adb shell am switch-user 0`.
- `1f4a79aabfa4` — "aegisos: quiesce the real user after switching, not
  before" (`0012`) — the actual fix for the plan's core problem:
  `DecoyProfileHelper.freezeAndSwitchToDecoy` now calls
  `switchToUserSilently` FIRST, then `quiesceUserApps` on the now-backgrounded
  real user — reversing the original (broken) order, which tried to freeze
  the real user before switching away from it (impossible for user 0, since
  `stopUsersLU` also independently refuses to stop the *current* user for
  any user). Removed the now-unused `IActivityManager` import.
- `f15c3d86962e` — "aegisos: add verifyPrimaryCredentialNoRouting for the
  duress return path" (`0013`) — a new package-private method on
  `LockSettingsService`, replicating `doVerifyCredentialInner`'s
  rate-limited verify sequence for a non-special user without any of its
  hook/side-effect logic (no decoy routing, no repair-mode write, no
  biometric lockout reset) — exists so the return path can check a
  credential against the real owner without re-entering
  `doVerifyCredentialInner` and re-triggering the forward decoy hook.
- `079d5588f483` — "aegisos: zeroize the synthetic password in
  verifyPrimaryCredentialNoRouting" (`0014`) — a real security-hygiene gap
  the implementer found and fixed as a separate follow-up commit (not an
  amend): `doVerifyCredentialInner` always zeroizes
  `AuthenticationResult.syntheticPassword` (raw decrypted key material)
  after use; the new method in `0013` didn't. Fixed with a `try/finally`
  around the return.
- `62b84f2aa8d1` — "aegisos: add ReturnToRealUserHelper, wire the return
  path" (`0015`) — new `ReturnToRealUserHelper.java`
  (`com.android.server.locksettings`), the mirror image of
  `DecoyProfileHelper`: routes a failed credential check for the
  currently-registered decoy user back to the real owner if the entered
  credential matches the real owner's own primary credential (via `0013`'s
  method, never `doVerifyCredentialInner`), then calls
  `switchToUserSilently(UserHandle.USER_SYSTEM)`. Wired into
  `LockSettingsService` (new field/construction mirroring
  `decoyProfileHelper`'s own, and a new `else if` branch in
  `doVerifyCredentialInner`'s not-matched path). Deliberately does not call
  `unquiesceUserApps` itself — `0011`'s generic hook handles it.
- `c1c367950ab6` — "aegisos: fix ReturnToRealUserHelper.java path (patch
  0014 misapplied it under frameworks/base/)" (`0016`) — the first attempt
  at `0015` placed the new file under a spurious extra `frameworks/base/`
  path prefix (the repo root is already `frameworks/base`), which broke
  compilation; a task reviewer's diff-stat check (2 files changed, 0
  deletions) confirmed this follow-up commit cleanly relocated the file
  rather than leaving a duplicate.
- `295f6f9cf610` — "aegisos: suppress call and media notifications for a
  duress-quiesced user" (`0017`) — fixes a real gap a task reviewer found in
  `0010`: the pre-existing `if (isBlocked && !(n.isMediaNotification() ||
  isCallNotification(pkg, uid, n)))` gate in `checkDisqualifyingFeatures`
  unconditionally exempted call/media notifications from suppression,
  regardless of *why* `isBlocked` became true — so a real incoming call for
  the quiesced real user would still ring/display. Introduces a local
  `quiescedForDuress` boolean and changes the gate to
  `isBlocked && (quiescedForDuress || !(...))`, so only the duress-quiesced
  case loses the exemption; every other blocking reason (banned app,
  snoozed, bridged-blocked) keeps its exemption unchanged. Independently
  truth-table-verified by two different reviewers across all four boolean
  combinations.
- Base commit for this second plan: `41aeb66e0893` (the first plan's own
  Task 6 HEAD, `patches/frameworks-base/0007`).

**Build verification:** a consolidated `m systemimage` build after all 10
commits landed: `ninja: 1h3m33.34s Build Succeeded: 2444 steps`,
`#### build completed successfully (01:03:38) ####` — confirms all 17
patches (`0001`-`0017`) compile together into a complete system image, not
just individual `m services` checks (each of `0008`-`0017` was also
independently confirmed via a fast `m services` check immediately after
landing, before the consolidated build ran).

**Emulator validation (ADR-058):** real, direct evidence gathered against
this exact build (`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`):
decoy match (`mCurrentUserId` switch, confirmed twice); real-user freeze
(32 of 36 user-0 processes `isFrozen=true`, remaining 4 plausibly excluded
by pre-existing AOSP freezer non-cached rules); notification suppression
(`dumpsys notification` usage-stats proof with a differential control case:
the quiesced user's notification blocked, the active user's own notification
posts normally); the return path (real match against the real owner's
credential, real switch back, unquiescing proven via a user-0 notification
posting normally again afterward); wrong-PIN and wipe-PIN both confirmed
correct and not confused with decoy routing (a real wipe fired, with direct
`DuressWipe`/storage-key-deletion/device-poweroff evidence). Two items are
explicitly left open (see `README.md`'s "What this is not" section and
ADR-058 for the full detail): the call/media-exemption fix (`0017`) lacks a
successful dynamic trigger against the specific backgrounded-user-0 scenario
in available emulator tooling; and a post-wipe emulator reboot
characterization was time-boxed and left inconclusive. A real, unrelated
infrastructure defect was also found and fixed during validation: the
emulator's initial cold boot hung 1h46m from loading a stale quickboot
snapshot against the freshly built image; fixed with `-no-snapshot-load`.
