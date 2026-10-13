#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Builds ipmap.bin from the seven pinned input files and data/anchors.tsv, and prints what it made.
# usage: make-ipmap.sh INPUT_DIR OUT.bin
#   INPUT_DIR holds the files named in PINS.sha256. They are fetched by hand from the places SOURCES.md names and kept
#   (the "latest" release of ip-location-db changes every day, so a pin can only be met from an archived copy).
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
IN="${1:?usage: make-ipmap.sh INPUT_DIR OUT.bin}"
OUT="${2:?usage: make-ipmap.sh INPUT_DIR OUT.bin}"
(cd "$IN" && sha256sum --check --strict "$HERE/PINS.sha256")
python3 -I "$HERE/tools/build-ipmap.py" \
  --country4 "$IN/server-country-ipv4.csv" --country6 "$IN/server-country-ipv6.csv" \
  --asn4 "$IN/origin-asn-ipv4.csv" --asn6 "$IN/origin-asn-ipv6.csv" \
  --city4 "$IN/dbip-city-ipv4.csv.gz" --anycast4 "$IN/anycast-v4.csv" --anycast6 "$IN/anycast-v6.csv" \
  --anchors "$HERE/data/anchors.tsv" --sources "$(cat "$HERE/PINS.sha256")" --out "$OUT"
sha256sum "$OUT"
