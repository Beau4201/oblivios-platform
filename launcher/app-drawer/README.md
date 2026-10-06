# App drawer (SovereignOS platform integration)

Launcher3 patches that make the app drawer follow the home screen's themed
icons and show the apps as automatic category groups that open as a frosted,
centred card. Design: `docs/superpowers/specs/2026-10-06-app-drawer-theming-and-categories-design.md`
in `sovereignos-foundation`, with the visual reference
`docs/design/app-drawer-2026-10-06.html` and ADR-141 in the decision log.

## Layout

- `patches/packages-apps-launcher3/` — nine patches on Launcher3: `0001` themed
  icons in the drawer, `0002` the grouping logic and its unit test, `0003` the
  group items, container view, opened card and adapter wiring, `0004` the fixes
  after the first device round, `0005` the rebinding of a group on every update, `0006` the System group, `0007` the background load of a missing themed icon, `0008` the opened group in the dark theme and with a large font, `0009` the icon cells sized by their label.
- `SOURCE.md` — base commit, what each patch changes and why it diverges from
  upstream, rebase notes and the verification so far.

## Apply

On the pinned base named in `SOURCE.md`:

```sh
git -C packages/apps/Launcher3 am <path to this directory>/patches/packages-apps-launcher3/*.patch
```

No patch of this package is applied without ADR-141, which names all nine (ADR-141, ADR-142).
Never `git am` onto a build-server branch while a build runs.

## Scope

Display and grouping logic only. No IPC, permission, exported component,
network access, telemetry or sandbox change.
