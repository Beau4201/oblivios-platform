#!/bin/sh
# Compiles SettingsHardening.java from the exported patch and runs the host check.
# Usage: run-settings-test.sh <SettingsHardening.java>
set -e
src=${1:?path of SettingsHardening.java}
dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT
here=$(dirname "$0")
javac -d "$dir" "$src" "$here/SettingsHardeningTest.java"
java -cp "$dir" com.android.providers.settings.SettingsHardeningTest
