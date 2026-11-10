#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Compiles the pure-Java UI logic of AirPodsApp against airpods-core.jar and runs both host checks.
# No Android classes are needed: everything under app/src/org/oblivios/airpods/ui is plain Java.
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
JAR="$HERE/core/airpods-core.jar"
javac -d "$OUT" -cp "$JAR" "$HERE"/app/src/org/oblivios/airpods/ui/*.java "$HERE"/app/test/org/oblivios/airpods/ui/*.java
java -ea -cp "$OUT:$JAR" org.oblivios.airpods.ui.UiLogicCheck
java -ea -cp "$OUT:$JAR" org.oblivios.airpods.ui.SessionLogicCheck
java -ea -cp "$OUT:$JAR" org.oblivios.airpods.ui.EarDetectionCheck
java -ea -cp "$OUT:$JAR" org.oblivios.airpods.ui.ConversationAwarenessCheck
java -ea -cp "$OUT:$JAR" org.oblivios.airpods.ui.Plan7bCheck
java -ea -cp "$OUT:$JAR" org.oblivios.airpods.ui.ConnectionSheetCheck
java -ea -cp "$OUT:$JAR" org.oblivios.airpods.ui.Plan9Check
