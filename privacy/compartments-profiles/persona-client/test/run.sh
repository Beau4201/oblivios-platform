#!/bin/bash
# Host test of the persona reader that the Telephony, Wi-Fi and Bluetooth code copy: javac and java over a stand-in of
# android.system.Os, no framework. Usage: run.sh
set -euo pipefail
T=$(cd "$(dirname "$0")" && pwd)
S=$T/../../system-server/src/com/android/server/compartments
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -Xlint:all -d "$OUT" "$S/PersonaFile.java" "$T"/../src/PersonaReader.java "$T"/PersonaReaderTest.java "$T"/stubs/android/system/*.java
java -ea -cp "$OUT" com.android.server.compartments.PersonaReaderTest

# PersonaProcess: one JVM per scenario, because a process is bound once
OUT2=$(mktemp -d)
trap 'rm -rf "$OUT" "$OUT2"' EXIT
javac -Xlint:all -d "$OUT2" "$T"/../framework/PersonaProcess.java "$T"/PersonaProcessTest.java "$T"/stubs/android/os/Bundle.java "$T"/stubs/android/content/Intent.java
for s in none null full badzone partial twice; do
  java -ea -cp "$OUT2" com.android.internal.compartments.PersonaProcessTest "$s"
done
