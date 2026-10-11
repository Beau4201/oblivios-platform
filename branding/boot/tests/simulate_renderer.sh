#!/usr/bin/env bash
# Emulate BootAnimation::android() (frameworks/base cmds/bootanimation/BootAnimation.cpp, android()): clear colour, the shine
# quad drawn at x and x+w (x = -offset, offset = (1 - frac(4*frame/w)) * w), then the mask blended over it.
# usage: simulate_renderer.sh MASK SHINE FRAME OUT.png
set -euo pipefail
mask=$1; shine=$2; frame=$3; out=$4
w=$(identify -format %w "$shine")
off=$(awk -v f="$frame" -v w="$w" 'BEGIN{t=4.0*f/w; fr=t-int(t); printf "%d", (1-fr)*w}')
magick -size 1080x2400 xc:'#030706' \( "$shine" \) -geometry +$((0-off))+0 -compose Over -composite \
  \( "$shine" \) -geometry +$((w-off))+0 -compose Over -composite "$mask" -compose Over -composite "$out"
