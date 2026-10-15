#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Type-checks DataMapApp (resources, AIDL and Java) with the build tree's own tools and the system SDK stubs, in
# a scratch directory. It changes nothing in the tree. Run it ON the build server:
#   typecheck-on-server.sh SCRATCH_DIR     SCRATCH_DIR holds a copy of the app directory as SCRATCH_DIR/app
set -o errexit -o nounset -o pipefail
W="$(cd "$1" && pwd)"
T="${AOSP_TREE:-/srv/freeholdos/platform-primary}"
OBJ="${AOSP_OUT:-$T/out-lynx}"
ANDROID_JAR="${ANDROID_JAR:-$T/prebuilts/sdk/current/system/android.jar}"
AAPT2="$OBJ/host/linux-x86/bin/aapt2"
AIDL="$OBJ/host/linux-x86/bin/aidl"
JAVAC="$T/prebuilts/jdk/jdk21/linux-x86/bin/javac"
for f in "$ANDROID_JAR" "$AAPT2" "$AIDL" "$JAVAC"; do test -e "$f" || { echo "missing: $f" >&2; exit 2; }; done
A="$W/app"
OUT="$W/.typecheck-$(date +%Y%m%d-%H%M%S)"   # a new directory every run: nothing is deleted
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/res"
"$AAPT2" compile --dir "$A/res" -o "$OUT/res"
"$AAPT2" link -o "$OUT/app.apk" -I "$ANDROID_JAR" --manifest "$A/AndroidManifest.xml" \
  --java "$OUT/gen" --min-sdk-version 36 --target-sdk-version 37 "$OUT"/res/*.flat
while IFS= read -r f; do
  "$AIDL" --lang=java -o "$OUT/gen" -I "$A/src" "$f"
done < <(find "$A/src" -name '*.aidl' | sort)
SRCS=$(find "$OUT/gen" "$A/src" -name '*.java' | sort)
"$JAVAC" -proc:none -Xlint:-options -Xlint:unchecked -Xlint:deprecation -cp "$ANDROID_JAR" -d "$OUT/classes" $SRCS
echo "app type-check passed: $(echo "$SRCS" | wc -l) files"
