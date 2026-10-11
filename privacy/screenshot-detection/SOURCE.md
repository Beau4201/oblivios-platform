# Source record

## Base

GrapheneOS `frameworks/base`, build-server branch `sovereignos-2026091900-r2`, on top of
`aa9f259fd762` (the boot picture, ADR-152), read 2026-10-11. The file
`services/core/java/com/android/server/wm/WindowManagerService.java` has the blob hash
`70e74188b750` (sha1 of the content) on the build server and in the disposable local repository
(`sovereignos-platform/.worktrees/screenshot-detection`) the patch was authored in.

## Patch

| Patch | Subject | Files | Why it diverges from upstream |
| --- | --- | --- | --- |
| `frameworks-base/0001` | tell no app that the user took a screenshot | `WindowManagerService.java` (one constant, one early return, a comment) | Upstream `notifyScreenshotListeners` calls the `Activity.ScreenCaptureCallback` of every visible activity that registered one, and SystemUI (`ScreenshotDetectionController`) then tells the user which apps were notified. It now notifies nothing and returns an empty list, so SystemUI shows no notice either. The upstream body stays behind the constant so a rebase conflicts on three lines only. |

What it does not do: an app that may read the media store can still see the saved screenshot file, and
`FLAG_SECURE` still blocks the capture itself. Screen recording callbacks (`WindowManager.addScreenRecordingCallback`)
are not touched.

The only caller of `notifyScreenshotListeners` is SystemUI's `ScreenshotDetectionController.maybeNotifyOfScreenshot`
(read on the build server).

Landed with `git am` on 2026-10-11 as `294b71fbc991`; no build was running and the tree was clean. The
patch file here was regenerated with `git format-patch` from that landed history.

## Apply

On the build server, with no build running and the tree clean:

```sh
cd frameworks/base && git am <path>/privacy/screenshot-detection/patches/frameworks-base/0001-*.patch
```

## Verification

- Syntax: `javac` over the changed file on the build server reports only missing symbols (no classpath), no syntax error.
- Not run: a module build alone, a device check that `Activity.registerScreenCaptureCallback` is no longer called back
  (`PRIV-001`, see the validation matrix).
