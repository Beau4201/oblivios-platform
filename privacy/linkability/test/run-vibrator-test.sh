#!/bin/sh
# Compiles VibratorCoarse.java from the exported patch and runs the host check.
# Usage: run-vibrator-test.sh <VibratorCoarse.java>
set -e
src=${1:?path of VibratorCoarse.java}
dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT
here=$(dirname "$0")
javac -d "$dir" "$src" "$here/VibratorCoarseTest.java"
java -cp "$dir" android.os.VibratorCoarseTest
