#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Type-checks the exit probe app (manifest and Java) with the build tree's own tools and the module-lib SDK stubs, in
# a scratch directory. It changes nothing in the tree. Run it ON the build server:
#   typecheck-on-server.sh SCRATCH_DIR     SCRATCH_DIR holds a copy of the app directory as SCRATCH_DIR/app
set -o errexit -o nounset -o pipefail
W="$(cd "$1" && pwd)"
T="${AOSP_TREE:?set AOSP_TREE}"
OBJ="${AOSP_OUT:-$T/out-lynx}"
API="$(ls "$T/prebuilts/sdk" | grep -E '^[0-9]+$' | sort -n | tail -1)"
ML="$T/prebuilts/sdk/$API/module-lib"
# framework-connectivity.jar first: TestNetworkManager and the hidden-from-system parts of NetworkAgent live only there.
CP="$ML/framework-connectivity.jar:$ML/android.jar"
AAPT2="$OBJ/host/linux-x86/bin/aapt2"
JAVAC="$T/prebuilts/jdk/jdk21/linux-x86/bin/javac"
for f in "$ML/framework-connectivity.jar" "$ML/android.jar" "$AAPT2" "$JAVAC"; do test -e "$f" || { echo "missing: $f" >&2; exit 2; }; done
A="$W/app"
OUT="$W/.typecheck-$(date +%Y%m%d-%H%M%S)"   # a new directory every run: nothing is deleted
mkdir -p "$OUT/classes"
"$AAPT2" link -o "$OUT/app.apk" -I "$ML/android.jar" --manifest "$A/AndroidManifest.xml" \
  --min-sdk-version 36 --target-sdk-version 37
SRCS=$(find "$A/src" -name '*.java' | sort)
"$JAVAC" -proc:none -Xlint:-options -Xlint:unchecked -Xlint:deprecation -cp "$CP" -d "$OUT/classes" $SRCS
echo "app type-check passed: $(echo "$SRCS" | wc -l) files"
