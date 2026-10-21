#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Compiles the pure Java of the exit app (no Android class) and runs its host checks, including the golden vectors of the
# two control protocols. The vectors file is identical to freeholdos-services/exit-daemon/tests/vectors.txt (the Rust tests
# load that one); when a sibling services checkout exists the two are compared and a difference fails the run.
# usage: run-host-checks.sh [APP_DIR]   APP_DIR holds src/ and tests/; default: the app/ next to tools/
set -o errexit -o nounset -o pipefail
APP="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
SERVICES_VECTORS="$APP/../../../../freeholdos-services/exit-daemon/tests/vectors.txt"
if [[ -f "$SERVICES_VECTORS" ]] && ! cmp -s "$SERVICES_VECTORS" "$APP/tests/vectors.txt"; then
  echo "tests/vectors.txt differs from the daemon's copy: the two codecs are no longer checked against one file" >&2
  exit 1
fi
# Only the Android-free classes are compiled here (every file of src/ that imports nothing from android.*); the classes that
# use the framework need the SDK stubs (typecheck-on-server.sh).
PURE=$(grep -L '^import android\.' "$APP"/src/org/freeholdos/exit/*.java || true)
# shellcheck disable=SC2086
javac -Xlint:all -Werror -d "$OUT" $PURE "$APP"/tests/HostChecks.java "$APP"/tests/ControllerChecks.java
java -ea -Dvectors="$APP/tests/vectors.txt" -cp "$OUT" HostChecks
