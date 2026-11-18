#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Type-checks the Android classes of the Data Map service (and generates the Java of its AIDL) with the
# build tree's own tools and the jars of an earlier build, in a scratch directory. It changes nothing
# in the tree. Run it ON the build server:   typecheck-on-server.sh SCRATCH_DIR
# SCRATCH_DIR holds a frameworks/ tree with the new and changed files and the whole com/android/internal/datamap
# directory of AIDL files (a changed AIDL file with its tree copy on the include path is a duplicate to aidl).
set -o errexit -o nounset -o pipefail
W="$(cd "$1" && pwd)"
T="${AOSP_TREE:?set AOSP_TREE}"
OBJ="${AOSP_OUT:-$T/out-lynx}"
I="$OBJ/soong/.intermediates/frameworks/base"
FRAMEWORK="$I/framework-minus-apex/android_common/combined/framework.jar"
SERVICES="$I/services/core/services.core.unboosted/android_common/turbine-combined/services.core.unboosted.jar"
AIDL="$OBJ/host/linux-x86/bin/aidl"
JAVAC="$T/prebuilts/jdk/jdk21/linux-x86/bin/javac"
for f in "$FRAMEWORK" "$SERVICES" "$AIDL" "$JAVAC"; do test -e "$f" || { echo "missing: $f" >&2; exit 2; }; done
OUT="$W/.typecheck-$(date +%Y%m%d-%H%M%S)"   # a new directory every run: nothing is deleted
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/stub/com/android/server"
# The audit tags exist only after the logtags change is built; stand in for the three generated writers.
cat > "$OUT/stub/com/android/server/EventLogTags.java" <<'JAVA'
package com.android.server;
public class EventLogTags {
    public static void writeDatamapCollecting(int user, int enabled) {}
    public static void writeDatamapHistory(int user, int enabled, int days) {}
    public static void writeDatamapCleared(int user) {}
}
JAVA
while IFS= read -r f; do
  "$AIDL" --lang=java -o "$OUT/gen" -I "$W/frameworks/base/core/java" "$f"
done < <(find "$W/frameworks" -name '*.aidl' | sort)
SRCS=$(find "$OUT/gen" "$OUT/stub" "$W/frameworks/base/services/core/java/com/android/server/datamap" -name '*.java' | sort)
"$JAVAC" -proc:none -Xlint:-options -nowarn -cp "$FRAMEWORK:$SERVICES" -d "$OUT/classes" $SRCS
echo "type-check passed: $(echo "$SRCS" | wc -l) files"
