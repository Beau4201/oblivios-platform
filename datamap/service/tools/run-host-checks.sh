#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Compiles the pure Java core of the Data Map service (no Android classes) and runs its checks.
# usage: run-host-checks.sh [SRC_ROOT]   SRC_ROOT is a tree that holds frameworks/base/...; default: the
# tree materialized from the patches of this package.
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:-}"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
if [ -z "$SRC" ]; then
  echo "usage: run-host-checks.sh SRC_ROOT" >&2
  exit 2
fi
PKG="$SRC/frameworks/base/services/core/java/com/android/server/datamap"
PURE=(Category HostnameValidator ClassifierException Classifier ServiceKeys RecentAnswers DataMapStore LinkDeriver HistoryCodec UserSettings)
FILES=()
for f in "${PURE[@]}"; do FILES+=("$PKG/$f.java"); done
javac -Xlint:all -Werror -d "$OUT" "${FILES[@]}" "$HERE"/tests/host/com/android/server/datamap/*.java
for c in HostnameValidatorCheck ClassifierCheck ServiceKeysCheck RecentAnswersCheck DataMapStoreCheck LinkDeriverCheck HistoryCodecCheck UserSettingsCheck; do
  java -ea -cp "$OUT" com.android.server.datamap.$c
done
echo "all host checks passed"
