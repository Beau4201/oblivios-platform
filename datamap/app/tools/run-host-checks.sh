#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Compiles the pure Java of the Data Map app (no Android class) and runs its checks.
# usage: run-host-checks.sh [APP_DIR]   APP_DIR holds src/ and tests/host/; default: the app/ next to tools/
set -o errexit -o nounset -o pipefail
APP="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -Xlint:all -Werror -d "$OUT" "$APP"/src/org/sovereignos/datamap/model/*.java \
  "$APP"/tests/host/org/sovereignos/datamap/model/*.java
for c in GlobeMathCheck MapModelCheck ClusterCheck InitialsCheck MapSelectionCheck; do
  java -ea -cp "$OUT" org.sovereignos.datamap.model.$c
done
# The land file: made with tolerance 0 from the fixture and read back; the real file is read when given.
python3 "$APP/tools/make-land.py" "$APP/tests/fixtures/mini.geojson" "$OUT/mini.bin" --tolerance 0 >/dev/null
java -ea -cp "$OUT" org.sovereignos.datamap.model.LandDataCheck "$OUT/mini.bin" ${LAND_BIN:+"$LAND_BIN"}
echo "all app host checks passed"
