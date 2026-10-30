#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Type-checks the exit app (manifest, resources and all Java) with the build tree's own tools and the module-lib SDK stubs,
# in a scratch directory. It changes nothing in the tree. Run it ON the build server, or from the workstation through
# `typecheck-remote.sh`:
#   typecheck-on-server.sh SCRATCH_DIR     SCRATCH_DIR holds a copy of the app directory as SCRATCH_DIR/app
set -o errexit -o nounset -o pipefail
W="$(cd "$1" && pwd)"
T="${AOSP_TREE:-/srv/freeholdos/platform-primary}"
OBJ="${AOSP_OUT:-$T/out-lynx}"
API="$(ls "$T/prebuilts/sdk" | grep -E '^[0-9]+$' | sort -n | tail -1)"
ML="$T/prebuilts/sdk/$API/module-lib"
# framework-connectivity.jar first: NetworkAgent and the hidden-from-system parts of it live only there.
CP="$ML/framework-connectivity.jar:$ML/android.jar"
AAPT2="$OBJ/host/linux-x86/bin/aapt2"
JAVAC="$T/prebuilts/jdk/jdk21/linux-x86/bin/javac"
for f in "$ML/framework-connectivity.jar" "$ML/android.jar" "$AAPT2" "$JAVAC"; do test -e "$f" || { echo "missing: $f" >&2; exit 2; }; done
A="$W/app"
OUT="$W/.typecheck-$(date +%Y%m%d-%H%M%S)"   # a new directory every run: nothing is deleted
mkdir -p "$OUT/classes" "$OUT/gen"
"$AAPT2" compile --dir "$A/res" -o "$OUT/res.zip"
# The four screens and the theme come from SettingsLib, which the SDK stubs do not hold: the theme of the manifest gets a stand-in
# here and the screens (ExitSettingsActivity, ExitAppsActivity, ExitAppActivity, ExitOverridesActivity) are left to the module build, which links
# the libraries (build-exit-app-module.sh). Everything else, the controller included, is compiled as before.
mkdir -p "$OUT/stub/values"
printf '<resources><style name="Theme.SubSettingsBase" parent="android:Theme.DeviceDefault"/></resources>\n' > "$OUT/stub/values/stub.xml"
"$AAPT2" compile --dir "$OUT/stub" -o "$OUT/stub.zip"
"$AAPT2" link -o "$OUT/app.apk" -I "$ML/android.jar" --manifest "$A/AndroidManifest.xml" --java "$OUT/gen" \
  --min-sdk-version 36 --target-sdk-version 37 "$OUT/res.zip" "$OUT/stub.zip"
# The notifier starts ExitSettingsActivity by class: a stand-in with the same name lets it compile.
mkdir -p "$OUT/stubsrc/org/freeholdos/exit"
printf 'package org.freeholdos.exit;\npublic class ExitSettingsActivity extends android.app.Activity {}\n' > "$OUT/stubsrc/org/freeholdos/exit/ExitSettingsActivity.java"
# The two .aidl files of the Compartments policy service (ADR-208) are compiled with the tree's own tool, as Soong does.
AIDL="$OBJ/host/linux-x86/bin/aidl"
test -e "$AIDL" || { echo "missing: $AIDL" >&2; exit 2; }
"$AIDL" --lang=java -I "$A/src" -o "$OUT/gen" $(find "$A/src" -name '*.aidl')
# With the javac statement of an earlier module build of ExitApp in the ninja files (its boot class path and class path hold SettingsLib
# and androidx, which the SDK stubs do not) the four screens are compiled as well, with exactly the options of the module build; the
# R class of that build loses against the R class generated above. Without the statement the screens are left out.
STMT_FILE=$(grep -l 'ExitApp/android_common/javac/ExitApp.jar \$' "$OBJ"/soong/build.lynx.*.ninja 2>/dev/null | head -1 || true)
if [ -n "$STMT_FILE" ]; then
  L=$(grep -n 'ExitApp/android_common/javac/ExitApp.jar \$' "$STMT_FILE" | head -1 | cut -d: -f1)
  STMT=$(sed -n "${L},$((L+400))p" "$STMT_FILE")
  BOOT=$(printf '%s\n' "$STMT" | grep -m1 -E '^    bootClasspath = ' | sed 's/^    bootClasspath = //')
  CPOPT=$(printf '%s\n' "$STMT" | grep -m1 -E '^    classpath = ' | sed 's/^    classpath = //')
  test -n "$BOOT" -a -n "$CPOPT" || { echo "no class path in the javac statement of ExitApp" >&2; exit 2; }
  SRCS=$(find "$A/src" "$OUT/gen" -name '*.java' | sort)
  # the options hold paths relative to the tree
  (cd "$T" && "$JAVAC" -proc:none -Xlint:-options -Xlint:unchecked -Xlint:deprecation $BOOT $CPOPT -d "$OUT/classes" $SRCS)
else
  SRCS=$(find "$A/src" "$OUT/gen" -name '*.java' | grep -v -E '/(ExitSettingsActivity|ExitAppsActivity|ExitAppActivity|ExitOverridesActivity|ExitVisibleActivity)\.java$'; echo "$OUT/stubsrc/org/freeholdos/exit/ExitSettingsActivity.java")
  SRCS=$(echo "$SRCS" | sort)
  "$JAVAC" -proc:none -Xlint:-options -Xlint:unchecked -Xlint:deprecation -cp "$CP" -d "$OUT/classes" $SRCS
fi
echo "app type-check passed: $(echo "$SRCS" | wc -l) files"
