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
PURE=(Category HostnameValidator ClassifierException Classifier ServiceKeys RecentAnswers DataMapStore LinkDeriver HistoryCodec UserSettings PendingLimit ServerPlace IpMap)
FILES=()
for f in "${PURE[@]}"; do FILES+=("$PKG/$f.java"); done
javac -Xlint:all -Werror -d "$OUT" "${FILES[@]}" "$HERE"/tests/host/com/android/server/datamap/*.java
for c in HostnameValidatorCheck ClassifierCheck ServiceKeysCheck RecentAnswersCheck DataMapStoreCheck LinkDeriverCheck HistoryCodecCheck UserSettingsCheck PendingLimitCheck ServerPlaceCheck; do
  java -ea -cp "$OUT" com.android.server.datamap.$c
done
# ADR-161: the callback of netd (DataMapCollector) only passes an address on. The lookup runs on the service thread, in
# DataMapService.recordOnServiceThread; a lookup in the callback would block name resolution for every other consumer.
if grep -nE 'IpMap|placeOf|ServerPlace' "$PKG/DataMapCollector.java"; then
  echo "DataMapCollector must not look an address up" >&2
  exit 1
fi
# The reader of the address data against the file that tools/build-ipmap.py of the ipmap package made from its fixtures.
IPMAP="$HERE/../ipmap/tests/fixtures"
java -ea -cp "$OUT" com.android.server.datamap.IpMapCheck "$IPMAP/ipmap.bin" "$IPMAP/ipmap-expected.tsv"
echo "all host checks passed"
