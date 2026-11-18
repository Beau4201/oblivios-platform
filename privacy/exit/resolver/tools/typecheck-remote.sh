#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# The part of typecheck-on-server.sh that runs on the build server (see there). $1: the scratch directory with patched/.
set -uo pipefail
scratch=$1
tree="${AOSP_TREE:?set AOSP_TREE}"
out=out-lynx
variant=libnetd_resolv/android_arm64_armv8-2a-dotprod_cortex-a76_static_cfi_apex31
obj=$out/soong/.intermediates/packages/modules/DnsResolver/$variant/obj/packages/modules/DnsResolver/res_cache.o
cd "$tree"
[ -s "$scratch/cmd.txt" ] || prebuilts/build-tools/linux-x86/bin/ninja -f $out/combined-lynx.ninja -t commands "$obj" 2>/dev/null \
  | grep -F "res_cache.cpp" | head -1 > "$scratch/cmd.txt"
[ -s "$scratch/cmd.txt" ] || { echo "no compile command for $obj" >&2; exit 2; }

# The command of the module build, as a syntax check of one source file. The source and the object are the last two words.
check() {  # $1 source file; $2 label; prints the diagnostics
  python3 - "$scratch/cmd.txt" "$1" <<'EOF' > "$scratch/run.sh"
import re, sys
cmd = open(sys.argv[1]).read().strip()
cmd = cmd.replace("PWD=/proc/self/cwd /usr/bin/ccache ", "", 1)
cmd = re.sub(r" -MD -MF \S+ -o \S+ \S+$", "", cmd)
assert cmd.count(" -c ") == 1
print(cmd.replace(" -c ", " -fsyntax-only ", 1) + " " + sys.argv[2])
EOF
  bash "$scratch/run.sh" 2>&1
}

fail=0
echo "== 1. res_cache.cpp of the tree"
out1=$(check packages/modules/DnsResolver/res_cache.cpp); rc=$?
[ -z "$out1" ] && echo "no diagnostic" || { echo "$out1" | head -20; fail=1; }
echo "== 2. the patched res_cache.cpp"
out2=$(check "$scratch/patched/res_cache.cpp")
[ -z "$out2" ] && echo "no diagnostic" || { echo "$out2" | head -20; fail=1; }
echo "== 3. ExitMaskList.cpp"
out3=$(check "$scratch/patched/ExitMaskList.cpp")
[ -z "$out3" ] && echo "no diagnostic" || { echo "$out3" | head -20; fail=1; }
echo "== 4. control: a method that does not exist"
sed 's/exitMaskList().masksQuery(query)) return RESOLV_CACHE_SKIP;/exitMaskList().noSuchMethod(1)) return RESOLV_CACHE_SKIP;/' "$scratch/patched/res_cache.cpp" > "$scratch/control/res_cache.cpp"
cp "$scratch/patched/ExitMaskList.h" "$scratch/control/"
grep -q noSuchMethod "$scratch/control/res_cache.cpp" || { echo "control file was not changed" >&2; exit 2; }
out4=$(check "$scratch/control/res_cache.cpp")
n=$(echo "$out4" | grep -c "error:")
echo "$out4" | grep "error:" | sed -E 's#^[^ ]*:[0-9]+:[0-9]+: ##'
[ "$n" = 1 ] && echo "control: one error, as expected" || { echo "control: $n errors, expected 1"; fail=1; }
[ "$fail" = 0 ] && echo "resolver type-check passed" || echo "resolver type-check FAILED"
exit $fail
