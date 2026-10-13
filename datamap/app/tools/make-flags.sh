#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Makes res/drawable-nodpi/flag_xx.webp, one flag for every two-letter code of the flag-icons set (4 by 3, 120 by 90 pixels,
# lossless). The source is the release v7.5.0 of github.com/lipis/flag-icons (MIT), pinned by the checksum of its tarball.
# usage: make-flags.sh TARBALL RES_DIR     TARBALL is the archive of the release, fetched by the owner of the build
# needs: rsvg-convert (librsvg) and magick (ImageMagick); the output is written, nothing is deleted
set -o errexit -o nounset -o pipefail
TARBALL="${1:?usage: make-flags.sh TARBALL RES_DIR}"
RES="${2:?usage: make-flags.sh TARBALL RES_DIR}"
PIN=5409c3982cc70b233e9037366185e858e1e75380ebfbaac1070de78485655b77
echo "$PIN  $TARBALL" | sha256sum -c - >/dev/null || { echo "make-flags: the tarball is not the pinned release" >&2; exit 2; }
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
tar -xzf "$TARBALL" -C "$WORK" --wildcards '*/flags/4x3/*.svg' '*/LICENSE'
mkdir -p "$RES/drawable-nodpi"
n=0
for f in "$WORK"/*/flags/4x3/??.svg; do
  code="$(basename "$f" .svg)"
  rsvg-convert -w 120 -h 90 "$f" | magick png:- -define webp:lossless=true -define webp:method=6 \
    "$RES/drawable-nodpi/flag_$code.webp"
  n=$((n + 1))
done
echo "flags: $n written"
