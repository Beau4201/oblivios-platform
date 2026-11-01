#!/bin/bash
# Host test of the policy core: javac and java, no framework. Usage: run.sh
set -euo pipefail
T=$(cd "$(dirname "$0")" && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -Xlint:all -d "$OUT" "$T"/../src/com/android/server/compartments/*.java "$T"/*.java
java -ea -cp "$OUT" com.android.server.compartments.ProfilesTest
java -ea -cp "$OUT" com.android.server.compartments.PersonaTest
java -ea -cp "$OUT" com.android.server.compartments.PlacesTest
java -ea -cp "$OUT" com.android.server.compartments.RevokedLedgerTest
java -ea -cp "$OUT" com.android.server.compartments.LoopbackAppIdsTest
