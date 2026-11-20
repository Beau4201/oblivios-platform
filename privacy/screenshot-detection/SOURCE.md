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
| `frameworks-base/0002` | tell no app that the screen is being recorded | `ScreenRecordingCallbackController.java` (one constant, one early return, a comment) | Upstream `register` stores the callback of `WindowManager.addScreenRecordingCallback` (permission `DETECT_SCREEN_RECORDING`), adds a MediaProjection watcher and calls the callback whenever one of the app's visible activities enters or leaves a recording. `register` now returns `false` (not recorded) before it stores anything, so no callback exists to dispatch to, `onProcessActivityVisibilityChanged` finds no uid and returns, and the watcher is never added. The upstream body stays behind the constant. |

What they do not do: an app that may read the media store can still see the saved screenshot file, and
`FLAG_SECURE` still blocks the capture itself. The recording patch changes only what an app is told; the recording
itself and the system's own recording notice are untouched.

### Patch 0002 (2026-11-20)

Authored from `frameworks/base` of the build server, branch `oblivios-2026110200`, head `70532eddce4d`, read-only: the one
file was fetched into a disposable local repository, its blob hash `00e1c01bbadb` (sha1 of the content) equal to the one
on the server, committed as the base, edited and exported with `git format-patch`. The file was last changed upstream by
`7cffcfdc1c14`. Android 17 `CP3A.261005.002` has a single caller path to detect a recording: `IWindowManager.registerScreenRecordingCallback`
(permission `DETECT_SCREEN_RECORDING`), enforced in `WindowManagerService.registerScreenRecordingCallback`, which calls
`ScreenRecordingCallbackController.register`; the patch sits in the controller so that every caller of it is covered.
Not landed on the build tree, not built, not run on a device.

The only caller of `notifyScreenshotListeners` is SystemUI's `ScreenshotDetectionController.maybeNotifyOfScreenshot`
(read on the build server).

Landed with `git am` on 2026-10-11 as `294b71fbc991`; no build was running and the tree was clean. The
patch file here was regenerated with `git format-patch` from that landed history.

## Apply

On the build server, with no build running and the tree clean:

```sh
cd frameworks/base && git am <path>/privacy/screenshot-detection/patches/frameworks-base/000*.patch
```

## Verification

- Syntax: `javac` over the changed file on the build server reports only missing symbols (no classpath), no syntax error.
- Patch `0002`: `javac` over the changed file reports 80 errors, all missing symbols or the override errors that follow from them, none within the changed lines and no syntax error; `git format-patch` against the base blob `00e1c01bbadb` (the server's) gives an `index` line that `git am` accepts only on that blob.
- Not run: a module build alone, a device check of `0002` (an app that calls `addScreenRecordingCallback` while the screen is recorded gets no callback and an initial state of not recorded), a device check that `Activity.registerScreenCaptureCallback` is no longer called back
  (`PRIV-001`, see the validation matrix).
