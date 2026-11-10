# App drawer (ObliviOS platform integration)

Launcher3 patches that make the app drawer follow the home screen's themed
icons and show the apps as automatic category groups that open as a frosted,
centred card. Design: the internal record "app drawer theming and categories design" (not published), with the visual reference
the internal record "app drawer" (not published) and ADR-141 in the decision log.

## Layout

- `patches/packages-apps-launcher3/` — three exported patches on Launcher3: `0001` themed
  icons in the drawer and the grouping logic with its unit test, `0002` the group items, container view,
  opened card, adapter wiring and every later fix (the squashed landed `0003` to `0012`, see `SOURCE.md`), `0003` the
  drawer apps loaded with the theme icon from the start (ADR-183).
- `SOURCE.md` — base commit, what each patch changes and why it diverges from
  upstream, rebase notes and the verification so far.

## Apply

On the pinned base named in `SOURCE.md`:

```sh
git -C packages/apps/Launcher3 am <path to this directory>/patches/packages-apps-launcher3/*.patch
```

No patch of this package is applied without ADR-141 (ADR-141, ADR-142, ADR-155; `0003`: ADR-183).
Never `git am` onto a build-server branch while a build runs.

## Scope

Display and grouping logic only. No IPC, permission, exported component,
network access, telemetry or sandbox change.
