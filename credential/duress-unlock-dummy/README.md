# Duress Unlock — hardware-backed dummy-cost credential check

This package implements Stage 1 of SovereignOS's Duress Unlock feature: a
cost-symmetric "dummy" credential check, so that a failed real-credential
attempt costs the same real GateKeeper/Weaver round-trip whether or not an
SovereignOS decoy profile is registered on the device. Without this, failed-login
timing would itself reveal whether a decoy profile exists — the exact
side-channel this package closes.

Design: the internal record "duress dummy candidate b design" (not published).
Plan and full real execution record (build/boot/logcat evidence, two task
reviews, one fixed defect): the internal record "duress dummy cost primitive" (not published).

## Boundary

Do not apply this package to a Phase 1 (or later) baseline tree without a
corresponding ADR in `docs/governance/decision-log.md` in `sovereignos-docs`
naming this exact patch set (`CONTRIBUTING.md`'s hard rule). That
ADR is **ADR-040** (2026-08-15, updated 2026-08-16) — it names the exact
commit hashes below. The Phase 1 baseline itself is
`Accepted baseline (qualified, ADR-025)` per
the internal record "phase 1 emulator baseline review" (not published).

## What this is not

This is only the dummy-cost primitive — a prerequisite, not the feature.
`verifySpecialUserCredential(USER_DURESS_DUMMY, ...)` is provisioned at boot
but is **not called from any real code path yet**. Wiring it into the real
keyguard failure path (`doVerifyCredentialInner`), the freeze orchestrator,
and the Settings setup UI is Stage 2 — not started, not part of this package.

## Provenance

Real, dated commits on the build server's `platform-primary` checkout,
branch `aegisos-duress-dummy-primitive` (a disposable, single-copy AOSP
checkout — this package is the durable record; the checkout's own
operational history is not tracked in version control). Generated via
`git format-patch`, one file per real commit; the diff content is
unmodified, but the commit author identity was rewritten from the build
server's local git config to a generic `SovereignOS Build` identity before
committing here, per this project's standing rule against copying host/
account details into commits.

| Tree | Base commit | Patches |
| --- | --- | --- |
| `frameworks/base` | `7d7001f9c4e4` (`sovereignos: apply approved boot-identity assets`, ADR-019) | `patches/frameworks-base/0001` |
| `system/sepolicy` | `87a06ed71fd6` (`update zygote policy for zygote_next exec spawning support`, upstream) | `patches/system-sepolicy/0001` |
| `system/core` | `84ebea5e2111` (`use lazy preloading for compat zygote`, upstream) | `patches/system-core/0001` |

Apply in numeric order within each tree, from that tree's own repository
root, e.g.:

```sh
cd frameworks/base && git am ../../credential/duress-unlock-dummy/patches/frameworks-base/*.patch
cd system/sepolicy && git am ../../credential/duress-unlock-dummy/patches/system-sepolicy/*.patch
cd system/core && git am ../../credential/duress-unlock-dummy/patches/system-core/*.patch
```

The sepolicy and `frameworks/base` commits named below are the landed ones; the exported patches
are squashed (one per tree, see `SOURCE.md`).

## Why three trees, and why `system/sepolicy`/`system/core` exist at all

The original plan scoped this to `frameworks/base` alone. Real execution (not
assumption) found this insufficient twice, on two different full builds:

1. **Emulator build** (Task 2): `system_server` cannot create an arbitrary
   new `/metadata` subdirectory — a deliberate GrapheneOS SELinux hardening
   boundary (only `search` on the generic `metadata_file` type; every real
   `/metadata` consumer has its own dedicated type plus an `init.rc`-created
   directory). The first sepolicy commit adds a narrowly-scoped new type
   (`duress_dummy_metadata_file`) — it does **not** widen the generic grant.
   `patches/system-core/0001` adds the matching `init.rc` mkdir, in the
   generic `/metadata` block (matching `watchdog`/`tradeinmode`'s own
   unconditional pattern — not a device-conditional file like repair-mode's
   own device-tree-specific init script, since this feature has no
   device-capability gate).
2. **Physical `lynx` build** (Task 3, full `droid` goal — the emulator build
   never exercises these two checks): the second sepolicy commit adds
   test-data coverage `plat_file_contexts_data_test` requires and the
   emulator build never enforced. The third moves the
   new type from `public/file.te` to `private/file.te` — `se_freeze_test`
   rejects any new *public* type added after this device's vendor API
   freeze, and grepping the full tree (including `device/`/`vendor/`)
   confirmed nothing outside `private/` ever references this type, so it
   never needed to be public.

The second `frameworks/base` commit fixes a real defect a task review found
(`provisionDuressDummyIfNeeded` never cleared stale GateKeeper/Weaver state
before re-enrolling, unlike the real per-profile path it mirrors) —
independently re-reviewed and approved before landing here.

## Real measured cost (why this exists at all)

A real per-profile credential check (`unlockLskfBasedProtector`) measured
38.51ms mean on the emulator. A software-only dummy (scrypt stretch, no
hardware round-trip — GrapheneOS's own wipe-PIN check shape) measured
2.37ms mean — a 16.3x gap, non-overlapping ranges, ruling out a software-only
dummy by real data. This package's dummy instead reaches the real
GateKeeper/Weaver HAL via the same `verifySpecialUserCredential` path a real
per-profile check uses. Measured real dummy-check cost on the emulator:
147.03ms — about 3.8x the 38.51ms figure rather than closely matching,
plausibly a cold-boot-vs-warm-HAL difference, not confirmed as of this
writing. Physical hardware timing (which HAL backend `lynx` actually uses,
and its real cost) is Task 3's job — see the plan file for current status.
