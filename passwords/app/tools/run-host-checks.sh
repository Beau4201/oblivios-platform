#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Compiles the pure Kotlin of the Passwords app (no Android class) with the tree's kotlinc and runs the host checks.
# Run it ON the build server:   run-host-checks.sh APP_DIR [TEST_CLASS...]
# The pure sources are the files listed in tools/host-files.txt (paths below src/main/kotlin); the tests are every
# class with a `main` under tests/host. A test class must print `host checks passed: <name> <N>` with N at least 1, and a
# run in which no class printed it fails: a green exit with no check proves nothing.
set -o errexit -o nounset -o pipefail
APP="$(cd "$1" && pwd)"; shift
T="${AOSP_TREE:-/srv/freeholdos/platform-primary}"
J="$T/prebuilts/jdk/jdk21/linux-x86/bin/java"
K="$T/external/kotlinc/lib"
OUT="${HOST_OUT:-$(mktemp -d)}"
mkdir -p "$OUT"
# A directory this script made itself is removed when it ends; the server's /tmp filled with ~800 of them and every later run failed in
# mktemp (2026-10-23). A HOST_OUT given by the caller is theirs to keep.
if [[ -z "${HOST_OUT:-}" ]]; then trap 'rm -rf "$OUT" "$OUT.compile.log"' EXIT; fi
mapfile -t PURE < <(grep -v '^#' "$APP/tools/host-files.txt" | grep -v '^$' | sed "s#^#$APP/src/main/kotlin/#")
mapfile -t TESTS < <(find "$APP/tests/host" -name '*.kt' | sort)
for f in "${PURE[@]}"; do test -f "$f" || { echo "listed but missing: $f" >&2; exit 2; }; done
"$J" -cp "$K/kotlin-compiler.jar:$K/kotlin-stdlib.jar:$K/kotlin-reflect.jar:$K/trove4j.jar:$K/annotations-13.0.jar" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -Werror -cp "$K/kotlin-stdlib.jar" \
  -d "$OUT" "${PURE[@]}" "${TESTS[@]}" > "$OUT.compile.log" 2>&1 || true
grep -v '^$' "$OUT.compile.log" || true
if grep -q -E '(^|: )(error|exception):' "$OUT.compile.log"; then echo "COMPILE ERROR" >&2; exit 3; fi
classes=("$@")
if [[ ${#classes[@]} -eq 0 ]]; then
  while IFS= read -r f; do
    if grep -q '^fun main()' "$f"; then
      base="$(basename "$f" .kt)"; classes+=("org.freeholdos.passwords.hosttest.${base}Kt")
    fi
  done < <(printf '%s\n' "${TESTS[@]}")
fi
passed=0
for c in "${classes[@]}"; do
  out="$("$J" -ea -cp "$OUT:$K/kotlin-stdlib.jar" "$c" 2>&1)" || { echo "$out" >&2; echo "FAILED: $c" >&2; exit 1; }
  echo "$out"
  grep -Eq '^host checks passed: \S+ [1-9][0-9]*$' <<<"$out" && passed=$((passed + 1))
done
[[ $passed -ge 1 && $passed -eq ${#classes[@]} ]] || { echo "only $passed of ${#classes[@]} test classes reported passing checks" >&2; exit 1; }
echo "all host checks passed ($passed test classes)"
