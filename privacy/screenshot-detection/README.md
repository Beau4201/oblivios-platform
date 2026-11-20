# privacy/screenshot-detection

Two patches for `frameworks/base` that stop the system from telling apps, and websites in an app, that the user took a
screenshot (`Activity.ScreenCaptureCallback`) or records the screen (`WindowManager.addScreenRecordingCallback`). They
do not hide a saved file from an app that may read the media store. A check of the screenshot patch ran once, on one image
(`test/README.md`); the recording patch is not built or run yet. Provenance and limits in [`SOURCE.md`](SOURCE.md).
