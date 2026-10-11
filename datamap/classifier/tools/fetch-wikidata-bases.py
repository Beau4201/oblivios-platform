#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Looks up where companies are based in Wikidata (CC0) by the official website of each company.

usage: fetch-wikidata-bases.py COMPANIES.json OUT.tsv [TRACKERS.json [TOP]]
COMPANIES.json is dist/companies.json of the AdGuard companies database. For every company with a website it asks
Wikidata for the item whose official website (P856) is that site, and for the headquarters location (P159) of the
item when that is a human settlement with coordinates. When several items share a site, the one with the most
sitelinks (the well-known entity) decides. A company is written only when that item has exactly one such place;
two different places mean "no single base" and the company is left out.
OUT.tsv: company-id, wikidata item, name of the item, country (ISO 3166-1 alpha-2), city, latitude, longitude.
With TRACKERS.json (dist/trackers.json) a second step searches Wikidata by company name for the TOP companies
(default 400) that have the most domains and no place yet. A found item counts only when its official website
belongs to the company's own host: a name alone is never enough.
Network access, rate limited on purpose. Run by hand, never during a build.
"""
import json, sys, time, urllib.parse, urllib.request

ENDPOINT = "https://query.wikidata.org/sparql"
UA = "sovereignos-classifier-research/1.0 (privacy-oriented Android OS; contact via project repository)"
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


def query(urls=None, items=None):
    if items is not None:
        head = "VALUES ?item { " + " ".join(f"wd:{i}" for i in items) + " }"
    else:
        head = "VALUES ?url { " + " ".join(f"<{u}>" for u in urls) + " }"
    q = f"""SELECT ?url ?item ?itemLabel ?sl ?hq ?hqLabel ?lat ?lon ?cc WHERE {{
  {head}
  ?item wdt:P856 ?url .
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


def search(name):
    url = "https://www.wikidata.org/w/api.php?" + urllib.parse.urlencode(
        {"action": "wbsearchentities", "search": name, "language": "en", "limit": 5, "format": "json"})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return [x["id"] for x in json.load(r).get("search", [])]
        except Exception as e:
            print(f"search retry {attempt + 1}: {e}", file=sys.stderr)
            time.sleep(5 * (attempt + 1))
    return []


def same_site(url, host):
    h = host_of(url)
    return h == host or h.endswith("." + host) or host.endswith("." + h)


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
    if len(sys.argv) > 3:
        t = json.load(open(sys.argv[3]))
        keep = {"advertising", "pornvertising", "site_analytics", "mobile_analytics", "telemetry", "social_media", "cdn", "essential"}
        count = {}
        for dom, tid in t["trackerDomains"].items():
            tr = t["trackers"][tid]
            if tr.get("companyId") and t["categories"][str(tr["categoryId"])] in keep:
                count[tr["companyId"]] = count.get(tr["companyId"], 0) + 1
        top = int(sys.argv[4]) if len(sys.argv) > 4 else 400
        missing = [c for c, _ in sorted(count.items(), key=lambda x: -x[1])
                   if c not in found and c in companies and companies[c].get("websiteUrl")][:top]
        cand = {}   # candidate item -> company ids it may belong to
        for n, cid in enumerate(missing, 1):
            for q in {companies[cid]["name"], host_of(companies[cid]["websiteUrl"]).split(".")[0]}:
                for qid in search(q):
                    cand.setdefault(qid, set()).add(cid)
            if n % 50 == 0:
                print(f"searched {n}/{len(missing)}", file=sys.stderr)
            time.sleep(0.4)
        ids = sorted(cand)
        for i in range(0, len(ids), 80):
            for b in query(items=ids[i:i + 80]):
                item = b["item"]["value"].rsplit("/", 1)[1]
                for cid in cand[item]:
                    if same_site(b["url"]["value"], host_of(companies[cid]["websiteUrl"])):
                        found.setdefault(cid, set()).add((item, b["itemLabel"]["value"], b["cc"]["value"],
                                b["hqLabel"]["value"], b["lat"]["value"], b["lon"]["value"], int(b["sl"]["value"]),
                                b["hq"]["value"].rsplit("/", 1)[1]))
            time.sleep(2)
        print(f"after the name search: {len(found)} companies with a place", file=sys.stderr)
    with open(sys.argv[2], "w", encoding="utf-8") as out:
        for cid in sorted(found):
            best = max(r[6] for r in found[cid])
            top = [r for r in found[cid] if r[6] == best]   # the well-known entity decides
            if len({r[0] for r in top}) != 1 or len({r[7] for r in top}) != 1:
                continue   # two places for it: no single base (one place may carry several coordinates)
            r = sorted(top)[0]
            out.write("\t".join([cid, r[0], r[1].replace("\t", " "), r[2], r[3].replace("\t", " "), r[4], r[5]]) + "\n")


main()
