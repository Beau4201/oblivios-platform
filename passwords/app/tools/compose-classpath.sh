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
  autofill/autofill
  biometric/biometric
  core/core
  annotation/annotation-jvm
  collection/collection-jvm
  lifecycle/lifecycle-common-jvm
  lifecycle/lifecycle-runtime-android
  lifecycle/lifecycle-runtime-compose-android
  lifecycle/lifecycle-viewmodel-android
  fragment/fragment
  activity/activity
  activity/activity-compose
  navigationevent/navigationevent-android
  navigationevent/navigationevent-compose-android
  savedstate/savedstate-android
  compose/runtime/runtime-android
  compose/runtime/runtime-saveable-android
  compose/runtime/runtime-annotation-android
  compose/ui/ui-android
  compose/ui/ui-geometry-android
  compose/ui/ui-graphics-android
  compose/ui/ui-text-android
  compose/ui/ui-unit-android
  compose/ui/ui-util-android
  compose/foundation/foundation-android
  compose/foundation/foundation-layout-android
  compose/animation/animation-android
  compose/animation/animation-core-android
  compose/material3/material3-android
  compose/material/material-ripple-android
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
echo "${cp_out#:}:$T/external/kotlinc/lib/kotlinx-coroutines-core-jvm.jar"
