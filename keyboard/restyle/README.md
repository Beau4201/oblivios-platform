# Keyboard restyle (FreeholdOS platform integration)

LatinIME patches that give the on-screen keyboard its own rounded key caps, a rounded top, visible suggestion dividers and the same treatment on every page, and keep it from leaving an empty strip beside it in landscape. Design: the internal record "keyboard restyle design" (not published), with the visual reference the internal record "keyboard" (not published) and ADR-146 in the decision log.

## Layout

- `patches/packages-inputmethods-latinime/` — two patches: `0001` the restyle (resources only), `0002` the landscape fix (Java).
- `SOURCE.md` — base commit, what each patch changes and why it diverges from upstream, rebase notes and the verification.

## Apply

On the pinned base named in `SOURCE.md`:

    git -C packages/inputmethods/LatinIME am <path to this directory>/patches/packages-inputmethods-latinime/*.patch

Apply both patches together: `0001` puts the keyboard background on the full-height input view and `0002` moves it to the window background. No patch of this package is applied without ADR-146, which names both. Never `git am` onto a build-server branch while a build runs.

## Scope

Display and layout only. No IPC, permission, exported component, network access, telemetry or sandbox change.
