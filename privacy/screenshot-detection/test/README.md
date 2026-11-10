# SSCheck

A test app, not part of the product. It registers `Activity.ScreenCaptureCallback` and logs `FIRED` (tag
`SSCheck`) when the system reports a screenshot. With the patch of this package a screenshot must not log
`FIRED`; on an unpatched image it does.

Built on the build server with the tree's host tools (`aapt2 link`, `javac --release 17`, `d8` with the JDK on
`PATH`, `zipalign`, `apksigner` with a throw-away key), min and target SDK 34. Run: install, open the app, take a
screenshot (`adb shell input keyevent KEYCODE_SYSRQ`), read `adb logcat -d -s SSCheck:I`. Remove with
`adb uninstall org.oblivios.test.sscheck`.

Run on 2026-10-11 on `lynx` image `2026101101`: `REGISTERED`, a screenshot file was written, no `FIRED`. No
unpatched image was available, so the positive control (the callback fires) is the documented upstream behaviour
and the call path read in SystemUI (`MessageContainerController.onScreenshotTaken`), not a run.
