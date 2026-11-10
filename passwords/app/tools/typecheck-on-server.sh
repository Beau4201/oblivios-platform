#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Type-checks PasswordsApp (resources, manifest and Kotlin) with the build tree's own tools and the system SDK stubs, in
# a scratch directory. It changes nothing in the tree and is not a build (no Soong, no dex, no APK). Run it ON the
# build server:
#   typecheck-on-server.sh SCRATCH_DIR     SCRATCH_DIR holds a copy of the app directory as SCRATCH_DIR/app
# The Compose libraries (Task 14) are added to CLASSPATH_EXTRA by tools/compose-classpath.sh when it exists.
set -o errexit -o nounset -o pipefail
W="$(cd "$1" && pwd)"
T="${AOSP_TREE:-/srv/oblivios/platform-primary}"
OBJ="${AOSP_OUT:-$T/out-lynx}"
ANDROID_JAR="${ANDROID_JAR:-$T/prebuilts/sdk/current/system/android.jar}"
AAPT2="$OBJ/host/linux-x86/bin/aapt2"
JAVA="$T/prebuilts/jdk/jdk21/linux-x86/bin/java"
K="$T/external/kotlinc/lib"
for f in "$ANDROID_JAR" "$AAPT2" "$JAVA" "$K/kotlin-compiler.jar" "$K/kotlin-stdlib.jar"; do test -e "$f" || { echo "missing: $f" >&2; exit 2; }; done
A="$W/app"
OUT="$W/.typecheck-$(date +%Y%m%d-%H%M%S)"   # a new directory every run: nothing is deleted
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/res"

# Resources and manifest: compile, then link against the system SDK; this also checks every @-reference.
"$AAPT2" compile --dir "$A/res" -o "$OUT/res"
"$AAPT2" link -o "$OUT/app.apk" -I "$ANDROID_JAR" --manifest "$A/AndroidManifest.xml" \
  --java "$OUT/gen" --min-sdk-version 36 --target-sdk-version 37 "$OUT"/res/*.flat

CP="$ANDROID_JAR:$K/kotlin-stdlib.jar${CLASSPATH_EXTRA:+:$CLASSPATH_EXTRA}"
if [[ -x "$A/tools/compose-classpath.sh" ]]; then CP="$CP:$("$A/tools/compose-classpath.sh" "$T" "$OUT")"; fi
mapfile -t KT < <(find "$A/src" -name '*.kt' | sort)
mapfile -t JV < <(find "$OUT/gen" -name '*.java' | sort)
PLUGIN=()
if grep -q -r --include='*.kt' -E '@Composable' "$A/src" 2>/dev/null; then PLUGIN=("-Xplugin=$K/compose-compiler-plugin.jar"); fi
"$JAVA" -cp "$K/kotlin-compiler.jar:$K/kotlin-stdlib.jar:$K/kotlin-reflect.jar:$K/trove4j.jar:$K/annotations-13.0.jar" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -Xjvm-default=all \
  "${PLUGIN[@]}" -cp "$CP" -d "$OUT/classes" "${KT[@]}" "${JV[@]}" 2>&1 | grep -v '^warning: ' | tee "$OUT/kotlin.log" || true
if grep -q -E '(^|: )error:' "$OUT/kotlin.log"; then echo "app type-check FAILED: $OUT/kotlin.log" >&2; exit 1; fi
echo "app type-check passed: ${#KT[@]} Kotlin files, ${#JV[@]} generated Java files, $(ls "$A/res/drawable" | wc -l) drawables"
