#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check of tools/import-companiesdb.py on a tiny fixture: categories, owners, bases, the curated layer, ids."""
import json, pathlib, subprocess, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent.parent
T = pathlib.Path(tempfile.mkdtemp())
(T / "dist").mkdir(); (T / "curated").mkdir()

cats = {"0": "audio_video_player", "4": "advertising", "5": "essential", "6": "site_analytics", "7": "social_media",
        "8": "misc", "9": "cdn", "101": "mobile_analytics", "3": "pornvertising"}
trackers = {
    "t_ads": {"name": "Ads", "categoryId": 4, "companyId": "adco"},
    "t_an": {"name": "An", "categoryId": 6, "companyId": "adco"},
    "t_soc": {"name": "Soc", "categoryId": 7, "companyId": "nobase_co"},
    "t_cdn": {"name": "Cdn", "categoryId": 9, "companyId": "adco"},
    "t_core": {"name": "Core", "categoryId": 5, "companyId": "adco"},
    "t_misc": {"name": "Misc", "categoryId": 8, "companyId": "adco"},
    "t_mob": {"name": "Mob", "categoryId": 101, "companyId": "facebook"},
    "t_nocomp": {"name": "NoComp", "categoryId": 4},
    "t_dup": {"name": "Dup", "categoryId": 4, "companyId": "adco"},
    "t_long": {"name": "Long", "categoryId": 4, "companyId": "a_company_with_a_very_long_id"},
    "t_twin": {"name": "Twin", "categoryId": 4, "companyId": "adco_twin"},
}
domains = {"ads.example": "t_ads", "an.example": "t_an", "soc.example": "t_soc", "cdn.example": "t_cdn",
           "core.example": "t_core", "misc.example": "t_misc", "mob.example": "t_mob", "nocomp.example": "t_nocomp",
           "dup.example": "t_dup", "long.example": "t_long", "Bad_Host!": "t_ads", "twin.example": "t_twin"}
(T / "dist" / "trackers.json").write_text(json.dumps({"categories": cats, "trackers": trackers, "trackerDomains": domains}))
(T / "dist" / "companies.json").write_text(json.dumps({"companies": {
    "adco": {"name": "Ad Co", "websiteUrl": "http://adco.example/"},
    "nobase_co": {"name": "No Base", "websiteUrl": "http://nobase.example/"},
    "facebook": {"name": "Facebook", "websiteUrl": "http://facebook.com/"},
    "adco_twin": {"name": "Ad Co (second record)", "websiteUrl": "http://adco.example/"},
    "a_company_with_a_very_long_id": {"name": "Long", "websiteUrl": "http://long.example/"}}}))
(T / "bases.tsv").write_text(
    "adco\tQ1\tAd Company Ltd\tNL\tAmsterdam\t52.37\t4.89\n"
    "facebook\tQ2\tFacebook\tUS\tMenlo Park\t37.45\t-122.18\n"
    "a_company_with_a_very_long_id\tQ3\tLong\tDE\tBerlin\t52.52\t13.4\n"
    "adco_twin\tQ1\tAd Company Ltd\tNL\tAmsterdam\t52.37\t4.89\n")
c = T / "curated"
(c / "sources.tsv").write_text("C-001\tCurated source\tLicenseRef-Public-Fact\thttps://example.invalid/c1\n")
(c / "owners.tsv").write_text("meta\tMeta\tMeta Platforms, Inc.\tUS\tMenlo Park\t37.4542\t-122.1786\tC-001\n")
(c / "map.tsv").write_text("facebook\tmeta\n")
(c / "services-curated.tsv").write_text("dup.example\tmeta\tsocial\tC-001\n")
(c / "relations.tsv").write_text("")
(c / "publishers.tsv").write_text("")

out = T / "out"
r = subprocess.run([str(HERE / "tools/import-companiesdb.py"), str(T / "dist"), str(T / "bases.tsv"), str(c), str(out),
                    "--commit", "abc1234"], capture_output=True, text=True)
if r.returncode != 0:
    print(r.stdout, r.stderr); sys.exit(1)

def rows(name):
    p = out / name
    return [l.split("\t") for l in p.read_text(encoding="utf-8").split("\n") if l] if p.exists() else []

svc = {r[0]: r for r in rows("services-companiesdb.tsv")}
def check(cond, what):
    if not cond:
        print("FAILED:", what); sys.exit(1)

check(svc["ads.example"][1:] == ["adco", "advertising", "ADG"], "advertising of a company with a base")
check(svc["an.example"][2] == "analytics", "site analytics is analytics")
check(svc["soc.example"][1:3] == ["-", "social"], "a company without a base has owner -")
check(svc["cdn.example"][2] == "cdn" and svc["core.example"][2] == "core", "cdn and essential")
check(svc["mob.example"][1:3] == ["meta", "analytics"], "a curated company maps to its curated owner; mobile analytics")
check("misc.example" not in svc, "a category we do not use is left unknown")
check("nocomp.example" not in svc, "a tracker without a company is not a service of one")
check("dup.example" not in svc, "a curated service wins over the same suffix of the database")
check(svc["long.example"][1] == "-", "an id longer than 24 characters gives no owner")
check(not any(k.lower() != k or "!" in k for k in svc), "a name that is not a host name is dropped")

owners = {r[0]: r for r in rows("owners.tsv")}
check(svc["twin.example"][1] == "adco", "two records of one Wikidata item are one owner")
check(sum(1 for r in rows("sources.tsv") if r[0] == "WD-Q1") == 1, "and one source")
check(set(owners) == {"adco", "meta"}, "owners: only those that own a service, plus curated: " + str(set(owners)))
check(owners["adco"] == ["adco", "Ad Co", "Ad Company Ltd", "NL", "Amsterdam", "52.3700", "4.8900", "WD-Q1"], "generated owner row")
srcs = {r[0]: r for r in rows("sources.tsv")}
check(srcs["ADG"][2] == "CC-BY-SA-4.0" and "abc1234" in srcs["ADG"][1], "the database source with its commit")
check(srcs["WD-Q1"][2] == "CC0-1.0" and srcs["WD-Q1"][3] == "https://www.wikidata.org/wiki/Q1", "the Wikidata source")
check("C-001" in srcs, "curated sources are kept")
check("WD-Q2" not in srcs, "no Wikidata source for a company the curated layer replaced")
print("importer ok")
