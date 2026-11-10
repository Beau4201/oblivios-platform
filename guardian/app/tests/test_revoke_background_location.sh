#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

FIXTURE_PKG="org.oblivios.guardian.locationfixture"

echo "--- granting background location to fixture (test setup, not Guardian) ---"
adb shell pm grant "$FIXTURE_PKG" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$FIXTURE_PKG" android.permission.ACCESS_BACKGROUND_LOCATION

echo "--- pre-check: fixture holds background location ---"
adb shell dumpsys package "$FIXTURE_PKG" | grep -q "ACCESS_BACKGROUND_LOCATION: granted=true"

echo "--- triggering Guardian's real ingest -> approve cycle ---"
# Launch the fixture, then immediately background it via Home. MainActivity
# schedules its location request 8 seconds after onCreate() (see
# guardian/testfixtures/locationfixture/src/org/oblivios/guardian/locationfixture/MainActivity.java)
# specifically so it fires while backgrounded, since Guardian's watcher only
# flags background access. The 12-second wait below covers that 8-second
# delay plus margin -- confirmed sufficient against a real Task 8 emulator
# run (detection consistently visible well before 12s).
adb shell am start -n "$FIXTURE_PKG/.MainActivity"
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 12

echo "--- tapping Protect app (Guardian opens itself on real detection) ---"
# GuardianActivity is android:exported="false" -- it cannot be launched
# externally, including via adb (confirmed: `adb shell am start -n` on it
# fails with a Permission Denial, since shell's uid is not Guardian's own).
# GuardianWatcherService opens it directly from Guardian's own persistent
# process the moment a real incident is detected, so by the time this script
# reaches here the UI should already be on screen. Guardian does not
# auto-remediate -- approval still requires the owner's explicit tap (this
# is the intended design, not a missing feature). Confirmed real tap target
# against a 1080x1920 emulator display in Task 8; if this script is ever run
# against a different screen resolution the coordinates below will need to
# be re-derived (e.g. via `adb shell wm size` and a fresh screenshot), not
# assumed to still be correct.
adb shell input tap 787 1150
sleep 3

echo "--- independent post-check: background location is now denied ---"
if adb shell dumpsys package "$FIXTURE_PKG" | grep -q "ACCESS_BACKGROUND_LOCATION: granted=true"; then
    echo "FAIL: background location still granted"
    exit 1
fi

echo "--- foreground location permission is untouched ---"
if ! adb shell dumpsys package "$FIXTURE_PKG" | grep -q "ACCESS_FINE_LOCATION: granted=true"; then
    echo "FAIL: foreground location was unexpectedly revoked"
    exit 1
fi

echo "PASS"
