#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Writes the pure Java classes of the Data Map service (no Android class) from patch 0001 of this package into
# DEST/frameworks/base/..., the tree that run-host-checks.sh and run-mutations.py read.
# usage: materialize-core.sh DEST
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
DEST="${1:?usage: materialize-core.sh DEST}"
mkdir -p "$DEST/frameworks/base"
cd "$DEST/frameworks/base"
git init -q .
git apply --include='services/core/java/com/android/server/datamap/*.java' "$HERE"/patches/frameworks-base/0001-*.patch
