#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Checks the neverallow rules against the platform policy of a build, on the build server.
# The Pixel board configurations set SELINUX_IGNORE_NEVERALLOWS, so the build checks no neverallow at all (the module
# sepolicy_neverallows is disabled by it, and the device policy is compiled with secilc -N). This script compiles the platform
# CIL of the product output without -N, so every neverallow statement of the tree is checked, not only those that name an
# exit domain: a rule that applies to an exit domain through an attribute (the statements on mlstrustedsubject, which exitd,
# exitd_route and exit_app belong to since ADR-196) would otherwise go unseen. Until 2026-10-23 the tree itself violated the
# Data Map's `neverallow ... datamap_service` (shell.te; fixed by `datamap/system-sepolicy/0004`), which is why this script
# once kept only the statements that name an exit domain or the Data Map service. On the policy of image 2026102303 the
# compile with every neverallow passes (2026-10-29), and a deliberately violated statement fails it.
#   check-neverallows.sh [PRODUCT_OUT]     default: /srv/oblivios/platform-primary/out-lynx/target/product/lynx
set -o errexit -o nounset -o pipefail
OUT="${1:-/srv/oblivios/platform-primary/out-lynx/target/product/lynx}"
BIN="${OUT%/target/product/*}/host/linux-x86/bin"
CIL="$OUT/system/etc/selinux/plat_sepolicy.cil"
[[ -f "$CIL" && -x "$BIN/secilc" ]] || { echo "no plat_sepolicy.cil or secilc under $OUT" >&2; exit 2; }
W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT
python3 -I - "$CIL" <<'PY'
import re, sys
exit_n = datamap_n = mls_n = 0
with open(sys.argv[1]) as src:
    for line in src:
        if line.startswith(("(neverallow ", "(neverallowx ")):
            is_exit = re.search(r"exit(d|_app)", line) is not None
            exit_n += is_exit
            datamap_n += "datamap_service" in line and not is_exit
            mls_n += re.search(r"mlstrusted(subject|object)", line) is not None
# Counted apart (review of 2026-10-23, C2-08): the Data Map's statements used to count towards the floor of the exit service's, so
# the exit service's could have vanished and the check still passed. The statements on mlstrustedsubject and mlstrustedobject
# name no exit domain, yet apply to exitd, exitd_route and exit_app since ADR-196 made them trusted subjects (review of
# 2026-10-28), so they have their own floor.
if exit_n < 6:
    raise SystemExit(f"only {exit_n} neverallow statements name an exit domain: the policy is not the one with the exit service")
if datamap_n < 1:
    raise SystemExit("no neverallow statement names the Data Map service: the policy is not the one with the Data Map patches")
if mls_n < 1:
    raise SystemExit("no neverallow statement names mlstrustedsubject or mlstrustedobject: the policy is not the platform policy")
print(f"{exit_n} neverallow statements of the exit service, {datamap_n} of the Data Map service and {mls_n} on the MLS trusted types in the policy")
PY
"$BIN/secilc" -m -M true -G -c 30 -o "$W/policy" -f "$W/fc" "$CIL"
echo "every neverallow rule of the platform policy holds, those of the exit service, the Data Map service and the MLS trusted types included"
