#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Runs the AirPodsStateStore stress check on the build server (kotlinc and a JDK live there) and then
# runs it again with the lock removed, which must FAIL. BUILD_HOST = <user>@<host> (local memory file).
set -o errexit -o nounset -o pipefail
: "${BUILD_HOST:?set BUILD_HOST}"
: "${PLATFORM_TREE:?set PLATFORM_TREE to the platform source root on the build host}"
: "${WORK_DIR:?set WORK_DIR to a scratch directory on the build host}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
P="$PLATFORM_TREE"
W="$WORK_DIR/p7a-stress"
ssh -n "$BUILD_HOST" "rm -rf $W && mkdir -p $W/src"
rsync -a "$HERE/app/src/org/freeholdos/airpods/ui" "$HERE/app/src/org/freeholdos/airpods/app/AirPodsStateStore.kt" "$HERE/app/test/org/freeholdos/airpods/app/StateStoreStress.kt" "$HERE/core/airpods-core.jar" "$BUILD_HOST:$W/src/"
ssh "$BUILD_HOST" bash -s <<REMOTE
set -o errexit -o nounset
P=$P; W=$W
J=\$P/prebuilts/jdk/jdk21/linux-x86/bin; K=\$P/external/kotlinc/lib
CP=\$W/src/airpods-core.jar:\$K/kotlin-stdlib.jar
build() { rm -rf \$W/out && mkdir \$W/out && \$J/javac -d \$W/out -cp \$W/src/airpods-core.jar \$W/src/ui/*.java && \
  \$J/java -cp \$K/kotlin-compiler.jar:\$K/kotlin-stdlib.jar:\$K/kotlin-reflect.jar:\$K/trove4j.jar:\$K/annotations-13.0.jar org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -cp \$CP:\$W/out -d \$W/out \$W/src/AirPodsStateStore.kt \$W/src/StateStoreStress.kt 2>&1 | grep -v '^warning:' || true; }
build
echo "== with the lock:"; \$J/java -ea -cp \$W/out:\$CP org.freeholdos.airpods.app.StateStoreStress
sed -i 's/synchronized(lock) {/run {/' \$W/src/AirPodsStateStore.kt
build
echo "== without the lock (must fail):"; if \$J/java -ea -cp \$W/out:\$CP org.freeholdos.airpods.app.StateStoreStress >/dev/null 2>&1; then echo "SURVIVED: the check does not need the lock"; exit 1; else echo "killed: lost updates detected"; fi
REMOTE
