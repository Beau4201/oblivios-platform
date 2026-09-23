# SovereignOS boot identity

This package implements Superdesign draft
`05aa247d-7776-4385-83f0-0f01238729be` for GrapheneOS's built-in,
low-overhead boot renderer.

## Boundary

Do not apply this package to a Phase 1 baseline tree without a corresponding
ADR in `sovereignos-foundation/docs/governance/decision-log.md` naming this exact
patch set (`sovereignos-platform/AGENTS.md`'s hard rule). The Phase 1 baseline
itself is `Accepted (Task 5 waived)` per ADR-018 — that gate alone is not a
substitute for the per-patch ADR.

## Generate

```sh
branding/boot/tools/generate_boot_assets.sh /tmp/sovereignos-boot-assets
branding/boot/tests/test_boot_assets.sh
```

The generated files replace:

- `core/res/assets/images/android-logo-mask.png`
- `core/res/assets/images/android-logo-shine.png`

Apply both patches from the `frameworks/base` repository root:
`patches/frameworks-base-boot-background.patch` (clear color) and
`patches/frameworks-base-boot-framerate.patch` (redraw cap, see below).

## Design mapping

Android centers the 448x800 mask on the display. On the approved 1080x2400
target this maps the mask origin to `(316, 800)`, preserving:

- shield: `(360, 921)`, `360x410`;
- wordmark line box: top `1379`, `100px`;
- loading track: `(400, 1564)`, `280x6`.

The moving texture repeats every 384 pixels. Verified directly against
`BootAnimation::android()` in the real (unmodified) GrapheneOS source: the
renderer's position formula (`t = 4.0f * (time/16.667ms) / shine.width`)
resolves to a constant 240 pixels per second regardless of texture width —
this is hardcoded, not an asset parameter, so the bar recurs every 1.6
seconds and cannot be changed from this package. The approved Superdesign
draft's single sweeping bar uses `cubic-bezier(0.4, 0, 0.2, 1)` easing over
a fixed 280px track; the renderer's position math is linear in wall-clock
time with no easing at all, and structurally cannot reproduce a
non-repeating, clipped, eased sweep — only a constant-speed repeating
texture. That gap is accepted, not solved, by this package: linear texture
motion approximates the cubic easing to avoid a much larger C++ patch
(dynamic per-frame geometry, edge clipping, and an easing-curve evaluator
in the earliest boot path, before ADB or most safety nets exist).

`patches/frameworks-base-boot-framerate.patch` raises the stock redraw cap
from 12&nbsp;fps to 60&nbsp;fps (`sleepTime` constant `83333`us &rarr;
`16667`us — reusing the same 60Hz tick already used elsewhere in this
function). This does not change the 240px/s motion itself, only how often
it's resampled; owner-selected over an uncapped redraw to bound the
extra CPU/battery/thermal cost this adds to every single boot.

## Typography provenance

The wordmark paths were derived from Plus Jakarta Sans weights 500 and 600 from
the Google Fonts repository. The typeface is licensed under the SIL Open Font
License 1.1. No font file or runtime font dependency is required by these boot
assets.
