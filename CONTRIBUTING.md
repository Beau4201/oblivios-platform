# Contributing

How this repository is organised and what a change has to respect.

## Status

This repository is published as source only. No builds, images or update
service are offered, and no device is supported. Issues can be opened where the
host enables them. Pull requests are not accepted yet.

## What this repository is

Isolated SovereignOS framework and platform patches layered over pinned
GrapheneOS revisions. Android source trees, build output, vendor blobs, signing
material and device logs do not belong here. The pinned upstream revisions are
in `sovereignos-manifest`.

## How a package is laid out

Each directory with a `README.md` and a `SOURCE.md` is one package:

- `patches/<project>/NNNN-*.patch` are `git format-patch` files for one
  upstream project, named after its AOSP path with `/` replaced by `-`
  (`frameworks-base`, `packages-apps-settings`). Apply them in order with
  `git am` on the base named in the package's `SOURCE.md`.
- `SOURCE.md` is the provenance record: the base revision, what each patch does
  and why, how it was verified, and what is not verified.
- Own source files (apps, tools, tests) sit beside the patches. Each carries an
  `SPDX-License-Identifier` line.

## Rules for a patch

- **A decision first.** No patch is added without a decision record that names
  it (`docs/governance/decision-log.md` in `sovereignos-docs`).
- **Local first.** Author the change in a disposable local git repository that
  holds only the touched upstream files, commit it there, land it on the build
  branch with `git am`, then export the patch again with `git format-patch`
  from the landed history. Do not edit upstream files in place on a build tree,
  and do not trust a patch exported from the disposable repository: `git am`
  produces a different commit hash.
- **Patch files are never edited by hand.** Editing changes the patch hash and
  breaks the provenance chain. Fix a patch by landing a new commit and
  exporting again.
- **Keep the diff against upstream small and explain it.** Say in `SOURCE.md`
  why each patch diverges, so a rebase onto a new GrapheneOS release stays
  tractable. Do not weaken a mitigation that GrapheneOS already has.
- **Assets need provenance.** Every image, font or generated file needs a
  documented source, licence and recorded hash in its package's `SOURCE.md`. Do
  not commit a generated file that the package's tools do not produce and its
  hash list does not cover.
- **State what is not verified.** A change that compiled is not a change that
  works. Say which checks ran, on what, and which did not.

## Licence

Apache-2.0 (see `LICENSE` and `NOTICE`). Each patch keeps the licence of the
upstream file it modifies; `NOTICE` lists them. The name SovereignOS and its
logo are not licensed.

## Commits

Plain, functional, English messages in conventional-commits style.
