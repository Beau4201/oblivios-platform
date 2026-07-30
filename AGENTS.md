# AGENTS.md — aegisos-platform

This file is self-contained (don't assume it merges with a global AGENTS.md —
override behavior isn't guaranteed, so everything that matters lives here).

## What this repository is

Isolated AegisOS framework and platform patches layered over pinned
GrapheneOS revisions. Android source trees, build output, vendor blobs,
signing material, and device logs do not belong here
(`docs/architecture/repository-topology.md` in `aegisos-foundation`).

## Binding governance — read before adding or touching any patch

`aegisos-foundation` (sibling repo) is the authoritative source of truth.
Per `docs/architecture/repository-topology.md`, this repository's own
creation-order rule is: created only "when the first source patch is
approved." That approval is an explicit, dated entry in
`docs/governance/decision-log.md` (an ADR) — not a design draft, not a
Superdesign export, not an "Approved design" status line on a spec whose own
scope may not even cover the patch in question.

**Hard rule: no patch is added to or applied from this repository without a
specific, corresponding ADR in `aegisos-foundation`'s decision log naming
that patch.** This rule exists because it was already violated once: the
`branding/boot/` package was committed with a `frameworks/base` patch and no
backing ADR, citing a Superdesign draft that appears nowhere in
`aegisos-foundation`'s governance records and a same-day UI spec whose own
stated first-slice scope explicitly excludes boot-screen work. Before adding
another patch, check the decision log yourself — do not assume a prior commit
in this repo means the check was already done.

No patch here may be applied to any Phase 1 (or later) baseline source tree
until the applicable review record in `aegisos-foundation/docs/reviews/`
records an `Accepted` decision for that baseline. Check
`docs/reviews/phase-1-emulator-baseline-review.md` before ever running
`git apply`/`patch` against a real source tree — as of this writing that
record is `Pending`, not `Accepted`.

## Provenance requirements

Every asset (image, font, generated file) needs documented source, license,
and a recorded hash in the package's `SOURCE.md`. Do not commit a generated
file that `tools/` doesn't produce and `expected-assets.sha256` doesn't cover
— an orphaned, unhashed asset is exactly the kind of gap that made
`branding/boot/generated/aegisos-boot-preview.png` unverifiable.

## Git discipline

- Claude Code and other AI agents may commit directly in this repository
  without asking for per-commit authorization, provided every rule below is
  followed on every commit. This does not extend to force-push, history
  rewrite, branch deletion, or any other destructive operation — those still
  require explicit owner authorization every time. A source patch itself
  still requires a backing ADR before being applied to a real tree, per the
  binding-governance section above — commit permission is not patch approval.
