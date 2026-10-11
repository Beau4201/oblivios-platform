# SovereignOS boot picture

The lighthouse boot picture of the built-in boot renderer (ADR-152, which
supersedes ADR-019). Design:
`sovereignos-foundation/docs/superpowers/specs/2026-10-11-boot-picture-design.md`;
visual reference: `sovereignos-foundation/docs/design/lighthouse-2026-10-10.html`.

## What it is

`BootAnimation::android()` in `frameworks/base` (`cmds/bootanimation/BootAnimation.cpp`)
draws two textures: a static mask, centred on the display, over a texture that
scrolls horizontally at a constant 240 px/s. This package replaces both PNGs
and the clear colour:

| File | Size | Texture memory | Content |
| --- | --- | --- | --- |
| `core/res/assets/images/android-logo-mask.png` | 1080 x 2400 | 10.4 MB | The whole picture: sky, lighthouse with two beams, island, still water, the name. A 260 x 5 window at (410, 2152) is transparent. |
| `core/res/assets/images/android-logo-shine.png` | 720 x 2160 | 6.2 MB | The loading bar behind the window: a dark track with one mint segment. |

The PNG names are fixed by the renderer; the content is original artwork.

## Rules the renderer imposes

- The scrolling texture is drawn twice, side by side, from the mask's top left
  minus an offset. It must be at least as wide as the window's right edge
  (670 px), or part of the bar is blank in every pass.
  `tests/test_boot_assets.sh` simulates the renderer and fails otherwise.
- It is drawn from the mask's top, so it must reach the window: 2160 rows.
- Scrolling is linear at 240 px/s: the segment crosses the 260 px window in
  about 1.5 s and the pattern repeats every 3.0 s. Eased motion is not possible.
- A display taller than 2400 px shows the clear colour above and below the
  mask. The mask's top and bottom rows are `#030706`, the clear colour set by
  patch `0001`, so no seam shows.

## Generate and test

```sh
branding/boot/tools/generate_boot_assets.sh /tmp/boot-assets
branding/boot/tests/test_boot_assets.sh
```

Needs `rsvg-convert`, ImageMagick 7 (`magick`, `identify`) and `sha256sum`.
The SVGs in `source/` are the committed truth; `tools/make_sources.py`
documents how they were derived and is not part of the build.

The mask carries up to 0.75 of one 8-bit level of noise in the colour channels
(never alpha) to break up banding in the dark gradients. The noise is a pure
function of the pixel position, so the output is byte-identical on every run.

## Patches

`patches/frameworks-base/0001` to `0003`, to be applied in order with `git am`
on the stock GrapheneOS files; see `SOURCE.md` for the bases, the landing and
what each patch changes.

## Not done

Boot time, memory, CPU, heat and banding are not measured on a device;
marks and scene are not checked against existing logos or trademarks.
See the `BOOT-*` rows of the validation matrix.
