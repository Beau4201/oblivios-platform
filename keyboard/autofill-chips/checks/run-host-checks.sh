#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Host checks of the autofill chips in LatinIME (ADR-191): the policy check, the static guards with their self-test and
# the mutation run. No device, no image, no Soong. Needs a JDK 17 or newer (javac and java on the PATH, or JAVA_HOME).
#   run-host-checks.sh JAVA_DIR [--no-mutations]      JAVA_DIR is the java directory of a LatinIME tree
set -o errexit -o nounset -o pipefail
JAVA_DIR="$(cd "$1" && pwd)"
HERE="$(cd "$(dirname "$0")" && pwd)"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
"$JAVAC" -Xlint:all -Werror -d "$OUT" "$JAVA_DIR/src/com/android/inputmethod/latin/suggestions/AutofillChipPolicy.java" "$HERE/AutofillChipPolicyCheck.java"
result="$("$JAVA" -ea -cp "$OUT" com.android.inputmethod.latin.suggestions.AutofillChipPolicyCheck)"
echo "$result"
# A run in which the check printed no passing line proves nothing.
grep -Eq '^host checks passed: AutofillChipPolicy [1-9][0-9]*$' <<<"$result"
python3 "$HERE/check-guards.py" --selftest
python3 "$HERE/check-guards.py" "$JAVA_DIR"
if [[ "${2:-}" != "--no-mutations" ]]; then
  LATINIME_JAVA="$JAVA_DIR" python3 "$HERE/run-mutations.py"
fi
echo "all chip host checks passed"
