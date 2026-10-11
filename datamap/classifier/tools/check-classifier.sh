#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Runs the device's own classifier parser over a classifier file. A file that passes is a file the device
# reads; a file that fails is one it would discard (every service would then be "unknown").
# usage: check-classifier.sh FILE SRC_ROOT   SRC_ROOT holds frameworks/base/services/core/java/... (the pure
# classes of the datamap package), as made by tools/materialize-core.sh of the datamap package.
set -o errexit -o nounset -o pipefail
FILE="${1:?usage: check-classifier.sh FILE SRC_ROOT}"
SRC="${2:?usage: check-classifier.sh FILE SRC_ROOT}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
PKG="$SRC/frameworks/base/services/core/java/com/android/server/datamap"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -Xlint:all -Werror -d "$OUT" "$PKG"/{Category,HostnameValidator,ClassifierException,Classifier}.java \
  "$HERE/tests/host/com/android/server/datamap/ClassifierFileCheck.java"
java -ea -cp "$OUT" com.android.server.datamap.ClassifierFileCheck "$FILE"
