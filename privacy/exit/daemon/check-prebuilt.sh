#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Fails when arm64/exitd is not the file that SOURCE.md records: wrong hash, wrong size, not an AArch64 ELF with the Android
# linker, a dependency on anything but libc, libdl and libm, or a mask list that is not the one SOURCE.md records. A change
# to the daemon must come with a change of SOURCE.md.
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
BIN="$HERE/arm64/exitd"
LIST="$HERE/exit-mask-list.tsv"
[[ -f "$BIN" ]] || { echo "missing $BIN" >&2; exit 1; }
[[ -f "$LIST" ]] || { echo "missing $LIST" >&2; exit 1; }

row() { sed -n "s/^| $1 | \`\\([0-9a-f]\\{64\\}\\)\` |\$/\\1/p" "$HERE/SOURCE.md"; }
size_row() { sed -n 's/^| Size | \([0-9 ]*\) bytes |$/\1/p' "$HERE/SOURCE.md" | tr -d ' '; }
want_hash="$(row SHA-256)"
want_size="$(size_row)"
want_list="$(row 'Mask list SHA-256')"
[[ -n "$want_hash" && -n "$want_size" && -n "$want_list" ]] || { echo "SOURCE.md has no SHA-256, Size or Mask list SHA-256 row" >&2; exit 1; }

have_hash="$(sha256sum "$BIN" | cut -d' ' -f1)"
have_size="$(stat -c %s "$BIN")"
[[ "$have_hash" == "$want_hash" ]] || { echo "hash mismatch: file $have_hash, SOURCE.md $want_hash" >&2; exit 1; }
[[ "$have_size" == "$want_size" ]] || { echo "size mismatch: file $have_size, SOURCE.md $want_size" >&2; exit 1; }
have_list="$(sha256sum "$LIST" | cut -d' ' -f1)"
[[ "$have_list" == "$want_list" ]] || { echo "mask list mismatch: file $have_list, SOURCE.md $want_list" >&2; exit 1; }

# The exit app speaks one control protocol version; a daemon of another one answers Hello with Unsupported and the exit
# is never up. The version of the recorded build must be the app's (ADR-208 moved it from 3 to 4 with the country).
want_proto="$(sed -n 's/^| Control protocol | \([0-9][0-9]*\) |$/\1/p' "$HERE/SOURCE.md")"
app_proto="$(sed -n 's/.*DAEMON_PROTOCOL = \([0-9][0-9]*\);.*/\1/p' "$HERE/../app/src/org/oblivios/exit/ControlCodec.java")"
[[ -n "$want_proto" && -n "$app_proto" ]] || { echo "no Control protocol row in SOURCE.md or no DAEMON_PROTOCOL in ControlCodec.java" >&2; exit 1; }
[[ "$want_proto" == "$app_proto" ]] || { echo "control protocol mismatch: exitd builds as $want_proto, the exit app speaks $app_proto: rebuild exitd (SOURCE.md)" >&2; exit 1; }

if command -v readelf >/dev/null; then
  header="$(readelf -h "$BIN")"
  grep -q 'Machine:.*AArch64' <<<"$header" || { echo "not an AArch64 ELF" >&2; exit 1; }
  interp="$(readelf -l "$BIN" | sed -n 's/.*Requesting program interpreter: \(.*\)\]/\1/p')"
  [[ "$interp" == "/system/bin/linker64" ]] || { echo "interpreter is '$interp'" >&2; exit 1; }
  needed="$(readelf -d "$BIN" | sed -n 's/.*(NEEDED).*Shared library: \[\(.*\)\]/\1/p' | sort | tr '\n' ' ')"
  [[ "$needed" == "libc.so libdl.so libm.so " ]] || { echo "NEEDED is '$needed'" >&2; exit 1; }
fi
echo "exitd matches SOURCE.md ($have_size bytes, ${have_hash:0:12}...)"
