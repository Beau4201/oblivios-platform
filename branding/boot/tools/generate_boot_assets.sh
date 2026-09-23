#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 OUTPUT_DIRECTORY" >&2
  exit 64
fi

if ! command -v rsvg-convert >/dev/null 2>&1; then
  echo "rsvg-convert is required" >&2
  exit 69
fi

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir="$1"
mkdir -p "$output_dir"

rsvg-convert \
  --format=png \
  --width=448 \
  --height=800 \
  --output="$output_dir/android-logo-mask.png" \
  "$root_dir/source/sovereignos-boot-lockup.svg"

rsvg-convert \
  --format=png \
  --width=1920 \
  --height=800 \
  --output="$output_dir/android-logo-shine.png" \
  "$root_dir/source/sovereignos-loading-shine.svg"

sha256sum \
  "$output_dir/android-logo-mask.png" \
  "$output_dir/android-logo-shine.png"
