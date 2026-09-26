# Source record

## Correction 2026-09-26: `system-current.txt` sort order after the rename

`frameworks-base/0001` added `USER_TYPE_FULL_SOVEREIGNOS_DECOY` to
`core/api/system-current.txt` in the position the old `..._AEGISOS_DECOY`
name sorted to (before `..._GUEST`). The API file is checked in sorted order,
so `check_current_api` failed at 99% of the first `2026091900` `lynx` build.
The hunk now places the line after `..._SECONDARY`, before `..._SYSTEM`; nothing
else in the patch changed. Verified with a fresh `git am` of the whole
`frameworks-base` series (10 patches, after `branding/boot` and
`duress-unlock-dummy`): old base `2026071500` tree `c243494ada68`, new base
`2026091900` tree `e0b2c4bf0727`; on both, only `system-current.txt` differs
from the previous series. The tree hashes in the squash section below predate
this correction.

## Patch series squashed (2026-09-25) — read this before using any old patch number

The decoy package's patches were consolidated from 107 fine-grained patches
into 13: `frameworks-base` 93 → 10, `packages-apps-settings` 10 → 2,
`packages-apps-multiuser` 4 → 1. `build-release` (already one squashed
patch) is unchanged. Every "patch `NNNN`" reference elsewhere in this file,
in `README.md`, and in the decision log (ADR-053 through ADR-096) uses the
**pre-squash** numbering; the table below maps each old number to its new
patch. Each new patch's commit message also lists the old subjects it
absorbed.

Scope: package files only. The real build-server branch
`sovereignos-duress-decoy-switch` was **not** rewritten and still carries
the fine-grained history (and its original SHAs) in `frameworks/base`,
`packages/apps/Settings` and `packages/apps/Multiuser`. The two forms are
tree-equivalent, not SHA-equivalent.

Equivalence evidence (all three re-run from a clean `git am` of the
exported files, not taken from the squash step):

- Seeded a disposable local repo from the real base at the build server
  (read-only `git archive`), applied the old series, and compared every
  touched file against the branch tip on the build server: 48/48
  `frameworks/base`, 14/14 Settings, 2/2 Multiuser identical apart from the
  `AegisOS`→`SovereignOS` rename applied to this package by ADR-093.
- Squashed by cherry-pick per group; final tree identical to the unsquashed
  tree before export. One group reorders `0028` behind `0029`-`0035` (new
  `frameworks-base/0005`); the tree comparison covers that.
- Fresh `git am` of the exported patches onto the same base reproduces
  these tree hashes:
  - `frameworks/base` (after the two `duress-unlock-dummy` patches):
    `2f1399b82be7513fbc852746732bd16f0b200887`
  - `packages/apps/Settings`: `3bb5dc8f7e35a78ef7e4e6d6adedbf3d4752b58b`
  - `packages/apps/Multiuser`: `c86730033a917895b001a31d2c2d97c96cf251bb`
- Bases: `frameworks/base` `7d7001f9c4e4` (GrapheneOS `2026071500` plus the
  boot-identity commit) plus `credential/duress-unlock-dummy`;
  `packages/apps/Settings` the parent of the ten former patches on the
  build-server branch (tag `2026072900` lineage); `packages/apps/Multiuser` `44f89da`.
- Not rebuilt: this is a source-tree-identity result, no `m` build or
  device run was done for the squashed form.

### Old → new patch map

| New patch | Former patches | Subject |
| --- | --- | --- |
| `frameworks-base/0001` | 0001-0007 | sovereignos: add the duress decoy user type, routing and keyguard hook |
| `frameworks-base/0002` | 0008-0016 | sovereignos: quiesce the real user during a decoy session and add the return path |
| `frameworks-base/0003` | 0017-0027 | sovereignos: hide real-user identity from the decoy session's UI surfaces |
| `frameworks-base/0004` | 0029-0035 | sovereignos: make the decoy session's UI match a single-owner device |
| `frameworks-base/0005` | 0028, 0036-0038 | sovereignos: use the actual target user for CE-storage checks in backup and storage |
| `frameworks-base/0006` | 0039-0042 | sovereignos: conceal multi-user existence on pre-unlock surfaces |
| `frameworks-base/0007` | 0043-0057 | sovereignos: authorize a single-credential decoy switch and warm-start the target |
| `frameworks-base/0008` | 0058-0070 | sovereignos: skip the re-armed bouncer and active-dismiss on an authorized switch |
| `frameworks-base/0009` | 0071-0084 | sovereignos: authorize the return path and remove the unlock swipe and delay |
| `frameworks-base/0010` | 0085-0093 | sovereignos: report real quiesce failures and re-evaluate the user-switcher state |
| `packages-apps-settings/0001` | 0001-0007 | sovereignos: hide real-user identity from Settings for the decoy session |
| `packages-apps-settings/0002` | 0008-0010 | sovereignos: make Settings present the decoy as the device owner |
| `packages-apps-multiuser/0001` | 0001-0004 | sovereignos: hide real-user identity from the Multiuser widget for the decoy session |


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
  path" (originally `0015`, since squashed with `0016` into one commit at
  the file's correct final path — see the Duress Unlock final-closure
  Task 4 history-squash note near the end of this file) — new
  `ReturnToRealUserHelper.java`
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
  0014 misapplied it under frameworks/base/)" (originally `0016`, since
  squashed into `0015` above — see the Task 4 history-squash note near the
  end of this file) — the first attempt at `0015` placed the new file
  under a spurious extra `frameworks/base/` path prefix (the repo root is
  already `frameworks/base`), which broke compilation; a task reviewer's
  diff-stat check (2 files changed, 0 deletions) confirmed this follow-up
  commit cleanly relocated the file rather than leaving a duplicate.
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
  (originally `patches/frameworks-base/0018`, real build-server SHA,
  since dropped entirely from history — see the Task 3 history-squash
  note after the `0024` section below) — flips
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

**History note (Duress Unlock final closure, Task 2):** this repository's
original four commits (`e4722bcb53a3`, `aae51c7d0274`, `952bd37e4bbc`,
`10209de65d2a` — enable at `trunk_staging`, relocate to `cp2a`, fix
permission to `READ_ONLY`, revert to `DISABLED`) were squashed into one
commit on 2026-09-17, with owner sign-off, after a closure audit
(`docs/devices/lynx-duress-unlock-patch-audit.md` in `AegisOS-foundation`)
found this flag's own upstream declared default already resolves to
`state: DISABLED, permission: READ_ONLY` on this product's `cp2a` release
config — identical to `0004`'s final state — meaning the whole four-patch
enable/fix/fix/revert cycle nets out to exactly what a bare declaration
would have produced anyway. The owner chose to keep one explicit override
file as defense-in-depth (fails the build loudly if a future upstream
`.aconfig` edit ever changes the default, where no override file would
fail silently) rather than delete it outright. The narrative below
(original `0001`-`0004`, kept for historical record of how the mechanism
was actually discovered, built, and rejected) now describes commits that
no longer exist on the real branch; the squash's own record, with the
real post-squash SHA and the before/after behavioral-identity proof, is
at the end of this subsection.

- `e4722bcb53a3ad8e67aafad21c6540a83f552ee1` — "aegisos: enable
  credential_capture flag for skip-keyguard-when-switching-to-unlocked-users"
  (originally `patches/build-release/0001`, real build-server SHA, since
  squashed — see history note above) — adds
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
  actually reads" (originally `patches/build-release/0002`, real
  build-server SHA, since squashed — see history note above) —
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
  (originally `patches/build-release/0003`, real build-server SHA, since
  squashed — see history note above) — changes
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
  (originally `patches/build-release/0004`, real build-server SHA, since
  squashed — see history note above) — a one-line
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

### History squash: `0001`-`0004` collapsed into one commit (Duress Unlock final closure, Task 2)

Landed 2026-09-17, owner sign-off already obtained for this specific
squash. This is the first time this project has rewritten history on a
real, landed branch — done with the extra caution that implies, not less:
the original four commits remain recoverable via the branch's own reflog
on the build server (not force-pushed anywhere, not garbage-collected),
and every step below was verified against the real branch state
immediately before acting, not assumed from an earlier read.

**Why:** the Duress Unlock final-closure audit
(`docs/devices/lynx-duress-unlock-patch-audit.md` in `AegisOS-foundation`,
Step 2) found `credential_capture`'s own upstream declared default (no
explicit `state:` in `frameworks/base/core/java/android/content/pm/
multiuser.aconfig`, so `aconfig`'s own hardcoded
`DEFAULT_FLAG_STATE = DISABLED` applies) resolves, once `cp2a`'s real
release-config inheritance chain is followed
(`cp2a`→`cp1a`→`bp4a`→`bp3a`→`bp2a`→`bp1a`, `bp1a`'s own
`RELEASE_ACONFIG_FLAG_DEFAULT_PERMISSION` override sets `READ_ONLY`), to
`state: DISABLED, permission: READ_ONLY` — identical to `0004`'s explicit
end state. The four-commit enable/relocate/fix-permission/revert sequence
therefore nets out to exactly what zero override file would have produced
on its own. Rather than delete the file (the audit's own stated cleanest
technical outcome), the owner chose defense-in-depth: **keep one explicit
override**, so a future upstream `.aconfig` edit that changes this flag's
own default can't silently change this product's behavior — an override
file makes that scenario a loud build-time surprise instead of a silent
one. Mechanically this means the brief's "if ENABLED: no content edit
needed, squash the four commits into one" branch applies even though the
technical trigger condition found was DISABLED.

**How:** a disposable local git repo was seeded two ways — first a direct
shallow `git fetch --depth=6` of the real branch was attempted and failed
identically to `0002`'s own prior experience ("lazy fetching disabled;
some objects may not be available" / "aborting due to possible repository
corruption on the remote side" — `build/release` is a partial/promisor
clone on the build server, confirming this isn't a one-off). Fell back to
`AGENTS.md`'s own "fetch only the touched files" pattern instead: an
empty baseline commit, then the four locally-exported `0001`-`0004`
patches applied via `git am` on top of it (all four applied cleanly), with
the resulting file's `sha256sum` confirmed byte-identical to the real
branch tip's own file (fetched read-only via `git show HEAD:<path>` on the
build server) before proceeding — proof the four-patch replay genuinely
reconstructs the real branch tip, not an approximation. `git reset --soft`
back to the baseline commit collapsed all four into a single new commit
with the identical resulting tree (verified: `git diff` between the
4-commit HEAD and the squashed commit is empty), authored as `AegisOS
Build <build@aegisos.local>` — the same author identity the original four
commits themselves used (confirmed via `git log --format='%an <%ae>' -4`
on the real branch before writing the new commit, not assumed).

Landing on the real branch: confirmed no build in flight
(`pgrep -af "^ninja|soong_ui --build-mode$"`, re-checked immediately
before the operation, not just once earlier in the session), then
`git reset --hard 8976d78feac337469081409228886065a4dfefab` (the real
commit immediately before `0001`, moving the branch pointer back — the
history-rewriting step) followed by `git am` of the squashed patch. The
old four commits stayed reachable via `git reflog` immediately after
(`10209de65 HEAD@{2}: am: ...`, etc.) — a live safety net, not merely a
claim.

**New commit, real post-`git am` SHA:**
`0ded9b0e3972e2addd5d0d973db21f6891b26473` — "aegisos: squash
credential_capture enable/relocate/permission/revert history"
(`patches/build-release/0001`, replacing the old `0001`-`0004`). File
content confirmed byte-identical to the pre-squash tip by git blob hash
alone: the new commit's diff creates
`aconfig/cp2a/android.multiuser/credential_capture_flag_values.textproto`
with post-image blob `2af5b42d9` — the exact same blob hash `0004`'s own
original patch ended on. Base commit:
`8976d78feac337469081409228886065a4dfefab` (unchanged from the original
`0001`'s own base — this squash does not move the branch point, only
collapses what sits on top of it).

**Build verification (behavioral-identity proof, before vs. after):**
`aconfig` value files aren't compiled artifacts; the actual consumer is
`aconfig create-cache` (`build/soong/aconfig/aconfig_declarations.go`),
invoked per-module by Soong. A full `m android.multiuser.flags-aconfig`
was attempted first but timed out after 280s still in Soong's analysis
phase with no output — consistent with a full product-graph analysis
pass, not the lightweight single-file operation the brief expected;
per the brief's own instruction to stop rather than kick off a long build,
this was abandoned in favor of invoking the already-built host `aconfig`
binary directly (the same tool Soong itself calls, same inputs, zero
Kati/Soong analysis overhead):

- **Before** (real build artifact, produced by a genuine prior `m`
  invocation against this exact branch tip, timestamped after `0004`
  landed, untouched by any of this task's git operations):
  `aconfig dump-cache --cache out-lynx/soong/.intermediates/frameworks/base/
  android.multiuser.flags-aconfig/aconfig-cache.pb --filter=name:credential_capture
  --format=textproto` →
  ```
  parsed_flag {
    package: "android.multiuser"
    name: "credential_capture"
    namespace: "multiuser"
    description: "Perform authentication in the login app on desktop before switching to a user"
    bug: "448603243"
    state: DISABLED
    permission: READ_ONLY
    trace {
      source: "frameworks/base/core/java/android/content/pm/multiuser.aconfig"
      state: DISABLED
      permission: READ_ONLY
    }
    trace {
      source: "build/release/aconfig/cp2a/android.multiuser/credential_capture_flag_values.textproto"
      state: DISABLED
      permission: READ_ONLY
    }
    is_fixed_read_only: false
    is_exported: false
    container: "system"
    metadata { purpose: PURPOSE_UNSPECIFIED storage: NONE }
  }
  ```
  (Also confirms the audit's own Step 2 claim directly, not just by
  source-reading: the first `trace` entry — the flag's own declared
  default, computed before any value-file override is applied — already
  shows `DISABLED`/`READ_ONLY`, matching the override's own value exactly.)
- **After** (post-`git am`, direct `aconfig create-cache` invocation
  against the real, now-squashed working tree — `--package android.multiuser
  --container system --declarations frameworks/base/core/java/android/
  content/pm/multiuser.aconfig --values build/release/aconfig/cp2a/
  android.multiuser/credential_capture_flag_values.textproto
  --default-permission read_only --allow-read-write false`, then the same
  `dump-cache` filter): byte-identical output to the "before" block above —
  `diff` between the two saved outputs was empty, and both files' own
  `sha256sum` matched exactly
  (`d2ab7118d9242dd9df77584178f9b5d496db1767e905878b19d1fcb8854480b0`).

No behavioral change. `credential_capture` stays `state: DISABLED,
permission: READ_ONLY` on this product's `cp2a` release config, before and
after the squash, confirmed by the actual `aconfig` tool's own output, not
inferred.

No LAN/account coordinates in this section, the new patch file, or the
squashed commit message — grepped for `192\.168|beau@|beau-dev|
/srv/aegisos` before this commit.

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
  (originally `patches/frameworks-base/0024`, real build-server SHA, since
  edited to drop change 2 below — see the Task 3 history-squash note
  right after this section) — three changes:
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

### History squash: `0018` dropped, `0024`'s resource-revert hunk removed (Duress Unlock final closure, Task 3)

Landed 2026-09-17, owner sign-off already obtained for this specific
squash. This is the second time this project has rewritten history on a
real, landed branch (after Task 2's `build/release` squash minutes
earlier), and the first time that rewrite had to reach into the *middle*
of a much larger, already-landed sequence rather than collapse commits
sitting at the very start of one — handled with the extra caution that
implies: every commit strictly between the two touched ones (`0019`
through `0023` in the old numbering) had to be replayed with byte-for-byte
unchanged content, not merely "close enough," and the original commits
remain recoverable via the branch's own reflog on the build server (not
force-pushed anywhere, not garbage-collected).

**Why:** the Duress Unlock final-closure audit
(`docs/devices/lynx-duress-unlock-patch-audit.md` in `AegisOS-foundation`)
classified `0018` (`ebb58e3ccc2034c2685b889ceecbcd1830b382ef`, flips
`config_multiuserSkipKeyguardWhenSwitchingToUnlockedUsers` from `false` to
`true`) as a dead end: real hardware validation after it landed found the
mechanism both failed to fix the double-PIN-prompt bug it was meant to
fix and introduced a genuine device-wide keyguard bypass for ordinary
(non-decoy) user switches, which is exactly why `0024` reverted it back
to `false` days later. A commit that is enabled and then reverted within
the same patch series nets to "never touched" — squashing to that literal
net state (drop `0018` outright, and drop only the matching revert hunk
from `0024`, since there is nothing left for it to revert) is more
accurate history than keeping either half. `0024`'s other two changes
(decoy-user-type validation in `DecoyProfileHelper.java`, the
`@UnsupportedAppUsage` removal in `UserManager.java`) are real, needed
fixes unrelated to the resource flip and were re-confirmed independently
against the live branch (not just the plan's own framing) before this
squash touched anything — including one change the original plan text
never mentioned at all: the `USER_SETUP_COMPLETE` write was reordered in
that same commit to run after `DecoyProfileState.save()`, not before, and
that reordering is bundled in the same diff hunk as the type-validation
check, so it was preserved as an inseparable unit, not cherry-picked
apart.

**How:** `frameworks/base` is a much larger repository than `build/release`
(Task 2), so a full or even shallow clone was avoided. Per `AGENTS.md`'s
"fetch only the touched files, read-only" pattern, the full list of files
touched by patches `0018`-`0086` was collected (38 distinct files, one of
them — `DecoyRoutingFailureMarker.java` — created fresh partway through
the series so it had no baseline content), then `git archive` fetched
their real content as of `5f3a562a4eefea8a14c2aaf42d14fc9c49cf0c1a` (the
real commit immediately before `0018`) into a disposable local repo. All
69 locally-exported `0018`-`0086` patches applied cleanly via `git am` on
top of that baseline; every one of the 40 touched-file blob hashes in the
resulting tree matched the real branch tip's own file content exactly
(`git rev-parse fade68916b06...:<path>` over SSH) before any rebase
surgery began — proof the replay genuinely reconstructs the real branch,
not an approximation.

A real `git rebase -i` (not a hand-spliced patch file) then dropped the
`0018`-equivalent commit and marked the `0024`-equivalent commit `edit`.
The first attempt at resolving the resulting `config.xml` conflict used
`git commit --amend` directly instead of `git rebase --continue` — since
the conflict meant no commit had been created yet for that step, the
amend silently folded the *previous* commit's own changes (`0023`,
"clear calling identity for decoy setup-complete write") into the edited
commit instead of creating a new one on top of it. Caught by diffing the
result against the original replay before trusting it (`git show
--stat` on the amended commit showed `DecoyProfileHelper.java` changes
that belonged to the wrong commit) — the rebase was aborted and redone
correctly: resolve the conflict, `git add`, then `git rebase --continue`
(which creates the commit itself, using the original commit's own
message), then a second, separate `git rebase -i ... reword` pass to fix
only the message. The `--stat` output after doing it the right way
tracked exactly to the two intended files (`UserManager.java`,
`DecoyProfileHelper.java`, 26 insertions/3 deletions total, zero mention
of `config.xml`), and a full-tree diff between this corrected replay and
the original (unedited) 69-commit replay showed exactly one file
different — `config.xml`, and only the 12-line AegisOS comment block
removed, value unchanged — confirming nothing else in the entire replayed
history shifted.

**Renumbering:** dropping one commit in the middle of the series shifts
every subsequent patch's filename down by one. Old `0019` through `0086`
(68 patches) are now `0018` through `0085`; the `0024`-derived edited
commit is now `0023`. **Sections below this one that cite old patch
numbers `0019`-`0086` were deliberately left unchanged** — per this
project's own convention (established in Task 2's `build-release` squash
note), those citations are accurate records of what was true when they
were written, and rewriting them would misrepresent history. Subtract 1
from any `patches/frameworks-base/00NN` citation with `NN >= 19` in a
section below this point to find its current filename; sections above
this point (`0001`-`0017`) are unaffected and still match their own
citations exactly.

**New/edited commits, real post-`git am` SHAs:**
- Base commit (unchanged from `0018`'s own original parent):
  `5f3a562a4eefea8a14c2aaf42d14fc9c49cf0c1a`.
- `0018` (`ebb58e3ccc2034c2685b889ceecbcd1830b382ef`) no longer exists
  anywhere in the branch's ancestry. Confirmed with the exact check the
  plan's own brief specified,
  `git log --oneline --all -- core/res/res/values/config.xml | grep -i
  multiuserSkip`: zero results. That specific check passes partly by
  construction on a repo this size — neither original commit's own
  one-line subject contains the literal substring `multiuserSkip`, so the
  grep would have found nothing even before the squash — so it was
  cross-checked with a scoped, unambiguous version instead:
  `git log --oneline 5f3a562a4eefea8a14c2aaf42d14fc9c49cf0c1a..
  aegisos-duress-decoy-switch -- core/res/res/values/config.xml` (i.e.
  "does any commit reachable only from this branch, since `0018`'s old
  parent, touch this file at all") — zero commits. `core/res/res/values/
  config.xml`'s blob hash at the new branch tip is
  `d7a4004a27d6923894306b46f5f81044b471a55d` — the exact same hash it had
  at `0018`'s own parent, i.e. bit-identical to upstream AOSP, never
  touched by this series at all, not merely reverted to the same value.
- `866593d9735cc6fcf6444b158b0ee2b628a9a0ad` — "aegisos: validate decoy
  user type, drop UnsupportedAppUsage" (`patches/frameworks-base/0023`,
  replacing the old `0024`) — the original `cd072d704764a3a7...` commit
  with its `config.xml` hunk removed and nothing else changed; message
  updated to match (dropped the resource-revert bullet, added a closing
  note explaining why). Diff is exactly the `UserManager.java`/
  `DecoyProfileHelper.java` hunks from the original commit, byte-for-byte
  — confirmed by diffing this commit's own patch text against the
  original `0024` patch file with the `config.xml` hunk manually excised
  from the comparison.
- Every one of the 38 other touched files (`DecoyRoutingFailureMarker.java`
  excluded, since it doesn't exist yet at the baseline) has an unchanged
  blob hash at the new branch tip versus the pre-squash tip
  (`fade68916b06c61e7ad63dc5156b3928693c901a`) — verified by comparing all
  39 non-`config.xml` hashes pairwise, zero differences.
- New branch tip: `949164d7ae30c4743d0799f0dfc11275228ea4fc` (68 commits
  above the unchanged base, replacing the original 69). Landed with
  `git reset --hard 5f3a562a4eefea8a14c2aaf42d14fc9c49cf0c1a` followed by
  `git am` of the 68 re-exported patches (all applied cleanly, no
  conflicts) after re-confirming immediately beforehand: no build in
  flight, working tree clean, HEAD still at the expected pre-squash
  `fade68916b06...`. The original commits remain reachable via `git
  reflog` on the build server (time-bounded safety net, not permanent).

**Build verification (behavioral-identity proof, before vs. after):**
A real `m services framework-res` was attempted first, launched fully
detached (`OFFICIAL_BUILD=true OUT_DIR=out-lynx`, `lynx-cur-userdebug`,
`SOONG_INCREMENTAL_ANALYSIS=true`) and polled rather than run inline. It
did not succeed: repeated `memory stall` warnings from Soong's own tracer
during the Android.bp analysis pass preceded the single `soong_build`
process being killed outright (`stderr: Killed`, exit 137 — a host-level
OOM kill, not a content or dependency error) at 7m22s, before any actual
compilation began. `free -h` immediately after showed the host back at
30GiB free with 6GiB of swap in use, consistent with a genuine transient
memory-pressure event on this host rather than anything wrong with the
squashed tree itself. Per the plan's own instruction not to let a build
run unbounded when it looks disproportionate to the task, this was not
blindly retried; the same class of substitution Task 2 used when its own
`m android.multiuser.flags-aconfig` stalled was applied here instead —
real compiled-artifact evidence obtained without a full product build:

- **Source identity, the load-bearing fact underneath everything below:**
  every file `services`/`framework-res` would compile from is either
  completely unchanged by this squash, or (for `config.xml`) changed only
  by removing a comment. `DecoyProfileHelper.java` and `UserManager.java`
  — the two files `0024` touches — have git blob hashes at the new branch
  tip identical to the pre-squash tip's own hashes (see above). Java
  compilation is a deterministic function of source, classpath, and
  compiler flags; none of those changed, so recompiling cannot produce
  different bytecode for these two files' methods.
- **Real compiled bytecode, not a re-derivation:** rather than trust that
  claim on logic alone, the actual compiled classes were pulled from the
  most recent real build already on the server and disassembled with
  `javap -p -c -constants` (`prebuilts/jdk/jdk21`, not re-derived from
  source): `DecoyProfileHelper.class` from `services.jar`'s own
  `combined` intermediate (built 2026-09-16 21:33, i.e. against a
  frameworks-base tree only one commit removed from the pre-squash tip —
  `services.jar` is byte-for-byte outside the scope of anything this
  squash touches either way, per the source-identity point above), blob
  hash `e9abde6d978b5a49231fdaddc834f3f12db65d91eb985c182d0a1328aef5d064`;
  `UserManager.class` from `framework-minus-apex`'s own `combined`
  intermediate (built 2026-09-16 09:35), blob hash
  `d89a5776040f4a6e262a6a93ffcda06d899f98d843380679761ef5c767c95d16`. Both
  disassemblies confirm the exact methods `0024` touches are present with
  the expected signatures: `void setDecoyProfile(LockscreenCredential,
  int)` (the validation + save-ordering change) and `public List<UserInfo>
  getUsersForUiDisplay(boolean)` (the `@UnsupportedAppUsage` removal,
  visible in the disassembly as the absence of any
  `RuntimeVisibleAnnotations` entry referencing that annotation type on
  this method — annotations are part of a class's bytecode-level metadata,
  so their absence is directly observable here, not just inferred from
  source). Since source identity (point above) guarantees recompilation
  cannot change this output, this is equivalent-or-stronger evidence to an
  actual post-squash rebuild of these two files specifically — it is real
  tool output against real compiled bytecode, not inference alone, even
  though it substitutes for rather than replaces the brief's literal `m
  services` instruction.
- **Resource value, from a real compiled APK:** `aapt2 dump resources`
  against the most recent `framework-res.apk` already on the server
  (built 2026-08-30 07:27, ~47 minutes after the original `0024` landed)
  shows `resource 0x01110224 bool/config_multiuserSkipKeyguardWhenSwitching
ToUnlockedUsers` `() false` — the real compiled value, matching both the
  pre-squash and post-squash source exactly, since aapt2 strips XML
  comments when compiling to binary resources and the squash changed
  nothing but a comment (the `<bool>` value line itself was untouched by
  every commit in this squash, confirmed above by the full-tree diff
  showing only a 12-line comment block removed).

This does not carry the same weight as an actual fresh `m services
framework-res` run against the post-squash tree, and is recorded here as
a real, disclosed deviation from the brief's literal Step 4 instruction —
not a substitute for it that should be treated as equally conclusive. A
follow-up full build (or a retry once the host's memory pressure has been
independently investigated) is recommended before this squash is treated
as fully closed at the same confidence level as Task 2's.

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

### `frameworks/base`: decoy switch keyguard-skip authorization, Tasks 6-7 (`0045`-`0050`)

Tasks 6-7 of `docs/superpowers/plans/2026-09-07-duress-decoy-seamless-unlock.md`,
Component B's one-shot, decoy-scoped switch authorization store and its
consumption point — the same architectural bet ADR-061's regression case
already forced: the existing AOSP `skipKeyguardWhenSwitchingToUnlockedUsers`
condition at `UserController.java` stays completely untouched and stays
inert (gated on a permanently-disabled aconfig flag per ADR-061's revert);
Component B introduces its own separate, narrowly-scoped authorization
rather than reusing or widening that condition. Six commits, initially
landed as one combined `git format-patch --stdout` mailbox
(`0045-decoy-switch-authorization-store-and-keygua.patch`) on the reasoning
that Task 6 alone wires nothing observable — it only becomes a real,
verifiable capability once Task 7's consumption point and bridge exist.
That combined-mailbox choice deviated from this directory's own established
one-file-per-commit convention (every other multi-commit export here, e.g.
`0041`-`0043`, `0044` alongside `packages-apps-multiuser`'s `0004`, uses a
separate file per commit) and has since been corrected: the same six
commits, byte-for-byte, now live as six separate files `0045`-`0050`
below, verified to reconstruct the identical tree the single mailbox did
(see "Reproducibility").

- `6fc1a61a82e3` — "test: add coverage for the decoy switch authorization
  store" (Task 6 RED)
  (`patches/frameworks-base/0045-test-add-coverage-for-the-decoy-switch-authorization.patch`).
- `5461467e5003` — "aegisos: implement the decoy switch authorization
  store" (Task 6 GREEN, by trace only at landing time — see below)
  (`patches/frameworks-base/0046-aegisos-implement-the-decoy-switch-authorization-sto.patch`).
  New
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
  authorization" (Task 7 RED)
  (`patches/frameworks-base/0047-test-add-coverage-for-consuming-the-decoy-switch-aut.patch`).
  Adds
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
  findings" below)
  (`patches/frameworks-base/0048-test-register-ActivityManagerInternal-mock-for-foreg.patch`).
  Registers a `LocalServices`-backed
  `ActivityManagerInternal` mock inside `startForegroundUser()`
  (`LocalServices.removeServiceForTest` then `addService`, matching the
  existing `WindowManagerInternal` pattern in
  `UserManagerServiceTest.java`'s `setKeyguardLocked()`), torn down
  unconditionally in `tearDown()` so it never leaks into another test.
  Test-only; no production code in this commit.
- `e380eced01fa` — "aegisos: skip the keyguard for an authorized switch"
  (Task 7 GREEN)
  (`patches/frameworks-base/0049-aegisos-skip-the-keyguard-for-an-authorized-switch.patch`).
  Replaces the inner block at
  `UserController.java:2335-2345`: a new `if (consumeDecoySwitchAuthorization(userId))`
  branch is added *ahead* of the existing AOSP three-part condition,
  which is otherwise byte-identical to before this patch — confirmed by
  diff, not assumed. Log line ("Skipping keyguard for an authorized
  switch") deliberately worded without naming the mechanism, since it is
  reachable from a decoy session and this project's own hard constraint
  forbids "decoy"/"duress"/"Aegis" in any log string reachable from that
  path.
- `bbe0134976a4` — "aegisos: expose the decoy switch authorization mint
  via ActivityManagerInternal" (Task 7's bridge)
  (`patches/frameworks-base/0050-aegisos-expose-the-decoy-switch-authorization-mint-v.patch`).
  New
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

**Reproducibility.** Originally regenerated via `git format-patch --stdout`
directly from the real post-`git am` branch history (six commits
concatenated into the one `0045` file, not trusted from any local
worktree's own patch generation) and independently verified byte-for-byte:
applied via `git am` to a disposable local clone seeded at the real base
commit's own file content (four files: `UserController.java`,
`UserControllerTest.java`, `ActivityManagerInternal.java`,
`ActivityManagerService.java`), diffed against both the authoring
worktree's final state and the live server tree directly via `sha256sum`
— empty diff / matching hash on all four files, both ways.

**Split into `0045`-`0050`, one file per commit.** The single combined
mailbox above deviated from this directory's own established convention
(see the section intro), and was replaced with six individual
`git format-patch` files, one per commit, re-derived from the same six
commits (`865e3c4addaac5e79fea9fc0fbb842aa3d68d52e` through
`aedb0487345fce6602cef30e4b5ce4528047b5d7` in the local authoring
worktree, `aegisos-platform/.worktrees/decoy-switch-authorization/` —
each carrying the real post-`git am` SHA above in its own `From ` header,
not the local worktree SHA). Verified two ways in that same worktree, both
against a disposable clone seeded at the local baseline commit representing
the real base tree for all four touched files
(`dd81ed8` + `c0d62bd`, together standing in for the real base commit
`e15abc1a26ffa79563ec6665c20fda337d6d94f6`'s own content of those four
files): first, applying the six new `0045`-`0050` files in order via
`git am` reproduced the authoring worktree's own known-good tip
(`aedb0487345fce6602cef30e4b5ce4528047b5d7`) with an empty `git diff`;
second, applying the six new files versus applying the original combined
mailbox to two independent clones of the same seeded base produced
byte-identical resulting trees (`diff -rq`, empty, excluding `.git`) —
confirming the split lost or altered nothing.

### `frameworks/base`: mint before the reply, switch after (`0051`-`0053`)

Tasks 8-9 of `docs/superpowers/plans/2026-09-07-duress-decoy-seamless-unlock.md`,
implemented together in one worktree because Task 8's restructured
`DecoyProfileHelper` calls Task 9's marker class directly. Base commit:
`bbe0134976a439337cc84d4c4699d4389264b86a` (`0050`'s own real post-`git am`
SHA, the real branch tip at fetch time). Fetched five files read-only from
the real tree and confirmed byte-identical (`sha256sum`, both ways) before
editing: `DecoyProfileHelper.java`, `LockSettingsShellCommand.java`,
`DecoyProfileState.java` (read for reference, unchanged), and
`LockSettingsService.java` (needed for one wiring change described below).

- `8008837d9f38decc0381e58d23b81c72a094399d` — "aegisos: add
  DecoyRoutingFailureMarker for the seamless-switch fallback path" (Task 9's
  core class)
  (`patches/frameworks-base/0051-aegisos-add-DecoyRoutingFailureMarker-for-the-seamle.patch`).
  New file, package `com.android.server.locksettings`, modeled on the
  existing `DecoyProfileState`'s storage pattern exactly: `record(LockSettingsStorage,
  int reason)` and `describe(LockSettingsStorage)`, both static, storing a
  single `"<version>:<reason>:<epochMillis>"` string via
  `LockSettingsStorage.setString`/`getString` under `UserHandle.USER_SYSTEM`
  — the real owner's own DE storage, never readable from a routed-to
  session. `REASON_SWITCH_NOT_STARTED = 1` and `REASON_QUIESCE_FAILED = 2`
  as specified. `describe()` always returns a non-null, human-readable
  string (no recorded failure, a described failure, or a corrupt-entry
  message) so the shell command below has something to print
  unconditionally. The one `Slog.e` call (for a corrupt on-disk entry) was
  checked for the project's log-string ban and contains none of
  "decoy"/"duress"/"Aegis", matching the file's own already-neutral `TAG`
  convention (`"CredentialRoutingFailureMarker"`, mirroring
  `DecoyProfileState`'s `"CredentialRoutingState"`).
- `4d4ba9bbb654fc07f9c1a4c6d4f34c87ba152437` — "aegisos: add
  check-credential-routing-status shell command" (Task 9's read-only
  diagnostic entry point)
  (`patches/frameworks-base/0052-aegisos-add-check-credential-routing-status-shell-co.patch`).
  Two files. `LockSettingsShellCommand.java` gains a new subcommand,
  `check-credential-routing-status`, dispatched the same way
  `set-decoy-profile`/`clear-decoy-profile` are (intercepted at the top of
  `onCommand`, before the general credential-gated dispatch), but — per the
  brief's own instruction — takes no `--owner-credential` argument and
  never prompts for one, since it only reads state. It still enforces the
  same permission the sibling commands are ultimately gated on: it calls
  `mContext.enforceCallingOrSelfPermission(ACCESS_KEYGUARD_SECURE_STORAGE,
  "LockSettingsRead")` directly, the same permission constant and message
  string `LockSettingsService#checkReadPermission()` uses for every other
  read path on this class (confirmed by reading that method), rather than
  introducing a second, differently-scoped check. `LockSettingsShellCommand`
  gained one new constructor parameter, `LockSettingsStorage
  lockSettingsStorage` (stored as `mLockSettingsStorage`), because it
  previously had no way to reach storage at all — every existing command
  goes through `mLockPatternUtils`'s AIDL-backed wrapper instead. This is
  the one deviation from the brief's literal file list (which named only
  `LockSettingsShellCommand.java`): `LockSettingsService.java`'s
  `onShellCommand` needed a one-line change to pass its own `mStorage`
  field through the constructor call. This was judged the minimal correct
  wiring — the alternative (constructing a second, independent
  `LockSettingsStorage` instance inside the shell command) would have
  meant two live instances of a class that is not documented as safe to
  duplicate, for no real benefit.

  **`onHelp()` judgment call:** the brief asked to mirror "argument parsing,
  permission enforcement... onHelp registration" from the sibling commands.
  Read `onHelp()` first and found `set-decoy-profile`/`clear-decoy-profile`
  are themselves *not* listed there — deliberately undocumented, consistent
  with not advertising this mechanism to a casual `locksettings help`
  reader. Mirroring their actual pattern therefore means also not adding
  `check-credential-routing-status` to `onHelp()`, which is what this patch
  does; the brief's phrase is read as "match how the siblings actually
  behave" rather than "add an entry regardless." Flagged explicitly since
  it is an interpretation of ambiguous brief text, not a literal
  instruction.

  **Command naming:** named `check-credential-routing-status` rather than
  anything containing "decoy", per the brief's explicit instruction to keep
  this one neutral — consistent with the file's own established neutral
  `TAG` values for this feature area (`CredentialRoutingHelper`,
  `CredentialRoutingState`), even though the two literal sibling command
  names (`set-decoy-profile`, `clear-decoy-profile`) do contain "decoy"
  themselves (pre-existing, unchanged by this patch). The `check-` prefix
  matches the file's own precedent for a read-only diagnostic command
  (`check-non-ce-storage-keys`).
- `4ccf6785de23cf9cdebaa90ae3663a905e0bf61f` — "aegisos: mint the decoy
  switch authorization before replying, switch after" (Task 8: the ordering
  fix)
  (`patches/frameworks-base/0053-aegisos-mint-the-decoy-switch-authorization-before-r.patch`).
  Restructures `DecoyProfileHelper.maybeRouteToDecoy`: everything above the
  switch call (the no-decoy dummy check, the decoy credential verification)
  is unchanged. The tail now mints the switch authorization
  (`ActivityManagerInternal#authorizeDecoySwitchKeyguardSkip`, from `0050`)
  synchronously, before any response is returned — a null service or a
  rejected authorization still returns `null` (today's fail-closed-to-a-
  second-prompt behavior, unchanged for this specific failure point).
  `realUserId` is captured via `ActivityManager.getCurrentUser()`
  immediately before `getHandler().post(...)`, while the current user is
  still genuinely the real one — capturing it inside the posted runnable
  would instead race the switch itself, which is exactly the class of bug
  this task exists to fix one level up (the response-vs-switch race). The
  handler used is `LockSettingsService.mHandler` (Task 1's confirmed
  finding — its own dedicated `ServiceThread`, not `FgThread`, which has
  zero usages in this package), reached via a new private
  `DecoyProfileHelper#getHandler()` accessor rather than a new field or
  dependency, since `DecoyProfileHelper` already holds the
  `lockSettingsService` reference `mHandler` hangs off (same-package
  `protected` access). `VerifyCredentialResponse.OK` is returned
  immediately after posting, before the actual switch has run.

  `freezeAndSwitchToDecoy` is renamed `performDecoySwitch` and now returns
  `void` — it runs on the handler, strictly after the response was already
  delivered, so it has no response left to withhold. It re-fetches
  `ActivityManagerInternal` (a second `LocalServices.getService` call, not
  reused from the mint step, since it now runs on a different thread at a
  later time) and calls `switchToUserSilently`; on failure it calls
  `DecoyRoutingFailureMarker.record(..., REASON_SWITCH_NOT_STARTED)` and
  returns. On success it calls `quiesceUserApps(realUserId)`; on failure it
  calls `DecoyRoutingFailureMarker.record(..., REASON_QUIESCE_FAILED)`. The
  old `Slog.e(TAG, "Target-user credential matched but freeze/switch could
  not be initiated -- treating this attempt as not matched")` line (the
  real string in this file — the design doc's own citation paraphrased it
  slightly differently) is deleted outright, not reworded. This is a
  natural consequence of restructuring `maybeRouteToDecoy`/
  `performDecoySwitch` around the new mint-before-reply ordering — the
  marker now carries the equivalent information — not an implementation of
  Task 3's audit decision: that audit classified this exact line (row 2)
  as **RENAME**, not remove, and the rename (dropping "decoy" from the
  message body, keeping level and call site unchanged) had already landed
  in an earlier task, before this patch's own base commit. No new or
  changed `Slog`/`Log` call in this patch contains "decoy", "duress", or
  "Aegis" — checked line by line, and in fact this patch's net change to
  `Slog` call count in the file is negative (two calls removed, zero
  added).

  **Accepted behavioral change, not a regression:** before this patch, a
  failed `quiesceUserApps` made `maybeRouteToDecoy` return `null` — the
  decoy credential was reported as *not matched*, and no switch happened at
  all. That outcome is no longer reachable, because the response is already
  sent by the time `performDecoySwitch` runs. The real user can now be left
  un-quiesced (backgrounded but not suppressed) after a successful switch,
  if `quiesceUserApps` alone fails on that one call. This is the design's
  own explicit, accepted tradeoff (see the design doc's "Failure path"
  paragraph: fail-open to a working routed-to session rather than
  fail-closed to nothing, since the one moment this mechanism exists for is
  coercion) — `DecoyRoutingFailureMarker` exists specifically so this path
  is not silently invisible, and Task 13 (dynamic validation) is the
  designated place to actually exercise it, not this task.

- Base commit: `bbe0134976a439337cc84d4c4699d4389264b86a` (see above).

**Real branch SHAs**, confirmed via `git log --format="%H %s"` on the actual
branch after `git am`, not assumed from the apply output or the local
worktree's own commit SHAs (`8d0902c`/`faf84d2`/`8978eb7` in
`aegisos-platform/.worktrees/duress-seamless-task8-9/`, all superseded by
`git am` reconstructing the commits against the real tree):

```
4ccf6785de23cf9cdebaa90ae3663a905e0bf61f  aegisos: mint the decoy switch authorization before replying, switch after
4d4ba9bbb654fc07f9c1a4c6d4f34c87ba152437  aegisos: add check-credential-routing-status shell command
8008837d9f38decc0381e58d23b81c72a094399d  aegisos: add DecoyRoutingFailureMarker for the seamless-switch fallback path
```

`4ccf6785de23cf9cdebaa90ae3663a905e0bf61f` is the real branch tip, confirmed
a second time (independently of the `git am` output) immediately before the
build was launched, and a third time after the build completed with no
other patch having landed in between.

**Build verification.** A full `m -j6 systemimage` (not a fast targeted
build) was run for this task specifically because Task 9's own brief called
for it, given the shell-command wiring change touches
`LockSettingsService.java`'s constructor call site. Launched fully detached
on the remote side (`nohup ... & disown`), polled synchronously in bounded
loops rather than left to run unattended:

```
ninja: 1h16m02.22s Build Succeeded: 2873 steps - 0.63/s
#### build completed successfully (01:27:08 (hh:mm:ss)) ####
```

No compile errors; the only diagnostics in the log are pre-existing,
unrelated warnings (a Kotlin coroutines duplicate-class warning in an
unrelated MediaProvider module, an unsupported-API-level notice, and a
`metalava` `UnflaggedApi` info-level note on an unrelated file) — none
reference any file this patch series touches.

**Reproducibility.** Regenerated fresh via `git format-patch
bbe0134976a4..HEAD` directly on the real post-`git am` branch (not trusted
from the local worktree's own patch generation), then independently
verified: applied the three fresh patches via `git am` to a disposable
local clone seeded at the real base commit's own content of all five
touched files, and diffed each of the five resulting files against the live
server tree directly via `sha256sum` — all five matched exactly
(`DecoyProfileHelper.java`, `LockSettingsShellCommand.java`,
`DecoyProfileState.java`, `LockSettingsService.java`,
`DecoyRoutingFailureMarker.java`).

**Test coverage — explicitly none for this task.** No unit test was written
for `DecoyRoutingFailureMarker` or the restructured `DecoyProfileHelper`.
There is no practical unit-test seam for either without mocking
`LockSettingsStorage`: `LockSettingsStorageTests` does not exist anywhere
in this tree (confirmed by search, not assumed), and `DecoyProfileHelper`
itself has never had direct unit test coverage in this branch's history —
its behavior has only ever been exercised via `atest`
instrumentation/hardware rounds in prior tasks. Per the brief's own
instruction for exactly this situation, this is stated explicitly rather
than skipped silently: verification of both the marker and the reordering
is deferred to a later dynamic-validation task (the plan's Task 13), which
must exercise the seamless-unlock success path, the pre-`OK` authorization
failure path, and — deliberately — the post-`OK` `switchToUserSilently`/
`quiesceUserApps` failure paths this task's own restructuring newly makes
reachable without changing the response.

**Open question, not resolved here:** whether `switchToUserSilently` or
`quiesceUserApps` can genuinely fail for a real, correctly-typed decoy
target in practice, or whether the two `DecoyRoutingFailureMarker` reason
codes are defensive-only paths that will never actually fire on real
hardware. Nothing in this task's own source reading settled this either
way — `switchToUserSilently`'s own failure modes were not re-traced here,
and `quiesceUserApps`'s failure surface was previously explored only in
the context of the earlier real-user-quiescing plan
(`docs/superpowers/specs/2026-08-27-duress-real-user-quiescing-and-return-design.md`),
not specifically for whether it can fail against a *decoy* target under
these exact new call-timing conditions (posted to a handler, after the
response already went out). Left as a real open question for Task 13's
dynamic validation to answer with evidence, not guessed here.

### `frameworks/base`: warm-start the decoy on keyguard appearance (`0054`-`0056`)

Task 10 of `docs/superpowers/plans/2026-09-07-duress-decoy-seamless-unlock.md`
(Component C's warm-start mechanism). Base commit:
`4ccf6785de23cf9cdebaa90ae3663a905e0bf61f` (`0053`'s own real post-`git am`
SHA, the real branch tip at fetch time). Fetched two files read-only from the
real tree and confirmed byte-identical (`sha256sum`, both ways) before
editing: `DecoyProfileHelper.java` and `LockSettingsService.java` (the latter
needed for one `systemReady()` wiring line, disclosed below — not in the
brief's own stated file list of `DecoyProfileHelper.java` only).

**Step 1 gate (source-verified before any code was written).** Starting a
user does not unlock it. Two real, quoted findings:

- `UserController.finishUserUnlocking` (services/core/java/com/android/server/am/UserController.java:923):
  `if (!mInjector.isCeStorageUnlocked(userId)) return false;` with the
  comment "If the user's CE storage hasn't been unlocked yet, we cannot
  proceed." A background-started user's boot sequence
  (`finishUserBoot` -> `maybeUnlockUser` -> `finishUserUnlocking`) cannot
  advance past `STATE_RUNNING_LOCKED` unless CE storage is already unlocked
  by some other means.
- The one automatic unlock attempt a background start does trigger,
  `LockSettingsService#unlockUserKeyIfUnsecured`
  (services/core/java/com/android/server/locksettings/LockSettingsService.java:2589-2612),
  bails out for any secured user: `if (isUserSecure(userId)) { Slogf.d(TAG,
  "Not unlocking CE storage for user %d yet because user is secured",
  userId); return; }`. It only completes an unlock for a user with
  `LockscreenCredential.createNone()` — i.e. no credential at all. A
  registered decoy always has its own credential (that credential is what
  the whole feature routes against), so this call is always a no-op for a
  decoy target.

Net: a warm-started decoy lands in, and stays in, `RUNNING_LOCKED` with CE
storage genuinely locked, until its own credential is verified through the
normal routing path. This was also confirmed empirically post-reboot on the
emulator (see "Dynamic validation" below) — a decoy warm-started at boot
showed `RUNNING_LOCKED` via `am get-started-user-state`, not
`RUNNING_UNLOCKED`, until its PIN was actually verified.

**Step 2, trigger point.** `UserManagerService` already registers a
`KeyguardManager.KeyguardLockedStateListener`
(`initializeAndRegisterKeyguardLockedStateListener`,
services/core/java/com/android/server/pm/UserManagerService.java:821-833),
but it is private, and conditionally registered/unregistered based on the
private-space auto-lock setting
(`setOrUpdateAutoLockPreferenceForPrivateProfile`) — not a stable signal
`DecoyProfileHelper` could depend on being active. The listener *type* and
registration pattern is real, public API
(`KeyguardManager#addKeyguardLockedStateListener`,
`SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE`, signature-protected, held implicitly
by `system_server`), so `DecoyProfileHelper` registers its own instance of
the same API directly, from `LockSettingsService#systemReady()` (mirroring
`UserManagerService`'s own choice to register from a post-construction
lifecycle point rather than from a constructor, since
`WindowManagerService` — `KeyguardManager`'s real backing — is not
guaranteed ready at `LockSettingsService` construction time).

- `2fa61e4` (local worktree SHA, superseded — see real branch SHAs below) —
  "aegisos: warm-start the registered decoy target when the keyguard
  appears"
  (`patches/frameworks-base/0054-aegisos-warm-start-the-registered-decoy-target-when-.patch`).
  Adds a `KeyguardManager.KeyguardLockedStateListener` field
  (`keyguardLockedStateListener`, a method reference to
  `onKeyguardLockedStateChanged`), a new package-private `onSystemReady()`
  that registers it via `new HandlerExecutor(getHandler())` (Task 1's
  handler, `lockSettingsService.mHandler` — so the callback itself, guard
  checks included, always runs on that handler, never inline on whatever
  thread `WindowManagerService`'s own dispatch originates from — this is
  what the brief's "never inline on a keyguard callback" requirement
  actually means here, and `HandlerExecutor` gives it directly rather than
  needing a second explicit `post()`), and
  `onKeyguardLockedStateChanged(boolean isKeyguardLocked)`: returns early
  unless `isKeyguardLocked`, returns early unless a decoy is registered
  (`DecoyProfileState.maybeGet`), returns early if `ActivityManagerInternal`
  is unavailable or the decoy is already running
  (`ami.isUserRunning(decoyUserId, 0)`), otherwise starts it in the
  background. No new log line contains "decoy", "duress", or "Aegis" —
  checked line by line.
- `9f2237d` (local worktree SHA, superseded) — "aegisos: wire the
  credential-routing warm start into systemReady"
  (`patches/frameworks-base/0055-aegisos-wire-the-credential-routing-warm-start-into-.patch`).
  One line in `LockSettingsService.systemReady()`:
  `decoyProfileHelper.onSystemReady();`, placed immediately after the
  existing `mBiometricDeferredQueue.systemReady(...)` call, matching that
  method's existing pattern of calling each owned helper's own
  `systemReady()`/`onSystemReady()` in turn. This is the one file beyond
  the brief's stated list (`DecoyProfileHelper.java` only) — disclosed, same
  as the `0052` precedent's own one-file deviation, and for the same reason
  (no other call site exists to trigger registration at the right lifecycle
  point).
- `85c247c` (local worktree SHA, superseded) — "aegisos: fix the warm start to
  use the real background-start API"
  (`patches/frameworks-base/0056-aegisos-fix-the-warm-start-to-use-the-real-backgroun.patch`).
  **A real bug caught by this task's own build-before-landing discipline,
  not by review.** The first `m -j6 systemimage` attempt against `0054`
  failed to compile: `ActivityManagerInternal` (used everywhere else in
  this class for `authorizeDecoySwitchKeyguardSkip`/`switchToUserSilently`/
  `quiesceUserApps`) has no plain `startUserInBackground(int)` method — only
  `startUserInBackgroundTemporarily` (auto-stops the user; wrong shape).
  The line that actually exists on that class, misread from an
  `IActivityManager.aidl` grep result during Step 1's own research and
  wrongly attributed to `ActivityManagerInternal`, does not exist there at
  all. Root-caused against real source and fixed: the correct primitive is
  `IActivityManager#startUserInBackground`, obtained via
  `ActivityManager.getService()` — the exact same pattern
  `UserManagerService` itself already uses for a different background-start
  case (pre-created-user initialization,
  services/core/java/com/android/server/pm/UserManagerService.java:6773-6777,
  "starting pre-created user"). `RemoteException` is caught and logged
  (`Slog.w(TAG, "Failed to start background credential-routing target",
  e)` — clean of banned words) since the AIDL method signature declares it,
  even though this is an in-process call. Per the project's own git
  discipline (never amend a commit already landed on the real branch via
  `git am`; land a fix as a new commit instead), this was landed as a third
  patch on top of `0054`/`0055` rather than rewriting either. A second
  `m -j6 systemimage` after this fix succeeded (see "Build verification"
  below), and `dexdump` on the resulting `services.jar` confirmed the
  `keyguardLockedStateListener` field is genuinely present in the compiled
  `DecoyProfileHelper` class (its lambda target method itself is
  R8-synthesized into a `$r8$lambda$...` name, standard method-reference
  desugaring, not evidence of missing code).

**Real branch SHAs**, confirmed via `git log --format="%H %s"` on the actual
branch after each `git am` (never assumed from the apply output or the
local worktree's own commit SHAs):

```
fdeea399025c6d79a182ceb819f5f34d427285bc  aegisos: fix the warm start to use the real background-start API
bf50621534ff5aa80daf859a84a6429bba6721d8  aegisos: wire the credential-routing warm start into systemReady
94725c5aa8ca63ce24d4bde9403629d46702dccb  aegisos: warm-start the registered decoy target when the keyguard appears
4ccf6785de23cf9cdebaa90ae3663a905e0bf61f  aegisos: mint the decoy switch authorization before replying, switch after (0053, base)
```

(Confirmed via `git log --format="%H %s"` on the real branch, not assumed
from `git am`'s own apply output, and re-confirmed unchanged immediately
before and after the build.) No other patch landed on the tree between base and `fdeea399025c`, and no
build was ever in flight at any `git am` invocation (confirmed via `pgrep`
for `ninja`/`soong_ui`/`soong_build` each time).

**Build verification.** First `m -j6 systemimage` (against `0054`+`0055`
only) failed to compile — see the `0056` bug description above. Second
`m -j6 systemimage` (against all three patches) succeeded:

```
ninja: 5m51.57s Build Succeeded: 342 steps - 0.97/s
#### build completed successfully (05:58 (mm:ss)) ####
```

(342 steps, not the ~5000+ of a from-scratch build, because most of the
tree was already built from the immediately-preceding `0051`-`0053` build in
the same session — this build only needed to redo `services.core`/
`services` and their downstream dexpreopt/image-assembly steps.)
`OFFICIAL_BUILD=true` was set (this is the `sdk_phone64_x86_64` preflight
emulator tree, not a `lynx` device build, so the ADR-047 app-inclusion
check does not apply the same way, but it was set anyway as a
habit-reinforcing default).

**Reproducibility.** Regenerated fresh via `git format-patch
4ccf6785de23..fdeea399025c` directly on the real post-`git am` branch (not
trusted from the local worktree's own patch generation), then independently
verified: applied the three fresh patches via `git am` to a disposable
local clone seeded at the real base commit's own content of both touched
files (`git show 4ccf6785de23:<path>` on the real server, not a locally
cached copy), and diffed each of the two resulting files against the live
server tree directly via `sha256sum` — both matched exactly
(`DecoyProfileHelper.java`, `LockSettingsService.java`).

**Dynamic validation (emulator, not just compile-time).** The stale Task-4
emulator instance (PID unchanged since 2026-09-07, `adb shell` returning
"error: closed") was killed and a fresh `-wipe-data` boot was launched
against the freshly built `out-preflight` image. Using the project's
established `pm create-user --user-type
org.aegisos.usertype.full.DECOY`/`cmd lock_settings set-pin`/`cmd
lock_settings set-decoy-profile --owner-credential`/`cmd lock_settings
verify --old <pin> --user 0` test sequence (same pattern as
`docs/superpowers/plans/2026-08-27-duress-real-user-quiescing-and-return.md`'s
own Step 2-3, with synthetic test PINs, not the owner's real credential):

- Confirmed, via `dumpsys lock_settings` (`CredentialType: PIN` for the
  decoy) and `am get-started-user-state`, that a warm-started decoy really
  does sit in `RUNNING_LOCKED` after a fresh reboot (not
  `RUNNING_UNLOCKED`) until its own PIN is verified — the empirical
  confirmation of the Step 1 source finding above. (An earlier same-boot
  test without an intervening reboot showed the decoy reaching
  `RUNNING_UNLOCKED` almost immediately after being warm-started; this
  turned out to be a test-methodology artifact, not a code defect — a
  newly-created user's CE key is generated and already resident in the
  kernel keyring at creation time within the same boot, and only becomes
  genuinely re-locked across a following reboot. Re-run with a reboot
  between decoy registration and the warm-start check, which produced the
  result above.)
- Measured PIN-to-landing latency via real logcat timestamps (device
  clock, `UserController.UserState` transition lines and
  `reportOnUserCompletedEvent`), comparable in spirit to ADR-059's
  methodology but not identical to it (ADR-059 used a temporary
  `IUserSwitchObserver` instrumentation build on physical `lynx` hardware;
  this round used the emulator's own existing log lines, since adding and
  removing a second temporary instrumentation patch was judged
  disproportionate for one task's own validation round):
  - **Cold** (decoy never started before the credential check; n=3, fresh
    decoy user per trial, no intervening keyguard toggle): CE-storage-
    unlocked reached at 3507ms / 2011ms / 2643ms after the credential call
    (mean ≈2720ms); full switch-complete
    (`reportOnUserCompletedEvent`) at 8935ms / 7297ms / 8076ms (mean
    ≈8103ms).
  - **Warm** (decoy already `RUNNING_LOCKED` via this task's mechanism
    before the credential check; n=2, each requiring a fresh reboot since
    a decoy that has ever been unlocked stays resident for the rest of
    that boot): CE-storage-unlocked reached at 433ms / 618ms after the
    credential call (mean ≈526ms, **~5.2x faster** than cold); full
    switch-complete at 6133ms / 6310ms (mean ≈6222ms, **~1.3x faster**,
    ≈1.9s saved).
  - Honest reading: the warm start delivers a large, real cut specifically
    in the sub-step it targets (getting a not-yet-started decoy to
    `RUNNING_LOCKED`/CE-unlockable), and a smaller but still real
    ≈1.9-second cut in the full, holistic PIN-to-landing figure — the
    remaining time is dominated by broadcast dispatch and other observers
    (e.g. a logged "User switch slowed down by observer #1
    WallpaperManagerService: result took 918ms") that this task does not
    and cannot address. Small sample size (n=2 for warm, each costing a
    full reboot cycle) is disclosed rather than overstated as a precise
    ratio.
  - This is an emulator round, not physical `lynx` hardware — absolute
    figures are not expected to match ADR-059's physical-device numbers
    and are not claimed to.

**Test coverage — explicitly none.** No unit test was written for the new
`onSystemReady()`/`onKeyguardLockedStateChanged` methods, for the same
reason as every other `DecoyProfileHelper` change in this package: no
practical seam exists without mocking `LockSettingsStorage` and
`ActivityManagerInternal`, and none of this file's prior tasks have unit
coverage either.

### `frameworks/base`: stop the unused warm-started decoy (`0057`)

Task 10's own closing review flagged a real Important finding on the
`0054`-`0056` work above: `onKeyguardLockedStateChanged` warm-starts the
decoy on essentially every keyguard lock but has no matching stop call
anywhere. Since `config_multiuserMaxRunningUsers = 3` is well above the two
users actually running (owner + decoy), AOSP's own excess-user eviction
never reclaims it either — a decoy warm-started on the very first lock
after boot stays `RUNNING_LOCKED` for the rest of that boot session even
when it is never switched to, directly undermining warm-start's own reason
for existing over a permanently resident decoy (avoiding continuous
battery/RAM cost).

Base commit: `fdeea399025c6d79a182ceb819f5f34d427285bc` (`0056`'s own real
post-`git am` SHA, the real branch tip at fix time). Same two files as
`0054`-`0056`: `DecoyProfileHelper.java`, `LockSettingsService.java`.

- `b25147c` (local worktree SHA, superseded — see real branch SHA below) —
  "aegisos: stop an unused warm-started decoy on real credential match"
  (`patches/frameworks-base/0057-aegisos-stop-an-unused-warm-started-decoy-on-real-cr.patch`).
  Adds `DecoyProfileHelper#maybeStopUnusedWarmDecoy()`, called from
  `LockSettingsService#doVerifyCredentialInner` immediately after the real
  owner's own primary credential is confirmed matched for
  `UserHandle.USER_SYSTEM` under `LockDomain.Primary` (the one line added
  to `LockSettingsService.java`, mirroring the `0055` precedent's own
  one-file-beyond-`DecoyProfileHelper.java` disclosure). The new method
  posts to the handler (Task 1's `lockSettingsService.mHandler`, matching
  `maybeRouteToDecoy`'s own `getHandler().post(...)` pattern) and, if the
  registered decoy is currently running but is not the current foreground
  user, stops it via `IActivityManager#stopUserWithCallback(userId, null)`
  obtained from `ActivityManager.getService()` — confirmed against real
  source (`frameworks/base/services/core/java/com/android/server/pm/
  UserManagerService.java:2467`, `stopUserForQuietMode`) as the exact same
  real API and calling pattern `UserManagerService` itself already uses to
  stop a background user cleanly, rather than inventing a new mechanism.
  `RemoteException` is caught and logged (`Slog.w(TAG, "Failed to stop
  unused warm-started credential-routing target", e)` — clean of banned
  words), the same defensive shape as `0056`'s own `startUserInBackground`
  call.

  **Cannot race or interrupt an active decoy session, by construction and
  independently by an AOSP-enforced guard.** By construction: a live
  session on the warm-started target is only ever reached via
  `performDecoySwitch`, which runs when `maybeRouteToDecoy`'s own
  `doVerifyCredentialInner` call matched the *decoy's* credential; this new
  method instead runs when the *real owner's* credential matched. Those are
  two mutually exclusive outcomes of the same verify call for
  `UserHandle.USER_SYSTEM` — one credential cannot satisfy both, so the new
  stop path and a freshly switched-to session are never both reachable from
  the same credential check. Independently, even setting that reasoning
  aside: the method's own current-foreground-user check
  (`ActivityManager.getCurrentUser() == state.decoyUserId`) refuses to stop
  a foreground target, and confirmed against real source
  (`frameworks/base/services/core/java/com/android/server/am/
  UserController.java:1345`, `stopUsersLU`), `if (isCurrentUserLU(userId))
  return USER_OP_IS_CURRENT;` is an unconditional, built-in AOSP refusal to
  stop whichever user is currently in the foreground — the real code
  eventually reached through `stopUserWithCallback` — so even a caller-side
  logic error could not tear down an active decoy session.

**Real branch SHA**, confirmed via `git log --format="%H %s"` on the actual
branch after `git am` (never assumed from the apply output or the local
worktree's own commit SHA):

```
bd9074868224cef7c5f0506797a2e79c9bc22567  aegisos: stop an unused warm-started decoy on real credential match
fdeea399025c6d79a182ceb819f5f34d427285bc  aegisos: fix the warm start to use the real background-start API (0056, base)
```

No build was in flight at the `git am` invocation (confirmed via `pgrep`
for `ninja`/`soong_ui`/`siso` immediately before).

**Build verification.** `m -j6 systemimage` against the full tree including
`0057`:

```
ninja: 5m50.29s Build Succeeded: 501 steps - 1.43/s
#### build completed successfully (20:12 (mm:ss)) ####
```

`dexdump` on the resulting `services.jar`
(`out-preflight/soong/.intermediates/frameworks/base/services/services/
android_common/dex/services.jar`) confirmed `maybeStopUnusedWarmDecoy` is
genuinely present in the compiled output (2 references: declaration and
call site) — not just a clean exit code.

**Reproducibility.** Regenerated fresh via `git format-patch
fdeea399025c..bd9074868224` directly on the real post-`git am` branch (not
trusted from the local worktree's own patch generation) — the only
differences from the local worktree's own export were the commit SHA, the
git-version-dependent blob-hash abbreviation length, and the trailing `git
version` footer line, confirmed via a direct `diff` of both patch files.
Independently verified: applied the fresh patch via `git am` to a
disposable local clone seeded at the real base commit's own content of both
touched files (`git show fdeea399025c:<path>` on the real server, not a
locally cached copy), then diffed each of the two resulting files against
the live server tree directly via `sha256sum` — both matched exactly
(`DecoyProfileHelper.java`, `LockSettingsService.java`).

**Dynamic validation.** Not performed for this fix specifically — deferred
to Task 13's consolidated dynamic validation round, consistent with this
plan's own established batching precedent (`0054`-`0056`'s dynamic round
already happened before this review finding was raised; a second one-off
emulator round for a single-method fix was judged disproportionate ahead of
the task's own dedicated validation task). Compile-time verification
(build success + `dexdump` presence) is the only verification performed
here — this is disclosed, not implied to be more than it is.

**Test coverage — explicitly none.** Same rationale as `0054`-`0056`: no
practical unit-test seam exists without mocking `LockSettingsStorage` and
`ActivityManagerInternal`.

### `frameworks/base`: split the decoy switch authorization into two grants (`0058`-`0060`)

Task 11's own hardware validation found the `0045`-`0050` one-shot
authorization store (which lets `UserController`'s own internal switch path
skip its forced keyguard lock for exactly one authorized decoy switch) is
not the whole fix: `KeyguardViewMediator.handleUserSwitchComplete()` in
SystemUI, a separate and independent mechanism, unconditionally re-arms a
live bouncer roughly 500ms after the switch completes, since nothing tells
it the switch was already authorized. A follow-up investigation
(`task-11-fix-report.md`) confirmed no existing AOSP signal is safe to
repurpose for this (see that report's "Avenue 1") and that a naive second
consumer of the existing single-slot store would always find it already
spent by `UserController`'s own internal consumption, which fires strictly
before SystemUI ever gets a chance to ask (see "Avenue 2"). This closes
that gap at the primitive level: it extends the one mint to pay out two
independently-trackable single-use grants instead of one, so a future task
can wire SystemUI's own consumption point without either consumer starving
the other. **This task only extends and tests the primitive — no SystemUI
call site is wired to the new method yet; that is explicit follow-up
work.**

Base commit: `bd9074868224cef7c5f0506797a2e79c9bc22567` (`0057`'s own real
post-`git am` SHA, the real branch tip at fix time).

- `36aa82e` (local worktree SHA, superseded — see real branch SHA below) —
  "test: add coverage for dual-grant decoy switch authorization consumption"
  (`patches/frameworks-base/0058-test-add-coverage-for-dual-grant-decoy-switch-author.patch`).
  RED: adds 8 new tests to `UserControllerTest.java` exercising a
  not-yet-existing `consumeDecoySwitchAuthorizationForBouncerSkip(int)`
  method — both grants consumed exactly once, independently, from a single
  mint, in both possible orders; expiry evaluated against the mint's own
  timestamp rather than reset per grant; the ADR-061 zero-mint property
  re-proved for the new consume path; cross-user isolation for the new
  grant; and a re-mint resetting both grants. Does not touch or weaken any
  of Task 6's 5 or Task 7's 2 existing tests.

- `22516dd` — "test: pass LockSettingsStorage mock to LockSettingsShellCommand
  constructor" (`patches/frameworks-base/0059-test-pass-LockSettingsStorage-mock-to-LockSettingsSh.patch`).
  **Unrelated pre-existing compile break, found by this task's own RED run,
  fixed as its own distinct commit** — the same pattern Task 7 used for its
  own `ActivityManagerInternal` mock-registration fix. `LockSettingsShellCommand`'s
  production constructor gained a required `LockSettingsStorage` parameter
  somewhere in the `set-decoy-profile`/`check-credential-routing-status`
  shell-command work (`0051`-`0053`), but `LockSettingsShellCommandTest`'s own
  constructor call was never updated, breaking compilation of the whole
  `FrameworksServicesTests` module regardless of any of this task's own
  changes — confirmed unrelated via `git log` on the touched file (last
  touched by `4d4ba9bbb654`, "add check-credential-routing-status shell
  command"). Test-only: adds a `@Mock LockSettingsStorage` field and passes
  it as the constructor's 5th argument. No production code touched.

- `2845aa0` — "aegisos: split the decoy switch authorization into two
  single-use grants" (`patches/frameworks-base/0060-aegisos-split-the-decoy-switch-authorization-into-tw.patch`).
  GREEN. `UserController.java` changes:
  - Two new `@GuardedBy("mLock")` fields, `mForcedLockSkipConsumed` and
    `mBouncerSkipConsumed`, alongside the existing
    `mSwitchAuthorizedUserId`/`mSwitchAuthorizedAtMillis`.
  - `authorizeDecoySwitchKeyguardSkip` (mint, unchanged signature/type-check)
    now also resets both bits to `false` on every mint.
  - `consumeDecoySwitchAuthorization` (Task 7's existing, unchanged name and
    external true/false/expiry semantics for its own caller) now delegates
    to a new private `consumeGrantLocked(userId, forcedLockGrant)` helper
    instead of clearing the whole record itself.
  - New `consumeDecoySwitchAuthorizationForBouncerSkip(int userId)` —
    `@VisibleForTesting`, not yet called from anywhere — delegates to the
    same shared helper with `forcedLockGrant=false`.
  - `consumeGrantLocked`: matches `userId`, short-circuits to `false` if
    that specific grant is already consumed (without touching the other
    grant), computes `ageMillis` once against the mint's own timestamp,
    marks the named grant consumed, and fully clears the whole record
    (both bits, `mSwitchAuthorizedUserId` back to `USER_NULL`) the instant
    either both grants are consumed or this call finds the mint already
    expired — whichever happens first. This is why expiry is a property of
    the mint, not tracked per grant: a grant redeemed for the first time
    after the window has closed still returns `false` even if the other
    grant was already redeemed successfully within the window, and the
    full-clear on any expired hit prevents a stale, half-consumed record
    from lingering indefinitely.

  **Why this cannot create a new bypass.** No new minting path exists —
  `authorizeDecoySwitchKeyguardSkip` is otherwise byte-identical to Task 6's
  landed version, including its own unconditional, uncached
  `UserManagerInternal.getUserInfo(targetUserId).userType` check against the
  real decoy type on every single call. Both consume methods are pure
  readers/mutators of already-minted state; neither can manufacture a grant
  for a user that was never genuinely mint-authorized moments earlier by a
  real credential match. The two grants are strictly less permissive than a
  single reusable flag would be: each is single-use, both derive from the
  same mint (so there is no way to accumulate multiple pending
  authorizations for different switches — a fresh mint always overwrites
  the prior record, resetting both bits), and the new bouncer-skip method is
  not wired to any caller yet, so it currently has zero reachable attack
  surface beyond what Task 6/7 already established.

**Real branch SHA**, confirmed via `git log --format="%H %s"` on the actual
branch after `git am` (never assumed from the apply output or the local
worktree's own commit SHA):

```
677b6053207208736476d09e70ff58ed3805f25f  aegisos: split the decoy switch authorization into two single-use grants
35e81a432723727554499ce2c9856ab0c34565d5  test: pass LockSettingsStorage mock to LockSettingsShellCommand constructor
9dadf4735964dc14394c773c51e17b6fd479096c  test: add coverage for dual-grant decoy switch authorization consumption (0058, base)
```

No build was in flight at either `git am` invocation (confirmed via `pgrep`
for `ninja`/`soong_ui` immediately before each).

**RED confirmed via real `atest`.** First RED attempt failed to compile on
the unrelated `LockSettingsShellCommandTest` constructor mismatch above,
before ever reaching this task's own new tests — fixed by `0059`, landing
before this task's own `0058` result could be trusted. Second RED attempt,
after `0059` landed:

```
frameworks/base/services/tests/servicestests/src/com/android/server/am/UserControllerTest.java:2735: error: cannot find symbol
  symbol:   method consumeDecoySwitchAuthorizationForBouncerSkip(int)
  location: variable mUserController of type UserController
[... 11 more identical errors, one per new test's use of the not-yet-existing method ...]
12 errors
```

Exactly the expected RED shape: every new test's own reference to the
not-yet-implemented method, nothing else.

**GREEN could not be captured via real `atest` this session — a real,
disclosed verification gap, not a silent skip.** After `0060` landed, 8
consecutive `atest`/`m` invocations against this exact module all failed
before ever reaching this task's own compiled test code, alternating
between two distinct native-JVM failure signatures in the unrelated
`services.core.unboosted` Soong module (a `jarjar` step throwing
`NullPointerException: Deflater has been closed`, and a `javac` worker
crashing with `SIGBUS`) — both consistent with the disk-space/resource
lessons already recorded in `docs/research/build-and-signing-engineering-
notes.md` and `AGENTS.md`. Root-caused as far as read-only access allowed:
raising the shell's open-file `ulimit` and overriding `TMPDIR`/
`JAVA_TOOL_OPTIONS` changed which failure appeared but never fixed it;
`df`/`df -i` on every filesystem in the chain (`/srv`, `/tmp`, `/dev/shm`)
showed free space and free inodes throughout, and a direct 2GB test write
into the exact failing output directory succeeded instantly, ruling out a
literal, user-visible disk-full condition. The one confirmed environmental
fact is a concurrently running `qemu-system-x86_64-headless` emulator
process (not started by this task, still healthy and `adb`-responsive, not
killed) sustaining ~80% CPU and ~4.5GB RSS throughout every attempt on this
project's own documented narrow-margin 8-vCPU/31GB-RAM host — the leading
hypothesis is memory-pressure-driven exhaustion of a Soong/Siso build
action's own sandboxed scratch space, not a literal full disk, but this was
not confirmed with certainty given read-only, non-root access to the guest
VM and no host-level visibility. **Neither hypothesis nor the failures
themselves reference any file this task touched** — the crash point is
always in `services.core.unboosted`'s own build graph, before any of this
task's two touched files are even reached.

In place of a real `atest` GREEN run, a full manual trace of the
implementation against all 15 relevant tests (Task 6's 5, Task 7's 2, this
task's 8 new ones) was performed line-by-line against the landed
`consumeGrantLocked` code, following this project's own Task 6 precedent
for exactly this situation (see that task's own report for the same
disclosed gap). The full-class regression comparison against the
`unquiesceUserApps` pre-existing-failure baseline Task 7 established (28
failures) could likewise not be run this session for the same reason.

**Reproducibility.** Regenerated fresh via `git format-patch
bd9074868224..677b605320` directly on the real post-`git am` branch (not
trusted from the local worktree's own patch generation). Independently
verified: applied the fresh three-patch set via `git am` to a disposable
local clone seeded at the real base commit's own content of all three
touched files (`git show bd9074868224:<path>` on the real server, not a
locally cached copy), then diffed each of the three resulting files against
the live server tree directly via `sha256sum` — all three matched exactly
(`UserController.java`, `UserControllerTest.java`,
`LockSettingsShellCommandTest.java`).

**Test coverage.** 8 new tests in `UserControllerTest.java`, confirmed RED
via real `atest`; GREEN not confirmed via `atest` this session for the
environmental reasons above, only via manual trace.

### `frameworks/base`: wire the bouncer-skip grant into SystemUI's keyguard mediator (`0061`-`0064`)

Closes the gap `0058`-`0060`'s dual-grant primitive was built for but left
unwired: `KeyguardViewMediator.handleUserSwitchComplete()`
(`packages/SystemUI/src/com/android/systemui/keyguard/`, stock AOSP,
untouched by any earlier AegisOS patch) unconditionally schedules a
`dismiss()` call 500ms after any switch to any secure user, which shows a
live bouncer. `0061` bridges `UserController`'s existing package-private
`consumeDecoySwitchAuthorizationForBouncerSkip(int)` (landed inert by
`0060`) across the process boundary to SystemUI, mirroring `0050`'s own
`authorizeDecoySwitchKeyguardSkip` bridge shape exactly: an abstract
`ActivityManagerInternal` method with pure single-statement delegation in
`ActivityManagerService`'s `LocalService`, a new method appended to the end
of `ILockSettings.aidl` (an ordinary, unversioned internal system AIDL --
confirmed no `@VintfStability`/frozen `aidl_api` directory exists for it,
so appending at the end was safe), a pure-delegation implementation in
`LockSettingsService` gated by the same `checkHavePermission()`
(`ACCESS_KEYGUARD_SECURE_STORAGE`) already required for `isSecure()`, and
an uncached `LockPatternUtils` wrapper that fails closed (returns `false`,
same as if never minted) on any `RemoteException`. `0063` is the one-line
consumer: `KeyguardViewMediator.handleUserSwitchComplete()`'s existing
`if (mLockPatternUtils.isSecure(userId))` gains a second, `&&`-chained
condition, `!mLockPatternUtils.consumeDecoySwitchAuthorizationForBouncerSkip
(userId)` -- the original condition, and every other line in the method, is
byte-identical to stock AOSP. No new log string contains
`decoy`/`duress`/`Aegis` anywhere in this patch set.

`0062` adds two new `KeyguardViewMediatorTest` cases (a real, reachable
Mockito-based unit-test seam exists for this class --
`SystemUITests:com.android.systemui.keyguard.KeyguardViewMediatorTest`,
`mLockPatternUtils` is already a `@Mock` field): one proves the authorized
case (`consumeDecoySwitchAuthorizationForBouncerSkip` mocked `true`) means
`dismissAndCollapse()` is never called even after the same 500ms
`TestableLooper` time-advance the pre-existing
`testUserSwitchToSecureUserShowsBouncer` test uses; the other explicitly
re-proves the ADR-061 property at this layer (mocked `false` still shows
the bouncer), independent of relying on Mockito's own unstubbed-boolean
default. `0064` is an unrelated, pre-existing compile break this task's own
RED run found blocking 100% of the `SystemUI-tests` Soong module: `0025`
(the Wallet-tile ADR-065 fix, already landed, see the "treat decoy as
main-user-equivalent" section above) added a `UserManager` constructor
parameter to `QuickAccessWalletTile` but never updated
`QuickAccessWalletTileTest.java`'s own constructor call -- fixed exactly
the way `0059`/Task 7's own `865e3c4`/`6e2adc2a3406` fixed the same class
of gap: add a `@Mock UserManager mUserManager` field and pass it as the
14th constructor argument, no production code touched.

**Real `atest` RED/GREEN.** RED captured against a real device: with only
`0061`/`0062`/`0064` applied (no `0063`), both new
`KeyguardViewMediatorTest` cases failed with
`java.lang.NoSuchMethodError: No virtual method
consumeDecoySwitchAuthorizationForBouncerSkip(I)Z in class
Lcom/android/internal/widget/LockPatternUtils;` -- the emulator's
currently-loaded `framework.jar` predated the method entirely, confirming
the test infrastructure and mocks are wired correctly and would fail loudly
without the fix. A full `m -j6 systemimage` build (2819 steps, 1h5m,
succeeded) was needed to bake the new method into `framework.jar` before a
real GREEN `atest` run was practical within this session's remaining time;
GREEN was instead confirmed by real device dynamic validation (below),
which is strictly stronger evidence for this exact behavior than a unit
test could be.

**Real dynamic validation (not just a build).** Fresh `-wipe-data
-no-snapshot-load` boot of the rebuilt image, decoy and an ordinary
secondary user both registered, two independent full-reboot trials driving
the decoy's own PIN through the real on-screen bouncer keypad (`adb shell
input tap`, matching Task 11's own established real-input method). Both
trials: the switch to the decoy completed within ~1s
(`am get-current-user`), and polling `uiautomator dump` once per second for
10-12s afterward found no bouncer auto-appearing -- confirmed decisively via
`logcat`, which shows `KeyguardViewMediator: onUserSwitchComplete 10`
followed immediately by `adjustStatusBarLocked` and *no* subsequent
`DISMISS` message anywhere in the log (Task 11's own trace, by contrast,
showed a `DISMISS` message queued exactly 518ms later). The ADR-061
regression control was run on the same live device: after genuinely
unlocking user 0's real credential (`cmd lock_settings verify --user 0
--old <owner PIN>`, required first since `SWITCHABILITY_STATUS_
SYSTEM_USER_LOCKED` blocks any `am switch-user` call while user 0 is
locked), `am switch-user <ordinary secondary user>` showed a live "Enter
PIN" bouncer within 1 second with *no* swipe needed -- the general
mechanism is confirmed still fully intact and unaffected by this patch set
for any non-decoy switch.

**One further, honest finding, not fixed by this patch set.** After the
authorized decoy switch, the device lands on an ordinary swipeable lock
screen (clock/date, "Unlock for all features and data") rather than
directly on the home screen; swiping up reveals a *fresh* live PIN bouncer
requiring the decoy's PIN a second time (confirmed it accepts the same PIN
and reaches the real decoy home screen correctly when re-entered). This is
a different, deeper gap than the one this patch set targets: `dismiss()`
was never itself an active "mark this session unlocked" call (per its own
source comment, calling it on a secure user *shows* the bouncer -- it never
silently bypasses one), and no mechanism anywhere carries the credential
verified in the *original* (pre-switch) bouncer instance over to mark the
decoy's own, freshly-bound keyguard session as dismissed. `task-11-fix-
report.md`'s own Avenue 2 scoped this patch set narrowly as "suppress the
unwanted automatic re-challenge," not "perform an active unlock" -- this
finding confirms that narrower scope is exactly what was delivered, no
more: the automatic, unprompted 500ms re-arm is gone, but the first manual
interaction with the decoy's own fresh session still meets a live
challenge. Recorded as an open item, not implemented here.

**Reproducibility.** Regenerated fresh via `git format-patch
677b60532072..d4a76bb5dc14` directly from the real post-`git am` branch
history. Independently verified: applied the fresh four-patch set via
`git am` to a disposable local clone seeded at the real base commit's own
content of all eight touched files (`git show 677b60532072:<path>` on the
real server), then diffed each of the eight resulting files against the
live server tree directly -- all eight matched byte-for-byte
(`ActivityManagerInternal.java`, `ActivityManagerService.java`,
`ILockSettings.aidl`, `LockPatternUtils.java`, `LockSettingsService.java`,
`KeyguardViewMediator.java`, `KeyguardViewMediatorTest.java`,
`QuickAccessWalletTileTest.java`).

**Real branch SHA**, confirmed via `git log --format="%H %s"` on the actual
branch after `git am` (never assumed from the apply output or the local
worktree's own commit SHA):

```
d4a76bb5dc14473136790aecff363e7d6ed3e039  test: pass UserManager mock to QuickAccessWalletTile constructor (0064)
ac8e5f1dc7a18d7c06b91aa3f4e4189d87c18660  aegisos: skip the re-armed bouncer for an authorized decoy switch (0063)
f6747e91e7a4afbaaf939f6487ecb3fcaa1e61a1  test: add coverage for skipping the re-armed bouncer on an authorized decoy switch (0062)
82c26aef3c5d26c4994af429960ea324a0b31f99  aegisos: expose the decoy switch bouncer-skip authorization to SystemUI (0061)
```

No build was in flight at either `git am` invocation (confirmed via `pgrep`
for `ninja`/`soong_ui`/`siso` immediately before each).

### `frameworks/base`: close Task 11b's two review findings (`0065`)

Task 11b (the `0061`-`0064` patch set above) was approved with two Important
findings still open, closed here.

**Finding 1 -- narrow exception handling broke the fail-closed pattern.**
`LockPatternUtils.consumeDecoySwitchAuthorizationForBouncerSkip(int)` only
caught `RemoteException` around the Binder call. Every other branch in this
chain fails closed (returns `false`) on any doubt -- null service, no minted
grant, expired grant, wrong user -- but a `SecurityException` from a
permission misconfiguration on the `LockSettingsService` side (its own
`consumeDecoySwitchAuthorizationForBouncerSkip` calls `checkHavePermission()`,
which can throw `SecurityException` via
`Context.enforceCallingOrSelfPermission`) would have propagated uncaught into
`KeyguardViewMediator`'s call site instead of degrading safely. `0065` widens
the catch to `RemoteException | SecurityException`, both fail closed
identically (return `false`, the bouncer is simply shown again -- never a
bypass), and updates the method's own doc comment to describe both cases.
Verified the real server-side implementation before scoping the fix (not
assumed): `LockSettingsService.consumeDecoySwitchAuthorizationForBouncerSkip`
is `checkHavePermission(); ... ami.consumeDecoySwitchAuthorizationForBouncerSkip(userId)`
with no other try/catch anywhere in the call chain back to
`KeyguardViewMediator.handleUserSwitchComplete()`, confirming the exception
really could have propagated uncaught.

**Finding 2 -- no real `atest` GREEN run after the full `systemimage`
rebuild.** `0061`-`0064`'s own GREEN evidence was real device dynamic
validation, not a unit-test run, because the rebuild needed to bake the new
Binder method into `framework.jar` didn't leave session time for a follow-up
`atest` pass. Closed here: after landing `0065`, a fresh emulator instance
was booted (`-wipe-data -no-snapshot-load`; the long-lived instance reused
from `0061`-`0064`'s own dynamic validation had lost its `/sdcard` FUSE mount
after repeated reboots, confirmed via a failed `adb shell df /sdcard` and a
failing test write before being discarded) and a real `atest` run executed
against it:

```
x86_64 FrameworksServicesTests: Passed: 15, Failed: 0, Ignored: 0, Assumption Failed: 0
x86_64 SystemUITests: Passed: 61, Failed: 0, Ignored: 0, Assumption Failed: 73
All tests passed!
```

`FrameworksServicesTests` ran the explicit 15-method list of Task 11a's
decoy/grant `UserControllerTest` coverage (the 13 methods matching
`*[Dd]ecoy*` plus `testForegroundSwitch_authorizedDecoy_doesNotForceKeyguard`
and `testForegroundSwitch_unauthorizedSecureUser_forcesKeyguard`, which don't
match that pattern by name) -- run as an explicit method list rather than the
whole class because the full class hits a pre-existing, unrelated crash
(`testStallUserSwitchUntilTheKeyguardIsShown`, an `unquiesceUserApps` NPE
from `0011`) that truncates the run before reaching every test. `SystemUITests`
ran the entire `KeyguardViewMediatorTest` class (134 executions = 67 methods
under two `animation_library_shell_migration` flag parameterizations, an
unrelated, pre-existing flag gate this patch set doesn't touch); the 73
`ASSUMPTION_FAILED` results are the flag-gated skips, not failures. The three
tests this fix set most directly targets --
`testUserSwitchToSecureUserShowsBouncer`,
`testUserSwitchToAuthorizedDecoyUserSkipsBouncer`, and
`testUserSwitchToUnauthorizedSecureUserStillShowsBouncer` -- were confirmed
individually as `PASSED` (not merely counted) under the flag variant that
actually executes on this build.

**Real branch SHA**, confirmed via `git log` on the actual branch after
`git am`:

```
f459f29b65f7490fa60d45341c316cc1afe37f4f  fix: fail closed on SecurityException in bouncer-skip authorization check (0065)
```

No build was in flight at the `git am` invocation (confirmed via `ps aux`
for `soong_ui`/`ninja`/`siso` immediately before). Reproducibility
independently verified: `0065` applied via `git am` to a disposable local
clone seeded at `d4a76bb5dc14`'s own content of `LockPatternUtils.java`
(`git show d4a76bb5dc14:<path>` on the real server), the resulting file
diffed byte-for-byte against the live server tree -- empty diff.

### `frameworks/base`: a third grant closes the manual-swipe double-PIN gap (`0066`-`0070`)

`0061`-`0065` closed the *automatic* 500ms bouncer re-arm after an authorized
decoy switch. Real touch-input validation of that fix (Task 11b) found a
second, distinct gap it never claimed to close: after the switch, the device
lands on an ordinary swipeable lock screen, and the user's first manual swipe
still raises a fresh, live PIN bouncer -- because nothing had ever told the
keyguard "this specific session's credential was already verified moments
ago." `task-11-fix-report.md`'s Avenue 2 had already ruled out repurposing
`KeyguardUpdateMonitor.getUserCanSkipBouncer`/trust-agent infrastructure for
this (duration-based grants, a visible "extend unlock" indication, and a
larger biometric-gating blast radius than this feature can absorb) and named
a third, independently-trackable grant as the concrete way forward without
touching that machinery.

**Root investigation.** Traced the real, ordinary same-user PIN-unlock path
end to end:
`KeyguardSecurityContainerController.showNextSecurityScreenOrFinish(authenticated,
targetUserId, ...)` is the one function that decides whether a bouncer
request finishes immediately or actually challenges the user. Critically, it
is called *before* any bouncer is rendered:
`PrimaryBouncerInteractor.show()` (the real chokepoint every swipe-to-bouncer
request in this build's legacy, non-scene-container bouncer goes through --
confirmed via `SceneContainerFlag.assertInLegacyMode()` at the top of that
method, and independently confirmed this build's `compose_bouncer` aconfig
flag has no `state: ENABLED` override, i.e. is off by default, matching Task
11's own real-device evidence of a View-based, not Compose, PIN keypad) calls
`primaryBouncerView.delegate?.showNextSecurityScreenOrFinish()`, whose real
implementation (`KeyguardBouncerViewBinder.kt`) is
`securityContainerController.dismiss(selectedUserInteractor.getSelectedUserId())`
-- which always calls through with `authenticated = false`. If this pre-check
already decides `finish = true`, the bouncer is never shown at all. This
function already has exactly this shape for two other signals --
`mUpdateMonitor.getUserHasTrust(targetUserId)` and
`getUserUnlockedWithBiometric(targetUserId)` -- both "this user's credential
was already satisfied by some other mechanism, don't challenge again." A
third, sibling branch keyed off this feature's own authorization primitive
(rather than trust/biometric plumbing) is architecturally the same shape,
without either's documented side effects. Confirmed this branch cannot
bypass a genuinely required SIM PIN/PUK screen or the secure-lock-device
second-factor check: both are evaluated downstream of *why* `finish` became
true, unconditionally, for every branch including the pre-existing
trust/biometric ones -- this patch set adds no exception to that.

**Why a third grant, not a second use of an existing one.** The
forced-lock-skip grant (`0045`-`0050`) is consumed synchronously during the
switch itself; the bouncer-skip grant (`0061`-`0064`) is consumed
deterministically ~500ms later in `KeyguardViewMediator`. Both are routinely
already spent by the time the user gets around to swiping (which can happen
seconds later, whenever they choose to interact). A second consumption
attempt against either existing grant would reliably find it already burned
and return `false` -- exactly the "looks correct, isn't" trap this project's
own `docs/superpowers/sdd/task-11-fix-report.md` had already identified and
declined to build without a proper design pass.

**`0066`/`0067`: `UserController` grows a third grant.** The existing
`mForcedLockSkipConsumed`/`mBouncerSkipConsumed` boolean-pair mechanism is
generalized to three booleans (`mForcedLockSkipConsumed`,
`mBouncerSkipConsumed`, `mActiveDismissConsumed`) behind a small private
`DecoySwitchGrant` enum, replacing the old `boolean forcedLockGrant`
parameter to `consumeGrantLocked`. `authorizeDecoySwitchKeyguardSkip`'s own
type-check against the real user registry (`UserManagerInternal.getUserInfo
(targetUserId).userType == USER_TYPE_FULL_AEGISOS_DECOY`) is byte-identical
to before -- this patch only adds a third field reset inside the same
`synchronized` block that already ran on every mint, and a third named
consume method, `consumeDecoySwitchAuthorizationForActiveDismiss(int)`,
`@VisibleForTesting` and package-private like its two siblings, with zero
production callers until `0068`/`0070` wire it. The clearing condition
(`mSwitchAuthorizedUserId` reset to `USER_NULL`, all three bits reset) now
requires all three grants consumed, or any redemption attempt finding the
mint already expired -- whichever comes first, exactly generalizing the
existing two-grant rule. `0067` is 8 new `UserControllerTest` cases mirroring
the existing dual-grant test shape (basic consume-once/wrong-id/expired/
never-minted for the new grant, plus four tri-grant interaction tests:
all-three-consumed-clears-the-record, the active-dismiss grant consumed
independently of the other two without disturbing them, expiry applying to
all three even when others were already consumed within the window, and a
re-mint resetting all three). All 7 pre-existing tests (5 from `0045`-`0050`,
2 from `0058`-`0060`) are untouched and were re-traced to confirm no
behavioral divergence -- none of them ever touch the new third bit.

**`0068`: the bridge, mirroring `0061`'s shape exactly.** Pure delegation,
zero decision logic, at every layer: an abstract
`ActivityManagerInternal#consumeDecoySwitchAuthorizationForActiveDismiss`,
a single-statement `ActivityManagerService.LocalService` override, one more
method appended to the end of `ILockSettings.aidl` (same ordinary,
unversioned internal AIDL as `0061`'s addition), a `LockSettingsService`
override gated by the same `checkHavePermission()`
(`ACCESS_KEYGUARD_SECURE_STORAGE`) `isSecure()` already requires, and an
uncached `LockPatternUtils` wrapper that fails closed on either
`RemoteException` or `SecurityException` from the start (the two-exception
catch `0065` had to add after the fact for the bouncer-skip grant is present
here from the first commit).

**`0069`/`0070`: the consumer.** `KeyguardSecurityContainerController
.showNextSecurityScreenOrFinish` gains one new `else if` branch, positioned
after the existing `getUserUnlockedWithBiometric` branch and before the
`SecurityMode.None` branch:
`!authenticated && mLockPatternUtils.consumeDecoySwitchAuthorizationForActiveDismiss(targetUserId)`.
The `!authenticated` guard is deliberate and stricter than the pre-existing
trust/biometric branches need: it scopes this grant to the no-bouncer-shown-
yet pre-check specifically (the only call shape `dismiss(int)` -- the real
swipe-time chokepoint -- ever produces), so it can never be reached from a
call where a real credential was just typed into an already-showing bouncer
for this or any other reason. No `eventSubtype`/`uiEvent` is set on this
branch (unlike the pre-existing ones) -- deliberately, so this mechanism
does not create a new, mechanism-identifying metric reachable from a decoy
session. `0069` adds three `KeyguardSecurityContainerControllerTest` cases:
the authorized case finishes without any credential (`dismiss(int)` returns
`true`, `viewMediatorCallback.keyguardDone` is invoked), the unauthorized
case re-proves the ADR-061 property at this layer (mocked `false`, or
Mockito's own default, still requires a real credential), and a third case
proves the grant does not bypass a genuinely required SIM PIN screen --
mirroring the pre-existing `showNextSecurityScreenOrFinish_calledWithNoAuthentication_butRequiresSimPin`
test's own shape exactly, for this grant instead of trust.

**Real `atest` RED/GREEN, both modules, no shortcuts.** RED (real device,
before `0070`): the 3 new `KeyguardSecurityContainerControllerTest` cases
failed with `java.lang.NoSuchMethodError: No virtual method
consumeDecoySwitchAuthorizationForActiveDismiss(I)Z in class
Lcom/android/internal/widget/LockPatternUtils;` -- the same "correctly fails
without the fix, against a stale on-device `framework.jar`" RED shape
`0061`-`0064` hit, not a logical-wiring RED in the narrower sense, but
equally valid: it also caught the *pre-existing*
`showNextSecurityScreenOrFinish_BiometricUnlockedWithSecondFactorEnabled_DoesNotFinish`
test regressing for the same reason (this new branch runs unconditionally on
every unauthenticated pre-check, so it is reached by that test too), and
that regression was independently re-confirmed to disappear post-rebuild
below. A full `m -j6 systemimage` build (2696 steps, 1h5m32s, succeeded) was
run this time before claiming GREEN -- not deferred to dynamic validation
alone the way `0061`-`0064` had to be. Real, final `atest` GREEN, both
modules, against the rebuilt image:

```
x86_64 FrameworksServicesTests: Passed: 23, Failed: 0, Ignored: 0, Assumption Failed: 0
x86_64 SystemUITests: Passed: 48, Failed: 0, Ignored: 0, Assumption Failed: 1
All tests passed!
```

`FrameworksServicesTests` ran the full 23-method decoy/grant
`UserControllerTest` list (the pre-existing 15 plus this patch set's own 8
new tri-grant tests) as an explicit method list, for the same pre-existing,
unrelated `unquiesceUserApps` full-class-truncation reason `0065` already
documented. `SystemUITests` ran the entire `KeyguardSecurityContainerControllerTest`
class (49 methods); the single `ASSUMPTION_FAILED`
(`dismissesKeyguard_whenSceneChangesToGone`) is an unrelated, pre-existing
scene-container flag gate this patch set never touches, not a failure. The
previously-regressed biometric-second-factor test now passes cleanly,
confirming the earlier RED was purely the stale-`framework.jar` artifact.

**Real dynamic validation, decoy case.** Fresh `-wipe-data -no-snapshot-load`
boot of the rebuilt image, decoy and an ordinary secondary user both
registered with disposable test PINs. Three independent, clean full-reboot
trials (plus a fourth, informative data point below) drove the decoy's own
PIN through the real on-screen bouncer keypad, then swiped immediately
afterward: all three showed the device landing directly on the decoy's real
home screen (`App Store`/`Camera`/`Gallery`/`Messaging`/`Phone`/`Vanadium`
icons, matching Task 11's own reference list exactly), `isKeyguardShowing`
`false`, and `mCurrentFocus` the launcher itself -- not `NotificationShade`.
`logcat` confirms the real, ordinary dismiss sequence fired
(`keyguardGoingAway requested for userId: <decoyUserId>` -->
`START_KEYGUARD_EXIT_ANIM`) with **no** `LockSettingsService: Verifying
primary lockscreen credential` line at the swipe timestamp -- the only two
such lines in the whole trial are the original PIN entry into the owner's
own bouncer, moments earlier. One earlier trial's swipe was delayed (by this
session's own multi-step polling/dumping between PIN entry and swipe, not by
anything in the mechanism) past the grant's 30-second window and correctly
fell back to a live "Enter PIN" bouncer -- a real, informative confirmation
of the fail-closed expiry design, not a defect: the grant genuinely expires
and genuinely requires the ordinary credential path once it has.

**Real dynamic validation, ADR-061 regression case.** On the same live
device: genuinely unlocked user 0 (`cmd lock_settings verify --user 0 --old
<owner PIN>`, required first, matching `0061`-`0064`'s own precedent since a
locked system user cannot be switched away from at all), then `am
switch-user <ordinary secondary user>` -- a switch this authorization was
never minted for. The automatic bouncer appeared within 1 second with no
swipe needed (the general `0061`-`0064` mechanism, unaffected). Additionally,
and specifically for this patch set's own new chokepoint: a `KEYCODE_HOME`
attempt followed by a swipe still produced a live "Enter PIN" bouncer, a
submitted wrong PIN (the *decoy's* own PIN, deliberately, to also rule out
any cross-contamination between the two mechanisms) was rejected with
`LockSettingsService: Verifying primary lockscreen credential for user
<ordinarySecondaryUserId>` logged and "Wrong PIN. Try again." shown, and
only the genuinely correct PIN unlocked it -- confirming the new
active-dismiss branch never fires, and never can fire, for a switch this
authorization was never minted for.

**Reproducibility.** Regenerated fresh via `git format-patch
f459f29b65f7..6031f0f82921` directly from the real post-`git am` branch
history, not the local worktree's own patch files. Independently verified:
applied the fresh five-patch set via `git am` to a disposable local clone
seeded at the real base commit's own content of all nine touched files
(`git show f459f29b65f7:<path>` on the real server), then diffed each of the
nine resulting files against the live server tree directly -- all nine
matched byte-for-byte (`UserController.java`, `UserControllerTest.java`,
`ActivityManagerInternal.java`, `ActivityManagerService.java`,
`ILockSettings.aidl`, `LockSettingsService.java`, `LockPatternUtils.java`,
`KeyguardSecurityContainerController.java`,
`KeyguardSecurityContainerControllerTest.kt`). The disposable verify clone
was deleted immediately after.

**Real branch SHA**, confirmed via `git log` on the actual branch after
`git am` (never assumed from the apply output or the local worktree's own
commit SHAs):

```
6031f0f82921475d58d075a3d6fcdd9347f27d61  aegisos: finish keyguard without re-authentication for an authorized decoy switch (0070)
5eec1087106c2e5a56bbfc46ace0b016aa060504  test: add coverage for the active-dismiss grant in the bouncer pre-check (0069)
ee03f17bdf5937240ec545afb1ae46e164e35aa0  aegisos: expose the decoy switch active-dismiss authorization to SystemUI (0068)
80f02ec10e9699f7228b9fc48c8d2e2252d4e489  aegisos: split the decoy switch authorization into three single-use grants (0067)
76d35e561b57b2448bd637b9b57c08a93d9be323  test: add coverage for the third (active-dismiss) decoy switch grant (0066)
```

No build was in flight at the `git am` invocation (confirmed via `ps aux`
for `ninja`/`soong_ui`/`siso` immediately before). Grepped the full diff,
every patch file, this section, and the report for `192\.168|beau@|beau-dev|
/srv/aegisos` -- no LAN/account coordinates. Grepped for new log call sites
containing `decoy`/`duress`/`Aegis` -- the one new `Log.e` call
(`"failed to check active-dismiss authorization for switch"`) is deliberately
worded without naming the mechanism, matching every prior patch in this
package.

**What remains open.** Six of the ADR-067/ADR-080 leak-audit's own narrower
items are still UNCHECKED (unchanged by this patch set, see the router). The
non-decoy-user identity-leak and pre-unlock-concealment work in this package
remains as previously recorded. This patch set closes exactly the
manual-swipe double-PIN gap Task 11b's own report left open -- it does not
revisit `credential_capture` (still disabled, still untouched by any patch
in this package) or attempt the "imperceptible transition" / "land directly
on the home screen with zero user interaction at all" framing from the
original design doc's Component C, which remains a materially larger,
separately-scoped SystemUI surface question.

### `frameworks/base`: an intervening switch invalidates outstanding decoy grants (`0071`-`0072`)

A review finding on `0066`-`0070`'s own active-dismiss grant identified a
real gap: because that grant is redeemed only on user-driven interaction (a
swipe), it -- and, more generally, either of the other two grants from the
same mint -- could remain outstanding for up to the full 30-second window
even after the other grants were already spent. Nothing invalidated the
outstanding grant(s) if a **second, distinct switch event** retargeted the
device to the same already-authorized decoy user id before the first
switch's own swipe consumed the active-dismiss grant -- for example,
switching away from the decoy and back again via a route that never goes
through `authorizeDecoySwitchKeyguardSkip`'s own real credential-verification
mint (the lock-screen avatar switcher is one known-unchecked surface from
prior audits; `am switch-user` over adb, if USB debugging is enabled, is
another). Narrower than ADR-061 -- it can only ever re-enter the same
already-once-authorized decoy session, within the bounded 30-second window,
and only by someone who already possesses/forced the decoy PIN once -- but
still a real widening of "one verified credential authorizes exactly one
thing."

**The fix.** Bind each grant's validity to the specific switch event it was
minted for, not just to the target user id. `UserController` gains a new
monotonically-incrementing counter, `mSwitchSequenceNumber`, advanced once
for every foreground switch actually initiated by `switchUser()` or
`switchToUserSilently()` (never for a no-op switch to the already-current
user, and never merely for a switch enqueued behind one already in
progress -- its own eventual dispatch, via `endUserSwitch`'s re-entrant
`switchUser()` call, is what actually advances the counter once it starts
for real). `authorizeDecoySwitchKeyguardSkip` records the counter's value at
mint time in a new field, `mSwitchAuthorizedAtSequence`.

The one subtlety: the real production order is *mint, then switch* --
`DecoyProfileHelper` mints synchronously (Task 8 of the seamless-unlock
plan, "mint before the reply, switch after it") and only afterwards posts
the actual `switchToUserSilently` call for the exact same target. This means
the switch-sequence number of "the switch this mint is for" genuinely isn't
known yet at mint time, so a naive "the sequence must never advance past the
value recorded at mint" rule would immediately invalidate every mint's own
legitimate switch the instant it starts, breaking the feature entirely. A
new boolean, `mAwaitingAuthorizedSwitch`, resolves this: true from the
moment a mint is made, it grants a single exemption to the first switch
initiated for that mint's own target -- consuming the exemption without
advancing the sequence -- then is permanently false until the next mint. Any
switch after that exemption is used, to any target including a return to the
exact same decoy id, advances the sequence like any other switch. This is
implemented once, in a new private `recordSwitchInitiatedLocked(int
targetUserId)`, called from both `switchUser()` and `switchToUserSilently()`
immediately after `mTargetUserId` is set (the point a switch is genuinely
initiated, as opposed to merely queued behind one already in flight).
`consumeGrantLocked` gains a second, independent validity gate alongside the
existing time-based expiry check -- `mSwitchAuthorizedAtSequence ==
mSwitchSequenceNumber` -- and a redemption is only honoured when both hold;
either failing clears the whole record exactly like an expired mint always
did (fails closed, single evaluation, no retry).

**Why not literally "record raw sequence at mint, require exact match
forever," as first considered.** That reading is exactly what
`mAwaitingAuthorizedSwitch`'s exemption prevents from breaking the normal
flow: without it, the mint's own authorized switch would always invalidate
itself, since it is itself a switch initiation that happens after the mint.
The exemption is scoped as narrowly as possible -- it is spent by the first
switch to the mint's own target and never granted again for that mint, so a
second switch to the same target (the literal reviewer scenario) is not
exempt and correctly invalidates.

**Self-review against the two specific risks named going in.** (1) Is the
sequence counter manipulable by an untrusted caller? No -- `switchUser()`
and `switchToUserSilently()` are both only reachable through existing,
already-permission-checked `ActivityManagerService`/`ActivityManagerInternal`
call paths; nothing new is exposed. The one new test-only entry point,
`noteForegroundSwitchInitiatedForTests`, is package-private and
`@VisibleForTesting`, exercised only by `UserControllerTest`. (2) Does
binding to "any switch" break the legitimate 3-consumer flow for the SAME
authorized switch? No -- none of the three consume methods themselves call
`switchUser()`/`switchToUserSilently()`; the only switch-initiation for an
authorized target during normal operation is `DecoyProfileHelper`'s own
single `switchToUserSilently` call, which is exactly the one exempted by
`mAwaitingAuthorizedSwitch`. All three grants remain independently
consumable across their normal ~0-30s window for that one switch, confirmed
by a dedicated test (`testDecoySwitchAuthorization_authorizedSwitchInitiationDoesNotInvalidateGrants`).

**Test coverage (`0071`, 5 new `UserControllerTest` cases, all 23 pre-existing
cases from `0045`-`0070` untouched and re-run alongside them).**
`testDecoySwitchAuthorization_authorizedSwitchInitiationDoesNotInvalidateGrants`
proves the normal single-switch flow is unaffected (mint, note the one
authorized switch, all three grants still consumable).
`testDecoySwitchAuthorization_interveningSwitchInvalidatesAllOutstandingGrants`
is the core gap-closure test: mint, note the authorized switch, note a
second switch to an unrelated user, then confirm all three grant types now
report `false`.
`testDecoySwitchAuthorization_switchAwayFromDecoyAndBackStillInvalidatesGrants`
reproduces the reviewer's literal scenario (switch away, then back to the
exact same decoy id) and confirms it still invalidates.
`testDecoySwitchAuthorization_expiryStillAppliesEvenWithNoInterveningSwitch`
and `testDecoySwitchAuthorization_interveningSwitchInvalidatesEvenWellWithinTimeWindow`
prove the two validity gates (expiry, sequence match) are each enforced
independently of the other.

**Real `atest` RED/GREEN.** RED, real device: `git am`'d only `0071`
(the test file) onto the branch and ran the 5 new tests --
`cannot find symbol: noteForegroundSwitchInitiatedForTests(int)`, a real
compile failure, since `0072` had not landed yet. GREEN, after landing
`0072` (no `m` rebuild needed to exercise `UserControllerTest` itself --
`atest` recompiled the touched module fresh): all 28 relevant
`UserControllerTest` methods (the 23 pre-existing decoy/grant cases plus
these 5 new ones) passed:

```
x86_64 FrameworksServicesTests: Passed: 28, Failed: 0, Ignored: 0, Assumption Failed: 0
All tests passed!
```

A full `m -j6 systemimage` build was then run to confirm the change
compiles into the actual system image, not just the isolated test module:
`Build Succeeded, 162 steps, 3m31s`. `grep -ac` on the resulting
`system.img` for `recordSwitchInitiatedLocked`/
`noteForegroundSwitchInitiatedForTests` found 2 occurrences, confirming both
new methods are genuinely present in the built output. This fix is
unit-test-level only; no dynamic (emulator or physical) re-validation was
performed, since none of the three grants' own consumer call sites or the
production switch call sites changed shape -- only their shared validity
check gained a second, independently-tested gate. If a future session
touches this mechanism again, a fresh consolidated hardware round (matching
this package's own established batching precedent) is the right place to
also confirm this fix dynamically, rather than a standalone repeat here.

**Reproducibility.** Regenerated fresh via `git format-patch
6031f0f82921..540d4a0366f2` directly from the real post-`git am` branch
history, not the local worktree's own patch files. Independently verified:
applied the fresh two-patch set via `git am` to a disposable local clone
seeded at the real base commit's own content of both touched files (`git
show 6031f0f82921:<path>` on the real server), then diffed each resulting
file against the live server tree directly -- both matched byte-for-byte
(`UserController.java`, `UserControllerTest.java`). The disposable verify
clone was deleted immediately after.

**Real branch SHA**, confirmed via `git log` on the actual branch after
`git am` (never assumed from the apply output or the local worktree's own
commit SHAs):

```
540d4a0366f2  aegisos: invalidate decoy switch grants on an intervening switch (0072)
7568e491414d  test: add coverage for intervening-switch invalidation of decoy grants (0071)
```

No build was in flight at either `git am` invocation (confirmed via `ps aux`
for `ninja`/`soong_ui`/`siso` immediately before each). Grepped the full
diff, both patch files, and this section for `192\.168|beau@|beau-dev|
/srv/aegisos` -- no LAN/account coordinates. Grepped for new log call sites
containing `decoy`/`duress`/`Aegis` -- none; this fix adds no new logging at
all, only internal counter/flag bookkeeping.

**What remains open.** Same as `0066`-`0070` above: the six UNCHECKED
leak-audit items, `credential_capture` (untouched), and the Component C
"imperceptible transition" framing are all unaffected by this patch set.
Additionally, this fix's own dynamic (non-unit-test) re-validation has not
been performed, as noted above.

### `frameworks/base`: return-path single-credential grant, and eliminating the swipe (`0073`-`0078`)

The owner asked to close the two usability gaps ADR-085 carried forward:
the decoy→owner return path demanding two full password entries, and a
swipe still required between the decoy PIN and the decoy's home screen.

**`0073`-`0074`: return-path grant.** `ReturnToRealUserHelper.
maybeRouteToRealUser` matched the real owner's credential and called a
bare `switchToUserSilently(USER_SYSTEM)`, with no authorization mint at
all -- unlike the outbound direction, which needed three single-use grants
across five sub-cycles (`0058`-`0070`) before a single credential entry
actually held. Added `UserController#authorizeRealUserSwitchKeyguardSkip
(int callerUserId)`, the return-direction mirror of
`authorizeDecoySwitchKeyguardSkip`: always targets `UserHandle.USER_SYSTEM`
and checks the *caller* is a genuine decoy-type user (the inverse of the
outbound check, same ADR-064-motivated rigor), then mints from the exact
same one-shot, three-grant record. Every existing
`consumeDecoySwitchAuthorizationFor*` call site is already generic on
target user id, so none needed to change. `ReturnToRealUserHelper` now
mints this authorization and, matching `DecoyProfileHelper`'s own
established pattern, defers the actual switch to the handler instead of
calling it inline, avoiding the same forced-`lockNow()`-vs-bouncer-
dismissal race `DecoyProfileHelper` already avoids. Six new
`UserControllerTest` cases cover minting, consumption, wrong-user-id
non-consumption, all-three-grants-from-one-mint, expiry, and that a fresh
outbound mint is not confused with a return-direction one.

**`0075`-`0076`: eliminate the swipe.** Physical-hardware testing (this
session) found that even after the outbound direction's own single-
credential fix, a swipe was still required after the decoy PIN before
landing on the decoy's home screen (the known, previously-disclosed
ADR-085 gap). Traced to `KeyguardViewMediator#handleUserSwitchComplete`'s
authorized branch: it correctly skips the automatic bouncer *re-arm*, but
does nothing else -- the device is left on a swipeable lock screen until
the user's own next gesture happens to call `PrimaryBouncerInteractor
#show()`, which is the one place the third (active-dismiss) grant from
the same mint gets redeemed. Fixed by proactively calling the same
`dismiss()` the unauthorized branch already calls, redeeming that grant
immediately instead of leaving it to a gesture -- reusing the identical
`showNextSecurityScreenOrFinish()` chokepoint trust-agent and biometric
dismissals already use, not a new dismiss authority.

**Real physical `lynx` hardware validation (first candidate, this
session):** a fresh boot, owner's credential never entered that boot,
decoy PIN typed into the real on-screen bouncer. `adb logcat` showed
`Successfully verified primary lockscreen credential for user 10` at
`13:35:25.927`, then an automatic `keyguardDonePending: 10` at
`13:35:27.294` (no user gesture), `KeyguardTransitionRepository`
`LOCKSCREEN→GONE FINISHED` at `13:35:28.124` -- landed on the decoy's
home screen with **zero swipe**, total credential-to-landed time ~2.2s
(down from ~4.8-6.5s including swipe-reaction time on the pre-fix
candidate). This is real, direct evidence the swipe is gone, not a
source-level inference.

**`0077`-`0078`: drop the reused 500ms delay.** The owner reported the
still-flashed first candidate had traded the swipe for a perceptible
pause -- type the PIN, then visibly wait for it to unlock, unlike a real
same-user unlock (owner-confirmed: PIN typed into an already-showing
bouncer lands immediately). Root cause: the new authorized-branch
`dismiss()` call above reused the unauthorized branch's existing
`postDelayed(..., 500)`. That 500ms is original, pre-AegisOS AOSP code,
there specifically to let a *freshly shown* bouncer's own async layout
listeners settle before interaction -- the authorized branch never shows
a bouncer at all, so that race does not apply. Changed to
`postDelayed(..., 0)`, keeping the handler-posted ordering and the
`mDismissToken` tagging (so the callback stays cancellable by the
existing `removeCallbacksAndMessages(mDismissToken)` path if an
intervening switch supersedes it) -- only the specific 500ms wait was
removed, not the posting pattern itself.

**What this patch set does *not* claim to fix.** Real hardware evidence
from the first candidate's own trial showed ~865ms between credential-
verified and `onUserSwitchComplete` (the switch machinery itself --
freeze/quiesce/permission re-evaluation), plus ~830ms for the transition
animation. Those are inherent to this mechanism being a real user switch
under the hood, not an artificial pause `0077`-`0078` can remove --
eliminating them would need a fundamentally different mechanism than the
credential-gated-switch architecture this whole feature (`0041` onward)
is built on. The design's own acceptance criterion has always been
"materially below the 643ms warm baseline," not literally zero.

**Build verification.** `0073`-`0074` built clean in isolation (`m -j6
services`, `Build Succeeded: 533 steps`). All four patches together
required two real, hard-won rebuild corrections, both matching the exact
`out-lynx` Ninja/Soong incremental-staleness class already recorded
against this same directory (`0008`/`0009` above, "a consolidated `lynx`
build reported success while silently omitting..."): a plain `m -j6
systemimage` twice left `SystemUI.apk` at its pre-patch mtime despite a
clean log, caught by checking the artifact's own mtime rather than
trusting the build line, and fixed by force-`touch`ing the patched
source files and invoking `m systemextimage systemimage` directly rather
than the generic full-image target. The resulting `SystemUI.apk` was
independently confirmed to actually carry the compiled change both times
by extracting `classes2.dex` and `dexdump`-disassembling
`handleUserSwitchComplete` itself: the first round showed the new
`if`/`else` branch structure (two independent lambda instantiations,
case `0` for the existing path and case `3` for the new one); the second
round showed the authorized branch's `postDelayed` now loading `const-
wide/16 v5, #int 0` where it previously loaded `#int 500`, confirming
the delay-removal patch's real presence in compiled output, not just
source.

**Signing and physical flash.** Built `OFFICIAL_BUILD=true` (confirmed
via a fresh `installed-files.txt`/`installed-files-product.txt` grep for
all four gated apps), signed with the alpha key
(`265ade399a0f3ee65003e56daef26a8f4d70e6a4`, matching every prior
duress-unlock hardware candidate), rollback index `1783209600`,
`--padding_size 65536`. Both candidates (pre- and post-delay-fix)
verified 13/13 on two independent machines and `avbtool` installs (the
build server's own tree binary, and a separate `avbtool 1.4.0` install
on the controller's own workstation) -- the fingerprint was
independently confirmed matching on both machines via two different
`avbtool` subcommands (`extract_public_key`, and `info_image`'s own
reported `Public key (sha1)` field plus a separately extracted and
hashed pubkey file), not assumed from a single "13/13" line, per the
`0072`-era ADR-079 lesson about a self-consistent-but-wrong-key result
passing `verify_image` cleanly. All signing and flashing was performed
by the owner directly, per this project's standing rule; the controller
never had or requested access to the alpha private key or the
sign-vm-equivalent signing environment this session.

**A real incident during the first candidate's flash, found and
recovered, not silently worked around.** The controller's own flash
instructions did not separate physical partitions (flashable directly
in bootloader `fastboot`) from logical/dynamic partitions (`product`,
`system`, `system_ext`, `system_dlkm`, `vendor`, `vendor_dlkm` -- require
`fastbootd`, reached via `fastboot reboot fastboot`). The owner's first
flash attempt failed six `resize-logical-partition` commands mid-run,
leaving the device with a genuinely mixed partition state (new `boot`/
`dtbo`/`init_boot`/`pvmfw`/`vendor_boot`, old everything else, old
`vbmeta` never reached) that then booted on its own. Independently
verified this was not a real failure: `ro.boot.verifiedbootstate=orange`
(not a red-state AVB rejection -- this device's bootloader is unlocked,
so AVB mismatches are advisory, not enforced), `sys.boot_completed=1`,
zero crash/ANR/tombstone dropbox entries for that boot. Recovered by
completing the flash properly in two stages (physical partitions in
bootloader mode, then `fastboot reboot fastboot` with an explicit
`is-userspace: yes` wait before the six logical partitions plus
`vbmeta`), independently re-verified clean afterward (fingerprint, boot
state, dropbox). Two separate `/tmp`-full incidents also interrupted
this session's own tooling -- once in the controller's own sandbox
(blocking a partition-image download), once on the build server itself
(blocking both `avbtool extract_public_key`'s output write and, later,
`lunch`'s own `dumpvars` mechanism) -- both resolved by clearing
disposable files (the controller's own prior verification scratch
directories) rather than anything belonging to the owner.

**Real branch SHAs**, confirmed via `git log` on the actual branch after
each `git am` (never assumed from the apply output or a local worktree's
own commit SHAs):

```
e0a88d56218c  aegisos: drop the artificial delay before redeeming the active-dismiss grant (0078)
5bee3e4264bc  test: expect the authorized-branch dismiss with no artificial delay (0077)
4ec56d8ebedb  aegisos: redeem the active-dismiss grant proactively instead of on a gesture (0076)
15426c69eeb1  test: expect the redeemed active-dismiss grant to land without a swipe (0075)
12c52edb9703  aegisos: mirror the decoy switch authorization for the return path (0074)
8e39d14f4640  test: add coverage for the return-direction switch authorization (0073)
```

Patches regenerated fresh via `git format-patch 540d4a0366f2..HEAD`
directly from the real post-`git am` branch history before being copied
into this package, not carried over from any local worktree's own patch
files. Grepped all six patch files for `192\.168|beau@|beau-dev|
/srv/aegisos` -- no LAN/account coordinates.

**What remains open, stated plainly, not glossed over.**

- **The return-path fix (`0073`-`0074`) itself has not been dynamically
  exercised on hardware this session.** Its own `UserControllerTest`
  coverage is unit-level only (not run against real device hardware this
  session -- see the build-verification note above for what *was*
  confirmed: compilation and presence in the built image). The actual
  decoy→owner return flow, with this exact candidate flashed, was queued
  for a real touch-input trial when the session was redirected to this
  documentation instead. Do not treat single-credential return as
  hardware-confirmed until that trial actually happens.
- **The delay-removal fix (`0077`-`0078`) has not been dynamically
  exercised on hardware either.** It is compile- and bytecode-confirmed
  present (see above) and was flashed and booted cleanly (fingerprint,
  `orange` boot state, zero crash/ANR/tombstone), but the actual felt
  latency -- whether removing the 500ms genuinely reads as "immediate"
  rather than merely "faster" -- was not confirmed by a real PIN-entry
  trial before this session ended.
- The swipe-elimination fix itself (`0075`-`0076`) **is** hardware-
  confirmed, with the direct logcat evidence quoted above.
- Same open items as every prior entry in this file: the six UNCHECKED
  leak-audit items, `credential_capture` (untouched), and Component C's
  latency floor (~1.7s of inherent switch-machinery-plus-animation time,
  independent of anything this patch set changed).
- No ADR has been written for this patch set yet, and the
  `aegisos-foundation` handoff-router's current-state narrative has not
  been updated to reflect it. Both are still needed before this work can
  be described as closed anywhere else in the project's own records.

## Apply (this section)

```sh
cd packages/apps/Multiuser && git checkout -b aegisos-duress-decoy-switch && \
  git am ../../credential/duress-unlock-decoy/patches/packages-apps-multiuser/*.patch
```

# frameworks/base `0079`-`0086` (2026-09-17)

Fixes the two defects the owner hit on the candidate `0073`-`0078`
produced: the decoy-to-owner return stranded the session on the
direct-boot placeholder, and the duress unlock visibly swiped itself up
instead of landing like an ordinary unlock. Both are now
**hardware-confirmed on the physical `lynx`**, with the evidence quoted
below rather than inferred from a clean build.

## The return-path hang (`0079`-`0080`)

`0073`-`0074` removed the second credential prompt on the return path.
That prompt was load-bearing: it was the only thing that ever unlocked
the returning user's CE storage, because `verifyPrimaryCredentialNoRouting`
(`0013`/`0014`) deliberately skipped every side effect of an ordinary
verification and zeroized the synthetic password.

Device event log, captured from the owner's own failed attempt:

```
18:07:30.572  boot_progress_start                  <- fresh boot, user 0 CE-LOCKED
18:07:50.814  uc_finish_user_unlocked: 10          <- duress PIN; decoy unlocks
18:09:43.546  wm_resume_activity [0, com.android.settings/.FallbackHome]
18:09:43.614  uc_finish_user_unlocking: 0          <- ...and then nothing
18:14:27.413  uc_finish_user_unlocked: 0           <- 4m44s later, no reboot
```

`FallbackHome` is the direct-boot placeholder. `UserController.java:1001`
writes `UC_FINISH_USER_UNLOCKING`, then line 1006 returns false at
`if (!mInjector.isCeStorageUnlocked(userId))` -- exactly the truncation
the log shows.

`0080` applies the same `onCredentialVerified` side effects
`doVerifyCredentialInner` applies on a match, **outside** the
`mSpManager` lock (matching upstream's own placement -- that call makes
binder calls into StorageManager and ActivityManager, and holding
`mSpManager` across those is a deadlock shape). Verified at the bytecode
level in the shipped `services.jar`, not just in source:

```
0003: monitor-enter v2
003e: monitor-exit v2                                  <- lock released
004b: invoke-virtual LockSettingsService.onCredentialVerified
0056: invoke-virtual SyntheticPassword.zeroize
```

**Deliberately does not** mirror `sendCredentialsOnUnlockIfRequired`,
which would hand the raw credential to a DPC and to profiles tied to
this lock. Accepted consequence: such a profile stays locked after a
return and is challenged on next access.

Confirmed on hardware: `Unlocked CE storage for secured user 0` now
appears on the return, and the owner reported the placeholder gone.

## The swipe (`0081`-`0086`, three attempts -- two of them wrong)

Recorded in full because the two dead ends are the useful part: they are
the wrong layers, and a future session should not re-enter them.

**Attempt 1 (`0081`-`0082`) -- insufficient, kept.** Set
`KEYGUARD_GOING_AWAY_FLAG_NO_WINDOW_ANIMATIONS`. Confirmed on hardware to
leave the swipe completely intact. The going-away is merged into the
user-switch transition and animated by the Shell's
`KeyguardTransitionHandler`, which dispatches on
`TRANSIT_FLAG_KEYGUARD_GOING_AWAY` alone -- there is **no reference to
the no-animation flag anywhere** under
`libs/WindowManager/Shell/src/com/android/wm/shell/keyguard/`. Kept
because it is still the semantically correct request and is honoured on
the non-Shell path, but it does nothing on this build.

**Attempt 2 (`0083`-`0084`) -- also insufficient, also kept.** Suppressed
the canned unlock animation in
`KeyguardUnlockAnimationController#playCannedUnlockAnimation`, using the
same instant path the adjacent `isWakeAndUnlock` branch already uses.
Confirmed working -- `surfaceBehindEntryAnimator` never starts any more --
and the swipe still survived it. Kept because the animator would
otherwise return.

**Attempt 3 (`0085`-`0086`) -- the actual fix.** A lossless 64MiB logcat
capture (the earlier diagnoses were made from a default-sized buffer that
was silently dropping SystemUI lines during the switch burst -- that is
why two wrong layers were fixed first) showed the keyguard's own state
machine:

```
RESET
STARTED  PRIMARY_BOUNCER -> LOCKSCREEN  by FromPrimaryBouncerTransitionInteractor
FINISHED PRIMARY_BOUNCER -> LOCKSCREEN                       (471ms)
keyguardGoingAway requested for userId: 10
STARTED  LOCKSCREEN -> GONE  by FromLockscreenTransitionInteractor
FINISHED LOCKSCREEN -> GONE                                  (416ms)
```

The incoming user's lock screen is animated in and then animated away
again. Both are visible; together they are the swipe. An ordinary unlock
on the same device, captured for comparison, goes `PRIMARY_BOUNCER ->
GONE` in one step and never presents a lock screen.

Cause: `resetStateLocked()` collapsing the bouncer during the switch
drops `primaryBouncerShowing`, and
`FromPrimaryBouncerTransitionInteractor#listenForPrimaryBouncerNotShowing`
turns exactly that into a transition to `LOCKSCREEN`. Everything after is
forced.

`0086` uses `resetStateLocked`'s **existing** `hideBouncer` parameter to
leave the bouncer up for an authorized switch. The bouncer covers the
screen at least as completely as the lock screen it replaces, so nothing
of the outgoing user's session is exposed, and it is the surface the
credential was just typed into. The bouncer-skip grant moves to
`handleBeforeUserSwitching` because the decision must be made there;
switch-complete now reads that decision instead of redeeming twice.
Ordinary secure-user switches are untouched and still collapse their
bouncer (asserted in `0085`, the ADR-061 property at this layer).

Hardware-confirmed after flashing:

```
from=PRIMARY_BOUNCER, to=GONE, transitionState=STARTED,  by FromPrimaryBouncerTransitionInteractor
from=PRIMARY_BOUNCER, to=GONE, transitionState=FINISHED, by FromPrimaryBouncerTransitionInteractor
```

Owner's own verdict on the flashed result: it works.

**Real branch SHAs**, read from `git log` on the actual branch after each
`git am`, and the exported patches regenerated fresh from that same
history (never carried over from a local worktree's own patch files):

```
fade68916b06  aegisos: keep the bouncer up across an authorized switch (0086)
f0fa81c8ac63  test: expect an authorized switch to keep its bouncer up (0085)
f5c64142b278  aegisos: suppress the canned unlock animation for an authorized switch (0084)
0bb1b53a84ca  test: expect the authorized-switch dismiss mark to be single-use (0083)
b069fd0096f9  aegisos: cut rather than animate the authorized-switch dismissal (0082)
f12d17c6d6dd  test: expect the authorized-switch dismiss to suppress the unlock animation (0081)
bef0d05fb66c  aegisos: unlock the returning user on a matched return credential (0080)
cdab10e2ee4f  test: expect the return-path credential check to unlock CE storage (0079)
```

Built with `OFFICIAL_BUILD=true`; all four gated packages confirmed
present (`Seedvault`/`Updater` in this build's own fresh
`installed-files.txt`, `Auditor`/`PdfViewerGOS` in the product
partition's, which was not rebuilt and not reflashed). Signed with the
alpha key `265ade399a0f3ee65003e56daef26a8f4d70e6a4`, rollback index
`1783209600` unchanged, verified 13/13 on two separate machines with two
different `avbtool` binaries before each flash.

## Measured latency, and what is left open

Measured on hardware after the fix, PIN verified to home:

| | |
|---|---|
| ordinary owner unlock | **771 ms** |
| duress unlock | **~2050 ms** |

Breakdown of the duress path: 438ms credential-check plus decoy routing
and mint, 493ms switch machinery, **1110ms decoy-launcher cold start**,
809ms transition tail. The launcher process for the decoy starts at
`am_proc_start [10, ..., com.android.launcher3]` *during* the switch.

- **The gap itself is a tell.** 2.0s against 0.8s is stopwatch-measurable.
  Note the inverse also holds: making the duress path *faster* than an
  ordinary unlock would be just as much a tell, so `TO_GONE_DURATION`
  (500ms, identical to an ordinary unlock) must not be shortened.
- **Pre-warming the decoy launcher cannot close this fully.**
  `com.android.launcher3` is not `DIRECT_BOOT_AWARE` (confirmed via
  `dumpsys package`), and a warm-started decoy stays in `RUNNING_LOCKED`
  until its own credential is verified -- so its launcher physically
  cannot start before the duress PIN is typed. Only the overlap between
  CE-unlock and the switch machinery is recoverable (~300-700ms).
- **The obvious implementation of that overlap is unsafe and was not
  landed.** `RootWindowContainer#canStartHomeOnDisplayArea` has no
  current-user check, and `startHomeOnAllDisplays` goes through
  `startHomeOnTaskDisplayArea(..., onTop = true)` -- it would bring the
  decoy's home forward while user 0 is still current, with only the
  bouncer between it and the owner's session. A safe version needs a new
  internal API passing `onTop = false`, plus hardware proof that no decoy
  frame is visible before the switch. Not attempted.
- **The pre-switch unlock window — resolved at source level, not yet on
  hardware.** `0080` calls `unlockUser` roughly a second before the switch
  lands, while the decoy is still foreground; previously this happened
  after the owner was already in front. The concern was whether user-0
  processes started *by* that unlock escape the quiescer's notification
  suppression. They do not: `0010` gates on
  `mAmi.isUserQuiescedForDuress(userId)` at notification-post time, a
  per-user check rather than per-process state, so it covers processes
  started at any point while the user is quiesced. User 0 is still
  quiesced throughout that window, since `0011` only unquiesces once the
  user becomes current. Windows are separately filtered by current user,
  and the bouncer covers the screen regardless. Not observed directly on
  hardware, so stated as a source-level conclusion.

## Bouncer-retention security review (`0086`)

`0086` keeps a live bouncer across a user switch, which upstream never
does, so its identity surface was reviewed specifically rather than
assumed benign.

**The bouncer cannot render the user-switcher/avatar header on this
device.** `KeyguardSecurityContainer` does have a `MODE_USER_SWITCHER`
with an avatar header (`bouncer_user_switcher_header_bg`), selected by
`KeyguardSecurityContainerController#configureMode` when
`canDisplayUserSwitcher()` is true -- which reads exactly one value,
`R.bool.config_enableBouncerUserSwitcher`. Read out of the **actually
built** `SystemUI.apk` for `lynx`, not from the source default:

```
resource 0x7f050017 bool/config_enableBouncerUserSwitcher
  () false
```

The only SystemUI overlay on the product partition
(`GlanceableHubSysuiConfigOverlay.apk`) does not touch it -- confirmed by
dumping its resources. `vendor/adevtool/config/device/common/
overlay-inclusion.yml` does list this key as `= true`, but that file is
adevtool's extraction inclusion list, not an applied product overlay, and
the built artifact is what settles it. So `configureMode` can only ever
pick `MODE_DEFAULT` here, and no avatar or user name is on the bouncer to
leak.

Residual, low-risk and not visually verified: the bouncer's
`KeyguardMessageArea` and carrier text remain live across the retained
window. Neither is user-identifying in this flow (no message is set), but
a real touch-input trial watching that window has not been done.

**The authorization surface is unchanged by `0079`-`0086`.** Re-checked
rather than assumed:

- The mint (`authorizeDecoySwitchKeyguardSkip`,
  `authorizeRealUserSwitchKeyguardSkip`) lives on
  `ActivityManagerInternal`, an in-process LocalService -- unreachable
  from apps or shell, and only ever called from `DecoyProfileHelper` /
  `ReturnToRealUserHelper` after a real Gatekeeper-backed credential
  match.
- The consume side is Binder-reachable via `LockPatternUtils`, but
  `LockSettingsService` guards every one of those entry points with
  `checkHavePermission()` ->
  `enforceCallingOrSelfPermission(ACCESS_KEYGUARD_SECURE_STORAGE)`, whose
  `protectionLevel` is `signature|setup`. It also fails closed on
  `RemoteException`, where the only consequence is that the ordinary
  bouncer is shown.
- `0086` changes *when* SystemUI redeems the bouncer-skip grant
  (handleBeforeUserSwitching rather than handleUserSwitchComplete), not
  who may redeem it. No new IPC endpoint or permission was added by this
  patch set.
- `0080` cannot be used to unlock anything without the owner's real
  credential: it is gated on `response.isMatched()` from
  `unlockLskfBasedProtector`, rate-limited on the same `LskfIdentifier`
  as an ordinary check, and asserted in both directions by `0079`.
- Same open items as every prior entry: the six UNCHECKED leak-audit
  items, and `credential_capture` (untouched).
- No ADR has been written for this patch set yet, and the
  `aegisos-foundation` handoff-router has not been updated.

## Apply (this section)

```sh
cd frameworks/base && git am \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/007[9]-*.patch \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/008[0-6]-*.patch
```

# frameworks/base `0086`-`0087` (2026-09-18)

## `REASON_QUIESCE_FAILED` made reachable

Before this change, `ActivityManagerService`'s `LocalService#quiesceUserApps`
hardcoded `return true`, so `RealUserQuiescer.quiesce()`'s per-process freeze
failures were logged but never escalated --
`DecoyRoutingFailureMarker.REASON_QUIESCE_FAILED`, wired at
`DecoyProfileHelper.java:258` since the seamless-unlock work (`0051`-`0053`),
was structurally unreachable dead code. This was flagged as an open
completeness gap during the `credential_capture`/Finding-13 closure pass
("Component B's failure-path fallback ... is DEAD CODE, confirmed by direct
source trace") and is Component 3 of `docs/superpowers/specs/2026-09-17
-duress-unlock-final-closure-design.md` in the `aegisos-foundation` repo.
Owner's explicit threshold (2026-09-17): any single process failing to
freeze counts as a quiesce failure -- not "all fail" or "majority fail".

## A stale design assumption, found while reading the real callers

The 2026-08-27 real-user-quiescing-and-return design doc defined an
"asymmetric error handling rule": `quiesceUserApps`'s return value was meant
to reflect *notification-suppression install* success only, with freeze
failures deliberately excluded ("freeze failures never affect this return
value"). `ActivityManagerInternal.java`'s own javadoc for `quiesceUserApps`
still stated this verbatim, word for word, right up to this change.

That premise no longer matches the mechanism as actually built.
`RealUserQuiescer` never implements an installable "notification-suppression
flag" -- its own class javadoc says so directly ("This class does not touch
notification state"). Notification suppression is instead a live query
(`NotificationManagerService` consults `isUserQuiescedForDuress()` at
notification-post time, confirmed at
`services/core/java/com/android/server/notification/NotificationManagerService.java:10317`),
which cannot fail in the sense the original design envisioned -- there was
never a real install step whose success this return value could report. So
the hardcoded `return true` was not a bug relative to the *original*
2026-08-27 semantic; it was a faithful placeholder for a step that does not
exist in the mechanism as shipped. The owner's 2026-09-17 decision repurposes
the same return value and reason code for a different, now-real failure mode
(per-process freeze failure) rather than reviving the original one.

Also confirmed while reading: `performDecoySwitch` (`DecoyProfileHelper.java`,
landed `0051`-`0053`) already calls `switchToUserSilently(decoyUserId)`
*before* `quiesceUserApps(realUserId)` -- the reverse of the 2026-08-27 doc's
stated sequencing (quiesce, then notification-suppression, then switch). This
is a correct, necessary divergence: `RealUserQuiescer`'s own javadoc requires
the target user to have already stopped being foreground before its
processes can be frozen. One consequence, independently useful for this
task: because the switch always happens first and unconditionally, a
`quiesce()` failure structurally cannot block or unwind the decoy switch --
by the time `quiesce()` runs, the switch has already succeeded.
`DecoyProfileHelper.java`'s existing failure branch (`if
(!ami.quiesceUserApps(realUserId)) { DecoyRoutingFailureMarker.record(...,
REASON_QUIESCE_FAILED); }`, with nothing after it) already does exactly the
right thing with zero changes required there -- it was correctly written in
anticipation of a real boolean, just never exercised.

Minor, not fixed (out of this task's file list, which named
`DecoyRoutingFailureMarker.java` read-only-confirm): the marker's own class
javadoc describes every recorded failure as causing "the seamless
credential-routing switch [to fall] back to the pre-seamless behavior (a
second credential prompt)". Accurate for `REASON_SWITCH_NOT_STARTED`
(`switchToUserSilently` failed, the real user stays current, the next unlock
attempt shows an ordinary keyguard) but not for `REASON_QUIESCE_FAILED`,
where the switch has already succeeded and no second prompt occurs -- the
decoy session is already live; the only real consequence is that some
backgrounded real-user process may still be unfrozen. Flagged for a future
doc-accuracy pass.

## The fix (`0086`-`0087`)

`0086` adds `RealUserQuiescerTest.java` (new file; no prior test existed for
this class) with three cases: zero processes (trivially true), a single
process whose `forceFreezeAppAsyncLSP` throws (returns false, user still
marked quiesced), and a process belonging to a different user (correctly
skipped, returns true). `ProcessRecord`/`ProcessList` are real AOSP classes
not designed for mocking -- `ProcessList` is `final` (unmockable,
unsubclassable) and `ProcessRecordInternal`'s `uid`/`processName` are
`public final` fields set only by `ProcessRecord`'s full constructor (which
needs a wired `ActivityTaskManagerService`). The test instead constructs a
real, minimal `ProcessList` directly and uses the same reflection-based
final-field-setting helper already established in this exact package
(`AnrHelperTest`, `ProcessRecordTests` both define their own
`setFieldValue`, using `Field.class.getDeclaredField("accessFlags")`, not
`"modifiers"` -- confirmed this codebase's toolchain needs that exact
approach) to populate only `uid`/`processName` on a Mockito-mocked
`ProcessRecord`.

`0087` is the real fix: `RealUserQuiescer.quiesce(int): void` becomes
`quiesce(int): boolean`, tracking `allSucceeded` across the freeze loop --
any single caught `RuntimeException` sets it false. `userId` is still added
to `mQuiescedUserIds` regardless, so notification suppression stays intact
on a partial freeze failure. `ActivityManagerService.LocalService
#quiesceUserApps` becomes a real pass-through of this value instead of the
hardcoded `true`. `ActivityManagerInternal.java`'s javadoc is corrected to
describe the new, real contract instead of the stale asymmetric-error
-handling text quoted above.

## Verification: BLOCKED on real `atest` execution (disclosed, not hidden)

Real branch tip after `git am`: `16e532d8667418d51a950881ae938193e8bbda79`
(`frameworks/base`, `aegisos-duress-decoy-switch`), confirmed via `git log`
/`git rev-parse HEAD` directly on the build server both before and after
landing, not trusted from the local worktree.

`atest FrameworksServicesTests:com.android.server.am.RealUserQuiescerTest`
was attempted four times and never produced a single compiled or executed
test result -- every attempt died in Soong's own bootstrap module-graph
analysis (`[100% 2/2] analyzing Android.bp files and generating ninja file
at out-preflight/soong/build.sdk_phone64_x86_64.ninja`), before any test
code was ever compiled or run:

1. Plain `atest ... RealUserQuiescerTest -v` -- 9m07s, `exit=137
   signal:killed`.
2. Identical retry after confirming memory had recovered (30Gi free at
   rest) -- 11m02s, same `exit=137 signal:killed`, same step.
3. `atest -j 3 ...` -- 7m35s, same failure. Confirmed by reading
   `build/soong/ui/build/soong.go`/`config.go` that atest's own `-j`/
   `--build-j` flag controls *test execution* parallelism only; the actual
   invoked command
   (`./build/soong/soong_ui.bash --make-mode WRAPPER_TOOL=atest
   ATEST_RUN_ID=... .../module-info.json`) carries no `-j` at all, so
   `config.Parallel()` fell back to its default (`runtime.NumCPU()+2` = 10)
   for the bootstrap ninja/siso invocation that actually failed.
4. One mitigated attempt, run directly (bypassing atest) as
   `./build/soong/soong_ui.bash --make-mode -j2
   out-preflight/target/product/emu64x/module-info.json` -- confirmed via
   source read that `-j` parsed on `soong_ui`'s own argv does reach
   `config.Parallel()` and from there the bootstrap `--local_jobs`/`-j`
   ninja arg. Still failed identically: 6m35s, `exit=137 signal:killed`,
   same `build.sdk_phone64_x86_64.ninja` target.

All four failures share one signature: `FAILED:
out-preflight/soong/build.sdk_phone64_x86_64.ninja`, `stderr: Killed`,
`exit=137 # signal:killed` -- almost certainly the kernel OOM killer, though
`journalctl -k` confirmation was inconclusive (no elevated read access on
this account). At-rest memory was healthy before every attempt (28-30Gi free
of 31Gi, 16Gi swap present with 6-10Gi free) -- this is a spike *during*
Soong's full Android.bp module-graph re-analysis (triggered by "Globs
changed, rerunning soong" once a new `.java` file was added under
`frameworks/base/services/tests/servicestests/src/**`), not sustained
exhaustion. This is the same failure class (signal-killed during a full
Soong analysis pass, same host) as Task 3 hit the same day during its own
`m services` attempt in `out-lynz` -- a second, independent occurrence
strengthens rather than weakens the case that this is a real, general
build-server resource constraint, not a fluke tied to this one test module.

**What this means concretely:** neither Step 3 (RED: confirm the test fails
to compile against the old `void` signature) nor Step 5/7 (GREEN: confirm
both new test cases pass, plus the wider `UserControllerTest`/
`ActivityManagerServiceTest` regression suite) was executed via real
`atest`. The RED claim rests on static/source-level reasoning only (the
pre-image `RealUserQuiescer.quiesce` was confirmed `void` by direct read
before editing; the test calls `assertThat(mQuiescer.quiesce(0)).isTrue()`,
which requires a `boolean` return -- a `void`-returning method there is a
compile error, not a runtime assertion failure). The GREEN claim rests on
manual code trace only: the `allSucceeded` flag is set `false` on the exact
same `catch (RuntimeException e)` branch the brief's own sample code
specifies, and is never reset before `return`; the loop's `continue` for a
non-matching `userId` was traced by hand for the third test case. This is a
real, disclosed verification gap, not a masked one -- `REASON_QUIESCE_FAILED`
is now structurally reachable and covered by a real test file on the real
branch, but that test file has not yet been confirmed to actually compile
and pass on this build server. Revisit before Task 7's consolidated build
(which will compile this code regardless, as part of `m -j6 systemimage`,
and will surface a genuine compile error immediately if the manual trace
above is wrong).

## Apply (this section)

```sh
cd frameworks/base && git am \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/0086-*.patch \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/0087-*.patch
```

## `UserRepositoryImpl`'s stale-cache bug on a keyguard transition (`0088`)

`UserRepositoryImpl._userSwitcherSettings` is a `StateFlow` built from
`globalSettings.observerFlow()` over three `Settings.Global` keys
(`SETTING_SIMPLE_USER_SWITCHER`, `ADD_USERS_WHEN_LOCKED`,
`USER_SWITCHER_ENABLED`). Its `getSettings()` mapper calls
`manager.isUserSwitcherEnabled()`, which is itself live and
keyguard-state-aware (patch `0043`: it additionally hides the switcher
for a Duress Unlock decoy session's own current-user view, without
touching the device-wide setting) -- but the `StateFlow`'s cached value
only refreshes when one of those three `Settings.Global` keys changes
via `ContentObserver`. A keyguard showing-state transition (e.g. the
duress decoy switch) with zero `Settings.Global` writes left the cached
value stale until some unrelated settings write happened to refresh it.

This is a different, newer finding than the one originally recorded
against this file (patch `0034`, the QS-footer-icon raw-setting bypass
fixed by delegating to `manager.isUserSwitcherEnabled()`). That fix is
still correct and unrelated -- `getSettings()` already calls the live,
keyguard-aware API; the bug this patch fixes is one layer up, in when
the `StateFlow` re-invokes that mapper at all.

**API confirmed by reading the real source before writing code (no
`KeyguardStateController`/`KeyguardTransitionRepository` guess):**
`KeyguardTransitionRepository` (`keyguard/data/repository/
KeyguardTransitionRepository.kt`) only exposes `TransitionStep`s between
`KeyguardState` values (`STARTED`/`RUNNING`/`FINISHED`/`CANCELED`), no
plain "is showing" boolean -- confirmed by reading its full interface.
`KeyguardRepository.isKeyguardShowing: StateFlow<Boolean>`
(`keyguard/data/repository/KeyguardRepository.kt`) is the real signal:
already consumed the identical way (`keyguardRepository.isKeyguardShowing`)
by `DeviceEntryFaceAuthRepository` in a sibling `data/repository` package,
confirming both the API shape and that direct `KeyguardRepository`
injection is an established, working pattern in this Dagger graph.
Checked for a circular dependency before injecting it:
`KeyguardRepositoryImpl`'s own constructor (`StatusBarStateController`,
`KeyguardStateController`, `KeyguardUpdateMonitor`,
`DozeTransitionListener`, `AuthController`,
`DreamOverlayCallbackController`, `SystemClock`, `FacePropertyRepository`,
`UserTracker`, `LockPatternUtils`) has no dependency on `UserRepository`
-- `UserTracker` is a lower-level primitive already shared by both
classes, not a cycle.

`0088` merges `keyguardRepository.isKeyguardShowing.map { Unit }` into
`_userSwitcherSettings`'s upstream `merge(...)` alongside the existing
`globalSettings.observerFlow(...)`, so a keyguard transition re-evaluates
`getSettings()` even when none of the three settings keys changed. Test:
`UserRepositoryImplTest.userSwitcherSettings_reEvaluatesOnKeyguardTransition_
withoutSettingsChange` uses `FakeKeyguardRepository`
(`kosmos.fakeKeyguardRepository`, already existing in
`tests/utils/.../keyguard/data/repository/`, no new fake needed) and its
`setKeyguardShowing(Boolean)` to flip the keyguard state with zero
`Settings.Global` writes, then asserts the cached `isUserSwitcherEnabled`
reflects the new `manager.isUserSwitcherEnabled()` mock return value.
Test and fix landed as a single commit (rather than this branch's usual
separate `test:`/`aegisos:` pair) because the fix requires a new
constructor dependency (`KeyguardRepository`) that the test itself must
also thread through `create()` -- splitting them would leave the
intermediate `test:` commit failing to compile, not failing its
assertion, which is a materially different (and less useful) RED state
than the brief specified.

Confirmed only one call site constructs `UserRepositoryImpl` directly
(`UserRepositoryImplTest.create()`, updated) -- every other consumer
across `packages/SystemUI` goes through Dagger's `@Inject constructor`
or the `Kosmos` fixture `kosmos.fakeUserRepository`/`userRepository`
(a hand-written `FakeUserRepository`, never `UserRepositoryImpl`
itself), so the new constructor parameter has zero blast radius outside
the one file changed. Also confirmed the QS-footer icon's own
`UserSwitcherRepositoryImpl.isEnabled` (`UserSwitcherRepository.kt`,
same package) is a fully independent `conflatedCallbackFlow` with no
reference to `UserRepositoryImpl` or `_userSwitcherSettings` -- untouched
by this change, by construction, not just by observation.

## Verification: BLOCKED on real `atest` execution (disclosed, not hidden)

Real branch tip after `git am`, confirmed via `git log`/`git status`
directly on the build server both before and after landing, not trusted
from the local worktree: `fee7a84087f9691786ce082fc9c17eadbc8e3a94`
(`frameworks/base`, `aegisos-duress-decoy-switch`).

`atest UserRepositoryImplTest -v` was attempted twice and never produced
a compiled or executed test result -- both attempts died in Soong's own
bootstrap module-graph analysis, the identical failure signature already
recorded for `0086`/`0087` above:

1. First attempt used a stale `lunch sdk_phone64_x86_64-userdebug`
   invocation (missing the `-<release>-` component current `lunch`
   requires) and failed immediately with a `lunch` usage error -- not the
   real environmental failure, so not counted against the two-attempt
   budget.
2. Corrected invocation (`OUT_DIR=out-preflight` exported before `source
   build/envsetup.sh`, `lunch sdk_phone64_x86_64-cp2a-userdebug`, then
   `atest UserRepositoryImplTest -v`): `soong_build`
   (`out-preflight/host/linux-x86/bin/soong_build`) ran for ~22 minutes of
   real CPU time, climbing to 94% system memory (30.9GB RSS of 31Gi
   total) with `siso`-level "memory stall" warnings logged
   (`trace_sys_linux.go:89`), then died -- confirmed by
   `out-preflight/target/product/emu64x/module-info.json` still stamped
   `Aug 6`, meaning the module-graph analysis never completed.

This is the **third** task today to hit this exact failure on this
server (Task 3's `m services` in `out-lynz`, Task 5's `atest
RealUserQuiescerTest` four separate ways, now this task's `atest
UserRepositoryImplTest` two ways) -- a confirmed, reproducible
build-server capacity limit for this branch's current size against the
`sdk_phone64_x86_64`/`out-preflight` config, not a fluke tied to any one
test module or invocation method. Per the same standing instruction that
governed `0086`/`0087`'s verification gap, no further retry was
attempted.

**What this means concretely:** neither the RED state (the new test
failing against the pre-image `_userSwitcherSettings`, which only merges
`globalSettings.observerFlow(...)`) nor the GREEN state (the same test
passing against `0088`'s merged flow) was executed via real `atest`. Both
claims rest on source-level reasoning, not a build-server run:
`FakeKeyguardRepository.setKeyguardShowing()` only ever reaches
`UserRepositoryImpl` through `keyguardRepository.isKeyguardShowing`,
which the pre-image file never read at all (confirmed by direct read of
the file before editing) -- so the pre-image would leave
`settings?.isUserSwitcherEnabled` at its stale `true` value after the
keyguard transition, exactly the brief's predicted RED failure. The
GREEN claim rests on `merge()`'s documented semantics (each source flow's
emissions propagate downstream independently) plus the `unconfined` test
dispatcher already used by every other test in this file, none of which
call `runCurrent()` for an analogous synchronous trigger
(`userUnlockedFlow_tracksBroadcastedChanges` triggers a broadcast and
reads the result immediately, the same shape used here). This is a real,
disclosed verification gap, not a masked one -- revisit before this
branch's next consolidated build, which will compile this code
regardless and surface a genuine compile error immediately if the manual
trace above is wrong.

## Duress Unlock final closure — Task 4: squash `0015`+`0016` (history cleanup only)

Per the final-closure plan's Task 1 audit and owner sign-off (see the
Duress Unlock final-closure Task 3 note earlier in this file for the same
pattern applied to a different candidate). `0015` ("add
`ReturnToRealUserHelper`, wire the return path") and `0016` ("fix
`ReturnToRealUserHelper.java` path, patch 0014 misapplied it under
`frameworks/base/`") squashed into one commit that adds the file directly
at its correct final path — pure history simplification, zero content
change. `0016` was itself a 100%-similarity pure rename with a 0-line
diff, so the combined patch is built by taking `0015`'s own diff and
correcting only the destination path in its header.

Verified two independent ways before landing, not assumed: (1) the
squashed commit was first test-applied on a disposable throwaway branch
(never touching the real branch), and `git diff` against the pre-squash
tip came back empty; (2) the new file's git blob hash
(`4aeb1e0b71daad2654dc68629d325a4ffcf6f5a1`) was confirmed byte-identical
before and after — blob hashes are content-only in git, so this is a
direct cryptographic proof the squash changed no bytes, independent of
the diff check. Landed on the real branch via `git reset --hard` to
`0015`'s own real parent (`079d5588f4830a916c1b892e535a8871c8deb0f1`)
followed by `git am` of the combined patch, then `git am` of the 74
unchanged original commits from `0017` through the real tip in their
original order (exported fresh from the real branch before the reset, so
nothing but `0015`/`0016` themselves ever changed). Landed by the owner
directly (not a subagent) after this session's harness permission
classifier twice refused the equivalent action when attempted
programmatically — the owner ran the exact, pre-verified command block
handed to them, output matched expectations exactly (75 commits from
`0015`'s parent to the new tip, was 76; empty diff against the pre-squash
tip, reconfirmed independently by the controller afterward via a fresh
SSH check).

New real branch tip: `08ba5b75a24df34df4b983af66c90378a6e617fb`. Squashed
commit itself: `babea734ee48` ("aegisos: add ReturnToRealUserHelper, wire
the return path").

Whole `frameworks-base` patch series regenerated fresh from real branch
history (`e6e5746502a3ea1b8970210cb9ccbfac979e490d..HEAD`), per this
project's standing "never hand-renumber" rule. Regenerating fresh
produced 89 patches, not the expected 87 — the extra two are the same
pre-existing, already-reverted, never-independently-exported "temporary
latency instrumentation for decoy switch (Task 8 Step 4/5)" commit pair
documented earlier in this file's Third-plan/`0018` section (the "88 vs
86 commits" discrepancy a Task 3 reviewer flagged as worth reconciling —
this confirms the pair's real commit subjects directly from the
regenerated patch filenames themselves, settling that earlier open
question: they really are the latency-instrumentation pair, not a
different pair). Excluded both from the numbered series again, matching
established precedent, then renumbered continuously: `0001`-`0014`
unchanged, `0015` is the new squashed commit, `0016`-`0087` are the
previously-`0017`-`0088` commits shifted down by one (with the two
temporary-instrumentation commits removed from the count entirely, not
occupying numbered slots either before or after this squash).

## Duress Unlock final closure — Task 7: two build-blocking fixes discovered mid-build

Neither of these was anticipated by the final-closure plan or by ADR-091's
own accounting of Tasks 1-6. Both surfaced only when Task 7 (build the
consolidated hardware candidate) actually forced a genuinely clean
recompile of code that six weeks of stale incremental Soong cache had
been silently protecting from ever being recompiled at all.

**`0088` -- migrate `ReferenceGlobalRootComponent` to Kotlin.** KSP
(Kotlin Symbol Processing), this module's annotation processor since a
real upstream commit (`e00adb95523b8`, October 2025) enabled
`enable_ksp` for `SystemUI-application`, does not process plain `.java`
sources. `ReferenceGlobalRootComponent.java` was the only remaining
`.java` file in the whole SystemUI tree still declaring a Dagger
`@Component` -- every sibling component had already been migrated to
Kotlin. This has nothing to do with the Duress Unlock patches; it is a
pure upstream/tooling compatibility fix, confirmed via `git blame`
(upstream commit, not an `aegisos:` patch) before touching it.

**`0089`+`0090` -- a real Dagger dependency cycle Task 6 itself
introduced.** Rebuilding past `0088` surfaced a genuine
`[Dagger/DependencyCycle]` compiler error:
`KeyguardRepositoryImpl -> KeyguardRepository -> UserRepositoryImpl ->
UserRepository -> SelectedUserInteractor -> AssistManager -> Dependency ->
back to KeyguardRepositoryImpl`. Task 6's own `KeyguardRepository`
injection into `UserRepositoryImpl` (patch `0087`) created this cycle --
directly against that same task's own Step 2 instruction to pick a
signal "without introducing a new circular dependency." Never caught
before because Task 6 had never actually been compiled until this build.

`0089` wrapped the dependency in `dagger.Lazy<KeyguardRepository>`.
This compiled clean and a dex-level bytecode check on the built
`SystemUI.apk` confirmed the exact expected `Ldagger/Lazy;.get()`
pattern in `UserRepositoryImpl.<init>` -- every automated check
available passed. The candidate was alpha-signed, flashed to the real
physical device, and `SystemUI` StackOverflow-crash-looped continuously.
Root cause, confirmed by reading `CachedAppOptimizer` and the real crash
trace: `Lazy<T>` only defers *which* instance Dagger hands back, not
*when* `.get()` runs. Calling it inline as a `merge()` argument still
executed it synchronously inside `UserRepositoryImpl`'s own constructor,
before Dagger's `DoubleCheck` memoization committed for this singleton --
so the same cycle re-entered at runtime as a genuine `StackOverflowError`.
`0090` fixes this for real by deferring the call into a cold
`flow { emitAll(...) }` builder, which only runs once `stateIn`'s
coroutine actually executes -- dispatched, not synchronous with the
constructor's own call stack -- by which point the singleton is already
memoized. Confirmed correct via a real on-device boot test after
reflashing just `system_ext`+`vbmeta`: crash buffer empty, `SystemUI`
stable at one PID across 31+ minutes, and (separately, during the
following hardware-verification sweep) direct confirmation that the
owner's own Select-user dialog -- the exact scenario Task 6 was written
to fix -- now shows the real, current user list immediately after a
lock/unlock cycle with zero `Settings.Global` writes.

New real branch tip: `f3f3c9ebc980`. Full record, including every
command run and every piece of on-device evidence:
`docs/superpowers/plans/2026-09-17-duress-unlock-final-closure.md`'s own
Task 7/8 execution (this repo), and
`docs/product/2026-09-20-duress-unlock-final-closure-session.md` (the
narrative session record, `AegisOS-foundation` repo).

## Apply (this section)

```sh
cd frameworks/base && git am \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/0088-*.patch \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/0089-*.patch \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/0090-*.patch
```

## `0091`: RealUserQuiescerTest never actually compiled until real `atest` ran

ADR-092 carried forward "obtain real `atest` evidence for
`RealUserQuiescerTest`/`UserRepositoryImplTest`" as a genuinely open item —
no adb-reachable device/emulator existed on the build server as of that
closure. Re-opened per that entry's own stated re-open condition: a
build-server emulator was brought up (nested KVM,
`sdk_phone64_x86_64-cp2a-userdebug`, manual launch procedure, `adb` attached
and booted) and `atest FrameworksServicesTests:RealUserQuiescerTest` was run
for real for the first time in this plan's history.

The very first real compile of this test target failed:
`RealUserQuiescerTest.java:56/59: error: unreported exception Exception;
must be caught or declared to be thrown`. Root cause: `setFinalField()`
declares `throws Exception` but was called directly inside the `Runnable`
lambda passed to `DexmakerShareClassLoaderRule.runWithDexmakerShareClassLoader`,
whose functional interface is plain `java.lang.Runnable` (confirmed by
reading the real `DexmakerShareClassLoaderRule.java` source — its `Runnable`
overload has no checked-exception-throwing variant). This had never been
caught because `atest` had never successfully compiled this exact test
target since patch `0085` added it — Task 7's earlier consolidated
`m systemimage` builds only ever needed the production `RealUserQuiescer`
source to compile, never this test.

Fixed (`0091`) by wrapping the two `setFinalField` calls in a
try/catch and rethrowing unchecked, matching the idiom already used
elsewhere for this same JUnit rule. Authored under `AGENTS.md`'s
local-first discipline in a disposable local git repo
(`aegisos-platform/.worktrees/duress-quiesce-atest-compile-fix/`, seeded
read-only from the real branch HEAD `f3f3c9ebc980`) and landed on the real
branch with `git am` (applied cleanly, real SHA `95131c37a0a3`).

The next real `atest` run compiled but then failed at runtime: both tests
exercising the freeze-failure path (`quiesce_singleProcessThrowsOnFreeze_returnsFalse`,
`quiesce_otherUsersProcess_isIgnoredAndReturnsTrue`) threw
`UnsupportedOperationException: cannot proxy inaccessible class
com.android.server.am.ProcessRecord` / `IllegalAccessError`. Root cause:
`ProcessRecord` is package-private (unlike the `public`
`ActivityManagerService`/`CachedAppOptimizer` already mocked inside
`runWithDexmakerShareClassLoader` in `setUp()`), and these two tests each
called `mock(ProcessRecord.class)` directly inside the `@Test` method body,
outside that wrapper — exactly the case `DexmakerShareClassLoaderRule`'s own
javadoc exists to cover ("allows mocking package-private methods").

Fixed (`0092`) with a small `createMockProcessRecord()` helper that builds
the mock inside `runWithDexmakerShareClassLoader`, reusing the existing
mechanism rather than introducing a new one (`sun.misc.Unsafe.allocateInstance`
and a full `ProcessRecordTests`-style real-constructor setup were both
considered and rejected: zero precedent for the former anywhere in this test
tree, and the latter needs a fully wired `ActivityTaskManagerService` this
test was explicitly designed to avoid). Authored the same way, in
`aegisos-platform/.worktrees/duress-quiesce-atest-mock-classloader-fix/`,
landed real SHA `d0cdc34c8227`.

**Real `atest FrameworksServicesTests:RealUserQuiescerTest` result after
both fixes: all 3 tests PASSED** (`quiesce_allProcessesFreezeCleanly_returnsTrue`,
`quiesce_singleProcessThrowsOnFreeze_returnsFalse`,
`quiesce_otherUsersProcess_isIgnoredAndReturnsTrue`), on a build-server
`sdk_phone64_x86_64-cp2a-userdebug` emulator (nested KVM). This closes
ADR-092's re-open condition 1 for `RealUserQuiescerTest` specifically, and
its condition 2 (Component B's freeze-failure path) via this same
Mockito-based coverage — the underlying `forceFreezeAppAsyncLSP` still has
no organic synchronous-exception path reachable from real hardware
(confirmed by reading `CachedAppOptimizer.freezeAppAsyncInternalLSP`: it
only posts a delayed `Handler` message and returns), so this mock-based unit
test remains the only real dynamic coverage of that catch block — a ceiling
already accepted in ADR-092 itself, not a new gap.

New real branch tip: `d0cdc34c8227`.

## Apply (this section)

```sh
cd frameworks/base && git am \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/0091-*.patch \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/0092-*.patch
```

## `0093`: UserRepositoryImplTest never actually compiled until real `atest` ran

Same re-open condition as `0091`/`0092`, other test target. First real
compile of `SystemUiRoboTests:com.android.systemui.user.data.repository.UserRepositoryImplTest`
failed: `UserRepositoryImplTest.kt:567: error: argument type mismatch:
actual type is 'FakeKeyguardRepository', but 'Lazy<KeyguardRepository>' was
expected`. Root cause: Task 6's own Dagger-cycle fix (landed as part of
`f3f3c9ebc980`, see the `0088`-`0090` entry above) changed
`UserRepositoryImpl`'s constructor to take `Lazy<KeyguardRepository>`
instead of a plain `KeyguardRepository` — but this test's own `create()`
helper was never updated to match, because `SystemUiRoboTests` had never
actually been compiled via `atest` before now (Task 7's `m systemimage`
builds only need the production `SystemUI` target).

Fixed (`0093`) by wrapping the call site: `dagger.Lazy { keyguardRepository }`
— the exact idiom already used elsewhere in this same package
(`CustomizationProviderTest.kt`), not a new pattern. Authored in
`aegisos-platform/.worktrees/duress-closure-userrepo-lazy-fix/`, landed real
SHA `93aa9b2ae960`.

**Real `atest` result after this fix: the specific test this whole plan
cares about, `userSwitcherSettings_reEvaluatesOnKeyguardTransition_withoutSettingsChange`,
PASSED** — closing ADR-092's re-open condition 1 for `UserRepositoryImplTest`
specifically. 12 of the file's other 15 test methods also passed; 16 more
parameterized variants were `ASSUMPTION_FAILED` (a `do_not_use_run_blocking=true`
parameterization skip, not a failure). **4 unrelated pre-existing tests in
the same file failed** (`refreshUsers`, `refreshUsers_sortsByCreationTime_guestUserLast`,
`userSwitcherSettings`, `isLogoutWithUserManagerEnabled_userManagerLogoutDisabled_alwaysFalse`)
— all show an expected 3-or-4-user list coming back empty (`but was: []`)
from the same fake `manager`/`tracker` setup `refreshUsers()` relies on.
This is untouched by `0093`'s one-line change (which only touches the
`keyguardRepository` parameter) and, like every other bug in this SOURCE.md
entry, was simply never exposed before because this test target never
compiled. **Not investigated further this session** — real hardware
evidence already confirms the actual production behavior these tests cover
works correctly (ADR-092's Task 8 "owner-regression" sweep), so this reads
as a Robolectric/test-double wiring gap rather than a production defect,
but that is not yet confirmed by reading the real cause. Carried forward as
a new, separate open item — not one of ADR-092's original four re-open
conditions.

New real branch tip: `93aa9b2ae960`.

## Apply (this section)

```sh
cd frameworks/base && git am \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/0091-*.patch \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/0092-*.patch \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/0093-*.patch
```
