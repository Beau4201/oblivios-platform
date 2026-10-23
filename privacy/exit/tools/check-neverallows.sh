#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Checks the neverallow rules of the exit service against the platform policy of a build, on the build server.
# The Pixel board configurations set SELINUX_IGNORE_NEVERALLOWS, so the build checks no neverallow at all (the module
# sepolicy_neverallows is disabled by it, and the device policy is compiled with secilc -N). This script compiles the platform
# CIL of the product output without -N, keeping only the neverallow statements that name an exit domain or the Data Map
# service: a violation of one of them fails the compile. (Compiled with every neverallow of the tree, secilc stops at other
# neverallows of the tree; the Data Map's `neverallow ... datamap_service` was violated by shell.te until the sepolicy patch
# `datamap/system-sepolicy/0004`, 2026-10-23, and is checked here since.)
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
exit_n = datamap_n = 0
with open(sys.argv[1]) as src, open(sys.argv[2], "w") as out:
    for line in src:
        if line.startswith(("(neverallow ", "(neverallowx ")):
            is_exit = re.search(r"exit(d|_app)", line) is not None
            is_dm = "datamap_service" in line
            if not (is_exit or is_dm):
                continue
            exit_n += is_exit
            datamap_n += is_dm and not is_exit
        out.write(line)
# Counted apart (review of 2026-10-23, C2-08): the Data Map's statements used to count towards the floor of the exit service's, so
# the exit service's could have vanished and the check still passed.
if exit_n < 6:
    raise SystemExit(f"only {exit_n} neverallow statements name an exit domain: the policy is not the one with the exit service")
if datamap_n < 1:
    raise SystemExit("no neverallow statement names the Data Map service: the policy is not the one with the Data Map patches")
print(f"{exit_n} neverallow statements of the exit service and {datamap_n} of the Data Map service kept")
PY
"$BIN/secilc" -m -M true -G -c 30 -o "$W/policy" -f "$W/fc" "$W/mine.cil"
echo "the neverallow rules of the exit service and of the Data Map service hold"
