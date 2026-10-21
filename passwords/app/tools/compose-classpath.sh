#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Prints a classpath (colon separated) of the AndroidX libraries the app compiles against, unpacked from the aar and jar
# files of the build tree's own m2repository into the scratch directory. Used by typecheck-on-server.sh; it changes
# nothing in the tree.
#   compose-classpath.sh TREE SCRATCH
set -o errexit -o nounset -o pipefail
T="$1"; OUT="$2/androidx"
R="$T/prebuilts/sdk/current/androidx/m2repository"
mkdir -p "$OUT"
# group/artifact pairs below m2repository/androidx; the newest version directory of each is used.
ARTIFACTS=(
  credentials/credentials
  biometric/biometric
  core/core
  annotation/annotation-jvm
  lifecycle/lifecycle-common
  lifecycle/lifecycle-runtime
  fragment/fragment
  activity/activity
  savedstate/savedstate
)
cp_out=""
for a in "${ARTIFACTS[@]}"; do
  dir="$R/androidx/$a"
  [[ -d "$dir" ]] || continue
  ver="$(ls "$dir" | grep -E '^[0-9]' | sort -V | tail -n 1)"
  base="$(basename "$a")"
  if [[ -f "$dir/$ver/$base-$ver.aar" ]]; then
    unzip -q -o -j "$dir/$ver/$base-$ver.aar" classes.jar -d "$OUT/$base-$ver" && cp "$OUT/$base-$ver/classes.jar" "$OUT/$base.jar"
    cp_out="$cp_out:$OUT/$base.jar"
  elif [[ -f "$dir/$ver/$base-$ver.jar" ]]; then
    cp_out="$cp_out:$dir/$ver/$base-$ver.jar"
  fi
done
echo "${cp_out#:}"
