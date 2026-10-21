#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Fails when arm64/libvault_core.so is not the file that SOURCE.md records: wrong hash, wrong size, or a library that
# needs anything but libc, libdl and libm. A change to the library must come with a change of SOURCE.md.
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
LIB="$HERE/arm64/libvault_core.so"
[[ -f "$LIB" ]] || { echo "missing $LIB" >&2; exit 1; }

# The hash and the size are the ones in the table row of SOURCE.md.
want_hash="$(sed -n 's/^| SHA-256 | `\([0-9a-f]\{64\}\)` |$/\1/p' "$HERE/SOURCE.md")"
want_size="$(sed -n 's/^| Size | \([0-9 ]*\) bytes |$/\1/p' "$HERE/SOURCE.md" | tr -d ' ')"
[[ -n "$want_hash" && -n "$want_size" ]] || { echo "SOURCE.md has no SHA-256 or Size row" >&2; exit 1; }

have_hash="$(sha256sum "$LIB" | cut -d' ' -f1)"
have_size="$(stat -c %s "$LIB")"
[[ "$have_hash" == "$want_hash" ]] || { echo "hash mismatch: file $have_hash, SOURCE.md $want_hash" >&2; exit 1; }
[[ "$have_size" == "$want_size" ]] || { echo "size mismatch: file $have_size, SOURCE.md $want_size" >&2; exit 1; }

if command -v readelf >/dev/null; then
  needed="$(readelf -d "$LIB" | sed -n 's/.*(NEEDED).*Shared library: \[\(.*\)\]/\1/p' | sort | tr '\n' ' ')"
  [[ "$needed" == "libc.so libdl.so libm.so " ]] || { echo "NEEDED is '$needed'" >&2; exit 1; }
fi
echo "libvault_core.so matches SOURCE.md ($have_size bytes, ${have_hash:0:12}...)"
