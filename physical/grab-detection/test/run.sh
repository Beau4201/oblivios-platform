#!/bin/bash
# Host test of GrabDetector.java.
# Usage: run.sh <dir that holds com/android/server/grab/GrabDetector.java>
set -euo pipefail
T=$(cd "$(dirname "$0")" && pwd)
H=$(cd "$1" && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -Xlint:all -Werror -d "$OUT" \
  "$H/com/android/server/grab/GrabDetector.java" \
  "$T/com/android/server/grab/GrabDetectorTest.java"
java -cp "$OUT" com.android.server.grab.GrabDetectorTest
