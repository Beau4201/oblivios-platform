# privacy/screenshot-detection

One patch for `frameworks/base` that stops the system from telling apps, and websites in an app, through
`Activity.ScreenCaptureCallback` that the user took a screenshot. It does not hide the saved file from an app that may
read the media store, and screen-recording callbacks are untouched. A check on a device ran once, on one image
(`test/README.md`); provenance and limits in [`SOURCE.md`](SOURCE.md).
