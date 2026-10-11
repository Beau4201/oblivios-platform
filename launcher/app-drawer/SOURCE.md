# Source record

## Base

GrapheneOS `platform_packages_apps_Launcher3`, commit
`47accce386f2abdc84e87c05b5587be6e0b0854f` (the build server checkout of
manifest tag `2026091900`, read on 2026-10-06).

The patches were authored in a disposable local git repository that held only
the touched upstream files, then landed on the build server with `git am` as
branch `sovereignos-app-drawer` of `packages/apps/Launcher3` (`0004` to `0010` were
added on top after device rounds; no history was rewritten). The files here
were regenerated with `git format-patch` from that landed history, not copied
from the local commits (`git am` gives different commit hashes). The author is
the one recorded in each patch; the committer on the server is the generic
`SovereignOS Build <build@sovereignos.local>` identity.

## Patches

| Patch | Subject | Files | Why it diverges from upstream |
| --- | --- | --- | --- |
| `0001` | theme the icons of the app drawer | `BubbleTextView.java` (3 insertions, 1 deletion) | Upstream themes only the workspace, folders and the taskbar, so the drawer stays in the apps' own colours. |
| `0002` | group apps by category | new `allapps/category/AppGrouper.kt`, new `AppGrouperTest.kt` | The drawer has no grouping upstream; `enabled_folders_in_all_apps` is declared but unused. Pure logic; its unit tests are in `AppGrouperTest.kt` (nine after `0006`). |
| `0003` | show the app drawer as category groups | new `CategoryGroups.kt`, `CategoryGroupView.kt`, `CategoryGroupSheet.kt`, `res/layout/category_group_icon.xml`, `res/values/sovereignos_app_drawer.xml`; edits to `AbstractFloatingView.java` (5 lines), `BaseAllAppsAdapter.java` (18), `AlphabeticalAppsList.java` (58), `AllAppsGridAdapter.java` (6) | Same reason as `0002`: group items, the container view, the opened card, the grouped list and two-per-row spans. |
| `0004` | fix the drawer groups after the first device round | `CategoryGroupView.kt`, `CategoryGroupSheet.kt`, `res/values/sovereignos_app_drawer.xml` | Fixes in code of `0003`: space above each container (the drawer clips its list below the first row), an app starts from its icon in the open card instead of after the card closed, the card icons use the stock folder icon size (66 dp, not 88 dp), the grip of the design. |
| `0005` | rebind the drawer groups on every update | `CategoryGroups.kt` | A group draws its icons itself and compared equal when only an icon changed, so it kept the unthemed, default-shape icons of its first binding. Stock icon items always rebind (`AdapterItem.isContentSame`); a group now does too. |
| `0006` | group every preinstalled app in System | `AppGrouper.kt`, `AppGrouperTest.kt`, `CategoryGroups.kt`, `res/values/sovereignos_app_drawer.xml` | Owner request (ADR-142): every preinstalled app (`AppInfo.runtimeStatusFlags & FLAG_SYSTEM_YES`) is in the System group whatever its category; the three fixed per-package groups of `0002` are removed. Nine unit tests, eight mutations. |
| `0007` | load the themed icon of a drawer group in the background | `CategoryGroupView.kt` | A group draws its icons itself, so an app whose themed icon is not cached yet (a generated one, for an app without a monochrome icon) kept its own colours there. The group asks the icon cache for it, once per binding, as `BubbleTextView.verifyHighRes` and the folder preview (`PreviewItemManager`) do. |
| `0008` | fix the opened group in the dark theme and with a large font | `CategoryGroupSheet.kt` | Found in the device round of 2026-10-06: the card surface (`allAppsSurfaceHighest`) stayed light in the dark theme while the text turned light; it now uses the surface of the group containers (`groupHighlight`). The icon cells had a fixed height, so with a font scale of 1.5 a label touched the next row; a cell is now at least that height and grows with its label. |
| `0009` | size the cells of the opened group by their label | `CategoryGroupSheet.kt`, `category_group_icon.xml`, `sovereignos_app_drawer.xml` | `BubbleTextView.onMeasure` sizes its top padding from the height it is given when `centerVertically` is set, so a `wrap_content` cell got a huge padding (measured: cells of 950, 475 and 229 px). The layout no longer sets `centerVertically`; a cell is as tall as its icon and label, with 12 dp below. The fixed cell height dimension is removed. |
| `0010` | fix the drawer groups after a code review | `AppGrouper.kt`, `AppGrouperTest.kt`, `CategoryGroups.kt`, `CategoryGroupSheet.kt`, `AllAppsGridAdapter.java` | Review of the final sources by an independent reviewer, each finding checked against upstream before it was taken: a group counts as a row and an item for accessibility (the row count was negative) and has two columns; the opened card removes its pre-draw listener from the observer it was added to and does not reopen a closed card (a stale listener could re-blur the drawer); the card ignores touches while it opens and closes (a second tap on the container launched an app); it announces itself, clears its blur when removed in any way and is never opened twice; the category of a system app is no longer read (a binder call per app and update). Not taken: a cached group row height (upstream caches the height of laid-out children), a fast scroller dereference (none found), the wider theming of the all-apps icons (intended, ADR-141). |
| `0011` | space the last row of drawer groups like the rows between them | `AppGrouper.kt`, `AppGrouperTest.kt`, `CategoryGroups.kt`, `CategoryGroupView.kt` | Every group had space above it and none below, so the last row touched the Private Space header. The groups of the last row keep the same space below their label; the row arithmetic is a pure function with a unit test (eleven tests, one mutation fails it). |
| `0012` | close the opened drawer group when an icon is dragged out | `CategoryGroupSheet.kt` | The card stayed open over the home screen after a drag started from one of its icons. It listens for the start of a drag (`DragController.DragListener`) and closes, as the stock icon menu (`PopupContainer`) does; its view is removed when the drag ends. Type-checked, not run on a device (`DRW-015`). |

The constants and the one behaviour change in upstream files carry a `sovereignos:` comment saying why.

## Rebase notes

The upstream lines the patches touch: `BubbleTextView.shouldUseTheme`; the
`TYPE_*` constants and `TYPE_ALL` of `AbstractFloatingView`; the view type
constants, `onCreateViewHolder` and `onBindViewHolder` of `BaseAllAppsAdapter`;
`AlphabeticalAppsList.updateAdapterItems` and its row-index loop;
`AllAppsGridAdapter.GridSpanSizer` and `incrementTotalHeight`. All new code is
in new files, so only these lines can conflict. Resources are in one new file,
so no upstream resource file changes.

## Verification

- Unit tests of `AppGrouper` (ten after `0010`), run on the build server with
  `tools/test-app-grouper.sh` in `sovereignos-docs`; nine
  mutations of the logic each fail them.
- Type-check of the whole change against the tree's compiled Launcher3 classes
  with `tools/launcher-drawer-typecheck/typecheck.sh` (in `sovereignos-docs`); a deliberately
  broken call fails it. It does not cover `BubbleTextView.java`, resource
  compilation or the layout file, and it cannot see layout or colour defects.
- Module build of `Launcher3QuickStep` and `lynx-cur-userdebug` image builds
  (`OFFICIAL_BUILD=true`, ccache) of images `2026100217` to `2026100222`, each
  with `tools/check-required-packages.sh` printing `all required
  packages present` and zero failed steps. The build logs
  `lynx-p10-gate-*.log` and `lynx-p10-image-*.log` are on the build server. The
  last image, `2026100222`, carries patches `0001` to `0010` (branch
  `sovereignos-app-drawer`, `HEAD` `448d8cbc2a`).
- Device evidence on `lynx` (2026-10-06 and 2026-10-07): rows `DRW-001` to
  `DRW-015` of the internal record "validation matrix" (not published).
  Not run: `stallion`, work tab and Private Space, TalkBack, a 5-column grid,
  other locales and RTL, dragging an icon out of the card.
- Defects found only on the device, each fixed by a later patch: containers kept
  the apps' own colours (`0005`, `0007`), the card stayed light in the dark theme
  and its cells overlapped at font scale 1.5 (`0008`), the first fix for that
  produced cells of 950, 475 and 229 px (`0009`), a second tap started an app and
  the accessibility counts were wrong (`0010`).

## Assets and access

No assets, no network access, no new permission.
