#!/usr/bin/env bash
set -euo pipefail

FIXTURE_PKG="org.aegisos.guardian.locationfixture"

echo "--- granting background location to fixture (test setup, not Guardian) ---"
adb shell pm grant "$FIXTURE_PKG" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$FIXTURE_PKG" android.permission.ACCESS_BACKGROUND_LOCATION

echo "--- pre-check: fixture holds background location ---"
adb shell dumpsys package "$FIXTURE_PKG" | grep -q "ACCESS_BACKGROUND_LOCATION: granted=true"

echo "--- triggering Guardian's real ingest -> approve cycle ---"
# Populated in Task 4/5 once the real trigger mechanism is chosen.

echo "--- independent post-check: background location is now denied ---"
if adb shell dumpsys package "$FIXTURE_PKG" | grep -q "ACCESS_BACKGROUND_LOCATION: granted=true"; then
    echo "FAIL: background location still granted"
    exit 1
fi
echo "PASS"

echo "--- foreground location permission is untouched ---"
adb shell dumpsys package "$FIXTURE_PKG" | grep -q "ACCESS_FINE_LOCATION: granted=true"
