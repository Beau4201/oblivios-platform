#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Writes data/anchors.tsv: for every country (ISO 3166-1 alpha-2) the point where a server place that has no city is drawn.

usage: fetch-anchors.py OUT.tsv
The point is the capital's coordinates from Wikidata (CC0-1.0); for a country with several capitals the mean of the distinct
points, so that no capital is preferred over another; for a country with none the coordinates of the country's own item.
Network access, one query per kind, run by hand and never during a build.
"""
import json, sys, urllib.parse, urllib.request

ENDPOINT = "https://query.wikidata.org/sparql"
UA = "oblivios-classifier-research/1.0 (privacy-oriented Android OS; contact via project repository)"
CAPITALS = ('SELECT ?cc ?co WHERE { ?c wdt:P297 ?cc . ?c wdt:P36 ?cap . ?cap wdt:P625 ?co . '
            'FILTER NOT EXISTS { ?c wdt:P576 ?end } }')
COUNTRIES = 'SELECT ?cc ?co WHERE { ?c wdt:P297 ?cc . ?c wdt:P625 ?co . FILTER NOT EXISTS { ?c wdt:P576 ?end } }'


def ask(query):
    req = urllib.request.Request(ENDPOINT + "?format=json", data=urllib.parse.urlencode({"query": query}).encode(),
                                 headers={"User-Agent": UA, "Accept": "application/sparql-results+json"})
    return json.load(urllib.request.urlopen(req, timeout=120))["results"]["bindings"]


def point(wkt):
    lon, lat = wkt[len("Point("):-1].split()
    return float(lat), float(lon)


def mean_anchors(rows):
    """rows: (cc, "Point(lon lat)") -> {cc: (lat, lon)}, the mean of the distinct points of a country."""
    pts = {}
    for cc, wkt in rows:
        pts.setdefault(cc, set()).add(point(wkt))
    return {cc: (sum(p[0] for p in v) / len(v), sum(p[1] for p in v) / len(v)) for cc, v in pts.items()}


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    caps = mean_anchors([(b["cc"]["value"], b["co"]["value"]) for b in ask(CAPITALS)])
    own = mean_anchors([(b["cc"]["value"], b["co"]["value"]) for b in ask(COUNTRIES)])
    out = dict(own)
    out.update(caps)
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        f.write("# country\tlat\tlon\t(capital of the country from Wikidata, CC0-1.0; the item's own point where it has no capital)\n")
        for cc in sorted(out):
            f.write("%s\t%.4f\t%.4f\n" % (cc, out[cc][0], out[cc][1]))
    print("%s: %d countries (%d with a capital)" % (sys.argv[1], len(out), len(caps)))


if __name__ == "__main__":
    main()
