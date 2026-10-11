#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Writes the pure Java classes of the Data Map service (no Android class) from the patches of this package into
# DEST/frameworks/base/..., the tree that run-host-checks.sh and run-mutations.py read.
# usage: materialize-core.sh DEST
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
DEST="${1:?usage: materialize-core.sh DEST}"
mkdir -p "$DEST/frameworks/base"
cd "$DEST/frameworks/base"
git init -q .
# Every patch of the series in order: later patches change the pure classes (and DataMapService, which is
# written but not compiled on the host).
for p in "$HERE"/patches/frameworks-base/*.patch; do
  git apply --include='services/core/java/com/android/server/datamap/*.java' "$p"
done
