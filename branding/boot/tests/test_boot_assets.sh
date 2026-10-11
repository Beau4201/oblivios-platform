#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
mask="$work/android-logo-mask.png"; shine="$work/android-logo-shine.png"
fail() { echo "FAIL: $*" >&2; exit 1; }

"$root_dir/tools/generate_boot_assets.sh" "$work" >/dev/null

# sources must be self-contained vector art: no live text, no embedded or linked images
if grep -Eq '<text\b|<image\b|href=|xlink:' "$root_dir"/source/*.svg; then fail "sources must not contain text, images or links"; fi

[[ "$(identify -format '%wx%h' "$mask")" = "1080x2400" ]]  || fail "mask size"
[[ "$(identify -format '%wx%h' "$shine")" = "720x2160" ]]   || fail "shine size"

# r g b a (0-255) of one pixel
px() { magick "$1" -format "%[fx:int(255*p{$2,$3}.r+.5)] %[fx:int(255*p{$2,$3}.g+.5)] %[fx:int(255*p{$2,$3}.b+.5)] %[fx:int(255*p{$2,$3}.a+.5)]" info:; }
chan() { read -r r g b a <<<"$(px "$1" "$2" "$3")"; case "$4" in r) echo "$r";; g) echo "$g";; b) echo "$b";; a) echo "$a";; esac; }
near() { local d=$(( $1 - $2 )); (( d < 0 )) && d=$(( -d )); (( d <= $3 )); }

# the loading-bar window (260 x 5 at 410,2152) is transparent, and only that
[[ "$(chan "$mask" 540 2154 a)" = 0 ]]   || fail "window must be transparent"
[[ "$(chan "$mask" 420 2154 a)" = 0 ]]   || fail "window left part must be transparent"
for p in "540 2150" "540 2160" "405 2154" "675 2154"; do
  [[ "$(chan "$mask" $p a)" = 255 ]] || fail "pixel $p next to the window must be opaque"
done

# top and bottom edges equal the renderer's clear colour (3,7,6), so a taller display shows no seam
for p in "0 0" "540 0" "1079 0" "0 2399" "540 2399" "1079 2399"; do
  read -r r g b a <<<"$(px "$mask" $p)"
  near "$r" 3 2 && near "$g" 7 2 && near "$b" 6 2 && [[ "$a" = 255 ]] || fail "edge pixel $p is $r $g $b $a"
done

# the picture is there: bright name, bright lantern pane, mint beams to both sides
# "Sovereign" is white (high red) on the left, "OS" is mint (low red, high green) on the right
frac() { magick "$mask" -crop "$1" +repage -channel "$2" -separate -threshold "$3" -format '%[fx:mean]' info:; }
white="$(frac 320x90+290+2020 R 80%)"
mint="$(magick "$mask" -crop 150x90+640+2020 +repage -fx '(u.g>0.6 && u.r<0.45) ? 1 : 0' -format '%[fx:mean]' info:)"
awk -v w="$white" 'BEGIN{exit !(w > 0.03)}' || fail "white word Sovereign not found (fraction $white)"
awk -v m="$mint"  'BEGIN{exit !(m > 0.03)}' || fail "mint word OS not found (fraction $mint)"
(( $(chan "$mask" 540 776 g) > 200 )) || fail "lantern pane must be bright"
for x in 140 940; do
  read -r r g b a <<<"$(px "$mask" $x 777)"
  (( g - r > 20 )) || fail "beam colour at x=$x is $r $g $b"
done

# loading texture: dark track with a mint segment, ink elsewhere
(( $(chan "$shine" 360 2154 g) > 170 )) || fail "segment must be bright mint"
read -r r g b a <<<"$(px "$shine" 20 2154)";  near "$r" 26 1 && near "$g" 59 1 && near "$b" 53 1 || fail "track colour $r $g $b"
read -r r g b a <<<"$(px "$shine" 20 100)";   near "$r" 3 1 && near "$g" 7 1 && near "$b" 6 1 || fail "ink colour $r $g $b"
# the segment must sit inside one period so the two side-by-side quads join without a seam
[[ "$(chan "$shine" 0 2154 g)" = "$(chan "$shine" 719 2154 g)" ]] || fail "texture edges must match"

# the renderer draws only two copies of the shine, side by side, so the whole window must stay covered at every scroll offset:
# with a texture narrower than the window's right edge the bar goes blank for part of every pass
for f in 0 7 30 61 90 119 150 181 240 359 360 361; do
  "$root_dir/tests/simulate_renderer.sh" "$mask" "$shine" "$f" "$work/sim.png"
  for x in 415 540 665; do
    read -r r g b a <<<"$(px "$work/sim.png" $x 2154)"
    near "$r" 3 1 && near "$g" 7 1 && near "$b" 6 1 && fail "frame $f: window at x=$x shows the clear colour (uncovered)"
  done
done

# deterministic, and identical to the recorded hashes
( cd "$work" && sha256sum --check "$root_dir/expected-assets.sha256" )
echo "all boot-asset checks passed"
