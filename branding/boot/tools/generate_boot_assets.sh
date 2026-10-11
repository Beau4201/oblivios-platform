#!/usr/bin/env bash
# Render the two boot textures from source/*.svg, deterministically.
set -euo pipefail

if [[ $# -ne 1 ]]; then echo "usage: $0 OUTPUT_DIRECTORY" >&2; exit 64; fi
for tool in rsvg-convert magick sha256sum; do
  command -v "$tool" >/dev/null 2>&1 || { echo "$tool is required" >&2; exit 69; }
done

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="$1"; mkdir -p "$out"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT

rsvg-convert --format=png --width=1080 --height=2400 --output="$work/mask.png" "$root_dir/source/lighthouse-mask.svg"
rsvg-convert --format=png --width=720  --height=2160 --output="$work/shine.png" "$root_dir/source/lighthouse-shine.svg"

# The dark sky and sea gradients would show 8-bit bands on an OLED panel. Add up to +-0.75 of one 8-bit level
# of fixed pseudo-random noise to the colour channels (never alpha), so band edges break up. The noise is a pure
# function of the pixel position, so the output is byte-identical on every run.
magick "$work/mask.png" -channel RGB \
  -fx 'zz=sin(i*12.9898+j*78.233)*43758.5453; u+((zz-floor(zz))-0.5)*1.5/255' +channel \
  -strip -define png:exclude-chunk=all "$out/android-logo-mask.png"
magick "$work/shine.png" -strip -define png:exclude-chunk=all "$out/android-logo-shine.png"

sha256sum "$out/android-logo-mask.png" "$out/android-logo-shine.png"
