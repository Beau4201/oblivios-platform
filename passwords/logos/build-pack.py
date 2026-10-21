#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Builds the logo pack of the Passwords app from a checkout of Simple Icons (CC0-1.0, simpleicons.org).

    build-pack.py --simple-icons DIR --out DIR [--overrides FILE] [--top FILE]

DIR holds `data/simple-icons.json` (a list of {title, hex, source, slug?}) and `icons/<slug>.svg`. For every icon the
registrable domain is derived from the `source` URL; the pack is two tab-separated files for the app's assets:

  icons.tsv     slug <TAB> path data (24 x 24 units) <TAB> title
  domains.tsv   registrable domain <TAB> slug, sorted, one row per domain

`overrides.tsv` (domain <TAB> slug, or domain <TAB> - to drop a derived row) wins over the derived rows. Two icons for one
domain keep the first by title and the other is reported on stderr, so the pack never has a domain twice. `--top` names a
file of domains (one per line, e.g. the most visited sites): the coverage of the pack over them is printed.

Only the shapes are used (the app draws them in the text colour of the theme); the brand colour and the trademarks stay with
their owners (NOTICE, SOURCES.md).
"""
import argparse
import json
import os
import re
import sys
import unicodedata
from urllib.parse import urlparse

# Suffixes of two labels that a site name hangs under. The pack needs the registrable domain of a brand's own site and
# the app matches by suffix, so a rare miss costs one logo, never a wrong one.
TWO_LABEL_SUFFIXES = {
    "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.nz", "co.jp", "ne.jp", "or.jp", "com.br",
    "com.cn", "com.hk", "com.tw", "com.sg", "com.mx", "com.tr", "co.in", "co.kr", "co.za", "com.ar", "com.ua",
}


def registrable(host: str):
    host = host.lower().strip(".")
    if not host or re.fullmatch(r"[0-9.]+", host) or ":" in host:
        return None
    labels = host.split(".")
    if len(labels) < 2:
        return None
    if ".".join(labels[-2:]) in TWO_LABEL_SUFFIXES and len(labels) >= 3:
        return ".".join(labels[-3:])
    return ".".join(labels[-2:])


def title_to_slug(title: str) -> str:
    """Simple Icons' own rule for a title without a `slug` field."""
    s = title.lower().replace("+", "plus").replace(".", "dot").replace("&", "and")
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]", "", s)


def svg_path(svg: str):
    paths = re.findall(r'<path[^>]*\sd="([^"]+)"', svg)
    return paths[0] if len(paths) == 1 else None


def build(src: str, overrides: dict, log=sys.stderr):
    with open(os.path.join(src, "data", "simple-icons.json"), encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, dict):
        data = data.get("icons", [])
    icons, domains, skipped = {}, {}, []
    for entry in sorted(data, key=lambda e: e["title"].lower()):
        slug = entry.get("slug") or title_to_slug(entry["title"])
        svg_file = os.path.join(src, "icons", slug + ".svg")
        if not os.path.isfile(svg_file):
            skipped.append((entry["title"], "no svg"))
            continue
        with open(svg_file, encoding="utf-8") as f:
            d = svg_path(f.read())
        if d is None:
            skipped.append((entry["title"], "not a single path"))
            continue
        icons[slug] = (d, entry["title"])
        host = urlparse(entry.get("source", "")).hostname
        dom = registrable(host) if host else None
        if dom is None:
            continue
        if dom in domains and domains[dom] != slug:
            print(f"domain {dom}: {domains[dom]} kept, {slug} ignored", file=log)
            continue
        domains[dom] = slug
    for dom, slug in overrides.items():
        if slug == "-":
            domains.pop(dom, None)
        else:
            domains[dom] = slug
    for dom, slug in list(domains.items()):
        if slug not in icons:
            print(f"domain {dom}: icon {slug} is not in the pack, row dropped", file=log)
            del domains[dom]
    for title, why in skipped:
        print(f"skipped {title}: {why}", file=log)
    return icons, domains


def read_overrides(path):
    out = {}
    if path and os.path.isfile(path):
        for line in open(path, encoding="utf-8"):
            line = line.rstrip("\n")
            if line and not line.startswith("#"):
                dom, slug = line.split("\t")
                out[dom.strip().lower()] = slug.strip()
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--simple-icons", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--overrides")
    ap.add_argument("--top")
    a = ap.parse_args()
    icons, domains = build(a.simple_icons, read_overrides(a.overrides))
    os.makedirs(a.out, exist_ok=True)
    with open(os.path.join(a.out, "icons.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for slug in sorted(icons):
            d, title = icons[slug]
            f.write(f"{slug}\t{d}\t{title}\n")
    with open(os.path.join(a.out, "domains.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for dom in sorted(domains):
            f.write(f"{dom}\t{domains[dom]}\n")
    print(f"{len(icons)} icons, {len(domains)} domains")
    if a.top:
        top = [l.strip().lower() for l in open(a.top, encoding="utf-8") if l.strip()]
        hit = sum(1 for d in top if d in domains)
        print(f"coverage of {a.top}: {hit} of {len(top)} ({100 * hit // max(1, len(top))}%)")


if __name__ == "__main__":
    main()
