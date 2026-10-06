# Source record

## Base

GrapheneOS `platform_packages_apps_Launcher3`, commit
`47accce386f2abdc84e87c05b5587be6e0b0854f` (the build server checkout of
manifest tag `2026091900`, read on 2026-10-06).

The patches were authored in a disposable local git repository that held only
the touched upstream files, then landed on the build server with `git am` as
branch `sovereignos-app-drawer` of `packages/apps/Launcher3` (`0004` was added
on top after the first device round; no history was rewritten). The files here
were regenerated with `git format-patch` from that landed history, not copied
from the local commits (`git am` gives different commit hashes). The author is
the one recorded in each patch; the committer on the server is the generic
`SovereignOS Build <build@sovereignos.local>` identity.

## Patches

| Patch | Subject | Files | Why it diverges from upstream |
| --- | --- | --- | --- |
| `0001` | theme the icons of the app drawer | `BubbleTextView.java` (3 insertions, 1 deletion) | Upstream themes only the workspace, folders and the taskbar, so the drawer stays in the apps' own colours. |
| `0002` | group apps by category | new `allapps/category/AppGrouper.kt`, new `AppGrouperTest.kt` | The drawer has no grouping upstream; `enabled_folders_in_all_apps` is declared but unused. Pure logic with seven unit tests. |
| `0003` | show the app drawer as category groups | new `CategoryGroups.kt`, `CategoryGroupView.kt`, `CategoryGroupSheet.kt`, `res/layout/category_group_icon.xml`, `res/values/sovereignos_app_drawer.xml`; edits to `AbstractFloatingView.java` (5 lines), `BaseAllAppsAdapter.java` (18), `AlphabeticalAppsList.java` (58), `AllAppsGridAdapter.java` (6) | Same reason as `0002`: group items, the container view, the opened card, the grouped list and two-per-row spans. |
| `0004` | fix the drawer groups after the first device round | `CategoryGroupView.kt`, `CategoryGroupSheet.kt`, `res/values/sovereignos_app_drawer.xml` | Fixes in code of `0003`: space above each container (the drawer clips its list below the first row), an app starts from its icon in the open card instead of after the card closed, the card icons use the stock folder icon size (66 dp, not 88 dp), the grip of the design. |

The constants and the one behaviour change in upstream files carry a `sovereignos:` comment saying why.

## Rebase notes

The upstream lines the patches touch: `BubbleTextView.shouldUseTheme`; the
`TYPE_*` constants and `TYPE_ALL` of `AbstractFloatingView`; the view type
constants, `onCreateViewHolder` and `onBindViewHolder` of `BaseAllAppsAdapter`;
`AlphabeticalAppsList.updateAdapterItems` and its row-index loop;
`AllAppsGridAdapter.GridSpanSizer` and `incrementTotalHeight`. All new code is
in new files, so only these lines can conflict. Resources are in one new file,
so no upstream resource file changes.

## Verification so far

- Seven unit tests of `AppGrouper`, run on the build server with
  `tools/build-vm/test-app-grouper.sh` of `sovereignos-foundation`; six
  mutations of the logic each fail them.
- Type-check of the whole change against the tree's compiled Launcher3 classes
  with `tools/build-vm/launcher-drawer-typecheck/typecheck.sh`; a deliberately
  broken call fails it. It does not cover `BubbleTextView.java`, resource
  compilation or the layout file.
- The build and the device evidence are not recorded here yet.

## Assets and access

No assets, no network access, no new permission.
