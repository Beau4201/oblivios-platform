#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Emulate BootAnimation::android() (frameworks/base cmds/bootanimation/BootAnimation.cpp, android()): clear colour, the shine
# quad drawn at x and x+w (x = -offset, offset = (1 - frac(4*frame/w)) * w), then the mask blended over it.
# drawTexturedQuad maps y with GL's y-up convention (mapLinear(yStart, 0, height, -1, 1)), so a texture is lined up with the
# BOTTOM edge of the mask, not the top: the shine is placed at the south-west. Measured against a frame captured on a device
# (2026-10-11): 0 pixels differ with this placement, about 200 with a north-west placement.
# usage: simulate_renderer.sh MASK SHINE FRAME OUT.png
set -euo pipefail
mask=$1; shine=$2; frame=$3; out=$4
w=$(identify -format %w "$shine")
off=$(awk -v f="$frame" -v w="$w" 'BEGIN{t=4.0*f/w; fr=t-int(t); printf "%d", (1-fr)*w}')
magick -size 1080x2400 xc:'#030706' -gravity southwest \( "$shine" \) -geometry +$((0-off))+0 -compose Over -composite \
  \( "$shine" \) -geometry +$((w-off))+0 -compose Over -composite -gravity northwest "$mask" -compose Over -composite "$out"
