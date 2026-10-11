#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Turns the AdGuard companies database (dist/) into the data files that build-classifier.py reads.

usage: import-companiesdb.py DIST_DIR BASES.tsv CURATED_DIR OUT_DIR --commit HASH

DIST_DIR     dist/ of github.com/AdguardTeam/companiesdb (CC-BY-SA-4.0, built on whotracks.me, CC-BY-4.0)
BASES.tsv    where the companies are based, from tools/fetch-wikidata-bases.py (Wikidata, CC0-1.0)
CURATED_DIR  facts checked by hand against primary sources: sources.tsv, owners.tsv, services-curated.tsv,
             relations.tsv, publishers.tsv and map.tsv (database company id, curated owner id). A curated owner or
             service wins over the same one of the database.
OUT_DIR      receives a complete data directory for the builder.
A service gets an owner only when the company has a base ("a company without a documented single base is not an
owner row", docs SOURCES.md); otherwise its owner is "-". Categories the classifier does not use stay unknown.
"""
import json, pathlib, re, shutil, sys

CATEGORY = {"advertising": "advertising", "pornvertising": "advertising", "site_analytics": "analytics",
            "mobile_analytics": "analytics", "telemetry": "analytics", "social_media": "social", "cdn": "cdn",
            "essential": "core"}
SUFFIX = re.compile(r"^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
MAX_ID = 24


def clean(s):
    return " ".join(s.replace("\t", " ").split())


def owner_id(company_id):
    oid = re.sub(r"[^a-z0-9_]", "_", company_id.lower())
    return oid if 1 <= len(oid) <= MAX_ID else None


def read_tsv(path):
    return [l.split("\t") for l in path.read_text(encoding="utf-8").split("\n") if l and not l.startswith("#")] \
        if path.exists() else []


def main():
    argv = sys.argv[1:]
    commit = argv[argv.index("--commit") + 1]
    del argv[argv.index("--commit"):argv.index("--commit") + 2]
    dist, bases_file, curated, out = (pathlib.Path(a) for a in argv[:4])
    trackers = json.load(open(dist / "trackers.json", encoding="utf-8"))
    companies = json.load(open(dist / "companies.json", encoding="utf-8"))["companies"]
    cats = trackers["categories"]
    bases = {r[0]: r for r in read_tsv(bases_file)}
    cmap = {r[0]: r[1] for r in read_tsv(curated / "map.tsv")}
    curated_owners = read_tsv(curated / "owners.tsv")
    curated_services = read_tsv(curated / "services-curated.tsv")
    curated_suffixes = {r[0] for r in curated_services}
    curated_ids = {r[0] for r in curated_owners}

    # Two records of the database can be the same company (one Wikidata item): the first id stands for both.
    canonical = {}
    for cid in sorted(bases):
        if owner_id(cid):
            canonical.setdefault(bases[cid][1], cid)

    services, used = [], set()
    skipped = {"category": 0, "no company": 0, "not a host name": 0}
    for domain, tid in sorted(trackers["trackerDomains"].items()):
        tr = trackers["trackers"][tid]
        cat = CATEGORY.get(cats[str(tr["categoryId"])])
        if cat is None:
            skipped["category"] += 1
            continue
        cid = tr.get("companyId")
        if not cid:
            skipped["no company"] += 1
            continue
        if not SUFFIX.match(domain):
            skipped["not a host name"] += 1
            continue
        if domain in curated_suffixes:
            continue
        if cid in cmap:
            owner = cmap[cid]
        elif cid in bases and owner_id(cid):
            cid = canonical[bases[cid][1]]
            owner = owner_id(cid)
        else:
            owner = "-"
        if owner != "-":
            used.add((cid, owner))
        services.append((domain, owner, cat, "ADG"))

    owners, sources = [], []
    for cid, oid in sorted(used):
        if oid in curated_ids or cid in cmap:
            continue
        b = bases[cid]
        name = clean(companies.get(cid, {}).get("name") or b[2])
        owners.append((oid, name, clean(b[2]), b[3], clean(b[4]), f"{float(b[5]):.4f}", f"{float(b[6]):.4f}",
                       "WD-" + b[1]))
        sources.append(("WD-" + b[1], f"Wikidata item {b[1]}, headquarters location (read with the database)",
                        "CC0-1.0", "https://www.wikidata.org/wiki/" + b[1]))
    sources.insert(0, ("ADG", f"AdGuard companies database, commit {commit}: tracker domains, companies and "
                              "categories (built on whotracks.me)", "CC-BY-SA-4.0",
                       "https://github.com/AdguardTeam/companiesdb"))

    out.mkdir(parents=True, exist_ok=True)
    def write(name, rows):
        (out / name).write_text("".join("\t".join(r) + "\n" for r in rows), encoding="utf-8")
    write("sources.tsv", read_tsv(curated / "sources.tsv") + sources)
    write("owners.tsv", curated_owners + owners)
    write("services-companiesdb.tsv", services)
    write("services-curated.tsv", curated_services)
    for name in ("relations.tsv", "publishers.tsv"):
        if (curated / name).exists():
            shutil.copy(curated / name, out / name)
        else:
            write(name, [])
    print(f"{len(services)} services, {len(owners)} generated owners, skipped {skipped}", file=sys.stderr)


main()
