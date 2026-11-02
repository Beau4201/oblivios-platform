# Source record

## Feature 2026-11-02: a password as the second credential (`packages-apps-settings/0003`)

The owner asked for a password as the Duress Unlock second credential (ADR-145 had made it PIN-only
"until a password is built"). The second credential has the type of the screen lock: a PIN under a PIN
lock, a password under a password lock. Reason `[verified-in-source]`: `SyntheticPasswordManager.java`
(`Credential type mismatch`, around line 1729) rejects a typed credential whose type differs from the
stored one, and the keyguard types the lock's own type. `frameworks/base` needs no change: the routing
(`DecoyProfileHelper.maybeRouteToDecoy`) and the wipe check (`DuressPasswordHelper.isDuressCredential`)
already handle both types.

- `packages-apps-settings/0003`: the main and set-up screens follow the type of the owner credential
  (input type, wording through one `%1$s` noun, `createPassword` or `createPin`, "Passwords don't match");
  the Auto confirm unlock length rule applies to a PIN only. When the lock type changes after the
  set-up the old decoy never matches, so the main screen says so and Change types the current value as
  the type the decoy still has (`getCredentialTypeForUser`).
- Real branch: `packages/apps/Settings` `freeholdos-2026100600`, commit `dcb63695a6f` on top of
  `363826216c8`. Authored in a disposable local repository seeded from the build server and landed
  with `git am`.
- Compiled: no. Settings has no host-side type-check here; the first module build is the check.
  Run on a device: no (rows `DUR-014` to `DUR-018` of the validation matrix).

## Correction 2026-10-15: `system-current.txt` sort order after the FreeholdOS rename (`frameworks-base/0006`)

The rename of `USER_TYPE_FULL_SOVEREIGNOS_DECOY` to `USER_TYPE_FULL_FREEHOLDOS_DECOY` (ADR-170) left the line where
the old name sorted (after `..._SECONDARY`), so `check_current_api` failed at 96% of the first `lynx` build of the
renamed tree (`2026101407`, the only failed step of 10 732). The same defect had happened at the earlier rename (see
"Correction 2026-09-26" below). `frameworks-base/0006` moves the line before `..._GUEST`; one line, nothing else.
Verified: `core/api/system-current.txt` after the patch is byte-identical to the API file the build generated
(`diff` empty, blob `131a3e7ab661`), the build server's `freeholdos-2026100600` carries it as `e9f0c1e67143`. The
rename tool does not re-sort API files; any further renamed name in a `*-current.txt` needs the same check.

## Patch series squashed again (2026-10-13) — read this before using any patch number

The twelve `frameworks-base` and four `packages-apps-settings` patches were regrouped into five and
two. Only neighbouring patches were joined, so the order is unchanged. On the bases below the new
series gives the same tree as the old series applied in order (equal tree hash after `git am` of
each in a disposable repository seeded from the build server), so nothing in the end state
changed. Every number in this file and in the records that cite it is a number of the series as
it stood before this squash (the 2026-10-09 export, or older where the text says so); the table
maps those to the exported patches. `build-release/0001` and `packages-apps-multiuser/0001` are
unchanged.

| Exported patch | Former patches |
| --- | --- |
| `frameworks-base/0001` add the decoy user type, routing and keyguard hook, and quiesce the real user | `0001`, `0002` |
| `frameworks-base/0002` hide the real user from the decoy session's UI and conceal multi-user existence | `0003` to `0006` |
| `frameworks-base/0003` authorize a single-credential decoy switch and the return path, remove the swipe | `0007` to `0009` |
| `frameworks-base/0004` report real quiesce failures; ignore a registration whose user no longer exists | `0010`, `0011` |
| `frameworks-base/0005` let Settings ask whether a PIN is the duress wipe PIN | `0012` |
| `packages-apps-settings/0001` hide the real user from Settings and present the decoy as the owner | `0001`, `0002` |
| `packages-apps-settings/0002` the Duress Unlock set-up flow, screens and the Device unlock row | `0003`, `0004` |

Bases: `frameworks/base` `40ef3305fb73` (the second `duress-unlock-dummy` patch as landed),
`packages/apps/Settings` `4ed80d47c0f`. The Settings base sits before the AirPods Settings patch
that was landed between the two Settings pairs; the patches touch different files, so the order
does not matter.

## Feature 2026-10-07: Duress Unlock Settings UI (`frameworks-base/0012`, `packages-apps-settings/0003`, `0004`)

A Settings screen to set up, change and remove the decoy, as a second row below
"Duress password" on the Device unlock page. Design:
the internal record "duress unlock settings ui design" (not published), plan:
the internal record "duress unlock settings ui" (not published).

- `frameworks-base/0012`: `ILockSettings#isDuressWipeCredential(ownerCredential,
  candidate)`, appended last. Settings cannot tell whether a decoy PIN equals the
  duress wipe PIN, and a decoy PIN equal to it would silently disable the wipe
  because decoy routing replaces the verification response before the wipe hook
  sees it. Needs the write permission and the owner credential; runs no wipe and
  no routing; zeroizes both credentials.
- `packages-apps-settings/0003`: `DuressUnlockFlow` (the order of set-up, change and
  remove, no Android types) and `DuressUnlockFlowTest` (14 JUnit tests; ten
  mutations of the flow each fail at least one).
- `packages-apps-settings/0004`: the row (`order 1010`, system user only), the main
  screen, the set-up and change screen, the Android adapter, strings in
  `strings_sovereignos.xml` (not in the upstream `strings_ext.xml`), layout and
  manifest entries.
- Real branches: `frameworks/base` `sovereignos-2026091900-r2`, commit
  `887d69072797` on top of `d0e538f28aac`; `packages/apps/Settings`
  `sovereignos-2026091900`, commits `9e55d98fe45` and `54e69e46662` on top of
  `d5b871d8aa3`. Authored in disposable local worktrees and landed with `git am`;
  these patches are exported fresh from the landed commits and re-applied onto a
  pristine copy of the base files (identical tree hashes).
- Review: one independent reviewer, no blocking defect; its important findings
  (Auto confirm unlock PIN length, the process kept foreground while the work runs,
  rotation, zeroizing in system_server, a decoy left without a PIN, two surviving
  test mutants) are fixed in these patches. The plan records each finding.
- Compiled: yes, into the `lynx` `userdebug` image `2026100226` (built 2026-10-07 on the
  tree at the commits above; `DUR-002`). Run on a device: yes, on `lynx` on
  2026-10-07; rows `DUR-003` to `DUR-013` of the validation matrix have evidence, with
  the parts that were not run stated in each row.

## Fix 2026-09-29: wrong PIN crashed SystemUI when the decoy user had been removed (`frameworks-base/0011`)

Reported by the owner on `stallion` and `lynx`: after a wrong PIN the screen
turns off, and re-entering the same PIN shows "Already tried that PIN".

**Root cause, from logcat and the device's `locksettings.db` on `stallion`.**
`decoy_profile_state` was `0:10` while user 10 no longer existed: removing the
decoy user (`pm remove-user`) does not clear the registration. On every first
wrong PIN, `DecoyProfileHelper.maybeRouteToDecoy` called
`doVerifyCredentialInner` for user 10. A removed user has no protector, so its
`LskfIdentifier` reads as a special credential, and
`SyntheticPasswordManager.getSpecialUserPersistentData` threw
`IllegalArgumentException: Unknown special user id 10` out of
`SoftwareRateLimiter.writeStats`. That crossed Binder into
`LockPatternUtils.checkCredential` in SystemUI, which crashed and restarted
(`am_crash`, pid change), which is the screen going off. A repeated identical
guess is a duplicate wrong guess: it skips the hardware write, so it does not
crash, and the bouncer shows the upstream "Already tried that PIN" message.
That message is unmodified upstream behaviour and is deliberately untouched.

**Fix.** A registration whose user is missing or `partial` (being removed) is
treated as no decoy, including the equivalent-cost dummy check, so timing
matches a device with no decoy. One file, +16/-1.

- Real branch: `frameworks/base` `sovereignos-2026091900-r2`, commit
  `c013345b2161` on top of `8b9b021f5ff1`, landed with `git am` (authored in a
  disposable local worktree, then `git am`'d). This patch is exported fresh
  from that landed commit. Not yet on `sovereignos-duress-decoy-switch`.
- Compiled in: `javap` on the built class shows
  `private static boolean decoyUserExists(int)`.
- Hardware, `stallion`, `user` build `2026092802` (unlocked, before relock):
  - The three core Duress scenarios still pass (owner to decoy; decoy to owner
    with one owner-PIN entry; reboot then only the decoy PIN: user 0 CE
    storage was unlocked only by the later owner PIN, no second prompt).
  - Reproduction state (decoy removed, registration left): five
    verifications of user 0 and none of user 10, no `am_crash`, no
    `Unknown special user`, no SystemUI restart, and "Already tried that PIN"
    on the repeated guess.
- Limits: no unit test (no test file exists for `DecoyProfileHelper`). On a
  `user` build `locksettings.db` cannot be read, so the "registration is still
  stale" premise rests on the same procedure leaving `0:10` in place earlier
  the same day. Not tested on `lynx`. Follow-up not done: clear the
  registration when the decoy user is removed.

Apply after the series above:

```sh
cd frameworks/base && git am \
  ../../credential/duress-unlock-decoy/patches/frameworks-base/0011-*.patch
```

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
  authored under `CONTRIBUTING.md`'s new local-first discipline (ADR-054): edited
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
  `CONTRIBUTING.md`.
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

## Landing history

The per-plan landing narratives (second plan, third plan, the swipe, the quiesce and cache fixes, the
closure tasks and their `atest` blockers) are in `HISTORY.md`. They describe the series as it stood
before the squashes and use its patch numbers.
