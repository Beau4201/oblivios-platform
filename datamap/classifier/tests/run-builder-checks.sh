#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Checks of the classifier build (MAP-017): the good fixture builds and the device parser accepts it; each
# kind of unsourced or unlawful claim is refused, by the build or by the parser. A control that is removed
# must make a case here fail, which tests/run-builder-mutations.py shows.
# usage: run-builder-checks.sh SRC_ROOT     SRC_ROOT: see tools/check-classifier.sh
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:?usage: run-builder-checks.sh SRC_ROOT}"
W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT
GOOD="$HERE/tests/fixtures/good"
LIC="$HERE/tests/fixtures/licences.txt"
build() { "$HERE/tools/build-classifier.py" "$1" "$W/out.tsv" --licences "$LIC" >/dev/null 2>"$W/err"; }
accepted() { build "$1" && "$HERE/tools/check-classifier.sh" "$W/out.tsv" "$SRC" >/dev/null 2>"$W/err"; }
expect_refused() {   # name, case dir
  if accepted "$2"; then echo "FAIL: accepted: $1"; exit 1; fi
  echo "ok refused: $1"
}
fresh() { rm -rf "$W/case"; cp -r "$GOOD" "$W/case"; }

fresh; accepted "$W/case" || { echo "FAIL: the good fixture is refused"; cat "$W/err"; exit 1; }
echo "ok accepted: the good fixture"
a="$(cat "$W/out.tsv")"; build "$W/case"; [ "$a" = "$(cat "$W/out.tsv")" ] || { echo "FAIL: the build is not deterministic"; exit 1; }
echo "ok deterministic"
# the same data in another order gives the same bytes (sorted output)
fresh; tac "$W/case/services-fixture.tsv" > "$W/rev" && mv "$W/rev" "$W/case/services-fixture.tsv"
tac "$W/case/owners.tsv" > "$W/rev" && mv "$W/rev" "$W/case/owners.tsv"
build "$W/case"; [ "$a" = "$(cat "$W/out.tsv")" ] || { echo "FAIL: the output depends on the order of the input"; exit 1; }
echo "ok order of the input does not matter"

fresh; printf 'S-3\tBad\tGPL-3.0-only\thttps://example.invalid/3\n' >> "$W/case/sources.tsv"
expect_refused "a source whose licence is not allowed" "$W/case"
fresh; printf 'S-3\tBad\tMIT\thttp://example.invalid/3\n' >> "$W/case/sources.tsv"
expect_refused "a source with a url that is not https" "$W/case"
fresh; printf 'exc\tNo Base\tNo Base Ltd\tNL\tTestcity\t\t\tS-1\n' >> "$W/case/owners.tsv"
expect_refused "an owner without a base" "$W/case"
fresh; printf 'exa\towns\texb\t\t\n' >> "$W/case/relations.tsv"; sed -i '1d' "$W/case/relations.tsv"
expect_refused "a relation without a source id" "$W/case"
fresh; printf 'app.example.two\tghost\tS-1\n' >> "$W/case/publishers.tsv"
expect_refused "a package mapped to an unknown publisher" "$W/case"
fresh; printf 'x.example.test\texb\tspyware\tS-2\n' >> "$W/case/services-fixture.tsv"
expect_refused "a service with an unknown category" "$W/case"
fresh; printf 'y.example.test\texb\tanalytics\tS-9\n' >> "$W/case/services-fixture.tsv"
expect_refused "a service that cites a source that does not exist" "$W/case"
fresh; printf 'ads.example.test\texa\tadvertising\tS-1\n' >> "$W/case/services-fixture.tsv"
expect_refused "a service listed twice" "$W/case"
fresh; printf 'exa\towns\n' >> "$W/case/relations.tsv"
expect_refused "a record with too few fields" "$W/case"
echo "all builder checks passed"
