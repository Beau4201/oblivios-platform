#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Builds the two offline data files of the Compartments persona (ADR-208) from two pinned downloads, reproducibly.

    build-persona-data.py GEONAMES_CITIES15000_TXT OPENCELLID_CSV OUT_DIR [--report]

Inputs (the owner downloads them; the builder never touches the network):
  - the GeoNames file of cities (cities15000.txt: tab separated, 19 columns, CC BY 4.0);
  - an OpenCelliD export (CSV, `radio,mcc,net,area,cell,unit,lon,lat,range,samples,...`, an optional header line, CC BY-SA 4.0).
Outputs, in the formats that `Places.parse` and `CellSubset.parse` read:
  - places.tsv: `id name country latitude longitude timeZone mcc`
  - cells.tsv:  `placeId mcc mnc tac cid`

Rules: a city is kept with a population of at least 15000, a latitude within 80 degrees, a name without a control character
(at most 100 characters) and a time zone of the allowed characters. The mobile country code of a place is that of its country, the
most common one among the cells within 25 km of any city of the country (the lowest on a tie); a city of a country without such a
cell is dropped, because its persona would have no operator. The id of a place is its GeoNames id (a later release of the data keeps the id of a city, so a place the user chose stays the same place), ascending. Of the
cells only LTE cells with an mcc of 200 to 799, a tac of 1 to 65535 and a cell identity of 1 to 2^28-1 count; a place gets at most 20
of the cells within 25 km that carry its own mcc, the nearest to the city centre first, ties by cell identity. A place without cells
is kept. The output is a function of the two inputs and nothing else.
"""
import math
import sys

MIN_POPULATION = 15000
MAX_LATITUDE = 80.0
MAX_CELLS = 20
RADIUS_KM = 25.0
MAX_NAME = 100
ZONE_CHARS = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_/+-")
EARTH_KM = 6371.0088


def distance_km(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2
    return 2 * EARTH_KM * math.asin(min(1.0, math.sqrt(a)))


def parse_cities(text):
    cities = []
    for line in text.split("\n"):
        f = line.split("\t")
        if len(f) < 19:
            continue
        try:
            gid, lat, lon, pop = int(f[0]), float(f[4]), float(f[5]), int(f[14])
        except ValueError:
            continue
        if not (1 <= gid <= 999999999):
            continue
        name, country, zone = f[1], f[8], f[17]
        if pop < MIN_POPULATION or abs(lat) > MAX_LATITUDE or abs(lon) > 180.0:
            continue
        if not name or len(name) > MAX_NAME or any(ord(c) < 0x20 or 0x7F <= ord(c) <= 0x9F for c in name):
            continue
        if len(country) != 2 or not country.isascii() or not country.isupper() or not country.isalpha():
            continue
        if not zone or len(zone) > 64 or any(c not in ZONE_CHARS for c in zone):
            continue
        cities.append((country, name, lat, lon, zone, gid))
    cities.sort(key=lambda c: c[5])
    # a GeoNames id is unique; a repeated one would break the ascending order of the file, so the first line is kept
    return [c for i, c in enumerate(cities) if i == 0 or c[5] != cities[i - 1][5]]


def parse_cells(text):
    cells = []
    for line in text.split("\n"):
        f = line.split(",")
        if len(f) < 8 or f[0] != "LTE":
            continue
        try:
            mcc, mnc, tac, cid, lon, lat = int(f[1]), int(f[2]), int(f[3]), int(f[4]), float(f[6]), float(f[7])
        except ValueError:
            continue
        if not (200 <= mcc <= 799) or not (0 <= mnc <= 999) or not (1 <= tac <= 65535) or not (1 <= cid <= 2 ** 28 - 1):
            continue
        if abs(lat) > 90.0 or abs(lon) > 180.0:
            continue
        cells.append((lat, lon, mcc, mnc, tac, cid))
    return cells


class Grid:
    """The cells in buckets of half a degree, so that the cells near a city are found without a scan of all of them."""

    def __init__(self, cells):
        self.buckets = {}
        for c in cells:
            self.buckets.setdefault((math.floor(c[0] * 2), math.floor(c[1] * 2) % 720), []).append(c)

    def near(self, lat, lon):
        rows = int(math.ceil(RADIUS_KM / (111.2 * 0.5))) + 1
        cols = int(math.ceil(RADIUS_KM / (111.2 * 0.5 * max(math.cos(math.radians(lat)), 0.05)))) + 1
        r0, c0 = math.floor(lat * 2), math.floor(lon * 2)
        # the columns wrap at the antimeridian (bucket 719 is next to bucket 0); a set so that a bucket is visited once
        columns = {(c0 + dc) % 720 for dc in range(-cols, cols + 1)}
        out = []
        for dr in range(-rows, rows + 1):
            for c in sorted(columns):
                for cell in self.buckets.get((r0 + dr, c), ()):
                    d = distance_km(lat, lon, cell[0], cell[1])
                    if d <= RADIUS_KM:
                        out.append((d, cell))
        return out


def build(geonames_text, cells_text):
    cities = parse_cities(geonames_text)
    grid = Grid(parse_cells(cells_text))
    near = [grid.near(c[2], c[3]) for c in cities]
    votes = {}
    for city, found in zip(cities, near):
        for _, cell in found:
            votes.setdefault(city[0], {}).setdefault(cell[2], 0)
            votes[city[0]][cell[2]] += 1
    country_mcc = {cc: sorted(v.items(), key=lambda kv: (-kv[1], kv[0]))[0][0] for cc, v in votes.items()}
    places, cells = [], []
    for city, found in zip(cities, near):
        mcc = country_mcc.get(city[0])
        if mcc is None:
            continue
        pid = city[5]
        places.append("%d\t%s\t%s\t%.5f\t%.5f\t%s\t%d\n" % (pid, city[1], city[0], city[2], city[3], city[4], mcc))
        own = [(d, c) for d, c in found if c[2] == mcc]
        # nearest first; at equal distance by cell identity, then by the other fields so that the order never depends on the input
        own.sort(key=lambda dc: (round(dc[0], 6), dc[1][5], dc[1][3], dc[1][4]))
        for _, c in own[:MAX_CELLS]:
            cells.append("%d\t%d\t%d\t%d\t%d\n" % (pid, c[2], c[3], c[4], c[5]))
    return "".join(places), "".join(cells)


def main(argv):
    args = [a for a in argv[1:] if a != "--report"]
    if len(args) != 3:
        print(__doc__)
        return 2
    with open(args[0], encoding="utf-8") as f:
        geo = f.read()
    with open(args[1], encoding="utf-8", errors="replace") as f:
        cells = f.read()
    places, cell_lines = build(geo, cells)
    with open(args[2] + "/places.tsv", "w", encoding="utf-8", newline="\n") as f:
        f.write(places)
    with open(args[2] + "/cells.tsv", "w", encoding="utf-8", newline="\n") as f:
        f.write(cell_lines)
    print("places: %d lines, cells: %d lines" % (places.count("\n"), cell_lines.count("\n")))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
