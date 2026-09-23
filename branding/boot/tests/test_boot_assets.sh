#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

mask_path="$work_dir/android-logo-mask.png"
shine_path="$work_dir/android-logo-shine.png"

"$root_dir/tools/generate_boot_assets.sh" "$work_dir"

test -s "$mask_path"
test -s "$shine_path"

test "$(identify -format '%wx%h' "$mask_path")" = "448x800"
test "$(identify -format '%wx%h' "$shine_path")" = "1920x800"

pixel() {
  magick "$1" -format "%[pixel:p{$2,$3}]" info:
}

# The mask itself supplies the approved obsidian background inside its centered
# 448x800 update rectangle.
test "$(pixel "$mask_path" 0 0)" = "srgba(18,19,24,1)"

# The shield begins at source-canvas x=360,y=921. Cropping the approved
# 1080x2400 composition to a centered 448x800 texture maps that to x=44,y=121.
test "$(pixel "$mask_path" 224 135)" = "srgba(244,241,234,1)"

# The solid main A remains obsidian.
test "$(pixel "$mask_path" 224 245)" = "srgba(18,19,24,1)"

# The 280x6 loading track maps to x=84,y=764 and must remain transparent in
# the mask so the scrolling shine texture can show through.
test "$(pixel "$mask_path" 224 767)" = "srgba(0,0,0,0)"

# The shine texture provides the composited 16%-ivory track and a repeated
# 95px periwinkle segment. A 384px spatial period at the renderer's fixed
# 240px/s scroll rate produces a 1.6s recurrence.
test "$(pixel "$shine_path" 200 767)" = "srgb(54,55,58)"
test "$(pixel "$shine_path" 20 767)" = "srgb(119,131,255)"
test "$(pixel "$shine_path" 404 767)" = "srgb(119,131,255)"

grep -Fq 'M0 16C0 7.16344 7.16344 0 16 0H84C92.8366 0 100 7.16344 100 16V50C100 82 72 106 54 113.2C51.5 114.2 48.5 114.2 46 113.2C28 106 0 82 0 50V16Z' \
  "$root_dir/source/sovereignos-boot-lockup.svg"
grep -Fq 'M50 35L76 83H66.5L50 51.5L33.5 83H24L50 35ZM50 58L59 75.5H41L50 58Z' \
  "$root_dir/source/sovereignos-boot-lockup.svg"
grep -Fq 'M50 28L78 78H67L50 47L33 78H22L50 28Z' \
  "$root_dir/source/sovereignos-boot-lockup.svg"

# Production assets must not depend on font discovery at build time.
if grep -Eq '<text\b' "$root_dir/source/sovereignos-boot-lockup.svg"; then
  echo "wordmark must be converted to vector paths" >&2
  exit 1
fi

grep -Fq 'glClearColor(18.0f / 255.0f, 19.0f / 255.0f, 24.0f / 255.0f, 1.0f);' \
  "$root_dir/patches/frameworks-base-boot-background.patch"

(
  cd "$work_dir"
  sha256sum --check "$root_dir/expected-assets.sha256"
)
