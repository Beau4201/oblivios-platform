#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Compiles the pure Java of the probe (no Android class) and runs its host checks.
# usage: run-host-checks.sh [APP_DIR]   APP_DIR holds src/ and tests/; default: the probe-app/ next to tools/
set -o errexit -o nounset -o pipefail
APP="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
# Only the two Android-free classes are compiled here; the service needs the SDK stubs (typecheck-on-server.sh).
javac -Xlint:all -Werror -d "$OUT" "$APP"/src/org/oblivios/exitprobe/ProbeArgs.java \
  "$APP"/src/org/oblivios/exitprobe/PacketSummary.java "$APP"/tests/HostChecks.java
java -ea -cp "$OUT" HostChecks
