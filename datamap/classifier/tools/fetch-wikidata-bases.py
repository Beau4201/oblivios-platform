#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Looks up where companies are based in Wikidata (CC0) by the official website of each company.

usage: fetch-wikidata-bases.py COMPANIES.json OUT.tsv [--by-name]
COMPANIES.json is dist/companies.json of the AdGuard companies database. For every company with a website it asks
Wikidata for the item whose official website (P856) is that site, and for the headquarters location (P159) of the
item when that is a human settlement with coordinates. When several items share a site, the one with the most
sitelinks (the well-known entity) decides. A company is written only when that item has exactly one such place;
two different places mean "no single base" and the company is left out.
OUT.tsv: company-id, wikidata item, name of the item, country (ISO 3166-1 alpha-2), city, latitude, longitude.
A second step looks every company that still has no place up by its exact English name in Wikidata (one SPARQL
query for many names, not the search API, which rate limits). A found item counts only when its official website
contains the company's own host: a name alone is never enough.
Network access, rate limited on purpose. Run by hand, never during a build.
"""
import json, sys, time, urllib.parse, urllib.request

ENDPOINT = "https://query.wikidata.org/sparql"
UA = "freeholdos-classifier-research/1.0 (privacy-oriented Android OS; contact via project repository)"
CHUNK = 120


def host_of(url):
    u = urllib.parse.urlparse(url if "://" in url else "http://" + url)
    h = (u.hostname or "").lower()
    return h[4:] if h.startswith("www.") else h


def variants(host):
    out = []
    for scheme in ("http", "https"):
        for www in ("", "www."):
            for tail in ("", "/"):
                out.append(f"{scheme}://{www}{host}{tail}")
    return out


def query(urls=None, items=None, names=None):
    """names: list of (English name, host); the item must carry the name as its label and the host in its website."""
    if names is not None:
        pairs = " ".join('(' + json.dumps(n) + '@en ' + json.dumps(h) + ')' for n, h in names)
        head = ("VALUES (?name ?host) { " + pairs + " }\n  ?item rdfs:label ?name .\n"
                "  ?item wdt:P856 ?url . FILTER(CONTAINS(LCASE(STR(?url)), ?host))")
    elif items is not None:
        head = "VALUES ?item { " + " ".join(f"wd:{i}" for i in items) + " }\n  ?item wdt:P856 ?url ."
    else:
        head = "VALUES ?url { " + " ".join(f"<{u}>" for u in urls) + " }\n  ?item wdt:P856 ?url ."
    q = f"""SELECT ?url ?item ?itemLabel ?sl ?hq ?hqLabel ?lat ?lon ?cc ?host WHERE {{
  {head}
  ?item wikibase:sitelinks ?sl .
  ?item wdt:P159 ?hq .
  ?hq wdt:P31/wdt:P279* wd:Q486972 .
  ?hq p:P625/psv:P625 ?n . ?n wikibase:geoLatitude ?lat ; wikibase:geoLongitude ?lon .
  ?hq wdt:P17 ?country . ?country wdt:P297 ?cc .
  SERVICE wikibase:label {{ bd:serviceParam wikibase:language "en". }}
}}"""
    data = urllib.parse.urlencode({"query": q, "format": "json"}).encode()
    req = urllib.request.Request(ENDPOINT, data=data, headers={"User-Agent": UA, "Accept": "application/sparql-results+json"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=90) as r:
                return json.load(r)["results"]["bindings"]
        except Exception as e:  # rate limit or timeout: wait and retry
            print(f"retry {attempt + 1}: {e}", file=sys.stderr)
            time.sleep(10 * (attempt + 1))
    raise SystemExit("Wikidata did not answer")


def main():
    companies = json.load(open(sys.argv[1]))["companies"]
    by_host = {}
    for cid, c in companies.items():
        if c.get("websiteUrl"):
            h = host_of(c["websiteUrl"])
            if h:
                by_host.setdefault(h, []).append(cid)
    hosts = sorted(by_host)
    found = {}   # company id -> set of (item, name, cc, city, lat, lon)
    for i in range(0, len(hosts), CHUNK):
        chunk = hosts[i:i + CHUNK]
        url_to_host = {u: h for h in chunk for u in variants(h)}
        for b in query(list(url_to_host)):
            h = url_to_host.get(b["url"]["value"])
            if h is None:
                continue
            row = (b["item"]["value"].rsplit("/", 1)[1], b["itemLabel"]["value"], b["cc"]["value"],
                   b["hqLabel"]["value"], b["lat"]["value"], b["lon"]["value"], int(b["sl"]["value"]),
                   b["hq"]["value"].rsplit("/", 1)[1])
            for cid in by_host[h]:
                found.setdefault(cid, set()).add(row)
        print(f"{min(i + CHUNK, len(hosts))}/{len(hosts)} hosts, {len(found)} companies with a place", file=sys.stderr)
        time.sleep(2)
    if "--by-name" in sys.argv:
        todo = sorted(c for c in companies if c not in found and companies[c].get("websiteUrl") and companies[c].get("name"))
        for i in range(0, len(todo), 60):
            chunk = todo[i:i + 60]
            names = {}
            for cid in chunk:
                host = host_of(companies[cid]["websiteUrl"])
                for n in {companies[cid]["name"].strip(), cid.replace("_", " ").strip().title()}:
                    names.setdefault((n, host), []).append(cid)
            for b in query(names=list(names)):
                key = (b["itemLabel"]["value"], b["host"]["value"])
                # the label returned is the English label, which equals the name that was asked for
                for (n, h), cids in names.items():
                    if h == b["host"]["value"] and n.lower() == b["itemLabel"]["value"].lower():
                        for cid in cids:
                            found.setdefault(cid, set()).add((b["item"]["value"].rsplit("/", 1)[1], b["itemLabel"]["value"],
                                    b["cc"]["value"], b["hqLabel"]["value"], b["lat"]["value"], b["lon"]["value"],
                                    int(b["sl"]["value"]), b["hq"]["value"].rsplit("/", 1)[1]))
            print(f"by name {min(i + 60, len(todo))}/{len(todo)}, {len(found)} companies with a place", file=sys.stderr)
            time.sleep(2)
    with open(sys.argv[2], "w", encoding="utf-8") as out:
        for cid in sorted(found):
            best = max(r[6] for r in found[cid])
            top = [r for r in found[cid] if r[6] == best]   # the well-known entity decides
            if len({r[0] for r in top}) != 1 or len({r[7] for r in top}) != 1:
                continue   # two places for it: no single base (one place may carry several coordinates)
            r = sorted(top)[0]
            out.write("\t".join([cid, r[0], r[1].replace("\t", " "), r[2], r[3].replace("\t", " "), r[4], r[5]]) + "\n")


main()
