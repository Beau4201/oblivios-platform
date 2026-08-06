#!/usr/bin/env bash
set -euo pipefail

FIXTURE_PKG="org.aegisos.guardian.locationfixture"

echo "--- granting background location to fixture (test setup, not Guardian) ---"
adb shell pm grant "$FIXTURE_PKG" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$FIXTURE_PKG" android.permission.ACCESS_BACKGROUND_LOCATION

echo "--- pre-check: fixture holds background location ---"
adb shell dumpsys package "$FIXTURE_PKG" | grep -q "ACCESS_BACKGROUND_LOCATION: granted=true"

echo "--- triggering Guardian's real ingest -> approve cycle ---"
# Launch the fixture, then immediately background it via Home. MainActivity
# schedules its location request 8 seconds after onCreate() (see
# guardian/testfixtures/locationfixture/src/org/aegisos/guardian/locationfixture/MainActivity.java)
# specifically so it fires while backgrounded, since Guardian's watcher only
# flags background access. The 10-second wait below covers that 8-second
# delay plus margin. Both delays are a first estimate written without access
# to a real emulator in this worktree and may need tuning once run for real
# (Task 8).
adb shell am start -n "$FIXTURE_PKG/.MainActivity"
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 10

echo "--- independent post-check: background location is now denied ---"
if adb shell dumpsys package "$FIXTURE_PKG" | grep -q "ACCESS_BACKGROUND_LOCATION: granted=true"; then
    echo "FAIL: background location still granted"
    exit 1
fi
echo "PASS"

echo "--- foreground location permission is untouched ---"
adb shell dumpsys package "$FIXTURE_PKG" | grep -q "ACCESS_FINE_LOCATION: granted=true"
