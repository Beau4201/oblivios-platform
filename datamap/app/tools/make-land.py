#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Builds the land outlines of the globe (res/raw/land.bin) from a Natural Earth GeoJSON of countries.

usage: make-land.py INPUT.geojson OUTPUT.bin [--tolerance DEGREES]   (default tolerance 0.1)

Format, big endian: "DMLD", u8 version (1), u16 shapes; per shape u16 rings; per ring u16 points, then
the points as i16 longitude and i16 latitude in hundredths of a degree. A shape is one polygon: its first
ring is the outline and the others are holes (drawn with the even-odd rule). Antarctica is left out: a
polygon that contains a pole cannot be filled in an orthographic projection by joining its points.
"""
import json, math, struct, sys

EXCLUDED = {"ATA"}
MAX_POINTS_PER_RING = 20000


def perpendicular(p, a, b):
    (x, y), (x1, y1), (x2, y2) = p, a, b
    dx, dy = x2 - x1, y2 - y1
    if dx == 0 and dy == 0:
        return math.hypot(x - x1, y - y1)
    t = max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)))
    return math.hypot(x - (x1 + t * dx), y - (y1 + t * dy))


def simplify(points, tol):
    """Douglas-Peucker on an open polyline, iterative so that a long ring cannot overflow the stack."""
    if tol <= 0 or len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        lo, hi = stack.pop()
        worst, index = 0.0, -1
        for i in range(lo + 1, hi):
            d = perpendicular(points[i], points[lo], points[hi])
            if d > worst:
                worst, index = d, i
        if worst > tol:
            keep[index] = True
            stack.append((lo, index))
            stack.append((index, hi))
    return [p for p, k in zip(points, keep) if k]


def ring_points(ring, tol):
    pts = [(float(x), float(y)) for x, y in ring]
    if len(pts) > 1 and pts[0] == pts[-1]:
        pts = pts[:-1]   # the closing point repeats the first one
    pts = simplify(pts, tol)
    return pts if len(pts) >= 3 else None


def quantise(v, limit):
    q = int(round(v * 100))
    if abs(q) > limit * 100:
        raise SystemExit(f"coordinate out of range: {v}")
    return q


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    tol = 0.1
    if "--tolerance" in sys.argv:
        tol = float(sys.argv[sys.argv.index("--tolerance") + 1])
        args.remove(str(sys.argv[sys.argv.index("--tolerance") + 1]))
    src, dst = args
    data = json.load(open(src, encoding="utf-8"))
    shapes = []
    for feat in data["features"]:
        if feat["properties"].get("ADM0_A3") in EXCLUDED:
            continue
        geom = feat["geometry"]
        polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
        for poly in polys:
            rings = [r for r in (ring_points(x, tol) for x in poly) if r]
            if rings:
                shapes.append(rings)
    out = bytearray(b"DMLD" + struct.pack(">BH", 1, len(shapes)))
    for rings in shapes:
        out += struct.pack(">H", len(rings))
        for r in rings:
            if len(r) > MAX_POINTS_PER_RING:
                raise SystemExit("ring too long")
            out += struct.pack(">H", len(r))
            for x, y in r:
                out += struct.pack(">hh", quantise(x, 180), quantise(y, 90))
    open(dst, "wb").write(out)
    print(f"{len(shapes)} shapes, {sum(len(r) for s in shapes for r in s)} points, {len(out)} bytes")


main()
