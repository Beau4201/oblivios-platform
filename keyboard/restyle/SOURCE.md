# Source record

## Base

GrapheneOS `platform_packages_inputmethods_LatinIME`, commit `856f017bb2c78d3a5cfa6c29e7f29ab86e1e3302` (the build server checkout of manifest tag `2026091900`, read on 2026-10-07).

The patches were authored in a disposable local git repository that held only the touched upstream trees, with the generators of `sovereignos-foundation/tools/keyboard/`, then landed with `git am` as branch `sovereignos-keyboard-restyle-4` of `packages/inputmethods/LatinIME` (the first landing, branch `sovereignos-keyboard-restyle`, built the image `2026100702`; the later landings, branches `-2` to `-4`, carry the fixes of the device round and the smaller digits of the phone and number layouts in portrait and landscape). The files here were regenerated with `git format-patch` from that landed history, not copied from the local commits (`git am` gives different commit hashes). The touched subtrees (`java/res`, `java/src`, `common/src`, `java/AndroidManifest.xml`) of the landed branch and of the local repository have identical git tree hashes.

## Patches

| Patch | Subject | Files | Why it diverges from upstream |
| --- | --- | --- | --- |
| `0001` | restyle the keyboard as rounded caps | 24 new drawables and `values/sovereignos_keyboard.xml`; rewritten selectors of the key, functional key, space bar, popup key, suggestion word and key preview; `themes-lxx-light.xml`, `themes-lxx-dark.xml`, `colors.xml`; `config.xml` (hint letter padding, digit size), `key_styles_common.xml` (shifted keysets use the sticky-on background), `key_styles_number.xml` (phone mode keys are functional), `emoji_palettes_view.xml` (margins) | Upstream draws every key in the colour of the keyboard background, so the gaps are invisible. The new drawables are generated from one template by `tools/keyboard/restyle-latinime.py`. |
| `0002` | keep the keyboard inside the cutout and system-bar insets in landscape | `LatinIME.java`, one line each in `MainKeyboardView.java` and `MoreKeysKeyboardView.java`, and two new files `KeyboardWindowBackground.java` and `WindowContentOrigin.java` (cause recorded in `docs/research/keyboard-landscape-inset-investigation.md`) | The platform lays the window out inside the cutout, which leaves an empty strip beside the keyboard in landscape. The window is laid out under the cutout, the keyboard background becomes the window background, and previews and panels are placed relative to the content view. |

Changed upstream lines carry a `sovereignos:` comment saying why.

## Rebase notes

Upstream lines touched: the two `themes-lxx-*.xml` files (inputViewStyle, three backgrounds, the strip divider, the popup panel), nine values of `colors.xml` (two of them the emoji tab colour), one dimension and one fraction of `config.xml` and one fraction of `config.xml` in `values-land`, one line each of `key_styles_common.xml` and `key_styles_number.xml`, the delete and ABC keys of `emoji_palettes_view.xml`, six selector files per theme, and in Java `LatinIME` (`setInputView`, `onComputeInsets`, `updateSoftInputWindowLayoutParameters`) and one line each of `MainKeyboardView` (two calls) and `MoreKeysKeyboardView`. Everything else is new files. Patch `0001` alone paints the keyboard background on the full-height input view; the two patches are applied together. Re-run the generators on a new base instead of resolving conflicts: they assert every replaced text and fail loudly.

## Verification

- `tools/build-vm/typecheck-latinime.sh`: `aapt2` compile and link of the resources plus `javac` of all sources, `typecheck: OK`; deliberately broken resources and Java print `typecheck: FAILED`.
- `tools/keyboard/check-centring.py`: `centring: OK`; mutations of the padding, the cap inset and the action-cap size each fail it.
- A source review of the diff (read-only) found five defects before landing; all are fixed. The platform padding of the content root once the window is laid out under the cutout, on which the landscape fix relies, was confirmed on `lynx` (the emoji panel content starts at the cutout inset while the window spans the display).
- Device evidence: rows `KBD-*` of the validation matrix. The first landing ran on `lynx` (image `2026100702`, 2026-10-08); the second landing, which fixes what that round found, has not been built.
