#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Checks the neverallow rules of the exit service against the platform policy of a build, on the build server.
# The Pixel board configurations set SELINUX_IGNORE_NEVERALLOWS, so the build checks no neverallow at all (the module
# sepolicy_neverallows is disabled by it, and the device policy is compiled with secilc -N). This script compiles the platform
# CIL of the product output without -N, keeping only the neverallow statements that name an exit domain: a violation of one of
# them fails the compile. (Compiled with every neverallow of the tree, secilc stops at the Data Map's
# `neverallow ... datamap_service` that shell.te violates, which the build does not show.)
#   check-neverallows.sh [PRODUCT_OUT]     default: /srv/freeholdos/platform-primary/out-lynx/target/product/lynx
set -o errexit -o nounset -o pipefail
OUT="${1:-/srv/freeholdos/platform-primary/out-lynx/target/product/lynx}"
BIN="${OUT%/target/product/*}/host/linux-x86/bin"
CIL="$OUT/system/etc/selinux/plat_sepolicy.cil"
[[ -f "$CIL" && -x "$BIN/secilc" ]] || { echo "no plat_sepolicy.cil or secilc under $OUT" >&2; exit 2; }
W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT
python3 -I - "$CIL" "$W/mine.cil" <<'PY'
import re, sys
mine = 0
with open(sys.argv[1]) as src, open(sys.argv[2], "w") as out:
    for line in src:
        if line.startswith(("(neverallow ", "(neverallowx ")):
            if not re.search(r"exit(d|_app)", line):
                continue
            mine += 1
        out.write(line)
if mine < 6:
    raise SystemExit(f"only {mine} neverallow statements name an exit domain: the policy is not the one with the exit service")
print(f"{mine} neverallow statements of the exit service kept")
PY
"$BIN/secilc" -m -M true -G -c 30 -o "$W/policy" -f "$W/fc" "$W/mine.cil"
echo "the neverallow rules of the exit service hold"
