# Data Map app: sources of the data in the package

Each data file of the app that is not written by hand: where it comes from and how it is made again.

## `res/raw/land.bin`

The land of the globe and, as lines over it, the borders of the countries (the outlines of the country polygons).

- Source: Natural Earth, `ne_50m_admin_0_countries.geojson` from
  `github.com/nvkelso/natural-earth-vector`, tag `v5.1.2` (the 1:50 million set, chosen because it has at most
  150000 points with no simplification; see the measurement in the validation matrix, row `MAP-020`).
- Licence: public domain (Natural Earth terms of use).
- SHA-256 of the downloaded file: `3e458fc036ad0a66411f2c1e6cac49c5d7bfb81cb1123bc513b22511a2b7fdeb`.
- Made by `python3 tools/make-land.py FILE.geojson res/raw/land.bin --tolerance 0`.
- Result: 1512 shapes, 93166 points, 378743 bytes. Antarctica is left out: a polygon that holds a pole cannot be
  filled in an orthographic projection by joining its points.

## Country flags

- Source: `flags/4x3/*.svg` of release `v7.5.0` of `github.com/lipis/flag-icons` (tag object
  `50a8bff005239b0d2d661254094dedb9c75dbef3`, tarball sha256
  `5409c3982cc70b233e9037366185e858e1e75380ebfbaac1070de78485655b77`, 1 325 034 bytes, fetched 2026-10-14).
- Licence: MIT (copyright 2013 Panayiotis Lipiridis); the text is in the `NOTICE` of this repository.
- Made by `tools/make-flags.sh` (librsvg 2.63.2, ImageMagick 7.1.2-33): 257 two-letter codes, 120 by 90 pixels, lossless
  WebP, `res/drawable-nodpi/flag_xx.webp`. A second run gives identical files (compared byte by byte, 2026-10-14).
