#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Host test of the resolver's mask list (ExitMaskList): the unit checks, then the differential check against the daemon's
# reader on the build server. Usage: run.sh [--unit-only] [BUILD_DIR]   (EXIT_BUILD_SSH=user@host for the differential)
# The source under test is src/ unless SRC is set (the mutation runner sets it to a mutated copy).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
src=${SRC:-$here/../src}
unit_only=0
[ "${1:-}" = "--unit-only" ] && { unit_only=1; shift; }
out=${1:-$(mktemp -d)}
mkdir -p "$out"
# The resolver builds as C++20 with -Wall -Wextra -Werror (netd_defaults); the host compiler must accept it the same way.
${CXX:-g++} -std=gnu++20 -Wall -Wextra -Werror -O1 -fsanitize=address,undefined -fno-sanitize-recover=all \
  -I "$src" "$here/ExitMaskList_test.cpp" "$src/ExitMaskList.cpp" -o "$out/ExitMaskList_test"
"$out/ExitMaskList_test"
[ "$unit_only" = 1 ] && exit 0
python3 "$here/differential.py" --cpp-bin "$out/ExitMaskList_test"
