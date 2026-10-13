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
# Every *Check class of tests/host is run, so that a check that is added cannot be left out of this script.
# Three of them read a file and are run below with it.
for src in "$APP"/tests/host/org/sovereignos/datamap/model/*Check.java; do
  c="$(basename "$src" .java)"
  case "$c" in LandDataCheck|LogoNameCheck|SeededCheck) continue ;; esac
  java -ea -cp "$OUT" org.sovereignos.datamap.model.$c
done
# The land file: made with tolerance 0 from the fixture and read back; the real file is read when given.
python3 "$APP/tools/make-land.py" "$APP/tests/fixtures/mini.geojson" "$OUT/mini.bin" --tolerance 0 >/dev/null
java -ea -cp "$OUT" org.sovereignos.datamap.model.LandDataCheck "$OUT/mini.bin" ${LAND_BIN:+"$LAND_BIN"}
if [ -f "$APP/tests/host/org/sovereignos/datamap/model/LogoNameCheck.java" ]; then
  java -ea -cp "$OUT" org.sovereignos.datamap.model.LogoNameCheck "$APP/tests/fixtures/logo-names.tsv"
fi
# The texture points and the city lights, against what the design page's own generators give (ADR-160).
java -ea -cp "$OUT" org.sovereignos.datamap.model.SeededCheck "$APP/tests/fixtures/atlas-seeded.txt"
# The logo tool (ADR-159), once it exists.
if [ -f "$APP/tests/logos_check.py" ]; then
  python3 -I "$APP/tests/logos_check.py"
fi
# The logos that are in the package: every file has a record, every drawable equals what the tool makes from it.
# Skipped for a copy of the app that holds no logos/ next to it (the mutation runner tests such a copy).
if [ -f "$APP/../logos/logos.tsv" ]; then
  python3 -I "$APP/tools/import-logos.py" check "$APP/../logos" "$APP/../classifier/classifier.tsv" "$APP/res/drawable"
fi
echo "all app host checks passed"
