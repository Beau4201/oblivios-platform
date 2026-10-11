#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# The app compiles its own copy of the Binder interface. This fails unless the copy is byte-identical to the
# frameworks/base files, because a drifting transaction order or field would corrupt every call.
# usage: check-aidl-sync.sh FRAMEWORKS_BASE_ROOT [APP_DIR]
#   FRAMEWORKS_BASE_ROOT holds core/java/com/android/internal/datamap/*.aidl (a frameworks/base tree, or a
#   directory made with: git apply --include='core/java/com/android/internal/datamap/*.aidl' <patch 0003>).
set -o errexit -o nounset -o pipefail
FB="${1:?usage: check-aidl-sync.sh FRAMEWORKS_BASE_ROOT [APP_DIR]}"
APP="${2:-$(cd "$(dirname "$0")/.." && pwd)}"
SRC="$FB/core/java/com/android/internal/datamap"
DST="$APP/src/com/android/internal/datamap"
bad=0
[ -n "$(ls "$SRC"/*.aidl 2>/dev/null)" ] || { echo "no aidl files in $SRC" >&2; exit 2; }
for f in "$SRC"/*.aidl; do
  cmp -s "$f" "$DST/$(basename "$f")" || { echo "DIFFERENT or missing: $(basename "$f")"; bad=1; }
done
for f in "$DST"/*.aidl; do
  [ -e "$SRC/$(basename "$f")" ] || { echo "only in the app: $(basename "$f")"; bad=1; }
done
[ "$bad" -eq 0 ] && echo "aidl copies are identical"
exit "$bad"
