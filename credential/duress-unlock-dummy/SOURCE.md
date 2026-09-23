# Source record

## Commits (verbatim from `git format-patch`, real build-server history)

### `frameworks/base`

- `e37c96c886ea1228ce69644cd8033390446e03f4` — "sovereignos: add hardware-backed
  duress dummy-cost credential check" (`patches/frameworks-base/0001`)
- `e6e5746502a3ea1b8970210cb9ccbfac979e490d` — "sovereignos: clear stale state
  before duress dummy re-enrollment" (`patches/frameworks-base/0002`)

### `system/sepolicy`

- `a99b535ec4bc0d38580c17ae42a6f1f1030c4228` — "sovereignos: add SELinux type
  for duress-dummy metadata storage" (`patches/system-sepolicy/0001`)
- `2a0e1afc6c4447a1c965b88d573a5f483b36c1ee` — "sovereignos: add test-data
  coverage for duress-dummy metadata context" (`patches/system-sepolicy/0002`)
- `3f1a1c9e296067c3d8cfce3e9a5218c1215106a4` — "sovereignos: make duress-dummy
  metadata type private, not public" (`patches/system-sepolicy/0003`)

### `system/core`

- `c55eb4e7920d4bac88a8d541e9c0ef8594b898c7` — "sovereignos: create
  /metadata/duress-dummy at boot" (`patches/system-core/0001`)

## Verification trail (not asset provenance — no images/fonts in this package)

Unlike `branding/boot/`, this package carries no binary assets, so there is
no `expected-assets.sha256`. The equivalent verification record is the real
build+boot+review trail in `sovereignos-foundation`:

- `.superpowers/sdd/duress-dummy-task-1-report.md` — real timing measurement
  ruling out a software-only dummy.
- `.superpowers/sdd/duress-dummy-task-2-report.md` — real implementation,
  the SELinux root-cause investigation, emulator build/boot/logcat evidence.
- `.superpowers/sdd/duress-dummy-task-2-fix-report.md` — the stale-state-clear
  defect fix (`frameworks-base/0002` above), with real build+boot re-verification.
- `.superpowers/sdd/duress-dummy-task-3-report.md` — the physical `lynx`
  build that surfaced `system-sepolicy/0002` and `0003` (real full-`droid`-build
  failures the emulator build never exercised), plus 13/13 AVB signing
  verification on two independent machines.

Two independent task reviews ran against this diff (spec compliance + code
quality); one found a real defect (fixed in `frameworks-base/0002`,
independently re-reviewed and approved).
