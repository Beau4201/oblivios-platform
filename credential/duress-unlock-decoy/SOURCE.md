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

`build/release` has no prerequisite package — apply directly onto its real
HEAD (`8976d78feac337469081409228886065a4dfefab`, manifest tag
`2026071500`), on a branch (not detached HEAD):

```sh
cd build/release && git checkout -b aegisos-duress-decoy-switch && \
  git am ../../credential/duress-unlock-decoy/patches/build-release/*.patch
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

## Third plan: session indistinguishability, Component A (`0018`)

`docs/superpowers/plans/2026-08-28-duress-decoy-session-indistinguishability.md`
continues the same `frameworks/base` branch (`aegisos-duress-decoy-switch`).
The real branch tip at fetch time was `5f3a562a4eefea8a14c2aaf42d14fc9c49cf0c1a`
— ahead of `0017`'s own recorded HEAD (`295f6f9cf610`) because two
temporary ADR-059 physical-hardware-validation instrumentation commits were
added and then reverted on the branch after `0017` landed; neither was
independently exported as a patch here since both were temporary and are
already reverted on the real branch. This section covers Task 2 only (the
plan's first code-touching task); later tasks in this plan will add further
entries here as they land.

- `ebb58e3ccc2034c2685b889ceecbcd1830b382ef` — "aegisos: enable
  skip-keyguard-when-switching-to-unlocked-users"
  (`patches/frameworks-base/0018`, real build-server SHA) — flips
  `config_multiuserSkipKeyguardWhenSwitchingToUnlockedUsers` in
  `core/res/res/values/config.xml` from `false` to `true`, with an
  AegisOS-authored comment explaining why (a Duress Unlock decoy switch's
  credential was already verified moments earlier by `DecoyProfileHelper`,
  so re-locking and demanding the same PIN again is spurious). No new
  AegisOS code — this enables a dormant upstream AOSP mechanism that is
  otherwise unused. Device-wide effect for any user switch, not
  decoy-specific: confirmed by reading the real consumers directly
  (`UserController.java` line ~542, `KeyguardViewMediator.java` line
  ~712) rather than trusting the plan's own description — both gate on
  `android.multiuser.Flags.credentialCapture()` AND this resource bool
  AND `StorageManager.isCeStorageUnlocked(userId)` AND
  `mLockPatternUtils.isTrustAllowedForUser(userId)`, so the skip only
  ever applies to a target user whose CE storage is already unlocked and
  trusted — this task does not by itself relax either of those two
  additional runtime gates. Authored under `AGENTS.md`'s local-first
  discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-indistinguishability-task2a/`,
  seeded read-only from the real branch HEAD
  `5f3a562a4eefea8a14c2aaf42d14fc9c49cf0c1a`) and landed on the real
  branch with `git am --3way` (applied cleanly, no conflicts).
- Base commit for this third plan's Task 2:
  `5f3a562a4eefea8a14c2aaf42d14fc9c49cf0c1a` — the real branch tip at
  fetch time, which sits ahead of `0017`'s own recorded HEAD
  (`295f6f9cf610`, `patches/frameworks-base/0017`) due to two temporary
  ADR-059 instrumentation commits added and reverted on the branch after
  `0017` landed; not independently exported as patches since they were
  temporary.

**Build verification:** a fast `m services` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`) after `0018`
landed: `#### build completed successfully (14:23 (mm:ss)) ####`. This
confirms the resource-only XML change compiles; it is not a consolidated
`m systemimage` build and does not by itself validate runtime behavior —
that is later tasks' job (10/11 in this plan check for device-wide keyguard
regression on ordinary, non-decoy multi-user switches).

**Companion change, different repository:** this same task also enables
the `android.multiuser.credential_capture` aconfig flag (both switches are
required together — see `0018`'s description above) via a new
`build/release` patch. See the `build/release` section below; it is a
separate git repository with its own patch numbering, landed as its own
commit.

## Third plan: session indistinguishability, Component B, part 1 (`0019`)

Task 3 of the same plan adds the foundation API for Component B: a new
`@hide` `UserManager` method, `getUsersForUiDisplay(boolean excludeDying)`,
that every UI surface listing device users must call instead of
`getUsers()`/`getAliveUsers()`. It behaves exactly like the existing
`getUsers(boolean excludeDying)` (same permission check, same AIDL style)
except that when the calling user is an AegisOS Duress Unlock decoy user
(`UserManager.USER_TYPE_FULL_AEGISOS_DECOY`), it returns only that user
instead of the full device user list — preventing every subsequent UI
migration task (4, 5, 6) from leaking the real hidden owner's identity to
someone using the decoy session. Internal, non-display callers (e.g.
`RealUserQuiescer`) keep calling the real, unscoped `getUsers(boolean)`
directly; this new method is additive, not a replacement of the existing
one.

- `4e1ba3f10b99b1cacceacd6ce3a38a887ae4cccd` — "aegisos: add
  UserManager.getUsersForUiDisplay for decoy session UI filtering"
  (`patches/frameworks-base/0019`, real build-server SHA) — three-file
  change: `List<UserInfo> getUsersForUiDisplay(boolean excludeDying);`
  added to `core/java/android/os/IUserManager.aidl` immediately after the
  existing `getUsers(boolean excludeDying)` declaration;
  `UserManagerService.getUsersForUiDisplay` added to
  `services/core/java/com/android/server/pm/UserManagerService.java`
  immediately after the existing `getUsers(boolean excludeDying)` method,
  gated by the same `checkCreateUsersPermission("query users...")` call
  `getUsers` already requires, reading the calling user's `UserInfo` under
  `mUsersLock` via the existing `getUserInfoLU`, and falling back to the
  real `getUsers(excludeDying)` for every non-decoy caller; and a public
  `UserManager.getUsersForUiDisplay` wrapper added to
  `core/java/android/os/UserManager.java` immediately after the real
  `getUsers(boolean excludeDying)` wrapper, carrying that wrapper's own
  actual `@UnsupportedAppUsage` and
  `@RequiresPermission(anyOf = {MANAGE_USERS, CREATE_USERS})` annotations
  (confirmed by reading the real method at
  `core/java/android/os/UserManager.java:5253` before mirroring it, not
  assumed from the `getEnabledProfiles`/`getProfiles` sample read
  elsewhere this session) but deliberately **not** that wrapper's
  `@Deprecated` (this is a new, non-deprecated method) or a `@TestApi`/
  `@SystemApi` annotation (matching `getEnabledProfiles`/`getProfiles`'s
  own `@hide`-only precedent instead — no `system-current.txt` entry
  expected or added). `ArrayList` was already imported in
  `UserManagerService.java`; no new import was needed. Authored under
  `AGENTS.md`'s local-first discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-indistinguishability-task3/`,
  seeded read-only from the real branch HEAD
  `ebb58e3ccc2034c2685b889ceecbcd1830b382ef`) and landed on the real
  branch with `git am --3way` (applied cleanly, no conflicts).
- Base commit: `ebb58e3ccc2034c2685b889ceecbcd1830b382ef` — `0018`'s own
  real post-`git am` SHA, the real branch tip at fetch time (unchanged
  since `0018` landed).

**Build verification:** a fast `m services` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`) after `0019`
landed: `#### build completed successfully (29 seconds) ####`, confirmed
by an independent re-run after the first invocation's own real compile
(polled directly on the build server — no build-completion notification
was relied on) rather than trusted from a single invocation. This
confirms the new AIDL method, `UserManagerService` implementation, and
`UserManager` wrapper all compile together and produce no API-signature
lint failure (so the `0002`-style `system-current.txt` fallback fix was
not needed, consistent with this being a plain `@hide` method with no
`@SystemApi`). It is not a consolidated `m systemimage` build and does
not by itself validate runtime behavior — no task in this plan currently
schedules a dynamic exercise of this specific API in isolation; Tasks 4-6
exercise it indirectly by migrating real UI call sites onto it. Task 3
touches `frameworks/base` only; it has no `build/release` change of its
own (unlike `0018`, which required the companion aconfig flag flip
documented in the `build/release` section below).

## Third plan: session indistinguishability, Component B, part 2 (`0020`)

Task 4 of the same plan routes the two real SystemUI call sites that list
device users for on-screen display onto Task 3's `getUsersForUiDisplay`
API, closing the actual leak: before this patch, a decoy session could open
either the QS/lock-screen user-switcher dialog or the power-menu's user
list and see the real hidden owner's account. Task 1's own research
(independently reviewed, no code change) had already confirmed
`UserSwitcherFullscreenDialogDelegate.kt` has no enumeration logic of its
own — it is constructor-injected with `UserSwitcherViewModel` and carries
no separate `getUsers()`/`aliveUsers` call — so it is fixed automatically
by this patch's `UserRepositoryImpl.kt` change; no third call site exists
to migrate.

- `c3a11765d4d61f61c0a753d1b9a4dd5d17b84049` — "aegisos: route SystemUI
  user-switcher and power-menu through getUsersForUiDisplay"
  (`patches/frameworks-base/0020`, real build-server SHA) — two one-line
  changes: `UserRepositoryImpl.kt:267`'s `refreshUsers()` now calls
  `manager.getUsersForUiDisplay(true)` instead of `manager.aliveUsers`
  (`manager` is the same `UserManager` instance already used a few lines
  away for `manager.mainUser`); `GlobalActionsDialogLite.java:1570`'s
  `addUserActions(List<Action>, UserInfo)` now calls
  `mUserManager.getUsersForUiDisplay(true)` instead of
  `mUserManager.getUsers()`. Both real current lines were re-confirmed via
  `grep -n`/direct read against the actual build-server source before
  editing — the brief's own quoted line numbers carried a one-line drift
  (`UserRepositoryImpl.kt:266` in the brief vs. the real `:267`) but the
  quoted code text itself matched exactly. Authored under `AGENTS.md`'s
  local-first discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-indistinguishability-task4/`, a
  fetch-only worktree seeded read-only from the real branch HEAD
  `4e1ba3f10b99b1cacceacd6ce3a38a887ae4cccd` — only the two touched files,
  not a full checkout) and landed on the real branch with `git am --3way`
  (applied cleanly, no conflicts). The real post-`git am` branch SHA
  (`c3a11765d4d61f61c0a753d1b9a4dd5d17b84049`) differs from the local
  worktree's own commit SHA (`7a20a9ad778f34b1b33e8911f3121cf9a712377d`),
  confirmed via `git log` on the branch rather than assumed, consistent
  with every prior patch in this package.
- Base commit: `4e1ba3f10b99b1cacceacd6ce3a38a887ae4cccd` — `0019`'s own
  real post-`git am` SHA, the real branch tip at fetch time.

**Build verification:** a fast `m SystemUI` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`) after `0020`
landed, launched fully detached on the remote side (`nohup ... & disown`)
and polled to completion with a bounded synchronous loop rather than an
async background-task notification:
`ninja: 17m46.35s Build Succeeded: 545 steps - 0.51/s`,
`#### build completed successfully (17:51 (mm:ss)) ####`. This confirms
the two call-site changes compile against the real `getUsersForUiDisplay`
API added in `0019`. It is not a consolidated `m systemimage` build and
does not by itself validate runtime behavior (e.g. that a decoy session
genuinely sees only itself in either dialog) — no task in this plan has
yet scheduled a dynamic exercise of this specific pair of UI surfaces.

## Third plan: session indistinguishability, Component B, part 3 (`0021`, plus `packages/apps/Settings` `0001`)

Task 5 of the same plan closes the last real leak this Component targets:
Settings ▸ Storage ▸ "Other users" (name, avatar, and per-user storage size)
listed every device user unscoped, so a decoy session could see the real
hidden owner's account there too. This task turned out to span **two**
separate git repositories, not one, correcting the plan brief's own
framing: the brief's Step 5 ("apply any additional SettingsLib fix Task 1
Step 5 found") reads as if it might be a minor addendum inside
`packages/apps/Settings`. It is not — `packages/SettingsLib` is tracked
inside the `frameworks/base` git repository (confirmed via
`git -C frameworks/base ls-files packages/SettingsLib/...`), not as a
separate top-level project, so that one call site needed its own
`frameworks/base` worktree, its own patch, and its own `git am` onto the
`frameworks/base` branch — continuing that repository's own existing
`0001`-`0020` series as `0021` — while the other three call sites landed as
the first-ever patch to `packages/apps/Settings` (see that repository's own
section below). All four call sites migrate to the same
`getUsersForUiDisplay(false /* excludeDying */)` — matching the real
no-arg `UserManager.getUsers()` wrapper's own body
(`core/java/android/os/UserManager.java:5218-5220`:
`public @NonNull List<UserInfo> getUsers() { return getUsers(/* excludeDying= */ false); }`,
confirmed via direct read before editing) — not `true`; getting this
backwards would have silently changed which dying users are included
relative to today's behavior.

- `4167d1df8b4e53c05316e731d41d79f5ef2cfba3` — "aegisos: route SettingsLib
  storage measurement through getUsersForUiDisplay"
  (`patches/frameworks-base/0021`, real build-server SHA) — one-line
  change: `StorageMeasurement.java:148`'s `measureExactStorage()` now calls
  `mUser.getUsersForUiDisplay(false /* excludeDying */)` instead of
  `mUser.getUsers()` (`mUser` is the class's own `UserManager` field,
  confirmed by reading its declaration at line 99 before editing). This is
  the real, additional unscoped call site Task 1's own research
  (independently reviewed, no code change) had already identified, consumed
  by the real Settings app via `StorageItemPreferenceController.java`. The
  real current line was re-confirmed via `grep -n`/direct read against the
  actual build-server source (including a byte-for-byte `sha256sum` match
  between the fetched local copy and the live remote file) before editing.
  Authored under `AGENTS.md`'s local-first discipline in a disposable local
  git repo (`aegisos-platform/.worktrees/duress-indistinguishability-task5b/`,
  a fetch-only worktree seeded read-only from the real branch HEAD
  `c3a11765d4d61f61c0a753d1b9a4dd5d17b84049` — only the one touched file,
  not a full checkout) and landed on the real branch with `git am --3way`
  (applied cleanly, no conflicts).
- Base commit: `c3a11765d4d61f61c0a753d1b9a4dd5d17b84049` — `0020`'s own
  real post-`git am` SHA, the real branch tip at fetch time.

**Build verification:** the fast `m Settings` build check described in the
`packages/apps/Settings` section below (below, this same patch cycle)
recompiles `//frameworks/base/packages/SettingsLib:SettingsLib` (confirmed
by grepping the real build log for that exact Soong module target, not
assumed from `git am`'s own exit code) as a dependency of the `Settings`
app module — so that single build check covers this patch together with
`packages/apps/Settings`'s own `0001`, exactly as the task required. It is
not a consolidated `m systemimage` build and does not by itself validate
runtime behavior (e.g. that a decoy session's Storage screen genuinely
shows only itself) — no task in this plan has yet scheduled a dynamic
exercise of this specific UI surface.

## Third plan: session indistinguishability, Component C (`0022`)

Task 7 of the same plan closes a different, wider cascade than Components
A/B: a freshly created decoy user has never run Android's own SetupWizard,
which — per this plan's own design research
(`docs/superpowers/specs/2026-08-28-duress-decoy-session-indistinguishability-design.md`
in `aegisos-foundation`) — gates several unrelated, non-admin-gated
SystemUI behaviors (screenshot/clipboard overlays, full-screen call/alarm
intents, the status-bar alarm icon, Material You theming, the QS settings
gear) purely on the per-user `Settings.Secure.USER_SETUP_COMPLETE` flag.
Rather than chase each of those six call sites individually, this task
writes that one flag at the single place a decoy is ever registered.

- `4e10e6a87a4a48e9381f5f46afd65d6765f61dbf` — "aegisos: mark decoy user
  setup-complete at registration time"
  (`patches/frameworks-base/0022`, real build-server SHA) — adds a
  `Context` field/constructor parameter to `DecoyProfileHelper` (new
  imports: `android.content.Context`, `android.provider.Settings`) and, in
  `setDecoyProfile()`, one new statement immediately after the existing
  `decoyUserId == UserHandle.USER_SYSTEM` anti-recursion guard (untouched):
  `Settings.Secure.putIntForUser(context.getContentResolver(),
  Settings.Secure.USER_SETUP_COMPLETE, 1, decoyUserId)`, before the
  existing `DecoyProfileState.create(decoyUserId).save(...)` call.
  `LockSettingsService.java:813`'s `new DecoyProfileHelper(...)` call site
  is updated to pass `mContext` (an existing field, confirmed assigned
  earlier in the same constructor at line 769, before this call site) as
  the fourth argument. The real current file was re-confirmed via
  `grep -n`/direct read against the actual build-server source (imports
  block, `setDecoyProfile` body, and the constructor call site all matched
  the plan's expected text verbatim, no drift found) before editing.
  Authored under `AGENTS.md`'s local-first discipline in a disposable local
  git repo (`aegisos-platform/.worktrees/duress-indistinguishability-task7/`,
  a fetch-only worktree seeded read-only from the real branch HEAD
  `4167d1df8b4e53c05316e731d41d79f5ef2cfba3` — only the two touched files,
  not a full checkout) and landed on the real branch with `git am --3way`
  (applied cleanly, no conflicts).
- Base commit: `4167d1df8b4e53c05316e731d41d79f5ef2cfba3` — `0021`'s own
  real post-`git am` SHA, the real branch tip at fetch time.

**Build verification:** a fast `m services` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side (`nohup ... & disown`) and polled to
completion with a bounded synchronous loop: `ninja: 4m22.31s Build
Succeeded: 195 steps - 0.74/s`, `#### build completed successfully (04:28
(mm:ss)) ####`. `services.core` and every other `services/*` submodule
(including `locksettings`, part of `services.core`) compiled cleanly; the
only diagnostics were pre-existing warnings/info notes in unrelated files
(`AppFunctionAccessPregrant.kt`, `AppIdPermissionUpgrade.kt`,
`PackageManagerLocal.java`), none touching `DecoyProfileHelper.java` or
`LockSettingsService.java`. This is not a consolidated `m systemimage`
build and does not by itself validate runtime behavior (e.g. that a real
decoy session's SystemUI chrome genuinely reflects
`USER_SETUP_COMPLETE=1`) — no task in this plan has yet scheduled a
dynamic exercise of this specific behavior.

### Fix to `0022`: clear calling identity for the setup-complete write (`0023`)

Found during `0022`'s own review, not by any task in the plan itself: the
`setDecoyProfile` AIDL entry point
(`LockSettingsService.java:4575-4578`) only enforces `checkWritePermission()`
(`checkAccessSecureStoragePermission("LockSettingsWrite")`,
`LockSettingsService.java:1386-1388`) — a different, narrower permission
than `WRITE_SECURE_SETTINGS`, which `Settings.Secure.putIntForUser`
actually requires. `0022`'s new call ran under the original Binder
caller's identity (e.g. the `set-decoy-profile` shell command's own
caller), which very likely does not hold `WRITE_SECURE_SETTINGS` — so the
write plausibly threw `SecurityException` or silently failed on every
real invocation path this project has used so far. Separately, the
review also flagged that the call's `boolean` return value was discarded
outright, so even a clean, permission-satisfied failure (e.g. a
transient Settings-provider issue) would have gone completely unnoticed.

- `3941aa4131fe67eb25767d57dd589f1166c6c18a` — "aegisos: clear calling
  identity for decoy setup-complete write"
  (`patches/frameworks-base/0023`, real build-server SHA) — three changes
  to `DecoyProfileHelper.java`, all confined to `setDecoyProfile()`:
  1. New import `android.os.Binder` (confirmed not already imported).
  2. The `Settings.Secure.putIntForUser(...)` call is now wrapped in the
     standard `final long token = Binder.clearCallingIdentity(); try {
     ... } finally { Binder.restoreCallingIdentity(token); }` idiom,
     matching the ~15 existing call sites of this exact pattern elsewhere
     in `LockSettingsService.java` (e.g. lines 2460, 2473, 2504 — the
     `Binder.`-qualified form is used explicitly throughout this file and
     its sibling `RecoverableKeyStoreManager.java`, not an inherited
     unqualified call, so this fix follows the same style even though
     `DecoyProfileHelper` is not itself a `Binder` subclass).
  3. The call's `boolean` return value is now captured; if `false`, a
     `Slog.w(TAG, ...)` warning is logged identifying the affected decoy
     user and noting the profile remains distinguishable until retried.
     The existing anti-recursion `USER_SYSTEM` guard and the rest of
     `setDecoyProfile`'s control flow are untouched — `DecoyProfileState`
     is still saved regardless of the flag write's outcome (see the
     engineering-judgment note below for why this does not also abort).
  Re-confirmed the real current source (`DecoyProfileHelper.java`'s
  imports and `setDecoyProfile` body) via SSH before editing — matched
  `0022`'s own landed content exactly, no drift. Authored under
  `AGENTS.md`'s local-first discipline in a **new** disposable local git
  repo (`aegisos-platform/.worktrees/duress-indistinguishability-task7-fix/`,
  seeded read-only from the real branch HEAD
  `4e10e6a87a4a48e9381f5f46afd65d6765f61dbf` — i.e. `0022`'s own real
  post-`git am` SHA, not the earlier task's now-stale worktree) and landed
  on the real branch with `git am --3way` (applied cleanly, no conflicts).
  `0022`'s already-landed commit was left untouched — this is a new,
  separate commit, not an amend/rewrite.
- Base commit: `4e10e6a87a4a48e9381f5f46afd65d6765f61dbf` — `0022`'s own
  real post-`git am` SHA, the real branch tip at fetch time.

**Engineering-judgment note (failed write does not abort registration):**
a failed `USER_SETUP_COMPLETE` write is logged loudly but does not throw
or prevent `DecoyProfileState` from being saved, for two reasons: (1) the
security-relevant property this task's earlier `USER_SYSTEM` guard
protects is completely independent of this write's outcome — a failure
here cannot leak the real user's identity or credentials, it can only
leave the decoy somewhat more distinguishable than intended (an already
documented, already-open gap in this plan's own indistinguishability
design, alongside the multi-user UI chrome and first-run setup gaps in
the router's own status record); (2) a duress mechanism that refuses to
register a decoy at all because of an unrelated, non-security
Settings-provider write failure is a worse outcome for the person relying
on it than a decoy that is briefly more distinguishable than intended.
Changing `setDecoyProfile`'s throw contract was judged a larger
behavioral decision than this specific fix's scope — a clear, unmissable
`Slog.w` is the fix's own minimum bar, per this project's "no partial
implementations presented as done" standard; whether a failed write
should also abort is left open for explicit owner sign-off rather than
decided unilaterally here.

**Build verification:** a second fast `m services` build check (same
target/`OUT_DIR` as `0022`'s own check), launched fully detached on the
remote side and polled to completion with a bounded synchronous loop:
`ninja: 2m35.17s Build Succeeded: 19 steps - 0.12/s`, `#### build
completed successfully (02:41 (mm:ss)) ####` — fewer steps than `0022`'s
own check because most of the dependency graph was already built and
cached; only `services.core` (containing the edited
`DecoyProfileHelper.java`) needed recompiling. No new compiler
diagnostics beyond the same pre-existing, unrelated ones already noted
for `0022`. Not a consolidated `m systemimage` build and does not by
itself validate runtime behavior (e.g. that the write actually succeeds
under the real `set-decoy-profile` shell caller on physical hardware) —
no task in this plan has yet scheduled a dynamic exercise of this
specific behavior.

### `build/release`

This is the first AegisOS patch to this repository. Prior to this patch,
the repository had never received any AegisOS-authored commit; it was on
detached HEAD at `8976d78feac337469081409228886065a4dfefab` (manifest tag
`2026071500`). A real, named branch (`aegisos-duress-decoy-switch`,
matching `frameworks/base`'s existing branch name — the convention this
and future patch cycles in `packages/apps/Settings` and
`packages/apps/Multiuser` will also follow) was created off that detached
HEAD before landing anything, so this and future `build/release` commits
land on a real ref, not an anonymous detached-HEAD history.

- `e4722bcb53a3ad8e67aafad21c6540a83f552ee1` — "aegisos: enable
  credential_capture flag for skip-keyguard-when-switching-to-unlocked-users"
  (`patches/build-release/0001`, real build-server SHA) — adds
  `aconfig/trunk_staging/android.multiuser/credential_capture_flag_values.textproto`,
  setting the `android.multiuser` package's `credential_capture` aconfig
  flag to `state: ENABLED` / `permission: READ_WRITE`. This flag already
  existed as a real, declared-but-disabled-by-default AOSP flag
  (`frameworks/base/core/java/android/content/pm/multiuser.aconfig` line
  376, namespace `multiuser`, description "Perform authentication in the
  login app on desktop before switching to a user") — this patch only
  overrides its release-config value, adding no new flag declaration.
  Mirrors the existing
  `aconfig/trunk_staging/com.android.systemui/scene_container_flag_values.textproto`
  in the same tree structurally byte-for-byte (confirmed by direct `cat`
  comparison before authoring, not assumed from a prior transcription).
  Note: the plan brief this task followed expected the
  `aconfig/trunk_staging/android.multiuser/` directory not to exist yet;
  in fact it already existed with roughly 90 other, pre-existing AOSP flag
  override files unrelated to AegisOS — only the specific
  `credential_capture_flag_values.textproto` file was actually new. This
  does not change what the patch does (one new file, no conflicts), only
  the "first flag override for this package" framing in the brief, which
  was incorrect. Authored under `AGENTS.md`'s local-first discipline in a
  disposable local git repo
  (`aegisos-platform/.worktrees/duress-indistinguishability-task2b/`, a
  new-file-only worktree, nothing fetched) and landed on the real branch
  with `git am --3way` (applied cleanly, no conflicts).
- Base commit: `8976d78feac337469081409228886065a4dfefab` (this repository's
  real HEAD at the time, manifest tag `2026071500` — first entry for this
  repository, so this is also the branch point).

**Build verification:** none required for this repository alone — it holds
release configuration (aconfig flag value overrides) consumed by other
repositories' builds, not compiled code. The `frameworks/base` `0018`
build check above (`m services`, resource-XML-only) does not exercise this
flag either, since aconfig flag *state* is baked in at Soong analysis time
for the whole product, not just the one resource file. The actual
functional exercise of this flag together with `0018`'s resource default
happens in this plan's later consolidated-build and validation tasks, not
here.

### Fix to `0001`: correct release-config directory + permission (`0002`, `0003`)

Task 10's own emulator validation (`docs/superpowers/plans/2026-08-28-duress-decoy-session-indistinguishability.md`
Task 10, `.superpowers/sdd/task-10-report.md`) found `0001` a real, confirmed
functional defect, not a hypothetical: `sdk_phone64_x86_64-cp2a-userdebug`
and `lynx-cur-userdebug` both build release config `cp2a`
(`RELEASE_ACONFIG_VALUE_SETS` ends in `...-cp2a`, confirmed in both trees'
generated `release_config-*.vars`), and `cp2a`'s own
`aconfig_value_set-platform_build_release-cp2a` (`aconfig/cp2a/Android.bp`)
only globs `aconfig/cp2a/*/Android.bp` — it never reads
`aconfig/trunk_staging/`. `0001`'s value therefore had no on-device aconfig
storage slot at all (`adb shell aflags list`: zero `credential_capture`
entries; `aflags enable android.multiuser.credential_capture` errored "no
aconfig flag ... Does the flag have an .aconfig definition?"), leaving the
skip-keyguard mechanism permanently inactive and reproducing, end to end on
a real UI, the exact double-PIN-entry bug this whole plan exists to fix.

This fix landed in two patches, the second found only by the real rebuild
this fix required (not by inspection):

- `aae51c7d0274f8d610348a04caac7d778af5aac3` — "aegisos: move
  credential_capture flag override to the release config this product
  actually reads" (`patches/build-release/0002`, real build-server SHA) —
  pure rename,
  `aconfig/trunk_staging/android.multiuser/credential_capture_flag_values.textproto`
  → `aconfig/cp2a/android.multiuser/credential_capture_flag_values.textproto`,
  content otherwise unchanged (`permission: READ_WRITE` preserved at this
  step). No `Android.bp` change needed —
  `aconfig/cp2a/android.multiuser/Android.bp` already globs
  `*_flag_values.textproto`. Authored under `AGENTS.md`'s local-first
  discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-indistinguishability-task2-fix/`,
  a baseline-fetch-then-edit worktree — a direct `git fetch` of full branch
  history from the real branch failed here with "lazy fetching disabled;
  some objects may not be available" / "aborting due to possible repository
  corruption on the remote side," consistent with `build/release` being a
  partial/promisor clone this workstation cannot lazily complete; fell back
  to fetching only the touched file's content and committing it as an
  explicit baseline, per `AGENTS.md`'s own "fetch only the touched files"
  wording) and landed on the real branch with `git am --3way` (applied
  cleanly, no conflicts).
- `952bd37e4bbceda794f3cc823e18f1c0948beeeb` — "aegisos: mark
  credential_capture flag READ_ONLY for cp2a release config"
  (`patches/build-release/0003`, real build-server SHA) — changes
  `permission: READ_WRITE` to `permission: READ_ONLY` in the same file
  `0002` just relocated. Found by a genuine build failure, not anticipated
  by the original task brief (which called for moving the file with
  "identical content"): `cp2a` sets
  `RELEASE_ACONFIG_REQUIRE_ALL_READ_ONLY=true`
  (`build/release/flag_values/bp1a/RELEASE_ACONFIG_REQUIRE_ALL_READ_ONLY.textproto`,
  inherited into `cp2a`'s resolved vars), which Soong turns into
  `--allow-read-write false` on every `aconfig create-cache` invocation for
  this release config (`build/soong/aconfig/aconfig_declarations.go:189`).
  A `READ_WRITE` value is rejected outright under that flag:
  ```
  Error: failed to create cache
  Caused by:
      flag credential_capture has permission READ_WRITE, but
      allow_read_write is false
  ```
  confirmed as a real `m systemimage` failure (11 minutes in, 1 step
  failed of 68366) before this fix. Every other `*_flag_values.textproto`
  already checked into `aconfig/cp2a/android.multiuser/` uses
  `permission: READ_ONLY` — `0001`'s file, carried over unchanged from its
  original `trunk_staging` placement, was the only outlier. `READ_ONLY` is
  also correct on the merits: this flag only needs to be permanently baked
  in as enabled at build time, not toggleable post-build via
  `aflags`/`device_config`, and `READ_ONLY` cannot be flipped off at
  runtime by anyone with `device_config` write access. Authored in a
  second disposable local git repo
  (`aegisos-platform/.worktrees/duress-indistinguishability-task2-fix2/`,
  same baseline-fetch-then-edit pattern, seeded from `0002`'s own real
  post-`git am` SHA) and landed on the real branch with `git am --3way`
  (applied cleanly, no conflicts).
- Base commits: `e4722bcb53a3ad8e67aafad21c6540a83f552ee1` (`0001`'s own
  real post-`git am` SHA) for `0002`; `aae51c7d0274f8d610348a04caac7d778af5aac3`
  (`0002`'s own real post-`git am` SHA) for `0003`.

**Build verification:** a full, consolidated `m systemimage` rebuild
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side and polled to completion with a bounded
synchronous loop (self-inflicted false-positive found and fixed mid-poll:
an earlier `pgrep -fa` diagnostic matched the polling SSH command's own
command-line text, not the real build). First attempt (with only `0002`
landed) failed as described above at 11m0s. Second attempt (with `0003`
also landed) succeeded: `ninja: 1h27m20.99s Build Succeeded: 7036 steps -
1.34/s`, `#### build completed successfully (01:27:27 (hh:mm:ss)) ####`,
real `87m26.872s`. Post-boot, `adb shell aflags list` (after `adb root`)
now shows a real storage slot: `android.multiuser.credential_capture
enabled - default read-only system` (3120 total flags, vs. Task 10's 3119
on the unfixed build) — direct, on-device confirmation the release-config
placement fix actually reaches the running system this time.

**Re-verification (Task 10's exact Component A + non-decoy regression
tests, re-run against the fresh build): BLOCKED — the release-config fix
is real and confirmed working at the flag-storage and `UserController`
decision layers, but the plan's actual functional goal (single-PIN decoy
unlock) is still not achieved, and a second, more serious, previously
undetectable problem surfaced now that the flag is genuinely active for
the first time.** Full evidence: `.superpowers/sdd/task-2-fix-report.md`.

- Component A (decoy switch, single PIN entry): **still FAILS**, but via a
  *different* code path than `0001`'s original defect. Logcat confirms
  `UserController: Skipping keyguard due to
  skipKeyguardWhenSwitchingToUnlockedUsers` genuinely fires for the decoy
  switch now (proof the flag/resource/CE-storage/trust gates all passed) —
  but a live PIN bouncer still appears afterward, traced to a *separate*
  trigger: the decoy's home activity starting while the pre-existing
  (pre-switch) keyguard instance is still up invokes
  `LegacyActivityStarterInternalImpl.dismissKeyguardThenExecute`, which
  itself decides to *show* `PrimaryBouncerInteractor` rather than silently
  dismiss, because a secure keyguard shown before the switch began is not
  retroactively torn down by `skipKeyguardWhenSwitchingToUnlockedUsers` —
  that flag only suppresses a *new* forced lock during the switch itself.
  Re-entering the decoy PIN a second time via real on-screen taps and
  tapping the real `key_enter` button (`com.android.systemui:id/key_enter`)
  does unlock the device (`isKeyguardShowing` false) — i.e. the exact same
  symptom Task 10 already documented, not a new failure mode, just not
  fixed by relocating the release-config file.
- Non-decoy regression: **FAILS, and worse than "the fix didn't work" —
  a genuine, previously-undetectable security regression.** Creating an
  ordinary (non-decoy) user with a real PIN and switching to it with a
  plain `am switch-user <id>` (no credential ever supplied for that user —
  confirmed by the total absence of any "Verifying primary lockscreen
  credential for user 11" log line) landed on a **fully unlocked,
  bouncer-free desktop** (`isKeyguardShowing=false` immediately). Logcat
  shows both `KeyguardViewMediator: Skipping keyguard due to
  config_multiuserSkipKeyguardWhenSwitchingToUnlockedUsers` and
  `UserController: Skipping keyguard due to
  skipKeyguardWhenSwitchingToUnlockedUsers` fired for this switch. This is
  exactly the "device-wide effect for any user switch, not decoy-specific"
  caveat `0018`'s own SOURCE.md entry already flagged as a live risk once
  the flag became genuinely active — it was never observable before this
  fix because the flag had no storage slot at all. Task 10's own version of
  this same regression check reported PASS only because Component A was
  completely inert on that build; that PASS does not carry over now that
  the mechanism actually runs. **Not fixed here — out of this task's scope
  and a real security-relevant design question (should
  `skipKeyguardWhenSwitchingToUnlockedUsers` apply to any CE-unlocked,
  trust-allowed target user, or only to a decoy-routed switch?) that needs
  explicit owner review before Component A can be considered safe, not a
  unilateral code fix.**

### Deliberate revert: disable `credential_capture` again (`0004`)

This is a revert of an owner-approved decision, not a mistake being
undone. Given the two findings directly above — a confirmed, real
security regression (an ordinary, non-decoy `am switch-user` to a
PIN-protected user skipping the keyguard with zero credential check
performed) and the fact that the mechanism's own original goal
(single-PIN decoy unlock) still was not achieved by any of `0001`-`0003` —
the owner decided to revert the flag to inert now and treat "how to fix
the double-PIN UX bug without a device-wide keyguard bypass" as a future
redesign problem, not something to solve reactively. `0002` (correct
`aconfig/cp2a/` directory placement) and `0003` (`READ_ONLY` permission)
are **kept, unchanged** — both are genuine, correct fixes independent of
whether the flag itself is enabled or disabled.

- `10209de65d2accda1d7cdb8e178f41f9a20f166f` — "aegisos: disable
  credential_capture flag, revert to inert pending redesign"
  (`patches/build-release/0004`, real build-server SHA) — a one-line
  change, `state: ENABLED` → `state: DISABLED`, in the same file `0002`
  relocated and `0003` fixed the permission of. No other file touched.
  Confirmed via a direct read of `frameworks/base/services/core/java/com/
  android/server/am/UserController.java` (around line 542,
  `setInitialConfig()`) before authoring this patch that
  `frameworks/base`'s own `0018` resource override
  (`config_multiuserSkipKeyguardWhenSwitchingToUnlockedUsers`) is only
  ever consulted inside `if (android.multiuser.Flags.credentialCapture())
  { ... }` — with this flag disabled again, that resource value is simply
  never read, so `0018` does not need a separate revert. Authored under
  `AGENTS.md`'s local-first discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-indistinguishability-task2-revert/`,
  a baseline-fetch-then-edit worktree seeded from the real branch HEAD
  `952bd37e4bbceda794f3cc823e18f1c0948beeeb` — the fetched file's
  `sha256sum` matched the live remote file byte-for-byte before editing)
  and landed on the real branch with `git am --3way` (applied cleanly, no
  conflicts).
- Base commit: `952bd37e4bbceda794f3cc823e18f1c0948beeeb` (`0003`'s own
  real post-`git am` SHA — this repository's real HEAD at the time).

**Build verification:** a full, consolidated `m systemimage` rebuild
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`,
`OFFICIAL_BUILD=true`), launched fully detached on the remote side and
polled to completion with a bounded synchronous loop (using `kill -0
<known PID>` for liveness, not a `pgrep` pattern-match — an earlier launch
attempt in this same task self-matched its own remote shell's command-line
text via `pgrep -f "soong_ui --build-mode"`, the identical self-inflicted
bug class already documented for `0002`/`0003`'s own rebuild; caught before
it produced a wrong result and switched to `kill -0` against the real PID).
Result: `ninja: 1h23m05.55s Build Succeeded: 7224 steps - 1.45/s`, `####
build completed successfully (01:31:39 (hh:mm:ss)) ####`. Fresh
`system.img`/`super.img` timestamps confirmed inside the build window.

**Re-verification (the decisive non-decoy regression check, re-run against
the reverted build): PASSES — the bypass is closed.** Booted a fresh
emulator (`-no-snapshot-load -wipe-data`, ~1m30s to `boot_completed=1`,
consistent with prior boots). `adb shell aflags list | grep
credential_capture` returned **no entry at all** (3119 total flags,
matching the original pre-`0002` baseline count) rather than an explicit
`disabled` line — an asymmetry between how this build surfaces a
newly-`ENABLED` vs. newly-`DISABLED` read-only flag that was not further
root-caused (not needed: the dynamic test below is decisive regardless of
what `aflags list` shows). Created an ordinary (non-decoy) secondary user
(`aegisos-task2revert-ordinary`, id 10, confirmed cold — no `running` tag
— before the switch), set PIN `22222`, cleared logcat, then `adb shell am
switch-user 10`:
  - `dumpsys window` immediately showed `isKeyguardShowing=true` (the
    regression's own fingerprint was `isKeyguardShowing=false`
    immediately) — the keyguard is genuinely back, blocking the desktop.
  - Logcat after the switch contained **zero** matches for
    `credentialCapture|SkipKeyguard|skipKeyguardWhenSwitchingToUnlockedUsers`
    — neither `KeyguardViewMediator` nor `UserController` fired their skip
    decisions this time (both had fired for this exact scenario on the
    `0003` build).
  - Supplying the real credential (`adb shell cmd lock_settings verify
    --user 10 --old 22222`) produced `LockSettingsService: Verifying
    primary lockscreen credential for user 10` /
    `LockSettingsService: Successfully verified primary lockscreen
    credential for user 10` — this exact line was **absent entirely** in
    the regression (proof the credential is actually being checked again,
    not merely that the keyguard UI is visible).

Both pieces of evidence the task asked for are present, not just one.
Component A's decoy double-PIN bug is expected and accepted to still
reproduce on this build (not re-tested here per explicit instruction —
reverting the flag does not change that code path's outcome, only removes
the security regression it had introduced alongside it).

### `frameworks/base`: closing whole-branch review fixes (`0024`)

A closing whole-branch review of this entire third plan — run after all
11 tasks were marked complete and a physical `lynx` candidate had already
been flashed — found a real, Critical gap this session's own per-task
reviews had all missed: `DecoyProfileHelper.setDecoyProfile` never
validated that the registered `decoyUserId` is actually an existing
`UserManager.USER_TYPE_FULL_AEGISOS_DECOY` user — only that it isn't
`UserHandle.USER_SYSTEM`. Every Component B/C fix in `0019`-`0023` keys
its identity-leak filtering off `UserInfo.userType`, not off the
registered id, so an ordinary secondary user mistakenly registered as
"the decoy" (a real scenario given `USER_TYPE_FULL_AEGISOS_DECOY`'s own
confirmed max-count-of-one restriction) would still route and switch
correctly via `maybeRouteToDecoy`, but silently receive none of the
identity-leak or setup-complete protections. Independently confirmed
against the live branch (not just the reviewer's own disposable-worktree
copy) before being fixed: `services/core/java/com/android/server/
locksettings/DecoyProfileHelper.java`, `LockSettingsService.java:4576-4579`,
and `LockSettingsShellCommand.java:494-536` all confirmed to have no type
check anywhere in the call chain.

- `cd072d704764a3a7de10e33ec0d562f836031c5b` — "aegisos: validate decoy
  user type, revert Component A resource, drop UnsupportedAppUsage"
  (`patches/frameworks-base/0024`, real build-server SHA) — three changes:
  1. `DecoyProfileHelper.setDecoyProfile` now resolves `decoyUserId` via
     `LocalServices.getService(UserManagerInternal.class).getUserInfo(...)`
     and throws `IllegalArgumentException` unless the result is non-null
     and `UserManager.USER_TYPE_FULL_AEGISOS_DECOY.equals(userType)` —
     mirroring the existing `USER_SYSTEM` guard's own pattern and placed
     immediately after it. The `Settings.Secure.USER_SETUP_COMPLETE` write
     (`0022`/`0023`) was reordered to run *after*
     `DecoyProfileState.create(decoyUserId).save(...)` succeeds, not
     before — closing a secondary ordering gap the same review flagged
     (a failed save could otherwise leave a wrong or not-yet-durable user
     permanently marked setup-complete).
  2. `core/res/res/values/config.xml`'s
     `config_multiuserSkipKeyguardWhenSwitchingToUnlockedUsers` reverted
     from `true` (`0018`) back to the AOSP default `false`. Not required
     for correctness on this device's current release config — the
     `android.multiuser.credential_capture` aconfig flag is already
     `DISABLED` (`build/release` `0004`), and `UserController.java:542`'s
     `if (Flags.credentialCapture())` guard means the resource is never
     consulted while that flag stays disabled — but landed anyway as
     defense in depth: a future rebase or a different release config's own
     `RELEASE_ACONFIG_VALUE_SETS`/flag defaults could otherwise silently
     reactivate the exact device-wide keyguard-bypass regression ADR-061
     found and closed, without this resource-level revert.
  3. `core/java/android/os/UserManager.java`'s `getUsersForUiDisplay`
     wrapper (`0019`) had `@UnsupportedAppUsage` removed — that annotation
     exists to preserve reflective access for apps already calling a
     hidden member; a method that did not exist before `0019` has no such
     callers by construction, so it only widened the non-SDK reflection
     surface for no benefit.
  Authored under `AGENTS.md`'s local-first discipline in a disposable
  local git repo
  (`aegisos-platform/.worktrees/duress-indistinguishability-review-fix/`,
  a fetch-only worktree seeded read-only from the real branch HEAD
  `3941aa4131fe67eb25767d57dd589f1166c6c18a` — only the three touched
  files, not a full checkout) and landed on the real branch with
  `git am --3way` (applied cleanly, no conflicts).
- Base commit: `3941aa4131fe67eb25767d57dd589f1166c6c18a` (`0023`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

**Build verification:** a full, consolidated `m systemimage` rebuild for
the physical `lynx-cur-userdebug` target (`OUT_DIR=out-lynx`,
`OFFICIAL_BUILD=true`, confirmed via a fresh `installed-files*.txt` grep
for `Updater`/`Auditor`/`Seedvault`/`PdfViewerGOS` across `/system` and
`/product` before signing), launched fully detached on the remote side:
`ninja: 53m09.68s Build Succeeded: 3775 steps - 1.18/s`, `#### build
completed successfully (53:17 (mm:ss)) ####`. Only `system.img` needed
regenerating (all other 11 partition images were byte-identical to the
prior Phase A candidate, since none of their own inputs changed) — signed
as one fully coherent 12-partition set from this build's own output
(never a splice of this build's `system.img` onto an older candidate's
other partitions), same alpha key, same rollback index (`1783209600`),
13/13 independently re-verified on two separate machines, then flashed by
the device owner (never by an agent) and independently re-verified again:
matching build fingerprint, unchanged `orange`/`unlocked` boot state, zero
Dropbox crash/ANR/tombstone entries.

**Decisive real-hardware confirmation of the type-validation fix:** a
disposable ordinary (non-decoy) secondary user was created on the
physical device and the device owner ran
`cmd lock_settings set-decoy-profile --owner-credential <redacted> <id>`
themselves (the controller never saw, requested, or handled the real
credential). Real result, reported verbatim by the owner:
```
java.lang.IllegalArgumentException: decoyUserId must be an existing org.aegisos.usertype.full.DECOY user
        at com.android.server.locksettings.DecoyProfileHelper.setDecoyProfile(DecoyProfileHelper.java:150)
        at com.android.server.locksettings.LockSettingsService.setDecoyProfile(LockSettingsService.java:4578)
        ...
```
The exception fires exactly at the new check's own line, with the full
real call chain intact (shell command → `LockPatternUtils` →
`LockSettingsService` → `DecoyProfileHelper`). The disposable test user
was removed afterward; the device's real, correctly-typed decoy (already
registered before this fix) was independently confirmed unaffected
(`user_setup_complete` still `1`, `pm list users` unchanged).

### `frameworks/base`: Wallet QS tile decoy exemption (`0025`)

Follow-up work after this plan's own closing whole-branch review (`0024`):
Task 8's own text had left the Wallet and Mobile-Data/Internet QS tiles as
a deliberate, out-of-scope product decision — "both require `userId ==
UserHandle.USER_SYSTEM`/`mainUserId` by design for reasons unrelated to
this feature ... re-scope only if the design owner decides the
inconsistency itself ... is worth a cosmetic change." The device owner
was asked and chose to close this for the Wallet tile.

A fresh source read against the real `aegisos-duress-decoy-switch` branch
found the plan's own premise half wrong: `CellularTile.java` no longer
exists in this tree (merged into `InternetTileNewImpl.kt`), and no
`USER_SYSTEM`/main-user check exists anywhere in the Internet/Mobile-Data
tile's current implementation — if a per-user difference is ever visible
there, it comes from deeper `SubscriptionManager`/SIM-visibility logic,
not a tile-level gate. The owner scoped this patch to the Wallet tile
only, where a real gate does exist
(`QuickAccessWalletTile.java:209`'s `isWalletRoleAvailable()`, a raw
`mHost.getUserId() == UserHandle.USER_SYSTEM` comparison —
`RoleManager.isRoleAvailable(ROLE_WALLET)` itself, confirmed by reading
`QuickAccessWalletController.java:107-109`, is a device-capability check,
not per-user, so this raw comparison was the only thing hiding the tile
from any non-main user).

This is explicitly a product/UX decision, not a security fix: the tile's
underlying state (`RoleManager`, `QuickAccessWalletClient`) is already
per-caller, so its absence for the decoy was never an identity-leak
vector, just a cosmetic inconsistency with every other tile.

- `6740d16784cbcb7fbb9490523276e178e6aeb653` — "aegisos: treat decoy user
  as main-user-equivalent for Wallet QS tile"
  (`patches/frameworks-base/0025`, real build-server SHA) — adds a
  `UserManager` field to `QuickAccessWalletTile` (Dagger-injected via the
  same constructor-injection pattern already used by `HotspotTile.java`
  in this same directory, confirmed by reading it first) and a new
  `isDecoySession()` private helper reusing
  `UserManager#getUsersForUiDisplay(true)` — the same identity-aware
  primitive every Component B fix in `0019`-`0021` already uses. Confirmed
  by reading `UserManagerService#getUsersForUiDisplay`'s real
  implementation that when the calling user is the decoy, it returns a
  singleton list containing only the caller's own `UserInfo`, which this
  helper checks for `userType`. `isWalletRoleAvailable()`'s gate becomes
  `(userId == USER_SYSTEM || isDecoySession()) && ...`. Authored under
  `AGENTS.md`'s local-first discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-wallet-tile-fix/`, a fetch-only
  worktree seeded read-only from the real branch HEAD
  `cd072d704764a3a7de10e33ec0d562f836031c5b` — only the one touched file,
  its `sha256sum` confirmed byte-identical to the live remote file before
  editing) and landed on the real branch with `git am` (applied cleanly,
  no conflicts). This patch file was regenerated fresh via `git
  format-patch` directly from the real post-`git am` branch history, not
  exported from the disposable worktree's own commit, per this project's
  own standing lesson on why the two SHAs differ.
- Base commit: `cd072d704764a3a7de10e33ec0d562f836031c5b` (`0024`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

**Build verification:** a fast `m SystemUI` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side (`nohup ... & disown`), confirmed no
build was already in flight beforehand: `ninja: 25m52.77s Build
Succeeded: 773 steps - 0.50/s`, `#### build completed successfully
(34:04 (mm:ss)) ####`. The only diagnostics in the full log are
pre-existing warnings/infos in unrelated files (Kotlin nullability
notices, `@FlaggedApi` infos, `ErrorWhenNew` doc-link warnings); nothing
attached to `QuickAccessWalletTile.java`. `SystemUI.apk` was confirmed
freshly rebuilt (fresh mtime matching this run) with `0025`'s commit as
the file's own tip (`git log -1 -- .../QuickAccessWalletTile.java`).
This is compile-time verification only — it does not confirm the tile
actually renders for a real decoy session on hardware; that is deferred
to this feature's ongoing UI verification sweep, not yet run for this
specific patch.

### `frameworks/base`: fix `getUsersForUiDisplay` to key off current user, not caller (`0026`)

Found during the owner-requested UI verification sweep against the
physical `lynx` device that is already running `0025`'s own base
candidate (the `aegisos-test-decoy` user, id 10): the QS footer's "Select
user" dialog (`UserSwitchDialogDelegate` → `UserDetailView.Adapter` →
`BaseUserSwitcherAdapter` → `UserSwitcherController` →
`UserSwitcherInteractor` → `UserRepositoryImpl` →
`getUsersForUiDisplay`) showed the real Owner identity alongside the
decoy's own — a live, reproducible identity leak, not a hypothetical
one, directly contradicting ADR-063's claim that this exact surface was
confirmed leak-free. Root-caused with hard evidence, not guesswork:
`dumpsys window` showed `mCurrentFocus=Window{... u0 Select user}` while
`mFocusedApp` correctly showed `u10 com.android.launcher3` — SystemUI's
floating windows (this dialog, and the power menu's
`GlobalActionsDialogLite`, independently confirmed to share the same
`u0`-owned pattern) always render under user 0's Binder/window context,
regardless of which user is actually current. `getUsersForUiDisplay`'s
decoy special-case checked `UserHandle.getCallingUserId()`, which is
`0` for these callers no matter which user is foregrounded, so the
filter silently never applied to them. By contrast, Settings ▸ Storage
was independently confirmed architecturally safe: launching it as user
10 produces a genuine `u10_system` process and a `u10`-owned window
(`dumpsys window`'s `mCurrentFocus` confirmed this directly), so its own
call to the same API already received the correct calling identity —
consistent with ADR-063's claim holding for that one specific surface.

- `c09efcc32293ea20e09ef4d5545c361060643183` — "aegisos: fix
  getUsersForUiDisplay to key off current user, not caller"
  (`patches/frameworks-base/0026`, real build-server SHA) — replaces the
  `UserHandle.getCallingUserId()` check with the existing
  `getCurrentUserId()` helper already present in this same class
  (`UserManagerService.java`, `ActivityManagerInternal`-backed, already
  used by the neighboring `getCurrentAndTargetUserIds()`), confirmed via
  a real source read before reusing it rather than adding a new,
  unverified cross-service call. This is a fix to the shared primitive
  itself, not a per-call-site patch: it covers every present and future
  caller regardless of which process renders it, rather than requiring
  every new UI surface to somehow run in a per-user process to get
  correct filtering. Authored under `AGENTS.md`'s local-first discipline
  in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-getusersforuidisplay-fix/`, a
  fetch-only worktree seeded read-only from the real branch HEAD
  `6740d16784cbcb7fbb9490523276e178e6aeb653` — only the one touched file,
  its `sha256sum` confirmed byte-identical to the live remote file before
  editing) and landed on the real branch with `git am` (applied cleanly,
  no conflicts, confirmed no build was already in flight beforehand).
  This patch file was regenerated fresh via `git format-patch` directly
  from the real post-`git am` branch history, not exported from the
  disposable worktree's own commit.
- Base commit: `6740d16784cbcb7fbb9490523276e178e6aeb653` (`0025`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

**Build verification:** a fast `m services` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side, confirmed no build was already in
flight beforehand: `ninja: 4m17.33s Build Succeeded: 333 steps -
1.29/s`, `#### build completed successfully (04:23 (mm:ss)) ####`. No
diagnostics attached to `UserManagerService.java`; `services.jar` was
confirmed freshly rebuilt (fresh mtime matching this run) with `0026`'s
commit as the file's own tip. **This is compile-time verification
only** — it does not confirm the fix actually closes the leak at
runtime. Given this touches `system_server` identity-filtering code
shared by every Component B call site, not an isolated app APK, dynamic
re-validation (ideally emulator-first, given ADR-061's own precedent for
how a compiles-cleanly change to identity/keyguard-adjacent code
produced a real regression only visible at runtime) is required before
this can be considered closed, and has not yet been done as of this
entry.

### `frameworks/base`: exhaustive leak-audit fixes, part 1 (`0027`, `0028`)

Following ADR-067's exhaustive, owner-requested audit for every
remaining decoy identity-leak surface
(`docs/superpowers/specs/2026-08-30-duress-decoy-remaining-leaks-audit.md`,
21 items found), fixed in the audit's own recommended priority order.

- `dcb6019b1bfa57a046583b386220581e90697ba8` — "aegisos: fix legacy
  power-menu fallback to use getUsersForUiDisplay"
  (`patches/frameworks-base/0027`, real build-server SHA) — Critical
  item 1. `LegacyGlobalActions.addUsersToMenu()` called the raw
  `UserManager#getUsers()` and rendered every real user's name with a
  checkmark directly on the lock screen (`showDuringKeyguard()` returns
  `true`) — confirmed a live fallback path via `GlobalActions.java`
  (triggered when SystemUI fails to respond to the power-menu request
  within 5 seconds, or reports `onGlobalActionsAvailableChanged(false)`),
  not dead code. Fixed with a one-line substitution to
  `getUsersForUiDisplay(true)`.
- `12719d2f9393...` — "aegisos: fix uninstall dialogs' single-user
  check to use getUsersForUiDisplay" (`patches/frameworks-base/0028`,
  real build-server SHA) — High item 4. Three sibling call sites, each
  with their own `isSingleUser()`/`isSingleUserOnDevice()` helper keyed
  off the raw `UserManager#getUserCount()`/`.userCount`, picking
  between single-user and multi-user uninstall confirmation text:
  `packages/PackageInstaller/src/com/android/packageinstaller/handheld/UninstallAlertDialogFragment.java`,
  `.../television/UninstallAlertFragment.java`, and
  `.../v2/model/UninstallRepository.kt`. Confirmed `getUserCount()`'s
  own real implementation (`getUsers().size()`, with a null-safety
  fallback to 1) before substituting `getUsersForUiDisplay(true).size()`
  at all three, matching `getAliveUsers()`-based fixes elsewhere in this
  patch set.
  Both authored under `AGENTS.md`'s local-first discipline in disposable
  local git repos
  (`aegisos-platform/.worktrees/duress-legacyglobalactions-fix/`,
  `aegisos-platform/.worktrees/duress-uninstall-usercount-fix/`, each a
  fetch-only worktree seeded read-only from the real branch HEAD at
  fetch time — `c09efcc32293ea20e09ef4d5545c361060643183` and
  `dcb6019b1bfa57a046583b386220581e90697ba8` respectively — with every
  fetched file's `sha256sum` confirmed byte-identical to the live remote
  file before editing) and landed on the real branch with `git am`
  (applied cleanly, no conflicts, confirmed no build was already in
  flight beforehand each time). Both patch files were regenerated fresh
  via `git format-patch` directly from the real post-`git am` branch
  history, not exported from either disposable worktree's own commit.
- Base commits: `c09efcc32293ea20e09ef4d5545c361060643183` (`0026`'s own
  real post-`git am` SHA) for `0027`; `dcb6019b1bfa57a046583b386220581e90697ba8`
  (`0027`'s own real post-`git am` SHA) for `0028`.

**Build verification:** `0027` confirmed via a fast `m services` build
check (`Build Succeeded: 18 steps`, incremental) with no diagnostics
attached to the edited file. `0028` confirmed via a fast `m
PackageInstaller` build check (`Build Succeeded: 28 steps`) with no
diagnostics attached to any of the three edited files (Java and
Kotlin both compiled cleanly). Both are compile-time verification
only; runtime confirmation is deferred to a later consolidated
hardware-validation round for this whole audit-fix batch, per the
same reasoning `0026`'s own entry already recorded, since interactive
UI automation for this exact class of surface proved unreliable on
this session's emulator attempt (see ADR-068 for the specific
tooling-limitation record).

### `frameworks/base`: exhaustive leak-audit fixes, part 2 (`0029`)

- `57239cc4c696...` — "aegisos: route areThereMultipleUiSwitchableUsers
  through getUsersForUiDisplay" (`patches/frameworks-base/0029`, real
  build-server SHA) — High item 8, a dormant landmine rather than a
  currently-live leak. `UserManagerService#areThereMultipleUiSwitchableUsers()`
  (a private helper gating `isUserSwitcherEnabled()`, which in turn
  controls the status bar's `mMultiUserAvatar` icon —
  `KeyguardStatusBarView.java:77`, directly observed on the physical
  device's lock screen during this session's own earlier hardware
  testing) called the raw `getUsers(excludeDying=true)`. Confirmed via
  source that this is currently masked only as a side effect of the
  AegisOS decoy user type's own default restriction set
  (`UserTypeFactory.java`) not setting `DISALLOW_ADD_USER` — the
  sibling `isUserSwitcherEnabled()` check
  (`!hasUserRestriction(DISALLOW_ADD_USER)`) short-circuits before ever
  reaching this method for the decoy today, but a future restriction
  default change could silently reactivate the leak with no code change
  of its own. Fixed with a one-line substitution to
  `getUsersForUiDisplay(/* excludeDying= */ true)`, reusing this same
  class's own already-fixed (ADR-066) primitive.
  Authored under `AGENTS.md`'s local-first discipline in a disposable
  local git repo (`aegisos-platform/.worktrees/duress-multiuser-icon-fix/`,
  a fetch-only worktree seeded read-only from the real branch HEAD
  `12719d2f93938762de6384999d21bd25d4ec857e` — only the one touched
  file, its `sha256sum` confirmed byte-identical to the live remote file
  before editing) and landed on the real branch with `git am` (applied
  cleanly, no conflicts, confirmed no build was already in flight
  beforehand). This patch file was regenerated fresh via `git
  format-patch` directly from the real post-`git am` branch history, not
  exported from the disposable worktree's own commit.
- Base commit: `12719d2f93938762de6384999d21bd25d4ec857e` (`0028`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

**Build verification:** a fast `m services` build check
(`Build Succeeded: 18 steps`, incremental) confirmed no diagnostics
attached to the edited file. Compile-time verification only; runtime
confirmation deferred to the same consolidated hardware-validation
round as `0026`-`0029` and the `packages-apps-settings`/
`packages-apps-multiuser` fixes below.

### `frameworks/base`: crash-to-reboot fix (`0030`)

- `13967ec4bc63...` — "aegisos: fix UserBackupManagerService using
  wrong Context for CE-storage check" (`patches/frameworks-base/0030`,
  real build-server SHA) — not an identity leak, a real
  crash-to-full-device-reboot bug found while dynamically validating
  the audit-fix batch above on physical hardware. Entering the
  registered decoy's PIN on a device where the real owner had never
  credential-unlocked that boot crashed `system_server` fatally
  (`BackupManagerService.Lifecycle.onUserUnlocking`'s handler, via
  `UserBackupManagerService`'s constructor), which on this AOSP
  configuration triggers a full device reboot, dropping the
  just-established decoy session. Root-caused via the device's own
  event log (`uc_finish_user_unlocking`/`ssm_user_unlocking` firing
  for user 10, the decoy, never 0, immediately before the crash) down
  to `UserBackupManagerService.java:622`:
  `mBackupPreferences = new UserBackupPreferences(mContext, mBaseStateDir);`
  used `mContext` (the un-scoped, implicitly-user-0 construction-time
  context) instead of `userContext` (created 34 lines earlier in the
  same constructor, specifically for this purpose — "We need a context
  for the user in question"). Confirmed via `git blame` as genuine
  upstream AOSP code since 2019 (`9e111f19df8e5`), never touched by any
  AegisOS patch (`git log --grep=^aegisos -- services/backup/` returns
  nothing) — harmless on every ordinary Android flow, since user 0 is
  always already unlocked by the time any other user's backup service
  starts on a normal multi-user switch; AegisOS's Duress Unlock decoy
  routing is the first path found in this investigation that starts
  and unlocks a secondary user's session while user 0 has never
  unlocked at all — exactly the security property a duress mechanism
  needs, and exactly the condition this six-year-old bug was never
  exercised against. Fixed with a one-line substitution (`mContext` →
  `userContext`). Full incident record:
  `aegisos-foundation`'s
  `docs/devices/lynx-duress-decoy-backup-context-crash-incident.md`.
  Authored under `AGENTS.md`'s local-first discipline in a disposable
  local git repo (`aegisos-platform/.worktrees/duress-backup-context-fix/`,
  a fetch-only worktree seeded read-only from the real branch HEAD
  `57239cc4c69658e346f2f32720a548cbef58c27f` — only the one touched
  file, its `sha256sum` confirmed byte-identical to the live remote file
  before editing) and landed on the real branch with `git am` (applied
  cleanly, no conflicts, confirmed no build was already in flight
  beforehand). This patch file was regenerated fresh via `git
  format-patch` directly from the real post-`git am` branch history, not
  exported from the disposable worktree's own commit.
- Base commit: `57239cc4c69658e346f2f32720a548cbef58c27f` (`0029`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

**Build verification:** a full `lynx-cur-userdebug` rebuild
(`OFFICIAL_BUILD=true` reconfirmed, `m -j8`): `Build Succeeded: 224
steps`, 3m34s — fast, since only this one source file changed. Signed
with the alpha key, same parameters as the batch above, 13/13
independently verified on two machines. **Hardware-confirmed, not just
compile-verified**: the owner flashed the corrected candidate and
reproduced the exact original crash trigger for real (fresh boot,
decoy PIN entered directly, real owner's credential never entered that
boot) — the decoy switch completed cleanly with no crash and no
reboot, confirmed via the device's own event log and a clean `dumpsys
dropbox` scan.

### `frameworks/base`: add `UserManager.isMainForUiDisplay` primitive (`0031`)

Task 1 of the fourth plan
(`docs/superpowers/plans/2026-09-03-duress-decoy-primary-profile-visual-parity.md`)
adds the shared display-only primitive the plan's later tasks (2-4) use to
close two visual "tells" that mark a decoy session as an ordinary secondary
profile rather than the device's one true primary profile: a new `@hide`
`UserManager`/`UserManagerService` method,
`isMainForUiDisplay(@UserIdInt int userId)`, that returns true for a real
main/system user (mirroring `UserInfo#isMain()`/`UserHandle#USER_SYSTEM`
unchanged) or for an AegisOS Duress Unlock decoy user when `userId` is the
device's actual current (foreground) user — keyed off the current user
rather than the Binder calling user, for the same reason
`getUsersForUiDisplay` (`0019`) is. This task has no consumer of its own by
design; it is additive only, exercised for the first time by Tasks 2-4.

- `b2b66088045df71f2565525c2ca173dc929da373` — "aegisos: add
  UserManager.isMainForUiDisplay primitive"
  (`patches/frameworks-base/0031-aegisos-add-UserManager.isMainForUiDisplay-primitive.patch`,
  real build-server SHA) — three-file change:
  `boolean isMainForUiDisplay(int userId);` added to
  `core/java/android/os/IUserManager.aidl` immediately after the existing
  `getUsersForUiDisplay(boolean excludeDying)` declaration;
  `UserManagerService.isMainForUiDisplay` added to
  `services/core/java/com/android/server/pm/UserManagerService.java`
  immediately after the existing `getUsersForUiDisplay` method, gated by
  `checkManageUsersPermission(...)`, reading the target user's `UserInfo`
  under `mUsersLock` via the existing `getUserInfoLU`, returning `true`
  immediately for a real main or `UserHandle.USER_SYSTEM` user, `false` for
  any non-current user, and otherwise checking
  `UserManager.USER_TYPE_FULL_AEGISOS_DECOY` against the device's actual
  current user; and a public `UserManager.isMainForUiDisplay` wrapper added
  to `core/java/android/os/UserManager.java` immediately after the real
  `getUsersForUiDisplay` wrapper, carrying
  `@RequiresPermission(android.Manifest.permission.MANAGE_USERS)` but
  deliberately no `@UnsupportedAppUsage` — matching `getUsersForUiDisplay`'s
  own corrected precedent (`0024`): a brand-new `@hide` method has no prior
  reflective callers, so adding the annotation would needlessly widen the
  non-SDK reflection surface. The plan's own Step 3 verification caught a
  one-line caption discrepancy while confirming this insertion point: the
  plan's brief cited `UserManager.java`'s `getUsersForUiDisplay` wrapper's
  closing brace as line 5274, but the real closing brace (the method-level
  `}`, as opposed to the inner `catch` block's own `}` immediately above it)
  is line 5275 — the quoted code text itself matched the real source
  byte-for-byte with no drift, only the caption's line number was off by
  one, so the edit proceeded at the verified real location rather than the
  cited number. Authored under `AGENTS.md`'s local-first discipline in a
  disposable local git repo
  (`aegisos-platform/.worktrees/decoy-visual-parity-task1/`, a fetch-only
  worktree seeded read-only from the real branch HEAD
  `13967ec4bc635f07a7c81e1eabd7f764aee0c49d` — only the three touched files,
  not a full checkout) and landed on the real branch with `git am --3way`
  (applied cleanly, no conflicts, confirmed no build was already in flight
  beforehand via `ps aux` rather than the self-matching `pgrep -f` form).
  The real post-`git am` branch SHA (`b2b66088045df71f2565525c2ca173dc929da373`)
  differs from the local worktree's own commit SHA
  (`3e9718794d999072fcb12b23d22b42a4e9bf7951`), confirmed via `git log` on
  the branch rather than assumed, consistent with every prior patch in this
  package.
- Base commit: `13967ec4bc635f07a7c81e1eabd7f764aee0c49d` (`0030`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

**Build verification:** a fast `m services` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side (`nohup ... & disown`) and polled to
completion across several fresh SSH connections rather than one held-open
session (the very first launch invocation's own SSH connection did not
return within its own timeout, because `source build/envsetup.sh && lunch
...`'s stdout was not individually redirected before the whole chain was
backgrounded together with `nohup m services`, leaving that one
connection's pty attached even though the actual build process was already
correctly detached and writing to its own log file — confirmed harmless by
polling the log and process table over a brand-new connection, then
discarding the stuck one): `ninja: 17m39.79s Build Succeeded: 623 steps -
0.59/s`, `#### build completed successfully (17:47 (mm:ss)) ####`. No
compiler diagnostics referenced `IUserManager.aidl`, `UserManager.java`, or
`UserManagerService.java` — the only warnings in the log are pre-existing,
unrelated Metalava `UnresolvedLink` javadoc-resolution notices against
`Manifest.java`/`R.java`. This is not a consolidated `m systemimage` build
and does not validate runtime behavior — this task adds no call site of
its own; Tasks 2-4 exercise this API for the first time.

### `frameworks/base`: suppress "End session" affordance for a decoy session (`0032`)

Task 2 of the fourth plan
(`docs/superpowers/plans/2026-09-03-duress-decoy-primary-profile-visual-parity.md`)
is `isMainForUiDisplay`'s (`0031`) first real consumer, closing a genuine,
2026-09-03-audit-confirmed gap unrelated to `isMain()`/`isAdminUser()` and
untouched by any prior fix in this feature: the QS footer's "End session"
affordance is gated by `UserManagerService.getUserLogoutability()`, which
returns `LOGOUTABILITY_STATUS_CANNOT_LOGOUT_SYSTEM_USER` only for the
literal `userId == UserHandle.USER_SYSTEM`. A decoy user is necessarily a
non-zero user ID, so this check never applied to it — the decoy session
saw and could use an affordance ("End session", a secondary-user-only
concept) the real owner, always user 0 on a genuinely single-owner device,
never sees.

The plan's own Step 1 flagged that its cited line number for
`getUserLogoutability` (3426) was written before `0031` landed and would
have shifted — `0031` inserted its own new method earlier in the same
file, pushing every later line down. Re-derived via a fresh `grep -n`
against the real file rather than trusting the plan's guess: the real line
is 3458. The quoted pre-edit method body otherwise matched the plan's
expectation exactly, with no other drift.

- `ccc6c058a6f0761b04027c055211bfd68c62e9f1` — "aegisos: suppress End
  session affordance for decoy session"
  (`patches/frameworks-base/0032-aegisos-suppress-end-session-for-decoy.patch`,
  real build-server SHA) — a four-line addition to
  `services/core/java/com/android/server/pm/UserManagerService.java`'s
  `getUserLogoutability(@UserIdInt int userId)`: a new
  `if (isMainForUiDisplay(userId)) { return
  UserManager.LOGOUTABILITY_STATUS_CANNOT_LOGOUT_SYSTEM_USER; }` block
  inserted immediately after the existing `userId ==
  UserHandle.USER_SYSTEM` check and before the existing
  `getUserSwitchability(userId)` check — every other line of the method,
  including `getUserSwitchability` itself (a real, separate capability
  check governing whether the decoy session can actually be switched away
  from, used by this feature's own return path), is untouched. Only the
  "End session" UI affordance's visibility changes; the decoy's actual
  switchability is unaffected. Authored under `AGENTS.md`'s local-first
  discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/decoy-visual-parity-task2/`, a fetch-only
  worktree seeded read-only from the real branch HEAD
  `b2b66088045df71f2565525c2ca173dc929da373` — only the one touched file,
  its `sha256sum` confirmed byte-identical to the live remote file before
  editing) and landed on the real branch with `git am --3way` (applied
  cleanly, no conflicts, confirmed no build was already in flight
  beforehand via `ps aux`). The real post-`git am` branch SHA
  (`ccc6c058a6f0761b04027c055211bfd68c62e9f1`) differs from the local
  worktree's own commit SHA (`e64a77d61f7786704458a301f97aac140a17e832`),
  confirmed via `git log` on the branch rather than assumed, consistent
  with every prior patch in this package.
- Base commit: `b2b66088045df71f2565525c2ca173dc929da373` (`0031`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

**Build verification:** a fast `m services` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side with the entire launch chain's own
output redirected into one log file (`{ cd ... && export ... && source
... && lunch ... && nohup m services > task2-build.log 2>&1; } >
task2-launch.log 2>&1 & disown`) rather than only the final command's, per
Task 1's own recorded lesson that redirecting only the last command in the
chain leaves the SSH connection attached to the earlier commands' stdout.
This launch returned immediately, confirmed still genuinely running and
writing its log via a fresh SSH connection, then polled to completion over
further fresh connections: `ninja: 2m32.42s Build Succeeded: 26 steps -
0.17/s`, `#### build completed successfully (02:39 (mm:ss)) ####`. No
`error`/`warning` log lines referenced `UserManagerService.java`. This is
compile-time verification only — it does not confirm the fix actually
hides the affordance at runtime; dynamic re-validation of this task
alongside Tasks 3-4 is expected per this plan's own sequencing, not yet
done as of this entry.

### `frameworks/base`: hide user-switcher-enabled state for decoy current-user view (`0033`)

Task 1 of the fifth plan
(`docs/superpowers/plans/2026-09-03-duress-decoy-user-switcher-visibility.md`)
closes a real, audit-confirmed leak distinct from anything the fourth
plan's `isMainForUiDisplay` primitive (`0031`/`0032`) touches:
`Settings.Global.USER_SWITCHER_ENABLED` is a device-wide, non-per-user flag
that stock AOSP's `UserManagerService.dispatchUserAdded()` permanently sets
to `1` the first time any non-profile user (including a decoy) is ever
created — no admin action, never reset. At least six real UI surfaces read
this via `UserManager.isUserSwitcherEnabled()`/`isUserSwitcherEnabled(boolean)`,
all of which trace back to the single-user overload,
`isUserSwitcherEnabled(@UserIdInt int userId)`.

Deliberately different from the `isMainForUiDisplay` pattern: the new check
does **not** require `userId == getCurrentUserId()` before firing. Several
real callers (the QS footer icon, the lockscreen avatar) run under
SystemUI's cross-process rendering context and pass a `userId` that does
not necessarily equal the actual current user — the exact ADR-066 failure
mode. The fix instead reads the actual current user's own type
unconditionally, independent of whatever `userId` argument was passed, so
it applies correctly to those cross-process callers too.

- `544d6e6d4567966cf0d8308e110da4a130b7df6e` — "aegisos: hide
  user-switcher-enabled state for the decoy's own current-user view"
  (`patches/frameworks-base/0033-aegisos-hide-user-switcher-for-decoy-current-user.patch`,
  real build-server SHA) — a nine-line addition (8 code lines plus one
  blank separator) to
  `services/core/java/com/android/server/pm/UserManagerService.java`'s
  `isUserSwitcherEnabled(@UserIdInt int userId)`: reads the actual current
  user's `UserInfo` under `mUsersLock` via the existing `getUserInfoLU`,
  and returns `false` immediately if its `userType` is
  `UserManager.USER_TYPE_FULL_AEGISOS_DECOY`, before the existing
  `multiUserSettingOn` computation runs. Every existing line of the method
  is otherwise unchanged. No new import needed: `UserInfo`, `getUserInfoLU`,
  `mUsersLock`, and `getCurrentUserId()` are all already used elsewhere in
  this same file (confirmed by direct `grep` before editing, matching the
  prior cycle's `isMainForUiDisplay`/`getUsersForUiDisplay` additions).
  The plan's own quoted pre-edit method body (lines 3445-3455) was
  re-verified against the live build-server source (`sed -n
  '3443,3457p'`) before editing and matched byte-for-byte, with no line
  drift from the plan's own citation. Authored under `AGENTS.md`'s
  local-first discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/decoy-switcher-visibility-task1/`, a
  fetch-only worktree seeded read-only from the real branch HEAD
  `ccc6c058a6f0761b04027c055211bfd68c62e9f1` — only the one touched file)
  and landed on the real branch with `git am --3way` (applied cleanly, no
  conflicts, confirmed no build was already in flight beforehand via
  `pgrep -fa 'ninja|soong_ui'`). The real post-`git am` branch SHA
  (`544d6e6d4567966cf0d8308e110da4a130b7df6e`) differs from the local
  worktree's own commit SHA (`5837f34625f9a833b993e8e7c824f8d31924f841`),
  confirmed via `git log` on the branch rather than assumed, consistent
  with every prior patch in this package.
- Base commit: `ccc6c058a6f0761b04027c055211bfd68c62e9f1` (`0032`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

**Build verification:** a fast `m services` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side (`nohup m services > .../decoy-switcher-task1-build.log
2>&1 < /dev/null & disown`) and polled to completion over fresh,
independent SSH connections rather than the launch connection itself
(which again did not return within its own timeout, matching the prior
two cycles' documented quirk): `ninja: 2m25.96s Build Succeeded: 18 steps
- 0.12/s`, `#### build completed successfully (02:33 (mm:ss)) ####`. No
`error`/`warning` log lines referenced `UserManagerService.java` — the
only diagnostics in the log are three pre-existing, unrelated
`UnflaggedApi` info notices against `PackageManagerLocal.java`. This is
compile-time verification only; it does not confirm the fix actually
hides the switcher at runtime on any of the six identified surfaces —
dynamic validation across all six plus the owner-regression check is
Task 2 (emulator) and Task 3 (physical `lynx`), not yet done as of this
entry.

### `frameworks/base`: fix decoy user-switcher visibility regression (`0034`, `0035`)

Task 3 (physical `lynx` hardware validation) of the fifth plan found that
`0033` did not actually work: 3 of the 6 target surfaces (the QS footer
icon, the lockscreen avatar, and — on the emulator round this held, but
was re-suspected here — the Settings toggle) still leaked the decoy's
existence on real hardware despite `0033`'s bytecode being confirmed
present and correct via `dexdump` disassembly of the flashed
`services.jar`. The owner's response ("waarom werkt het niet, vind het")
triggered a `superpowers:systematic-debugging` root-cause investigation
rather than a guessed re-fix. Two of the three surfaces resolved to
genuine, source-confirmed code defects, independent of each other and of
`0033` itself:

**`0034` — QS footer icon, structural bypass.**
`UserRepositoryImpl.kt`'s `getSettings()` (the model backing both the QS
footer's `multi_user_switch` icon via `UserSwitcherController`/
`UserSwitcherInteractor`, and the legacy switcher UI) read
`Settings.Global.USER_SWITCHER_ENABLED` directly via
`globalSettings.getInt(...)` — it never called
`UserManager.isUserSwitcherEnabled()` at all, so `0033`'s decoy check,
however correct, was structurally unreachable from this call site. This
is not a caching or timing bug: a real-hardware forensic recheck (see
below) confirmed `am get-current-user` reporting `10` in the same command
block as a `uiautomator dump` still showing `multi_user_switch` present.
Fixed by deleting the duplicate raw-setting computation and delegating to
`manager.isUserSwitcherEnabled()` instead, so this repository has one
source of truth rather than two independently-diverging ones — SystemUI
already holds `MANAGE_USERS` (confirmed via its `AndroidManifest.xml`
before relying on it), so no permission change was needed.

**`0035` — lockscreen avatar, missing invalidation.**
`KeyguardStatusBarView.setUserSwitcherEnabled(boolean)` stored the new
value into `mIsUserSwitcherEnabled` but never called
`updateVisibilities()`, the only method that actually applies that field
to `mMultiUserAvatar`'s real visibility. `updateVisibilities()` only runs
elsewhere (this view's own `setVisibility(VISIBLE)`, or the unrelated
`setKeyguardUserAvatarEnabled`/`setKeyguardUserSwitcherEnabled` setters),
so a value change delivered by
`KeyguardStatusBarViewController#updateUserSwitcher()`'s async
`UserManager` call (dispatched via `mBackgroundExecutor` then posted back
via `mMainExecutor`) could easily be overtaken by an unrelated
visibility-triggered `updateVisibilities()` call using the still-stale
previous value, with nothing re-invoking it afterward once the real value
lands. This is genuine, pre-existing upstream AOSP code — confirmed via
the surrounding class untouched by any prior AegisOS patch — dormant
until Duress Unlock became the first caller whose answer to
`isUserSwitcherEnabled()` changes live, mid-session, rather than staying
fixed for a keyguard's entire lifetime. Fixed by calling
`updateVisibilities()` directly from the setter; the one real call site
(`KeyguardStatusBarViewController.java:759`) already dispatches onto
`mMainExecutor`, so the added call is on the correct thread for view
mutation without further change.

**Settings toggle: investigated, not re-fixed.** `UserSettings.java`
(`SettingsPreferenceFragment`, not `DashboardFragment`) explicitly calls
`mMainSwitchController.updateState()` from its own `onResume()`, which
calls `UserCapabilities.updateAddUserCapabilities()`, which recomputes
`mUserSwitcherEnabled = userManager.isUserSwitcherEnabled()` fresh, on
the same correct, patched API `0034` now also uses. This exact code
already passed on the Task 2 emulator round with `0033` alone
(`docs/superpowers/plans/2026-09-03-duress-decoy-user-switcher-visibility.md`
Task 2: "Settings 'Allow multiple users' toggle PASS"). No divergent
implementation or missing-invalidation defect was found in this call
path by direct source reading. The Task 3 Phase B forensic recheck
(dispatched specifically to answer this) found the physical check reused
an already-running `com.android.settings` process for user 10 whose
activity-launch freshness (cold vs. resumed task) could not be confirmed
either way, and could not complete a further live re-check (`dumpsys
user`, a fresh `isUserSwitcherEnabled()` probe) because the physical
device disconnected from USB mid-session. This surface is therefore left
**open, not closed**: the leading explanation is a stale-process test
artifact rather than a code defect, but this is not proven, and no
speculative fix has been made against unconfirmed evidence — a repeat
hardware check with a forced-fresh Settings process is the next step,
not a third patch.

- `75e0f39f9a6522aebf29f58e1e3fda47006df158` — "aegisos: route SystemUI
  user-switcher-enabled state through UserManager"
  (`patches/frameworks-base/0034-aegisos-route-SystemUI-user-switcher-enabled-state-t.patch`,
  real build-server SHA).
- `83596c4ea7415f4fc966dc5de5b10384ac631617` — "aegisos: refresh
  lockscreen multi-user avatar visibility on enabled-state change"
  (`patches/frameworks-base/0035-aegisos-refresh-lockscreen-multi-user-avatar-visibil.patch`,
  real build-server SHA).
- Base commit: `544d6e6d4567966cf0d8308e110da4a130b7df6e` (`0033`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

Authored under `AGENTS.md`'s local-first discipline in a disposable local
git repo
(`aegisos-platform/.worktrees/duress-switcher-visibility-regression-fix/`,
a fetch-only worktree seeded read-only from the real branch HEAD above —
only the two touched files) and landed on the real branch with `git am`
(applied cleanly, no conflicts; confirmed no build was already in flight
via `pgrep -fa 'ninja|soong_ui'` immediately before). Both patch files in
this package were regenerated fresh via `git format-patch` directly from
the real post-`git am` branch history, not exported from the disposable
worktree's own commits (whose local SHAs, `4afc117` and `2216489`, differ
from the real branch SHAs above as expected). Reproduction verified
byte-for-byte: applying both patches to a fresh worktree seeded at the
`0033` base and diffing the result against the real branch's live file
content produced an empty diff for both files.

**Build verification:** a fast, fully-detached `m -j6 SystemUI` build
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), polled to
completion over a fresh SSH connection rather than the launch connection
(which again did not return within its own timeout, matching every prior
cycle's documented quirk): `ninja: 9m59.80s Build Succeeded: 21 steps -
0.04/s`. No error or warning referenced either changed file; the log's
only diagnostics are pre-existing, unrelated `SystemUI-core` KSP/jarjar
notices. This is compile-time verification only — dynamic re-validation
of all three previously-failing surfaces (emulator first, then a repeat
physical `lynx` round) is still open as of this entry.

### `frameworks/base` + `packages/apps/Settings`: decoy default-avatar color (`0036`, Settings `0010`)

Found during dynamic validation of `0034`/`0035`, prompted directly by the
owner's insistence that the decoy session must present as a genuinely
single, fresh-out-of-box device — not merely one with every specific
identity-leak surface patched. `UserIcons.getDefaultUserIcon()`
(`frameworks/base/core/java/com/android/internal/util/UserIcons.java`)
picks a default avatar color from a fixed 8-entry palette keyed by
`userId % 8` whenever a user has no custom photo set. A real-hardware
screenshot of the fresh emulator's Settings ▸ Users screen confirmed this
directly: the owner (userId 0) got `user_icon_1` (`#e46962`, red) and the
decoy (userId 10, `10 % 8 == 2`) got `user_icon_3` (`#4c8df6`, blue) — a
real, standalone identity tell, independent of every name/list-membership
leak the `getUsersForUiDisplay`/`isMainForUiDisplay` audits already cover,
since it fires even when only the decoy's own single avatar is ever shown
on screen. Two independent call sites persist or compute this color for
"my own" avatar and both needed the same fix:

- `packages/apps/Settings/src/com/android/settings/users/UserSettings.java`'s
  `assignDefaultPhoto(Context, int userId)` computes and **persists** (via
  `UserManager#setUserIcon`) a colored default bitmap the first time a
  user's Settings ▸ Users row is ever loaded without a real photo set —
  this is what produced the blue icon.
- `frameworks/base/packages/SystemUI/src/com/android/systemui/user/data/repository/UserIconProvider.kt`'s
  `getUserImage()` independently computes (in-memory only, not persisted)
  the same kind of default colored icon for SystemUI's own rendering
  paths, for any user without a persisted photo.

Both fixed the same way: route the color-lookup `userId` (never the
icon's real target `userId`, which must stay correct for
`setUserIcon`/cache-key purposes) through the existing
`UserManager.isMainForUiDisplay()` primitive (`0031`) — if the current
user displays as main (true for a decoy, by design), substitute
`UserHandle.USER_SYSTEM` so the color always matches what a genuine
single, fresh-out-of-box owner would show. An ordinary second real user
(non-decoy, non-main) is unaffected — it keeps its own distinguishing
color exactly as before, which is correct: this fix only applies to the
one user AegisOS is already treating as main-for-display purposes
elsewhere.

- `defa62237873bdcb94cc75c8e9bbe843f2957818` — "aegisos: use owner's
  default-avatar color for a decoy's own SystemUI icon"
  (`patches/frameworks-base/0036-aegisos-use-owner-s-default-avatar-color-for-a-decoy.patch`,
  real build-server SHA). Base commit: `83596c4ea7415f4fc966dc5de5b10384ac631617`
  (`0035`'s own real post-`git am` SHA).
- `573708d3d513aa7972c674ec49015e12a4d92d3a` — "aegisos: use owner's
  default-avatar color for a decoy's own icon"
  (`patches/packages-apps-settings/0010-aegisos-use-owner-s-default-avatar-color-for-a-decoy.patch`,
  real build-server SHA). Base commit: `d5e8d97332f098eeb852deb9c2ff7a17423fc402`
  (`0009`'s own real post-`git am` SHA).

Authored under `AGENTS.md`'s local-first discipline in two disposable
local git repos
(`aegisos-platform/.worktrees/duress-switcher-visibility-regression-fix/`
for the `frameworks/base` change, added as a third file/commit onto the
same worktree used for `0034`/`0035`; a new
`aegisos-platform/.worktrees/duress-avatar-color-fix/` for the
`packages/apps/Settings` change) and landed on their respective real
branches with `git am` (both applied cleanly, no conflicts; confirmed no
build was already in flight on either repo beforehand). Both patch files
were regenerated fresh via `git format-patch` directly from the real
post-`git am` branch history, not exported from the disposable worktrees'
own commits. Reproduction verified byte-for-byte for both: applying each
patch to a fresh worktree seeded at its real base commit and diffing the
result against the real branch's live file content produced an empty
diff in both cases.

**Build verification:** a fully-detached `m -j6 SystemUI Settings` build
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), polled to
completion over a fresh SSH connection: `ninja: 12m41.57s Build Succeeded:
38 steps - 0.05/s`. No error or warning referenced either changed file.
`UserManager.isMainForUiDisplay()` requires `MANAGE_USERS` — confirmed
already held by both callers before relying on it (`UserSettings.java`
already calls this same method elsewhere in the file; SystemUI already
declares `MANAGE_USERS` in its manifest, confirmed for `0034`). Dynamic
re-validation on a fresh, pristine emulator boot (to avoid a
previously-created test decoy's already-persisted, wrong-colored icon
masking the fix) is the immediate next step as of this entry.

### `packages/apps/Settings`

This is the first AegisOS patch to this repository. Prior to this patch,
the repository had never received any AegisOS-authored commit; it was on
detached HEAD at `739dfe1d7bc` (add per-app setting for exec spawning). A
real, named branch (`aegisos-duress-decoy-switch`, matching
`frameworks/base`'s and `build/release`'s existing branch name — the same
convention `build/release`'s own section above already anticipated for
this repository) was created off that detached HEAD before landing
anything, so this and future `packages/apps/Settings` commits land on a
real ref, not an anonymous detached-HEAD history.

Task 5 of the third plan
(`docs/superpowers/plans/2026-08-28-duress-decoy-session-indistinguishability.md`)
routes the three real Settings call sites that build the Storage screen's
per-user breakdown onto Task 3's `getUsersForUiDisplay` API
(`patches/frameworks-base/0019`), closing the leak described in that
patch's own "Component B, part 3" section above: before this patch, a
decoy session could open Settings ▸ Storage and see the real hidden
owner's name, avatar, and per-user storage size under "Other users". All
three call sites migrate to `getUsersForUiDisplay(false /* excludeDying */)`
— matching the real no-arg `UserManager.getUsers()` wrapper's own body,
confirmed against `frameworks/base`'s actual source before editing (see
the `frameworks/base` `0021` entry above for the exact wrapper body cited).

- `50fe764d7c9a37b343f776d9e2e2d0d4ee2560d3` — "aegisos: route Settings
  Storage screen through getUsersForUiDisplay"
  (`patches/packages-apps-settings/0001`, real build-server SHA) —
  three one-line changes, each re-confirmed via `grep -n`/direct read
  against the actual build-server source (plus a byte-for-byte
  `sha256sum` match between each fetched local copy and the live remote
  file) before editing:
  `NonCurrentUserController.java:74`'s
  `getNonCurrentUserControllers(Context, UserManager)` now calls
  `userManager.getUsersForUiDisplay(false /* excludeDying */)` instead of
  `userManager.getUsers()`;
  `UserIconLoader.java:69`'s `loadUserIconsWithContext(Context)` now
  iterates `um.getUsersForUiDisplay(false /* excludeDying */)` instead of
  `um.getUsers()`;
  `StorageAsyncLoader.java:82`'s `getStorageResultsForUsers()` now calls
  `mUserManager.getUsersForUiDisplay(false /* excludeDying */)` instead of
  `mUserManager.getUsers()`. Authored under `AGENTS.md`'s local-first
  discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-indistinguishability-task5a/`, a
  fetch-only worktree seeded read-only from the real repository HEAD
  `739dfe1d7bc` — only the three touched files, not a full checkout) and
  landed on the real branch with `git am --3way` (applied cleanly, no
  conflicts).
- Base commit: `739dfe1d7bc` (this repository's real HEAD at the time —
  first entry for this repository, so this is also the branch point).

**Build verification:** a fast `m Settings` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`) after both
this patch and `frameworks/base`'s `0021` landed, launched fully detached
on the remote side (`nohup ... & disown`) and polled to completion with a
bounded synchronous loop rather than an async background-task
notification: `ninja: 6m11.80s Build Succeeded: 165 steps - 0.44/s`,
`#### build completed successfully (06:17 (mm:ss)) ####`, `EXIT_CODE:0`.
This one build check confirms both patches together: it needs
`packages/apps/Settings`'s own three call-site changes to compile at all
(they reference `getUsersForUiDisplay`), and the real build log confirms
`//frameworks/base/packages/SettingsLib:SettingsLib` — the module
containing `StorageMeasurement.java`, `0021`'s own file — was recompiled
as a dependency in the same build. It is not a consolidated
`m systemimage` build and does not by itself validate runtime behavior
(e.g. that a decoy session's Storage screen genuinely shows only itself)
— no task in this plan has yet scheduled a dynamic exercise of this
specific UI surface.

Task 8 of the third plan
(`docs/superpowers/plans/2026-08-28-duress-decoy-session-indistinguishability.md`,
"Component D — minor P3 fixes") routes the two remaining Settings
multi-user UI hints — `Utils.hasMultipleUsers(Context)` and
`WifiConfigController2`'s `mIsMultiUser` field initializer — onto the same
`getUsersForUiDisplay` API, matching the pattern `0001` above already
established. The task brief's own quoted code for both sites was marked
as a paraphrase, not a verbatim citation; both real bodies were read in
full (`sed -n`) before editing and matched the brief's predicted shape
closely: `hasMultipleUsers` called the real no-arg `UserManager.getUsers()`
directly, and `WifiConfigController2` called `UserManager.getUserCount()`,
which frameworks/base's real `UserManager.java` (line 5197-5199) shows is
itself implemented as `getUsers().size()`. Both therefore share `0001`'s
own precedent — the real no-arg `getUsers()` wrapper is `getUsers(/*
excludeDying=*/ false)` — not the task brief's suggested
`getUsersForUiDisplay(true)`. Per the task's own instruction to confirm
against real behavior rather than blindly apply `true` when the source
clearly implies otherwise, both sites were migrated to
`getUsersForUiDisplay(false /* excludeDying */)`, preserving each site's
exact prior semantics (dying users still counted), consistent with `0001`
above and not a genuinely ambiguous case.

- `229434697ac35133c86de3c7cfaeeda7740e8b47` — "aegisos: route Settings
  multi-user UI hints through getUsersForUiDisplay"
  (`patches/packages-apps-settings/0002`, real build-server SHA — the
  patch file itself was generated from local commit
  `0787db5109a5d35669e704a75aaa0b185133d4b9` in a disposable worktree;
  `git am` reconstructs the commit against the real tree, so it gets a
  genuinely different SHA even for byte-identical content) —
  `Utils.java:421-423`'s `hasMultipleUsers(Context)` now returns
  `context.getSystemService(UserManager.class)
  .getUsersForUiDisplay(false /* excludeDying */).size() > 1` instead of
  `.getUsers().size() > 1`;
  `WifiConfigController2.java:290-291`'s constructor now sets
  `mIsMultiUser = mContext.getSystemService(UserManager.class)
  .getUsersForUiDisplay(false /* excludeDying */).size() > 1` instead of
  `.getUserCount() > 1`. Both call sites' pre-edit bodies were confirmed
  via a byte-for-byte `sha256sum` match between the fetched local copy and
  the live remote file before editing. Authored under `AGENTS.md`'s
  local-first discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-indistinguishability-task8/`, a
  fetch-only worktree seeded read-only from the real repository HEAD
  `50fe764d7c9a37b343f776d9e2e2d0d4ee2560d3` — only the two touched files,
  not a full checkout) and landed on the real branch with `git am --3way`
  (applied cleanly, no conflicts).
- Base commit: `50fe764d7c9a37b343f776d9e2e2d0d4ee2560d3` (`0001` above,
  this repository's real HEAD at the time).

**Build verification:** a fast `m Settings` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side (`nohup ... & disown`) and polled to
completion with a bounded synchronous loop rather than an async
background-task notification: `ninja: 5m26.67s Build Succeeded: 22 steps -
0.07/s`, `#### build completed successfully (05:31 (mm:ss)) ####`. No
compile errors; unrelated pre-existing Kotlin deprecation warnings only.
Same caveat as `0001` above: this is not a consolidated `m systemimage`
build and does not validate runtime behavior.

### Fix found by the closing whole-branch review: `WifiUtils.java` (`0003`)

The same closing whole-branch review that found `frameworks/base` `0024`'s
Critical gap also found `0002` above was incomplete: the design doc
(`docs/superpowers/specs/2026-08-28-duress-decoy-session-indistinguishability-design.md`)
named **three** unscoped Wi-Fi call sites, not two —
`WifiConfigController2.java` and `WifiUtils.java` — but Task 8 only
migrated the first. Confirmed by direct source read before fixing:
`WifiUtils.java:387`'s `isCurrentUserNetworkOwner(WifiEntry, Context)`
called the unfiltered `userManager.getUserCount()`, so on a device with a
decoy present, a network created by the real owner before the decoy
existed would report as not-owned-by-the-current-user in the decoy
session — a real ownership-based multi-user tell.

- `b9b6d301e5a8386d25142ea1c047f88011629acc` — "aegisos: route
  WifiUtils#isCurrentUserNetworkOwner through getUsersForUiDisplay"
  (`patches/packages-apps-settings/0003`, real build-server SHA) — one
  call-site change: `isCurrentUserNetworkOwner`'s `userCount` now comes
  from `userManager.getUsersForUiDisplay(false /* excludeDying */).size()`
  instead of `userManager.getUserCount()`, matching `0001`/`0002`'s own
  `false` convention. Authored under `AGENTS.md`'s local-first discipline
  in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-indistinguishability-review-fix-settings/`,
  a fetch-only worktree seeded read-only from the real branch HEAD
  `229434697ac35133c86de3c7cfaeeda7740e8b47` — only the one touched file,
  not a full checkout) and landed on the real branch with `git am --3way`
  (applied cleanly, no conflicts).
- Base commit: `229434697ac35133c86de3c7cfaeeda7740e8b47` (`0002` above,
  this repository's real HEAD at the time).

**Build verification:** covered by the same consolidated `lynx-cur-userdebug`
`m systemimage` rebuild described in the `frameworks/base` `0024` entry
above (both repositories' final patches were landed and rebuilt together)
— `ninja: 53m09.68s Build Succeeded: 3775 steps`, clean, no compile errors
in `WifiUtils.java` or any file in this repository.

### Exhaustive leak-audit fixes, part 1 (`0004`, `0005`, `0006`)

Following ADR-067's exhaustive, owner-requested audit for every
remaining decoy identity-leak surface
(`docs/superpowers/specs/2026-08-30-duress-decoy-remaining-leaks-audit.md`,
21 items found), fixed in the audit's own recommended priority order.

- `ab4b0a932a049f5a3c484c72139dd81ff5661e4b` — "aegisos: fix Settings
  Users screen to use getUsersForUiDisplay" (`patches/packages-apps-settings/0004`,
  real build-server SHA) — Critical item 2.
  `UserSettings.updateUserList()` called the raw
  `UserManager#getAliveUsers()`, gated only on
  `UserManager.supportsMultipleUsers()`, not admin status — reachable
  from a non-admin AegisOS Duress Unlock decoy session, listing the
  real owner's name as a switchable preference. Confirmed
  `getAliveUsers()`'s own real implementation
  (`return getUsers(/* excludeDying= */ true)`) is exactly
  `getUsersForUiDisplay(true)`'s own non-decoy-branch semantics before
  substituting it.
- `fcbfa430a14b79b01fdea0aeaaaa449670950fc5` — "aegisos: don't name
  another real user in storage move/migrate dialogs"
  (`patches/packages-apps-settings/0005`, real build-server SHA) —
  Critical item 3. `StorageWizardMigrateConfirm` and
  `StorageWizardMoveConfirm` both iterate every real user via raw
  `getUsers()` to unlock their CE storage before a move/migrate can
  proceed — that enumeration is left unchanged, it's a genuine
  functional requirement, not a display list. But the credential dialog
  shown for an already-secured, currently-locked user named that user
  directly (`storage_wizard_move_unlock`'s `^1` template), reachable
  from within the decoy's own Settings if the real owner's storage
  happens to be locked. Fixed by adding a new string
  (`storage_wizard_move_unlock_generic`, no name) and a small
  `isDecoySession()` helper in each class (the same
  `getUsersForUiDisplay(true).size()==1 && userType==DECOY` pattern used
  throughout this feature), shown instead of the real templated string
  only when the current session is the decoy — the unlock loop itself
  is untouched.
- `195738a1ca6...` — "aegisos: fix Wi-Fi shared badge/dialog and
  factory-reset warning to use getUsersForUiDisplay"
  (`patches/packages-apps-settings/0006`, real build-server SHA) — High
  items 5, 6, 7. Three more raw-`getUserCount()` sibling call sites of
  the already-fixed `WifiUtils`/`WifiConfigController2`/uninstall-dialog
  pattern: `WifiEntryPreference.displaySharedIcon()`'s "shared" badge,
  `WifiDetailPreferenceController2`'s "forget shared network"
  confirmation dialog, and `MainClear`'s "other users present on this
  device" factory-reset warning (the latter combined with the existing
  `profilesSize` subtraction, unchanged). All three fixed with
  `getUsersForUiDisplay(true).size()`.
  All three authored under `AGENTS.md`'s local-first discipline in
  disposable local git repos
  (`aegisos-platform/.worktrees/duress-usersettings-fix/`,
  `aegisos-platform/.worktrees/duress-storagewizard-fix/`,
  `aegisos-platform/.worktrees/duress-wifi-factoryreset-fix/`, each a
  fetch-only worktree seeded read-only from the real branch HEAD at
  fetch time — `b9b6d301e5a8386d25142ea1c047f88011629acc`,
  `ab4b0a932a049f5a3c484c72139dd81ff5661e4b`, and
  `fcbfa430a14b79b01fdea0aeaaaa449670950fc5` respectively — with every
  fetched file's `sha256sum` confirmed byte-identical to the live remote
  file before editing) and each landed on the real branch with `git am`
  (applied cleanly, no conflicts, confirmed no build was already in
  flight beforehand every time). All three patch files were regenerated
  fresh via `git format-patch` directly from the real post-`git am`
  branch history, not exported from any disposable worktree's own
  commit.
- `79f050ee30fde0a7f3d835b0cabcbf3dd1a2d4e7` — "aegisos: fix
  Utils.isProfileOrDeviceOwner to use getUsersForUiDisplay"
  (`patches/packages-apps-settings/0007`, real build-server SHA) — found
  while tracing the remaining-leaks audit's own low-confidence item 16
  (`docs/superpowers/specs/2026-08-30-duress-decoy-remaining-leaks-audit.md`).
  The audit cited `AppButtonsPreferenceController.java:469` as possibly
  moot if the decoy user type is non-admin — confirmed non-admin (no
  `FLAG_ADMIN` in `UserTypeFactory.java`'s
  `getDefaultTypeFullAegisosDecoy()`, and no creation path can override
  it), but that turned out not to matter: unlike the sibling
  `AppInfoDashboardFragment.shouldShowUninstallForAll()` check (which
  *is* genuinely gated behind `!mUserManager.isAdminUser()`, confirming
  the audit's item 16 citations for `AppInfoDashboardFragment.java:607`
  and `:726` as moot), `Utils.isProfileOrDeviceOwner(UserManager,
  DevicePolicyManager, String)` has no admin gate anywhere in its own
  call path — `AppButtonsPreferenceController.updateUninstallButton()`
  calls it unconditionally. The same unfiltered-`getUsers()` call inside
  that method also has three more real callers the original 21-item
  audit missed entirely, in the newer Compose "SPA" App Info screens
  (`AppButtonRepository.kt:92`, `AppDisableButton.kt:87`,
  `AppInfoSettingsMoreOptions.kt:151`) — fixing the shared `Utils.java`
  method closes all four call sites at once rather than patching only
  the one the audit cited. `isDeviceOwnerAppOnAnyUser()`, the other
  cross-user check in the same method, is intentionally left unscoped:
  it is a genuine device-wide security control (should block uninstall
  regardless of which user asks), not a per-user display concern.
  Authored under `AGENTS.md`'s local-first discipline in a disposable
  local git repo
  (`aegisos-platform/.worktrees/duress-appbuttons-uninstall-fix/`, a
  fetch-only worktree seeded read-only from the real branch HEAD
  `195738a1ca67d6b43cc8dec760d76c1a82432bdb` — only the one touched
  file, its `sha256sum` confirmed byte-identical to the live remote file
  before editing) and landed on the real branch with `git am --3way`
  (applied cleanly, no conflicts, confirmed no build was already in
  flight beforehand). This patch file was regenerated fresh via `git
  format-patch` directly from the real post-`git am` branch history, not
  exported from the disposable worktree's own commit.
- Base commits: `b9b6d301e5a8386d25142ea1c047f88011629acc` (`0003`'s own
  real post-`git am` SHA) for `0004`;
  `ab4b0a932a049f5a3c484c72139dd81ff5661e4b` (`0004`'s own real
  post-`git am` SHA) for `0005`;
  `fcbfa430a14b79b01fdea0aeaaaa449670950fc5` (`0005`'s own real
  post-`git am` SHA) for `0006`; `195738a1ca67d6b43cc8dec760d76c1a82432bdb`
  (`0006`'s own real post-`git am` SHA) for `0007`.

**Build verification:** `0004` confirmed via a fast `m Settings` build
check (`Build Succeeded: 160 steps`), `0005` via another (`Build
Succeeded: 89 steps` — this run also confirms the new
`storage_wizard_move_unlock_generic` string resource resolved cleanly,
no missing-resource error), `0006` via another (`Build Succeeded: 24
steps`), `0007` via another (`Build Succeeded: 20 steps`, 5m49s — only
pre-existing, unrelated warnings, e.g. missing
`com.google.android.appfunctions` classes stripped from this
GrapheneOS-derived tree). No diagnostics attached to any edited file in
any of the four runs. All four are compile-time verification only;
runtime confirmation is deferred to a later consolidated
hardware-validation round for this whole audit-fix batch (see
ADR-068 for the first ten items; `0007` joins the same batch).
Separately, a full `m systemimage` consolidated build
(`out-preflight`, `sdk_phone64_x86_64-cp2a-userdebug`) confirmed the
original ten fixes (`aegisos-duress-decoy-switch` branch state as of
2026-08-30) compile together as one coherent build: `ninja: 54m13.01s
Build Succeeded: 3658 steps`, `#### build completed successfully
(54:19) ####`, log at
`/srv/aegisos/evidence/consolidated-build-20260902.log` on the build
server. `0007` was landed after that run completed (confirmed via
`pgrep` before `git am`, per the project's own
never-`git am`-during-a-build rule) and is not yet included in a full
systemimage-level consolidated build — that coverage comes from the
physical `lynx` candidate build this fix is now part of.

### Fourth plan: primary-profile visual parity, Task 3 (`0008`)

`docs/superpowers/plans/2026-09-03-duress-decoy-primary-profile-visual-parity.md`
Task 3 closes a real, audit-confirmed cosmetic gap distinct from any
identity leak: Settings ▸ Users already shows only the decoy's own row
(via `0004`'s already-landed `getUsersForUiDisplay` fix), but that row's
summary label is decided by `if (user.isMain()) "Owner" else if
(user.isAdmin()) "Admin" else null` — since the decoy is neither main nor
admin, its own (only visible) row showed no summary at all, while a
genuine single-owner phone's one row always reads "Owner". This is the
first patch in this package to consume the new `frameworks/base`
`isMainForUiDisplay` primitive (`0031`, ADR-074) rather than
`getUsersForUiDisplay` — a narrower, display-only main/system-user-or
-current-decoy check, not a user-list filter.

- `885b048dd3c80cefb4e47dde2332abb058edad47` — "aegisos: label the
  decoy's own row as Owner in Settings > Users"
  (`patches/packages-apps-settings/0008`, real build-server SHA — the
  patch file itself was generated from local commit
  `56992c0466955decba42ee724c562261d0737c74` in a disposable worktree;
  `git am` reconstructs the commit against the real tree, so it gets a
  genuinely different SHA even for byte-identical content) — one-line
  change: `UserSettings.java:1395`'s `if (user.isMain())` becomes `if
  (user.isMain() || mUserManager.isMainForUiDisplay(user.id))`;
  `mUserManager` is the class's own pre-existing field (confirmed at
  line 241, `private UserManager mUserManager;` — no new field or
  import needed). The `else if (user.isAdmin())` and final `else {
  pref.setSummary(null); }` branches are untouched. Real current content
  re-confirmed via `git show HEAD:...` against the actual build-server
  source (exact match to the brief's quoted block) before editing.
  Authored under `AGENTS.md`'s local-first discipline in a disposable
  local git repo
  (`aegisos-platform/.worktrees/decoy-visual-parity-task3/`, a
  fetch-only worktree seeded read-only from the real branch HEAD
  `79f050ee30fde0a7f3d835b0cabcbf3dd1a2d4e7` — only the one touched
  file, not a full checkout) and landed on the real branch with `git am
  --3way` (applied cleanly, no conflicts, confirmed no build was
  already in flight beforehand). This patch file was regenerated fresh
  via `git format-patch` directly from the real post-`git am` branch
  history, not exported from the disposable worktree's own commit.
- Base commit: `79f050ee30fde0a7f3d835b0cabcbf3dd1a2d4e7` (`0007` above,
  this repository's real HEAD at the time).

**Build verification:** a fast `m Settings` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side (`nohup ... & disown`, the launch SSH
connection itself hung per the known Task 1 quirk even though the build
process was confirmed genuinely running via a separate connection) and
polled to completion with a bounded synchronous loop: `ninja: 6m53.25s
Build Succeeded: 166 steps - 0.40/s`, `#### build completed successfully
(06:58 (mm:ss)) ####`. No compile errors; only pre-existing, unrelated
Kotlin deprecation warnings (`AccessPoint`, `ROUND_HALF_UP`, `bundleOf`,
`checkIfRestrictionEnforced`) in files this patch does not touch. This
confirms `frameworks/base`'s `0031` (`isMainForUiDisplay`) was genuinely
present and resolvable in the current `out-preflight` framework build
output — no unresolved-symbol failure occurred. Not a consolidated `m
systemimage` build and does not by itself validate runtime behavior
(that the decoy's own Settings ▸ Users row genuinely reads "Owner" on a
real device) — no task in this plan has yet scheduled a dynamic exercise
of this specific UI surface.

### Fourth plan: primary-profile visual parity, Task 4 (`0009`)

`docs/superpowers/plans/2026-09-03-duress-decoy-primary-profile-visual-parity.md`
Task 4 closes a second, distinct cosmetic gap the same audit found:
`DeviceAdminAdd.addDeviceAdminPolicies()` picks between two pre-existing
string variants (`pi.description`/`pi.descriptionForSecondaryUsers`,
`pi.label`/`pi.labelForSecondaryUsers`) based on
`UserManager.get(this).isAdminUser()` — a decoy activating a device-admin
app (e.g. a Find My Device-style app) would see the "secondary user"
wording a real single-owner admin never would. Like Task 3, this consumes
`frameworks/base`'s `isMainForUiDisplay` primitive (`0031`, ADR-074).

- `d5e8d97332f098eeb852deb9c2ff7a17423fc402` — "aegisos: show the owner's
  device-admin policy text to the decoy"
  (`patches/packages-apps-settings/0009`, real build-server SHA — the
  patch file itself was generated from local commit
  `91bd68206c70c04524c1ada184f47ebdf0d409bb` in a disposable worktree;
  `git am` reconstructs the commit against the real tree, so it gets a
  genuinely different SHA even for byte-identical content) — one-line
  change: `DeviceAdminAdd.java:802`'s `boolean isAdminUser =
  UserManager.get(this).isAdminUser();` becomes `boolean isAdminUser =
  UserManager.get(this).isAdminUser() ||
  UserManager.get(this).isMainForUiDisplay(UserHandle.myUserId());`;
  `UserHandle` is the class's own pre-existing import (confirmed at line
  62) and `UserHandle.myUserId()` is already used elsewhere in this exact
  file (lines 433, 547, 728, 797) — no new import needed. No other line
  in the method or file changes. Real current content re-confirmed via
  `git show HEAD:...` against the actual build-server source (exact
  match to the brief's quoted line) before editing. Authored under
  `AGENTS.md`'s local-first discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/decoy-visual-parity-task4/`, a
  fetch-only worktree seeded read-only from the real branch HEAD
  `885b048dd3c80cefb4e47dde2332abb058edad47` — only the one touched
  file, not a full checkout) and landed on the real branch with `git am
  --3way` (applied cleanly, no conflicts, confirmed no build was
  already in flight beforehand). This patch file was regenerated fresh
  via `git format-patch` directly from the real post-`git am` branch
  history, not exported from the disposable worktree's own commit —
  confirmed byte-identical to a second, independent regeneration from
  the real branch.
- Base commit: `885b048dd3c80cefb4e47dde2332abb058edad47` (`0008` above,
  this repository's real HEAD at the time).

**Build verification:** a fast `m Settings` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side (`nohup ... & disown`, the launch SSH
connection itself hung per the known Task 1 quirk even though the build
process was confirmed genuinely running via a separate connection) and
polled to completion: `ninja: 5m07.60s Build Succeeded: 20 steps -
0.07/s`, `#### build completed successfully (05:13 (mm:ss)) ####`. No
compile errors; only pre-existing warnings unrelated to this change
(a `[removal]` deprecation warning in `ManageApplications.java` and
missing-class warnings from an unrelated Google `appfunctions` schema
dependency, neither touched by this patch). This confirms
`frameworks/base`'s `0031` (`isMainForUiDisplay`) resolves correctly
from this repository too, matching Task 3's own confirmation. Not a
consolidated `m systemimage` build and does not by itself validate
runtime behavior (that a decoy activating a device-admin app genuinely
sees the owner's wording on a real device) — no task in this plan has
yet scheduled a dynamic exercise of this specific UI surface.

## Apply (this section)

```sh
cd packages/apps/Settings && git checkout -b aegisos-duress-decoy-switch && \
  git am ../../credential/duress-unlock-decoy/patches/packages-apps-settings/*.patch
```

### `packages/apps/Multiuser`

This is the first AegisOS patch to this repository. Prior to this patch,
the repository had never received any AegisOS-authored commit; it was on
detached HEAD at `44f89da6503c7af07e70e0f6f0f2e2c7ea287c2f` ("Snap for
14980854 from b4db28f24cf05e24299542c4f05afeb78b5a146c to 26Q2-release"). A
real, named branch (`aegisos-duress-decoy-switch`, matching
`frameworks/base`'s, `build/release`'s, and `packages/apps/Settings`'s own
existing branch name) was created off that detached HEAD before landing
anything, so this and future `packages/apps/Multiuser` commits land on a
real ref, not an anonymous detached-HEAD history.

Task 6 of the third plan
(`docs/superpowers/plans/2026-08-28-duress-decoy-session-indistinguishability.md`)
closes the last leak in "Component B" (the multiuser widget): the widget's
"Switch User" action tile was always shown whenever the platform-level
switcher was otherwise enabled, regardless of how many users the caller
could actually see — for a decoy session that can only see itself via
Task 3's `getUsersForUiDisplay`, the tile would still appear and offer to
switch to a list that only ever resolves to itself. Before writing any
code, this task also resolved a genuine, previously-unverified ambiguity:
`MultiuserWidgetLib` (the `android_library` module in
`packages/apps/Multiuser/Android.bp` that actually compiles
`UsersRepository.kt`) has neither `sdk_version` nor `platform_apis` set,
unlike the outer `Multiuser` `android_app` module which does have
`platform_apis: true` — so it was not knowable by reading the `.bp` file
alone whether `@hide`/`@SystemApi` framework methods like
`getUsersForUiDisplay` would even resolve at compile time for that
library. Resolved empirically, not assumed: the real build check (below)
compiled `MultiuserWidgetLib`'s `kotlinc` step cleanly with the new call
present, confirming this module does get platform API visibility (Soong
default resolution chain for an `android_library` with no `sdk_version`
in this tree's configuration was not further investigated beyond that
empirical confirmation).

- `0ab4e9349751ca354cd8cf768cf2db968cb71932` — "aegisos: hide Switch User
  widget action when nothing else is visible"
  (`patches/packages-apps-multiuser/0001`, real build-server SHA) — a
  three-line addition to `UsersRepository.kt`'s
  `checkUserSwitchRestrictions()`: after the existing
  `isUserSwitcherEnabled` check and before the final `return
  UserSwitchRestrictions.NONE`, a new check calls
  `userManager.getUsersForUiDisplay(true /* excludeDying */).size <= 1` and
  reuses the existing `UserSwitchRestrictions.DISABLED` case (the same
  outcome the adjacent `isUserSwitcherEnabled` check already returns for a
  conceptually similar "can't switch" state) — confirmed against the real
  current file via `grep -n`/direct read before editing; the real body
  matched the plan's expected text exactly, no drift found. Authored under
  `AGENTS.md`'s local-first discipline in a disposable local git repo
  (`aegisos-platform/.worktrees/duress-indistinguishability-task6/`, a
  fetch-only worktree seeded read-only from the real repository HEAD
  `44f89da6503c` — only the one touched file, not a full checkout; the
  fetched copy's `sha256sum` matched the live remote file byte-for-byte
  before editing) and landed on the real branch with `git am --3way`
  (applied cleanly, no conflicts). The exported patch was independently
  re-verified after landing: the real post-`git am` file's `sha256sum` on
  the build server matches the disposable worktree's own post-commit
  file exactly.
- Base commit: `44f89da6503c7af07e70e0f6f0f2e2c7ea287c2f` (this
  repository's real HEAD at the time — first entry for this repository,
  so this is also the branch point).

**Build verification:** a fast `m Multiuser` build check
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side (`nohup ... & disown`) and polled to
completion with a bounded synchronous loop: `ninja: 1m25.71s Build
Succeeded: 86 steps - 1.00/s`, `#### build completed successfully (01:31
(mm:ss)) ####`, `EXIT_CODE:0`. Both `MultiuserWidgetLib` (the module
containing the edited `UsersRepository.kt`) and the outer `Multiuser` app
module compiled cleanly through `kotlinc`/`r8`/`signapk`/`dexpreopt`; the
only compiler diagnostics were three pre-existing warnings in unrelated
files (`SettingsGlobalJob.kt`, `TextMeasurable.kt`,
`VerticalPaddingWrapperStack.kt`), none touching the edited code path.
This directly confirms the API-visibility question raised above:
`getUsersForUiDisplay` resolved for `MultiuserWidgetLib` at compile time
with no `@hide`/`@SystemApi` visibility error, so no amendment to Task 3
was needed. The task's own plan-supplied build command
(`m com.android.multiuser`) was itself wrong — that string is the app's
manifest `package=` attribute, not its Soong module name (confirmed via
`grep` against `AndroidManifest.xml` and `Android.bp`); it failed with
`Schedule Failure: unknown target "com.android.multiuser"` before any
compilation occurred, corrected to the real module name (`Multiuser`) per
`Android.bp`'s own `android_app { name: "Multiuser", ... }` declaration.
This is not a consolidated `m systemimage` build and does not by itself
validate runtime behavior (e.g. that a decoy session's widget genuinely
hides the tile) — no task in this plan has yet scheduled a dynamic
exercise of this specific UI surface.

### Exhaustive leak-audit fix (`0002`)

- `13340a1...` — "aegisos: route UsersRepository#getUsers() through
  getUsersForUiDisplay" (`patches/packages-apps-multiuser/0002`, real
  build-server SHA) — Medium item 12. This widget's own data layer
  (`UsersRepository.kt`'s `getUsers()`) called the raw
  `UserManager#getAliveUsers()`, three lines away from its own sibling
  `checkUserSwitchRestrictions()` in the same file, which was already
  migrated to `getUsersForUiDisplay(true)` back in `0001`. Every real
  user's name/avatar loaded into memory on every widget refresh,
  currently withheld from rendering only by an incidental filter
  elsewhere in this widget (`CurrentUserStateUseCase.kt`) — access
  control by accident, not design; found during an exhaustive audit of
  every `getUsers()`-style call site. Fixed by matching the sibling
  method's own already-correct pattern.
  Authored under `AGENTS.md`'s local-first discipline in a disposable
  local git repo
  (`aegisos-platform/.worktrees/duress-multiuser-widget-datalayer-fix/`,
  a fetch-only worktree seeded read-only from the real branch HEAD
  `0ab4e9349751ca354cd8cf768cf2db968cb71932` — only the one touched
  file, its `sha256sum` confirmed byte-identical to the live remote file
  before editing) and landed on the real branch with `git am` (applied
  cleanly, no conflicts, confirmed no build was already in flight
  beforehand). This patch file was regenerated fresh via `git
  format-patch` directly from the real post-`git am` branch history, not
  exported from the disposable worktree's own commit.
- Base commit: `0ab4e9349751ca354cd8cf768cf2db968cb71932` (`0001`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

**Build verification:** a fast `m Multiuser` build check
(`Build Succeeded: 30 steps`) confirmed both `MultiuserWidgetLib` and
the outer `Multiuser` app module compiled cleanly, with only the same
three pre-existing unrelated warnings noted in `0001`'s own entry above
— none touching the edited code path. Compile-time verification only;
runtime confirmation (does the widget genuinely omit every real user's
data from memory during a decoy session, not just from rendering)
deferred to the same consolidated hardware-validation round as the
other patches in this audit-fix batch.

### Avatar-color leak audit, remaining call sites (`0003`)

Prompted by the owner's demand for real certainty that every avatar-color
call site is covered, not just the two found first
(`frameworks/base` `0034`-`0036`, `packages/apps/Settings` `0010`). An
exhaustive `grep` across every real `UserIcons.getDefaultUserIcon`/
`getUserIconColors` call site in `frameworks/base`, `packages/apps/Settings`,
and `packages/apps/Multiuser` found this widget's own `ImageRepository.kt`
had the identical bug a fourth time: `getAvatar(userId)` falls back to
`UserIcons.getDefaultUserIcon(resources, userId, false)` (the real,
uncorrected `userId`) whenever `UserManager#getUserIcon(userId)` returns
null — i.e. whenever this widget happens to render a user before that
user's own Settings ▸ Users row has ever assigned and persisted a default
photo. A real, user-installable home-screen surface, not a hypothetical
one.

- `c701d199f831ef5ee05c60a4735dad99b900b875` — "aegisos: use owner's
  default-avatar color for a decoy in the widget"
  (`patches/packages-apps-multiuser/0003-aegisos-use-owner-s-default-avatar-color-for-a-decoy.patch`,
  real build-server SHA). Base commit: `13340a1ba34388f522203198650ddd6e70ab14be`
  (`0002`'s own real post-`git am` SHA).

Same fix pattern as the other three: route the color-lookup `userId`
through `isMainForUiDisplay`, substituting `UserHandle.USER_SYSTEM` when
the current user displays as main. `MANAGE_USERS` already declared in this
app's own `AndroidManifest.xml` — confirmed before relying on it, not
assumed from the sibling `0034`/`0036` fixes' own confirmation for a
different app.

The remaining ~10 `UserIcons`-color call sites across `frameworks/base` and
`packages/apps/Settings` were individually read and confirmed **not**
bugs, not merely skipped: `ApplicationPackageManager`/`CrossProfileApps`
badge/cross-profile item icons don't apply to a decoy session (no
sub-profiles exist under a decoy); `CrossProfileApps` itself already
hardcodes `UserHandle.USER_SYSTEM` for its "personal profile" icon
concept, unrelated to any specific real user; `UserSwitchingDialog`
(`UserController.showUserSwitchDialog`) is structurally unreachable for a
decoy switch — traced to source: `DecoyProfileHelper.freezeAndSwitchToDecoy`
calls `ActivityManagerInternal#switchToUserSilently`, whose
`UserController#switchToUserSilently` implementation calls
`sendStartUserSwitchFgMessage` directly and never checks
`mUserSwitchUiEnabled` or calls `showUserSwitchDialog` at all — confirming
ADR-053's "dialog-free silent switch" design intent with source evidence,
not by trusting the prior label; `EditUserPhotoController`'s call uses a
user-chosen tint color, not a userId-derived one; `CreateUserDialogController`
and `EditUserInfoController` both use `UserHandle.USER_NULL` (the
neutral-gray placeholder case), not a real userId, for their "add/edit
user" dialog previews; `UserCreator.kt` assigns a *newly created ordinary*
second user's own default icon color, which should legitimately differ
from the owner's — unrelated to decoy identity, since this path only
handles creating a plain `USER_TYPE_FULL_SECONDARY` user, never a decoy;
`UserAdapter.java`'s (personal/work profile picker) `tintColor` is a fixed
Material theme color (`materialColorPrimary`), never userId-derived, and
the bouncer's tablet-only user-switcher grid
(`KeyguardSecurityContainer`) is confirmed structurally dormant on `lynx`
via its real gating resource, `config_enableBouncerUserSwitcher`
(`false` in the base `values/config.xml`, the only `true` override
belonging to the `tangorpro` tablet vendor overlay, absent from this
device).

Also confirmed during this pass, independent of the audit itself: a
SECOND, ALREADY-CORRECT parallel repository,
`frameworks/base/packages/SystemUI/src/com/android/systemui/user/data/repository/UserSwitcherRepository.kt`
(`UserSwitcherRepositoryImpl`), backs part of the QS footer's status
model and already calls `userManager.isUserSwitcherEnabled(...)` — the
same correct, patched API `0034` also routes through — and its
`userSwitcherStatus` flow short-circuits to `UserSwitcherStatusModel.Disabled`
whenever that check is false, before ever combining in the (possibly
still-wrong-colored, if not yet persisted) avatar bitmap. This file
needed no fix; it is recorded here as evidence actually checked, not
assumed safe by association with its similarly-named sibling
(`UserRepositoryImpl.kt`, `0034`'s own target).

**Build verification:** a fully-detached `m -j6 SettingsLib Multiuser
Settings SystemUI` build (`sdk_phone64_x86_64-cp2a-userdebug`,
`OUT_DIR=out-preflight`), polled to completion over a fresh SSH
connection. Both `frameworks-base` `0037` and this `0003` patch were
regenerated fresh via `git format-patch` from the real post-`git am`
branch history and independently verified byte-for-byte reproducible
(apply-and-diff against the real branch tip, empty diff for both files).
A real, caught-in-time mistake during this round: a stale glob
(`git am /tmp/0003-*.patch`) on the build server matched three
differently-dated `/tmp/0003-*.patch` leftovers from earlier sessions and
applied the wrong one first (a `frameworks/base` patch, rejected outright
by `packages/apps/Multiuser`'s own tree with "does not exist in index");
caught immediately by the `git am` failure itself, `git am --abort`'d
cleanly, and re-run against the exact intended filename rather than a
glob. Dynamic re-validation of these two new patches together with the
rest of this batch is the immediate next step as of this entry.

### Decoy storage-volume-permanently-unmounted crash (`0038`, `0039`, `0040`)

Found during the physical `lynx` hardware re-validation of `0034`-`0037`:
entering the registered decoy's PIN directly on a freshly-booted device
(the owner's own real credential never entered this boot) crashed
Settings ▸ Storage with `IllegalArgumentException: Volume
external_primary not found`, and `adb shell sm list-volumes` showed the
decoy's own emulated storage volume as permanently `unmountable` despite
the decoy itself being `RUNNING_UNLOCKED`. A control test (a freshly
created, entirely ordinary `USER_TYPE_FULL_SECONDARY` user, no decoy
registration) reproduced the identical `unmountable` state while the
owner's session was locked, and the volume mounted correctly the moment
the owner unlocked for the first time that boot — confirming this is not
decoy-specific and not caused by any of `0033`-`0037`. Full incident
record with the complete evidence trail (logcat excerpts, `git blame`
citations, three consecutive fix attempts each found to be real but
individually insufficient):
`docs/devices/lynx-duress-decoy-storage-unmount-incident.md`.

This resolved to three stacked instances of the same underlying pattern
— a system service silently assuming user 0 always unlocks before any
other user's own lifecycle events matter, true on stock Android (you
must unlock the primary user to even reach the multi-user switcher) but
false by design under Duress Unlock, whose whole point is that the real
profile stays genuinely locked through a decoy switch (ADR-073). This is
the third time this exact bug *class* has been found in this project
(after ADR-073's `UserBackupManagerService` bug), and the first time it
took three consecutive, independently-real fixes stacked in the same
call chain before the symptom actually resolved.

- `94c6a5e888ed2ede04466c972edba36cf9bb3f5e` — "aegisos: check the actual
  target user, not user 0, for volume-report gating"
  (`patches/frameworks-base/0038-aegisos-check-the-actual-target-user-not-user-0-for-.patch`).
  `StorageManagerService.getVolumeList(int userId, ...)` hardcoded
  `isSystemUnlocked(UserHandle.USER_SYSTEM)` despite already having the
  correct target `userId` in scope (used a few lines later for the
  sibling check `isCeStorageUnlocked(userId)`); `git blame` confirmed
  the hardcoded line dates to an unrelated 2019 refactor
  (`157dd1dc40c5a`, "Move legacy obb data migration to a handler
  thread"), not a deliberate, still-valid design decision. Necessary —
  a real, standalone misreport for any per-user query of this method —
  but not sufficient to fix the crash on its own; re-testing after this
  alone still reproduced the identical crash.
- `ec3b888a36a5ecec81feb31b19ed13c161abeb0a` — "aegisos: initialize
  ExternalStorageService component on any user's unlock"
  (`patches/frameworks-base/0039-aegisos-initialize-ExternalStorageService-component-.patch`).
  The actual proximate cause, found via `adb logcat`, not further source
  guessing: `StorageSessionController#onUnlockUser(int userId)` only
  called `initExternalStorageServiceComponent()` — a one-time, global
  initialization resolving MediaProvider's `ExternalStorageService`
  component, identical for every user — when `userId == 0`. If user 0
  never unlocks, the component name stays permanently null and *no*
  user's storage session, including the decoy's own, can ever bind to
  it (`StorageUserConnection.connectIfNeeded()` throws "Not ready to
  bind"). Fixed by guarding on `mExternalStorageServiceComponent == null`
  instead of which user triggered the check — same one-time-init outcome
  for every existing Android flow, correct for the decoy-first case too.
  `mExternalStorageServiceComponent` is `volatile`; a benign race under
  concurrent unlocks is possible but harmless (same value resolved
  either way). Necessary, and a real improvement (`adb logcat` confirmed
  `onUnlockUser(10)` now genuinely attempted initialization for the
  first time) — but still not sufficient on its own; the attempt itself
  then failed one layer deeper.
- `4067c29a100d64da97a2f1ab6d3f81f458c6cdae` — "aegisos: resolve
  ExternalStorageService as the actually-unlocking user"
  (`patches/frameworks-base/0040-aegisos-resolve-ExternalStorageService-as-the-actual.patch`).
  `initExternalStorageServiceComponent()` itself still hardcoded
  `resolveExternalStorageServiceAsUser(UserHandle.USER_SYSTEM)`
  internally — a third, independent instance of the identical pattern,
  one call deeper than `0039` reached. Confirmed directly on hardware:
  the identical lookup failed with "No valid ExternalStorageService
  component found" when resolved as user 0 (never unlocked) and
  succeeded when resolved as the already-unlocked decoy instead. Fixed
  by threading the actually-unlocking `userId` through
  `initExternalStorageServiceComponent(int userId)` (previously took no
  parameter) in place of the hardcoded constant. Only one call site
  exists (`onUnlockUser`, already updated by `0039`), so this is a
  clean, mechanical signature change with no other callers to update.
- Base commit: `f1ef01a416c4f07c0b6296341a07b9e25f6f80fa` (`0037`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

All three authored under `AGENTS.md`'s local-first discipline in the
same disposable worktree already used for `0034`-`0037`
(`aegisos-platform/.worktrees/duress-switcher-visibility-regression-fix/`),
landed on the real branch with `git am` (each applied cleanly, no
conflicts, confirmed no build in flight beforehand each time),
regenerated fresh via `git format-patch` from the real post-`git am`
branch history as a combined 3-patch series and independently verified
byte-for-byte reproducible against both touched files
(`StorageManagerService.java`, `StorageSessionController.java`) in one
combined apply-and-diff check.

**Build verification:** each of the three was individually built
(`m -j6 services`, `lynx-cur-userdebug`, `OFFICIAL_BUILD=true`
reconfirmed each time) and repackaged into a fresh `system.img`
(`m -j6 systemimage`), with `system.img`'s own resulting timestamp
checked against the freshly-built `services.jar` each time to rule out
a repeat of ADR-078's `out-lynx` staleness incident. `system_ext.img`
and the other 10 partitions were confirmed byte-identical to the
`0037`-era candidate across all three rounds and reused as-is; only
`system.img` and `vbmeta.img` changed. Each candidate was signed with
the alpha key (`SHA256_RSA4096`, rollback index `1783209600`,
`--padding_size 65536`, matching every prior duress-unlock candidate),
13/13 independently verified on two machines (build-vm, this
workstation) each round, and flashed by the owner. `0038` alone did not
resolve the crash (confirmed via a fresh, matching `dumpsys dropbox`
entry with the identical stack trace after flashing it, and an
independent `services.jar` checksum match ruling out a stale-build
explanation). `0038`+`0039` improved the observed behavior
(`initExternalStorageServiceComponent()` genuinely attempted for the
decoy for the first time, confirmed via logcat) but still failed one
layer deeper. `0038`+`0039`+`0040` together is the confirmed-working
candidate: hardware re-validation of the exact original trigger (fresh
boot, decoy PIN only, Settings ▸ Storage, owner's credential never
entered) completed 2026-09-07 and passed against direct device
evidence — `adb shell dumpsys user` showing user 0 genuinely
`RUNNING_LOCKED` and the decoy `RUNNING_UNLOCKED`, `adb shell sm
list-volumes` showing `emulated;10` `mounted`, `adb logcat` showing a
clean `UNMOUNTED → CHECKING → MOUNTED` transition with zero
`ExternalStorageServiceException` occurrences, the Settings process
still alive, and no new `dumpsys dropbox` entry. Full record:
`docs/devices/lynx-duress-decoy-storage-unmount-incident.md`
(status: resolved), `docs/governance/decision-log.md` ADR-084.

### Pre-unlock concealment: `isUserSwitcherEnabled` (`0041`, `0042`, `0043`)

Task 4 of
`docs/superpowers/plans/2026-09-07-duress-decoy-seamless-unlock.md`
(Component A). `UserManagerService#isUserSwitcherEnabled(int)` already
returns `false` while the *current* user is a decoy (`0033`), but a
locked device showing the real owner's own lock screen still advertised
a second profile whenever a decoy existed anywhere on the device — the
coercer holding the locked phone is an earlier and more common point in
the duress scenario than one already inside a decoy session, so a
lock-screen user-switcher leak defeats the mechanism before it is ever
invoked. Fixed by extending the same method with a second, narrower
early return gated on both a decoy actually existing on the device
*and* the keyguard currently showing, using `isKeyguardShowing()` per
Task 1's own predicate-semantics finding
(`docs/devices/lynx-duress-decoy-prelock-audit.md`) — `isKeyguardLocked()`
does not distinguish a genuinely dismissed keyguard from one merely
occluded, per that same finding.

- `582a7ec39723dadde855098014c62a787d4dc20f` — "test: add coverage for
  pre-unlock decoy concealment in isUserSwitcherEnabled"
  (`patches/frameworks-base/0041-test-add-coverage-for-pre-unlock-decoy-concealment-i.patch`).
  Three `UserManagerServiceTest` cases: locked device with a decoy
  returns `false`; unlocked device with a decoy returns the real value;
  locked device with no decoy returns the real value (the regression
  guard pinning "no decoy present" as bit-for-bit stock). Added
  `addDecoyUser()`/`setKeyguardLocked(boolean)` test helpers matching
  the file's own existing conventions: `putUserInfo()`/`removeUserInfo()`
  for direct user-list manipulation (already used by the file's `setup()`
  for the same reason — this instrumented test cannot reach real
  `userlist.xml`), and `LocalServices.addService(WindowManagerInternal.class,
  mock(...))` for local-service substitution, an existing pattern this
  module already uses in `BaseLockSettingsServiceTests.java` and
  confirmed available via the module's own `Android.bp` mockito
  dependency even though this specific file had not used Mockito
  before.
- `9024eb84d36931ee717fef2290fc52dea1352fae` — "test: force user
  switcher setting on for pre-unlock concealment tests"
  (`patches/frameworks-base/0042-test-force-user-switcher-setting-on-for-pre-unlock-c.patch`).
  A real, environment-specific finding caught by actually running the
  first commit's tests on hardware rather than trusting the plan's own
  code sample: on this build's real emulator, `Settings.Global
  .USER_SWITCHER_ENABLED` is unset and `config_showUserSwitcherByDefault`
  resolves to `false`, so `isUserSwitcherEnabled()` already returned
  `false` regardless of decoy or lock state — a test suite that passes
  identically whether or not the concealment code exists proves nothing.
  Fixed by having each of the three tests force the setting to `1`
  (captured and restored per-test, not global to the file, so no other
  test's behavior changes) before exercising the method, establishing a
  known "true" baseline so the decoy+lock gate is the only thing able to
  flip the result to `false`. Confirmed this was necessary, not
  cosmetic: without it, `atest` on real hardware showed the *first* test
  passing vacuously and both "real value" tests failing with "expected
  to be true" against the stock code, before `0043` below ever landed.
- `362bc84237fb25e0a392cf552d2e9dc531fd3797` — "aegisos: conceal
  multi-user existence on pre-unlock surfaces"
  (`patches/frameworks-base/0043-aegisos-conceal-multi-user-existence-on-pre-unlock-s.patch`).
  The production change: two new private helpers, `hasDecoyUser()`
  (scans `mUsers` under `mUsersLock`, skipping `mRemovingUserIds`,
  matching `UserManager.USER_TYPE_FULL_AEGISOS_DECOY`) and
  `isDeviceCurrentlyLocked()` (reads `WindowManagerInternal
  .isKeyguardShowing()` via `LocalServices`, `false` if the service
  isn't published yet — too early in boot for a keyguard to matter),
  and one new early return in `isUserSwitcherEnabled()` gated on both.
  Deliberately queried outside `mUsersLock`, matching Task 1's own
  lock-ordering finding that no genuine inversion risk exists here but
  keeping the check outside the lock regardless. Display-only: neither
  user creation nor user switching consult this method, so this adds no
  new capability and removes none.
- Base commit: `4067c29a100d64da97a2f1ab6d3f81f458c6cdae` (`0040`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

Authored under `AGENTS.md`'s local-first discipline in a fresh
disposable worktree
(`aegisos-platform/.worktrees/prelock-concealment/`), landed on the
real branch with `git am` in RED-then-GREEN order (each applied
cleanly, no conflicts, confirmed no build in flight beforehand each
time), then regenerated fresh via `git format-patch` directly from the
real post-`git am` branch history as a combined 3-patch series and
independently verified byte-for-byte reproducible against
`UserManagerServiceTest.java`/`UserManagerService.java` in one combined
apply-and-diff check before export.

**Identity correction (2026-09-07):** the first export of this series
carried the authoring session's own personal git identity
(`beau4201 <beau.ten.berge@gmail.com>`) on the actual landed commit
objects, not merely on the exported patch text — a genuine mismatch
against every other `frameworks-base` patch in this directory, all of
which use `AegisOS Build <build@aegisos.local>`. A prior version of
this note claimed the fix was "a plain text edit of the patch file, not
a rewrite of the already-landed branch commits" — that claim was false;
no such edit had actually been made, so the `From:` header and the real
commit's author had silently diverged, a factual mismatch against the
SHA each patch cited. This was corrected as a real, narrow history
rewrite, explicitly owner-authorized for exactly these 3 tip commits on
`aegisos-duress-decoy-switch`: each commit was reconstructed via
`git cherry-pick` onto the unchanged base
(`4067c29a100d64da97a2f1ab6d3f81f458c6cdae`) followed by
`git commit --amend --author="AegisOS Build <build@aegisos.local>"`
(the `--author` flag is required — `GIT_AUTHOR_NAME`/`GIT_AUTHOR_EMAIL`
environment variables are silently ignored by `git commit --amend`,
which preserves the original author unless `--author` or
`--reset-author` is passed explicitly; this was discovered the hard way
when a first attempt using only env vars reproduced the original,
uncorrected commits byte-for-byte, same SHAs included), with the
original author date preserved via `--date` and the committer identity
and date preserved via `GIT_COMMITTER_NAME`/`GIT_COMMITTER_EMAIL`/
`GIT_COMMITTER_DATE` (matching every adjacent commit's own committer
field, which reflects the build server's own configured local git
identity rather than an explicit `AegisOS Build` git config — none
exists on this server at any level; this is expected, pre-existing
project convention confirmed present in commits predating this task,
not a defect). A backup ref
(`backup/task4-pre-identity-fix`) was left in place at the original,
uncorrected tip before the branch pointer was moved, and the resulting
tree was diffed empty against the original tip before and after the
rewrite, confirming zero content change — only the 3 commits' author
identity moved. The SHAs cited above and the patch files in this
directory reflect the corrected commits, regenerated fresh via
`git format-patch` from the real corrected branch history per this
project's own ADR-058 precedent (never trust a hand-edited or
previously-exported patch file), and independently re-verified
byte-for-byte reproducible in a disposable worktree before being
committed here.

**Build/test verification:** real, on the build server, via `atest`
against the `sdk_phone64_x86_64-cp2a-userdebug` emulator target — not
merely compiled. Two real environmental blockers were found and fixed
before `atest` could run at all: the server's `/tmp` partition (a
separate, small ~241M filesystem) was completely full, which first
surfaced as `atest`'s own module-info step failing with `sqlite3
.OperationalError: database or disk is full`, and, once past that,
as every `javac`/`jarjar` step failing with `Exception in thread "main"
java.lang.NullPointerException: Deflater has been closed` — traced to
the JVM's `hsperfdata` shared-memory files, which are hardcoded to
`/tmp/hsperfdata_$USER/` regardless of `TMPDIR`/`-Djava.io.tmpdir`.
Fixed by relocating (not deleting) three large stale files left over
from earlier, unrelated sessions
(`/tmp/tmplb3pokio`, `/tmp/dex-check`, `/tmp/svc_check`, totaling
~200MB) to `/srv/aegisos/tmp-primary/archived-tmp-debris/`. Separately,
bare class-name resolution (`atest UserManagerServiceTest:<method>`, as
the plan's own code sample shows) never found the test on this host at
all — the `CACHE` finder depends on `locate`/`plocate`, confirmed
absent as a system command, and `MODULE_CLASS` resolution came up empty
for reasons not further root-caused; the fully-qualified
`atest FrameworksServicesTests:com.android.server.pm
.UserManagerServiceTest#<method>` form bypasses that broken resolution
path entirely and is what actually built and ran. RED (`0041` alone):
1 pass (the vacuous case), 2 fail with "expected to be true". RED again
after `0042`: 1 genuine, meaningful failure ("expected to be false"
against stock code with a forced-true baseline), 2 pass. GREEN (`0041`
+ `0042` + `0043`): all 3 pass. No physical `lynx` hardware build for
this task — Task 13 of the same plan is the designated dynamic
validation round for this component, per the plan's own sequencing.

### `frameworks/base`: rename decoy-identifying log tags/messages (`0044`)

Task 5 of `docs/superpowers/plans/2026-09-07-duress-decoy-seamless-unlock.md`,
applying Task 3's own log-audit decisions
(`docs/devices/lynx-duress-decoy-prelock-audit.md`, "Log and event-log
traces" section). Task 3 found none of these traces reachable by an
attacker confined to the decoy session itself (`READ_LOGS`/`DUMP` are
both `signature|privileged|development`-gated, and a non-admin decoy
cannot reach Developer Options at all) — the rename is driven by the
project's own unconditional hard constraint ("no new log string on the
decoy path may contain 'decoy', 'duress', or 'Aegis'"), not by closing a
reachable leak. Pure string-literal changes: no log level, call site, or
trigger condition changed.

- `e15abc1a26ffa79563ec6665c20fda337d6d94f6` — "aegisos: rename
  decoy-identifying log tags and messages to neutral text"
  (`patches/frameworks-base/0044-aegisos-rename-decoy-identifying-log-tags-and-messag.patch`).
  Four files, tag and message changes only:
  - `DecoyProfileHelper.java`: `TAG` `"DecoyProfileHelper"` →
    `"CredentialRoutingHelper"`; the `Slog.e` at (former) line 74 reworded
    from "Decoy credential matched but freeze/switch could not be
    initiated..." to "Target-user credential matched but freeze/switch
    could not be initiated..."; the `Slog.e` at line 87 reworded from
    "...silent switch to decoy user " to "...silent switch to target
    user "; the `Slog.w` at line 188 reworded from "...for decoy user " +
    id + " -- the decoy profile is registered..." to "...for target user
    " + id + " -- the profile is registered...". The `Slog.e` at line 97
    ("Failed to install notification suppression for real user...") was
    already free of "decoy"/"duress"/"Aegis" in its own text — left
    verbatim, it only benefits from the tag rename above it.
  - `DecoyProfileState.java`: `TAG` `"DecoyProfileState"` →
    `"CredentialRoutingState"`; both `Slog.e` messages ("Unrecognized
    decoy profile state version..." / "Corrupt decoy profile state...")
    reworded to drop "decoy profile", becoming "Unrecognized routing
    state version..." / "Corrupt routing state...".
  - `ReturnToRealUserHelper.java`: `TAG` `"ReturnToRealUserHelper"` →
    `"SessionReturnHelper"`. Per the audit's own "RENAME, with a caveat"
    classification, neither this tag nor its one message literally
    contained a banned word — renamed anyway for defense-in-depth, since
    the class name (used verbatim as the tag) is otherwise
    mechanism-identifying by correlation with this project's own public
    design docs. The message itself ("Real owner credential matched but
    switch back could not be initiated...") was already neutral and is
    unchanged.
  - `RealUserQuiescer.java`: `TAG` `"RealUserQuiescer"` →
    `"SessionQuiescer"`. Same "RENAME, with the same caveat" basis as
    above; both messages ("Failed to freeze/unfreeze process...") were
    already neutral and are unchanged.
  Class names, javadoc comments, field/method/parameter identifiers
  (`decoyUserId`, `maybeRouteToDecoy`, etc.), and the
  `LOCK_SETTINGS_STORAGE_KEY` string constant (`"decoy_profile_state"`,
  a storage key, not a log call) are all source-only or non-log-call
  text and were deliberately left untouched — out of scope per Task 3's
  own scope note (RENAME/DROP/NO-FIX applies to
  `Slog`/`Log`/`EventLog`/`Trace` calls only). The `EventLog.writeEvent`
  call and `switchToUserSilently`'s five remaining generic `Slogf`
  lines (Task 3's two `NO-FIX` groups) and the
  `org.aegisos.usertype.full.DECOY` string exposed via `dumpsys` (the
  accepted residual) were left completely untouched, per Task 3's own
  decision table.
  Authored under `AGENTS.md`'s local-first discipline in a disposable
  local git repo (`aegisos-platform/.worktrees/duress-logrename-task5/`,
  a fetch-only worktree seeded read-only from the real branch HEAD
  `362bc84237fb25e0a392cf552d2e9dc531fd3797` — only the four touched
  files, each confirmed byte-identical via `sha256sum` against the live
  remote before editing) and landed on the real branch with `git am`
  (applied cleanly, no conflicts, confirmed no build was in flight
  beforehand). Regenerated fresh via `git format-patch` directly from
  the real post-`git am` branch history and independently verified
  byte-for-byte reproducible: applied to a disposable local clone seeded
  at the real base commit's own file content, diffed against the
  worktree's own final state — empty diff.
- Base commit: `362bc84237fb25e0a392cf552d2e9dc531fd3797` (`0043`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

**No reachable unit-test seam.** This is a pure string-literal change
with no behavioral difference (same log level, same call site, same
trigger condition). None of the four classes has any pre-existing test
scaffolding, and asserting exact `Slog` tag/message text would require
new log-interception infrastructure this codebase does not have —
writing one merely to pin string literals would not exercise any real
logic. Relying on Task 13's dynamic validation (grepping the flashed
candidate's `logcat`/`dumpsys` output for the old vs. new strings)
instead, per the brief's own allowance for fixes with no practical unit
seam.

**Build verification:** part of the same consolidated `m -j6
systemimage` build described below (Task 5's build step covers both of
this task's patches together).

### `packages/apps/Multiuser`: gate the widget's user grid on `isUserSwitcherEnabled` (`0004`)

Task 5 of the same plan, fixing Task 2's audit Finding 13
(`docs/devices/lynx-duress-decoy-prelock-audit.md`, "Pre-unlock UI
surfaces" section) — the one `BYPASSES CHOKEPOINT` surface that audit
found. `UsersRepository.getUsers()` already routes through
`getUsersForUiDisplay(true)` (`0002`), which only collapses the list
when the device's actual *current* user is the decoy — pre-unlock, or
whenever the device was unlocked earlier in the same boot and has since
merely re-locked, the current user is still the real owner, so this
data layer loaded every real user's name and avatar into memory
regardless of `isUserSwitcherEnabled`, unlike its own sibling
`checkUserSwitchRestrictions()` (already correctly gated) three lines
away.

- `e60b54249acea2ec5d2f181b866a2ba86f7bef47` — "aegisos: hide
  non-current users from the widget's grid when switcher disabled"
  (`patches/packages-apps-multiuser/0004-aegisos-hide-non-current-users-from-the-widget-s-gri.patch`)
  — a four-line change to `UsersRepository.kt`'s `getUsers()`: reads
  `userManager.isUserSwitcherEnabled(true)` once, then `.filter`s the
  `getUsersForUiDisplay(true)` result down to `switcherEnabled ||
  userInfo.id == currentUserId` before the existing `.map { ... }` — the
  same "`isUserSwitcherEnabled` OR `isCurrent`" shape as
  `BaseUserSwitcherAdapter.kt:40`'s already-correct pattern in
  `frameworks/base`, per the audit's own proposed fix and ADR-080's
  "route the caller through the already-correct gated pattern" model.
  No other call site needed touching: `checkUserSwitchRestrictions()`
  already gates correctly, and this is the only other place
  `getUsers()`'s own result is read.
  Authored under `AGENTS.md`'s local-first discipline in a disposable
  local git repo
  (`aegisos-platform/.worktrees/duress-multiuser-switcher-gate-task5/`,
  a fetch-only worktree seeded read-only from the real branch HEAD
  `c701d199f831ef5ee05c60a4735dad99b900b875` — only the one touched
  file, confirmed byte-identical via `sha256sum` against the live
  remote before editing) and landed on the real branch with `git am`
  (applied cleanly, no conflicts, confirmed no build was in flight
  beforehand). Regenerated fresh via `git format-patch` directly from
  the real post-`git am` branch history and independently verified
  byte-for-byte reproducible the same way as the `frameworks/base` patch
  above — empty diff.
- Base commit: `c701d199f831ef5ee05c60a4735dad99b900b875` (`0003`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

**No reachable unit-test seam.** `UsersRepository` takes a real
`UserManager?` directly, and this module's own existing test fixture,
`FakeUserRepository`, states its purpose in its own doc comment: "so we
don't have to mock `UserManager`" — confirmed no Mockito dependency
exists in `MultiuserWidgetUnitTests`' `Android.bp` `static_libs` either.
Exercising this fix directly would require either mocking
`UserManager` (a real, isolated departure from this module's own
established testing convention) or an instrumented test that actually
creates a second user on-device — neither is a "reachable" unit seam in
the sense Task 4's tests were. Relying on Task 13's dynamic validation
instead, per the brief's own allowance.

**Build verification:** part of the same consolidated `m -j6
systemimage` build described below.

### Task 5 consolidated build (`0044`, Multiuser `0004`)

A full `m -j6 systemimage` build
(`sdk_phone64_x86_64-cp2a-userdebug`, `OUT_DIR=out-preflight`), launched
fully detached on the remote side (`nohup ... & disown`) and polled to
completion with a bounded synchronous loop, confirmed no build was in
flight beforehand (`pgrep -f "ninja|soong_ui"` clean) both before
landing either patch and before launching the build itself:
`ninja: 4m30.19s Build Succeeded: 146 steps - 0.54/s`, `#### build
completed successfully (04:37 (mm:ss)) ####`. Only 146 steps because
the vast majority of the dependency graph (all of `frameworks/base`'s
other 43 patches, the rest of the platform) was already built and
cached from the immediately-preceding Task 4 build against the same
`out-preflight` tree; the touched modules
(`services.core`/`services`/`MultiuserWidgetLib`/`Multiuser`) all
recompiled cleanly with no new diagnostics beyond the same
pre-existing, unrelated `PackageManagerLocal.java`
`@FlaggedApi`/`UnflaggedApi` info notices already seen in prior builds
in this series. This confirms both of this task's patches compile
together with the full accumulated patch set (all 44 `frameworks-base`
patches plus all 4 `packages-apps-multiuser` patches); it does not by
itself validate runtime behavior (e.g. that the widget's grid genuinely
shrinks to one tile, or that the renamed log strings actually appear in
a real `logcat`) — no task in this plan has yet scheduled a dynamic
exercise of either fix. Task 13 is the designated dynamic-validation
round for this whole plan.

### `frameworks/base`: decoy switch keyguard-skip authorization, Tasks 6-7 (`0045`)

Tasks 6-7 of `docs/superpowers/plans/2026-09-07-duress-decoy-seamless-unlock.md`,
Component B's one-shot, decoy-scoped switch authorization store and its
consumption point — the same architectural bet ADR-061's regression case
already forced: the existing AOSP `skipKeyguardWhenSwitchingToUnlockedUsers`
condition at `UserController.java` stays completely untouched and stays
inert (gated on a permanently-disabled aconfig flag per ADR-061's revert);
Component B introduces its own separate, narrowly-scoped authorization
rather than reusing or widening that condition. Landed together as one
combined patch file (six commits, one `git format-patch --stdout` mailbox)
because Task 6 alone wires nothing observable — it only becomes a real,
verifiable capability once Task 7's consumption point and bridge exist —
matching this project's own precedent of batching tightly-coupled work
into one artifact rather than one file per commit.

- `6fc1a61a82e3` — "test: add coverage for the decoy switch authorization
  store" (Task 6 RED).
- `5461467e5003` — "aegisos: implement the decoy switch authorization
  store" (Task 6 GREEN, by trace only at landing time — see below). New
  `UserController` fields (`mSwitchAuthorizedUserId`/
  `mSwitchAuthorizedAtMillis`, `@GuardedBy("mLock")`) and two new methods:
  `authorizeDecoySwitchKeyguardSkip(int targetUserId)` (package-private) —
  resolves the target's real type via
  `mInjector.getUserManagerInternal().getUserInfo(targetUserId)` (the
  device's own authoritative user registry, never a caller-supplied
  value) and mints only if it is exactly `USER_TYPE_FULL_AEGISOS_DECOY`,
  entering the `synchronized (mLock)` block at all only on success — a
  caller cannot "lie" about the target's type because the type is never
  an input, closing the same class of gap ADR-064 found elsewhere in this
  project (a wrong-typed target silently receiving protections meant only
  for the real type), here structurally rather than policy-enforced,
  since the check runs on every call, not once at registration time.
  `consumeDecoySwitchAuthorization(int userId)` (package-private,
  `@VisibleForTesting`) — single scalar slot, exact integer equality, no
  wildcard/"any pending" fallback; always clears on a matching id whether
  or not the 30-second window (`DECOY_SWITCH_AUTHORIZATION_TIMEOUT_MS`)
  has elapsed, so an expired authorization can never be reused via a
  second call. A new `Injector.elapsedRealtime()` seam was added
  (defaulting to `SystemClock.elapsedRealtime()`) since none existed on
  this file before, following the file's own existing
  `getLastUserUnlockingUptime`-style pattern.
- `f8e6d4329aba` — "test: add coverage for consuming the decoy switch
  authorization" (Task 7 RED). Adds
  `testForegroundSwitch_authorizedDecoy_doesNotForceKeyguard` and
  `testForegroundSwitch_unauthorizedSecureUser_forcesKeyguard` — the
  second is the ADR-061 regression case expressed as a unit test and
  must never be weakened or removed in any future change to this file.
  `setUserSecure`/`startForegroundUser` helpers implemented against this
  file's existing `mUserManagerMock`/`setUpUser`/`waitForHandlerToComplete`
  seams, per the brief's own instruction to use existing test
  infrastructure rather than the literal sample's assumptions.
- `6e2adc2a3406` — "test: register ActivityManagerInternal mock for
  foreground-switch tests" (real-environment fix, found only once these
  tests actually ran via `atest` for the first time — see "Real `atest`
  findings" below). Registers a `LocalServices`-backed
  `ActivityManagerInternal` mock inside `startForegroundUser()`
  (`LocalServices.removeServiceForTest` then `addService`, matching the
  existing `WindowManagerInternal` pattern in
  `UserManagerServiceTest.java`'s `setKeyguardLocked()`), torn down
  unconditionally in `tearDown()` so it never leaks into another test.
  Test-only; no production code in this commit.
- `e380eced01fa` — "aegisos: skip the keyguard for an authorized switch"
  (Task 7 GREEN). Replaces the inner block at
  `UserController.java:2335-2345`: a new `if (consumeDecoySwitchAuthorization(userId))`
  branch is added *ahead* of the existing AOSP three-part condition,
  which is otherwise byte-identical to before this patch — confirmed by
  diff, not assumed. Log line ("Skipping keyguard for an authorized
  switch") deliberately worded without naming the mechanism, since it is
  reachable from a decoy session and this project's own hard constraint
  forbids "decoy"/"duress"/"Aegis" in any log string reachable from that
  path.
- `bbe0134976a4` — "aegisos: expose the decoy switch authorization mint
  via ActivityManagerInternal" (Task 7's bridge). New
  `public abstract boolean authorizeDecoySwitchKeyguardSkip(int targetUserId)`
  on `ActivityManagerInternal` (next to the existing `switchToUserSilently`
  from `0003`) and a `LocalService` override in `ActivityManagerService`
  that is pure delegation to `mUserController.authorizeDecoySwitchKeyguardSkip(targetUserId)`
  — no logic, no additional checks, nothing beyond the one-line
  pass-through the brief specified. This exists because Task 6's method
  is package-private in `com.android.server.am`, while its real caller
  (`DecoyProfileHelper`, wired in a future task) lives in
  `com.android.server.locksettings` — a different package. The safety
  property this bridge must not weaken is enforced entirely on the
  `UserController` side it delegates to (the type check described above);
  the bridge itself adds no new way to mint an authorization for a target
  the caller doesn't control.
- Base commit: `e15abc1a26ffa79563ec6665c20fda337d6d94f6` (`0044`'s own
  real post-`git am` SHA, the real branch tip at fetch time).

**Real `atest` findings.** This is the first genuine `atest` execution
either task's own code has ever had — Task 6's own report explicitly
flagged that its 5 tests were verified only by hand-trace, not execution,
because the brief prohibited `git am`/`format-patch` in that task. Real
RED confirmed both new tests failing for the *expected* reason first
(`lockDeviceNowAndWaitForKeyguardShown()` still called with an
authorization present), but only after a first RED attempt surfaced an
unrelated real bug: `startUserInternal`'s pre-existing (patch `0011`,
long predating this task)
`LocalServices.getService(ActivityManagerInternal.class).unquiesceUserApps(userId)`
call NPEs for *any* test that reaches a genuine foreground user switch,
since nothing in `UserControllerTest` had ever registered that service —
fixed by `6e2adc2a3406` above, scoped to only the new
`startForegroundUser()` helper. Real GREEN then confirmed both new tests
passing. A full-class run (97 tests) surfaced that this same pre-existing
NPE affects roughly 30 tests scattered through the file, unrelated to
this task's own two-commit change (which sits entirely downstream of the
crash point in the call chain) — confirmed genuinely pre-existing, not
introduced here, by checking out the real branch at `0044`'s own tip
(before either task's commits existed) and reproducing the identical
stack trace on three sampled tests, including two original, non-decoy
AOSP tests (`testShowKeyguardOnUserStart_showsKeyguard`,
`testStallUserSwitchUntilTheKeyguardIsShown`). One of the affected tests
(`testStallUserSwitchUntilTheKeyguardIsShown`) spawns a raw background
`Thread` to call `startUser()`; its uncaught NPE crashes the whole
instrumentation process, which is what caused the first full-class
attempt to silently stop after 41 of 97 tests ("Test run started but did
not end"). Re-running the other 96 tests (excluding only that one
process-crashing test) completed cleanly: 62 passed, 28 failed (all with
the identical pre-existing NPE signature), 6 assumption-failed (also
pre-existing, unrelated categories) — none of the 7
decoy/authorization-related tests (5 from Task 6, 2 from Task 7) are
among the failures. This pre-existing gap is out of this task's scope to
fix (Task 7's own mandate was the consumption point and a pure-delegation
bridge, nothing more) and is flagged as a real, separate follow-up item:
roughly 30% of `UserControllerTest`'s own regression coverage is
currently unusable via `atest` on this build tree because of a defect
`0011` introduced, unrelated to Duress Unlock's own correctness.

**Reproducibility.** Regenerated fresh via `git format-patch --stdout`
directly from the real post-`git am` branch history (six commits
concatenated into the one `0045` file, not trusted from any local
worktree's own patch generation) and independently verified byte-for-byte:
applied via `git am` to a disposable local clone seeded at the real base
commit's own file content (four files: `UserController.java`,
`UserControllerTest.java`, `ActivityManagerInternal.java`,
`ActivityManagerService.java`), diffed against both the authoring
worktree's final state and the live server tree directly via `sha256sum`
— empty diff / matching hash on all four files, both ways.

## Apply (this section)

```sh
cd packages/apps/Multiuser && git checkout -b aegisos-duress-decoy-switch && \
  git am ../../credential/duress-unlock-decoy/patches/packages-apps-multiuser/*.patch
```
