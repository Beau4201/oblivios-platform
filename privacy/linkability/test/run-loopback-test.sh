#!/bin/sh
# Extracts the loopback permission functions from the files of the patched module and runs the host check on them.
# Usage: run-loopback-test.sh <netd.c> <netd.h>      (bpf/progs/ of packages/modules/Connectivity with patches 0001 and 0002)
set -e
c=${1:?path of bpf/progs/netd.c}
h=${2:?path of bpf/progs/netd.h}
dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT
here=$(dirname "$0")
python3 -I - "$c" "$h" "$dir/extracted.h" <<'PY'
import re, sys
c, h, out = open(sys.argv[1]).read(), open(sys.argv[2]).read(), sys.argv[3]
parts = re.findall(r"^#define PERMISSION_BIT_\w+ \(?[\d<> ]+\)?$", h, re.M)
assert len(parts) >= 7, f"only {len(parts)} permission bits found"
m = re.search(r"^static inline bool is_system_uid\(uint32_t uid\) \{.*?^\}\n", h, re.M | re.S)
assert m, "is_system_uid not found"
parts.append(m.group(0))
for name in ("can_force_loopback", "uids_have_loopback_permissions", "uid_may_use_loopback"):
    m = re.search(r"^function bool " + name + r"\(.*?^\}\n", c, re.M | re.S)
    assert m, name + " not found"
    parts.append(m.group(0))
open(out, "w").write("\n".join(parts))
PY
cc -std=c11 -Wall -Wextra -Werror -O0 -I"$dir" -o "$dir/test" "$here/LoopbackPermsTest.c"
"$dir/test"
