#!/bin/bash
# Host test of SensorBiasFold.h against the real hardware/sensors.h.
# Usage: AOSP_ROOT=<tree, or a copy that has hardware/ and system/> run.sh <dir that holds SensorBiasFold.h>
set -euo pipefail
T=$(cd "$(dirname "$0")" && pwd)
H=$(cd "$1" && pwd)
: "${AOSP_ROOT:?set AOSP_ROOT}"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
g++ -std=c++17 -Wall -Werror -I"$H" \
  -I"$AOSP_ROOT/hardware/libhardware/include" -I"$AOSP_ROOT/system/core/libcutils/include" \
  -I"$AOSP_ROOT/system/core/include" -I"$AOSP_ROOT/system/logging/liblog/include" \
  "$T/SensorBiasFold_test.cpp" -o "$OUT/bias_test"
"$OUT/bias_test"
