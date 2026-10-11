#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Compiles the alias policy of patch 0012 (plain Java, no Android classes) from the authoring worktree and
# runs the host check against it. Usage: run-alias-policy-check.sh [WORKTREE]
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
TREE="${1:-$HERE/.worktrees/airpods-alias}"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/src/com/android/bluetooth/btservice"
cp "$TREE/android/app/src/com/android/bluetooth/btservice/AirPodsAliasPolicy.java" "$OUT/src/com/android/bluetooth/btservice/"
cp "$HERE/airpods/tools/AirPodsAliasPolicyCheck.java" "$OUT/src/com/android/bluetooth/btservice/"
javac -d "$OUT/classes" "$OUT"/src/com/android/bluetooth/btservice/*.java
java -ea -cp "$OUT/classes" com.android.bluetooth.btservice.AirPodsAliasPolicyCheck
