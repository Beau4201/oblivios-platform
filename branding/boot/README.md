# AegisOS boot identity

This package implements Superdesign draft
`05aa247d-7776-4385-83f0-0f01238729be` for GrapheneOS's built-in,
low-overhead boot renderer.

## Boundary

Do not apply this package to the Phase 1 unmodified baseline tree. Generate and
review the assets now; apply them only after the baseline build and boot review
have been accepted.

## Generate

```sh
branding/boot/tools/generate_boot_assets.sh /tmp/aegisos-boot-assets
branding/boot/tests/test_boot_assets.sh
```

The generated files replace:

- `core/res/assets/images/android-logo-mask.png`
- `core/res/assets/images/android-logo-shine.png`

Apply `patches/frameworks-base-boot-background.patch` from the
`frameworks/base` repository root.

## Design mapping

Android centers the 448x800 mask on the display. On the approved 1080x2400
target this maps the mask origin to `(316, 800)`, preserving:

- shield: `(360, 921)`, `360x410`;
- wordmark line box: top `1379`, `100px`;
- loading track: `(400, 1564)`, `280x6`.

The moving texture repeats every 384 pixels. The current GrapheneOS fallback
renderer moves it at 240 pixels per second, so the bar recurs every 1.6 seconds.
The renderer remains intentionally capped at 12 fps; the browser's cubic easing
is approximated by linear texture motion to avoid a larger C++ patch.

## Typography provenance

The wordmark paths were derived from Plus Jakarta Sans weights 500 and 600 from
the Google Fonts repository. The typeface is licensed under the SIL Open Font
License 1.1. No font file or runtime font dependency is required by these boot
assets.
