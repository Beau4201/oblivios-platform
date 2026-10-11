# Source record

## Approved design

- Reference page: `sovereignos-foundation/docs/design/lighthouse-2026-10-10.html`,
  approved by the owner on 2026-10-11. The mask and the loading texture are the
  still picture and the bar of that page.
- Decision: ADR-152. It supersedes ADR-019; the Aegis artwork, its Superdesign
  draft, its generated preview and its two flat patches are removed from this
  package.

## Typeface

- Manrope SemiBold, latin subset, file `manrope-latin-600-normal.ttf`
- Source: Arch package `ttf-manrope` 5.2.5-1 (upstream fontsource.org/fonts/manrope)
- File SHA-256: `886ac5757cab2d72cc47025ee1b94ae0a621ed9cda123e6deb6fddc2b878f768`
- Licence: SIL Open Font License 1.1 with Reserved Font Name (package metadata
  `OFL-1.1-RFN`). Only glyph outlines are retained, as vector paths in
  `source/lighthouse-mask.svg`; no font is loaded or shipped. The licence text
  is not embedded in this file, so confirm the terms before release.

## Tools used to produce the committed files

- fontTools 4.66.1 (`tools/make_sources.py`)
- rsvg-convert 2.62.4 (cairo 1.18.6)
- ImageMagick 7.1.2-32 Q16-HDRI

The pixel output depends on these versions; the recorded hashes are in
`expected-assets.sha256` and checked by `tests/test_boot_assets.sh`.

## Generated assets

See `expected-assets.sha256`. No other generated file is committed.

## Patches

Base: the stock GrapheneOS files of `frameworks/base` as they were before the
ADR-019 commits (`376774862cf9^` on the build server, branch
`sovereignos-2026091900-r2`), `cmds/bootanimation/BootAnimation.cpp` unchanged upstream
(1949 lines; the clear colour is `glClearColor(0,0,0,1)` on line 803). The two
PNGs of the stock tree have the sha256 `75efac98...` (mask) and `73c2cb64...`
(shine), equal to upstream commit `0f505137b78e`.

| Patch | Subject | Files | Why it diverges from upstream |
| --- | --- | --- | --- |
| `frameworks-base/0001` | boot clear colour matches the boot picture | `cmds/bootanimation/BootAnimation.cpp` (one line and a comment) | A display taller than the mask shows the clear colour above and below it, so it must equal the picture's top and bottom rows. |
| `frameworks-base/0002` | redraw at 60 fps | `cmds/bootanimation/BootAnimation.cpp` (the sleep constant and its comment) | The bar moves a fixed 4 px per redraw; the stock 12 fps would move it in 20 px steps. Unchanged from ADR-019. |
| `frameworks-base/0003` | boot picture assets | the two PNGs under `core/res/assets/images/` | The picture itself. |

The three patches were authored in a disposable local repository that held only
the three files at the stock blobs (`sovereignos-platform/.worktrees/boot-lighthouse-patches`).
`git am` of the three exported patches on the stock files gives the blobs
`BootAnimation.cpp` `673b4b6d`, `android-logo-mask.png` `d2a77598` and
`android-logo-shine.png` `dff60465`. The build server tree already carries the
ADR-019 versions of the clear colour and the redraw, so the change that is
landed there is a single delta commit on that state, authored the same way; it
gives the same three blobs. The package patches are the reproducible form for
a clean upstream tree.

## Apply

On the build server, with no build running and the tree clean at the expected
`HEAD`, on a tree that does not yet carry the ADR-019 commits:

```sh
cd frameworks/base && git am <path>/branding/boot/patches/frameworks-base/000*.patch
```

## Not checked

Resemblance to existing logos, illustrations or trademarks has not been checked.
