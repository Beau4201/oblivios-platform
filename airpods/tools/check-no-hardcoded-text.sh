#!/bin/bash
# Fails when user-visible text is hard-coded in the AirPods app: every string must be an Android
# string resource (design D5). A repo-local check, because lint is not evidenced to run in this
# build (docs/research/airpods-settings-ui-investigation.md section 5). Plain grep over layouts
# and Kotlin/Java sources; log messages are not user-visible and are not checked.
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
bad=0
check() { # $1 = description, $2 = extended regex, $3.. = files
  local what="$1" re="$2"; shift 2
  local hits; hits="$(grep -nE "$re" "$@" 2>/dev/null || true)"
  if [[ -n "$hits" ]]; then echo "HARD-CODED TEXT ($what):"; echo "$hits"; bad=1; fi
}
shopt -s globstar nullglob
SRC=("$HERE"/app/src/org/**/*.kt "$HERE"/app/src/org/**/*.java)
LAYOUTS=("$HERE"/app/res/layout*/**/*.xml)
if [[ ${#SRC[@]} -gt 0 ]]; then
  check 'literal passed to a UI setter' '\.(setTitle|setLabel|setSummary|setText|setContentDescription|setMessage)\("' "${SRC[@]}"
  check 'literal Compose/Toast text' '(Text|makeText)\([^)]*"' "${SRC[@]}"
  check 'literal named argument' '(title|label|summary|text|contentDescription)\s*=\s*"' "${SRC[@]}"
fi
if [[ ${#LAYOUTS[@]} -gt 0 ]]; then
  check 'layout attribute without @string' 'android:(text|contentDescription|hint|title|label)="[^@?]' "${LAYOUTS[@]}"
fi
if [[ $bad -eq 0 ]]; then echo "no hard-coded text found (${#SRC[@]} source files, ${#LAYOUTS[@]} layouts)"; fi
exit $bad
