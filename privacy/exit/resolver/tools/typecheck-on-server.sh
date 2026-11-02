#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Compiles the patched files of the resolver with the tree's own clang and the flags of the module build (syntax only).
#   EXIT_BUILD_SSH=user@host typecheck-on-server.sh DIR      DIR holds the patched res_cache.cpp, ExitMaskList.h and .cpp
# The compile command is the one `ninja -t commands` prints for `res_cache.o` of the `libnetd_resolv` variant that goes into
# the `com.android.resolv` APEX (static_cfi_apex31); it only prints, it builds nothing. Runs, each of which must give
# the stated answer:
#   1. res_cache.cpp of the tree (unpatched)          no diagnostic
#   2. the patched res_cache.cpp                      no diagnostic
#   3. ExitMaskList.cpp                               no diagnostic
#   4. control: the patched file with a deliberate error (a method that does not exist)   exactly that error
# Exit 0 only when all four hold.
set -euo pipefail
dir=${1:?directory of the patched files}
ssh_to=${EXIT_BUILD_SSH:?EXIT_BUILD_SSH=user@host (read it from the local server memory)}
here=$(cd "$(dirname "$0")" && pwd)
scratch=/srv/freeholdos/scratch/dnsresolver-cache/tc
ssh -n "$ssh_to" "mkdir -p $scratch/patched $scratch/control"
scp -q "$dir/res_cache.cpp" "$dir/ExitMaskList.h" "$dir/ExitMaskList.cpp" "$ssh_to:$scratch/patched/"
scp -q "$here/typecheck-remote.sh" "$ssh_to:$scratch/typecheck-remote.sh"
ssh -n "$ssh_to" "bash $scratch/typecheck-remote.sh $scratch"
