# Source record

## Fix 2026-11-05: the upstream boot animation zip is not shipped (`build-make/0001`)

Tag `2026110200` adds the upstream project `branding` and installs its `bootanimation_zip` (GrapheneOS logo, 540 frames) into every product through `build/make` `target/product/base_product.mk` (upstream commit `90e8e1d7f2`, 2026-10-09). The boot animation service plays `/product/media/bootanimation.zip` in preference to the textures of `framework-res.apk`, so on `lynx` `2026110501` the boot showed the GrapheneOS logo and the bar window stayed black although the two PNGs of `0002` were in the image `[measured: framework-res.apk and the source blobs equal, a replay of the service on the flashed image]`. With `/product/media` hidden by an empty tmpfs the same replay showed the track `1A3B35` and the segment `4AC2A3`.

- `build-make/0001`: removes `bootanimation_zip` from `PRODUCT_PACKAGES` in `base_product.mk` and from the generic filesystem module list in `target/product/generic/Android.bp` (the emulator products). Why it diverges from upstream: see the comment in the patch.
- Base: `build/make` `3ec7131c58`, branch `freeholdos-2026110200` (new, made from the detached HEAD of the tag). Authored in a disposable repository seeded from the build server (blob checksums equal), landed with `git am -3` as `b965238b6d`, tree clean, every touched file's sha256 equal to the disposable repository's. Not built.
- Check on the next image: no `bootanimation.zip` under `/product/media`, then `BOOT-009`, then the owner's `BOOT-003`.

## Refreshed for manifest tag 2026110200 (2026-11-05)

`patches/frameworks-base/0002` was regenerated against the textures of the new upstream revision (`frameworks/base` `ad133394d443`): upstream replaced `android-logo-mask.png` and `android-logo-shine.png` again, so the old binary diff no longer applied. The post-image blobs are unchanged (`d2a775989077` and `6761fae91b8d`, read back from the new patch); only the pre-image of each file and the byte counts of the stat changed. Message, author and date are the original ones. `BOOT-009` is to be rechecked on the first image of the tag.

## Approved design

- Reference page: the internal record "lighthouse" (not published),
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

| Landed commit | Subject | Files | Why it diverges from upstream |
| --- | --- | --- | --- |
| `frameworks-base/0001` | boot clear colour matches the boot picture | `cmds/bootanimation/BootAnimation.cpp` (one line and a comment) | A display taller than the mask shows the clear colour above and below it, so it must equal the picture's top and bottom rows. |
| `frameworks-base/0002` | redraw at 60 fps | `cmds/bootanimation/BootAnimation.cpp` (the sleep constant and its comment) | The bar moves a fixed 4 px per redraw; the stock 12 fps would move it in 20 px steps. Unchanged from ADR-019. |
| `frameworks-base/0003` | boot picture assets | the two PNGs under `core/res/assets/images/` | The picture itself. The loading texture is 720 x 256: the renderer lines a texture up with the bottom edge of the mask, so only the rows behind the window are needed (0.74 MB instead of 6.2 MB). |

The exported series was squashed on 2026-10-13 into two patches: `0001` is the landed `0001` and
`0002` (clear colour and 60 fps, one file), `0002` is the landed `0003` (the two PNGs). On the
stock files the two patches give the same tree as the three landed commits applied in order,
checked in a disposable repository seeded from the build server. The table lists the three
landed commits under their landed numbers.

The three patches were authored in a disposable local repository that held only
the three files at the stock blobs (`sovereignos-platform/.worktrees/boot-lighthouse-patches`).
`git am` of the three landed patches on the stock files gives the blobs
`BootAnimation.cpp` `673b4b6d`, `android-logo-mask.png` `d2a77598` and
`android-logo-shine.png` `6761fae9` (the first version of the shine, `dff60465`, was 720 x 2160 and left
the loading bar empty, see below). The build server tree already carries the
ADR-019 versions of the clear colour and the redraw, so the change that is
landed there is a single delta commit on that state, authored the same way; it
gives the same three blobs. The package patches are the reproducible form for
a clean upstream tree.

The delta commit was landed with `git am` on 2026-10-11 as `aa9f259fd762` in
`frameworks/base`, build-server branch `sovereignos-2026091900-r2`, on top of
`eb4138d4688d` (`memory/cached-process-cap` `0001`). No build was running, the
tree was clean and `git apply --check` passed first. The blobs read back from
`HEAD` were the three above, with the first shine.

The first shine was wrong. The boot picture ran on `lynx` image `2026101101` with the loading bar missing and the
picture still. `drawTexturedQuad` maps y as `mapLinear(yStart, 0, height, -1, 1)`, GL's y-up convention, so the
2160-row shine sat on the bottom of the mask and the window (screen row 2152) showed row 1912 of it, the clear
colour. A frame captured on the device (`screencap` during a replay of the service) matched a simulation with the
shine on the bottom edge to 5e-8 of the pixels and differed by 8e-5 from the one on the top edge. The fix is a
256-row shine with the track on rows 2 to 18; the real renderer, run with it (the file bind-mounted over
`framework-res.apk` for the replay only, restored and compared by sha256 afterwards), draws the dark track and a
mint segment at changing positions in eight frames. Landed as the delta commit `fda11b7a2c9d` in
`frameworks/base` (no build was running, tree clean, `git apply --check` first); `HEAD` reads back the shine `6761fae9`
and the mask `d2a77598`. Package patch `0003` (now `0002`) was regenerated against the stock blobs and gives the same two blobs.

## Apply

On the build server, with no build running and the tree clean at the expected
`HEAD`, on a tree that does not yet carry the ADR-019 commits:

```sh
cd frameworks/base && git am <path>/branding/boot/patches/frameworks-base/000[12]-*.patch
```

## Not checked

Resemblance to existing logos, illustrations or trademarks has not been checked.
