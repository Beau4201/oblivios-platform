#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Builds classifier.tsv from the data directory and refuses what the device would refuse, and more.

usage: build-classifier.py DATA_DIR OUTPUT.tsv [--licences ALLOWED.txt] [--flags DRAWABLE_DIR]

DATA_DIR holds one file per record type, without the record type in the first column:
  sources.tsv     id  title  licence  url
  owners.tsv      id  name  legal-name  country  city  latitude  longitude  source
  publishers.tsv  package  owner-id  source
  relations.tsv   owner-a  type  owner-b  note  source
  services-*.tsv  suffix  owner-id-or-"-"  category  source        (one file per imported list)
Rules beyond the device parser (MAP-017): every source licence is on the allowed list; every source has a
URL; with --flags, every country of an owner has a flag drawable. The device parser is run on the result by
tools/check-classifier.sh; this script never claims a file is valid by itself.
"""
import pathlib, sys

HEADER = "datamap-classifier\t1\n"


def rows(path):
    out = []
    for n, line in enumerate(path.read_text(encoding="utf-8").split("\n"), 1):
        if line == "" or line.startswith("#"):
            continue
        out.append((n, line.split("\t")))
    return out


def main():
    argv = sys.argv[1:]
    opts = {}
    for key in ("--licences", "--flags"):
        if key in argv:
            i = argv.index(key)
            opts[key] = argv[i + 1]
            del argv[i:i + 2]
    data, out = pathlib.Path(argv[0]), pathlib.Path(argv[1])
    allowed = None
    if "--licences" in opts:
        allowed = {l.strip() for l in pathlib.Path(opts["--licences"]).read_text(encoding="utf-8").split("\n")
                   if l.strip() and not l.startswith("#")}
    problems = []
    lines = []

    def emit(kind, files, fields):
        for f in files:
            for n, r in rows(f):
                if len(r) != fields:
                    problems.append(f"{f.name}:{n}: {kind} needs {fields} fields, found {len(r)}")
                    continue
                lines.append((kind, "\t".join([kind] + r), r))

    emit("source", [data / "sources.tsv"], 4)
    emit("owner", [data / "owners.tsv"], 8)
    emit("publisher", [data / "publishers.tsv"], 3)
    emit("relation", [data / "relations.tsv"], 5)
    emit("service", sorted(data.glob("services-*.tsv")), 4)

    for kind, _, r in lines:
        if kind == "source":
            if allowed is not None and r[2] not in allowed:
                problems.append(f"source {r[0]}: licence {r[2]!r} is not on the allowed list")
            if not r[3].startswith("https://"):
                problems.append(f"source {r[0]}: the url must be https")
        if kind == "owner" and "--flags" in opts:
            flag = pathlib.Path(opts["--flags"]) / f"flag_{r[3].lower()}.xml"
            if not flag.exists():
                problems.append(f"owner {r[0]}: no flag for country {r[3]} ({flag.name})")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        sys.exit(1)
    order = {"source": 0, "owner": 1, "service": 2, "publisher": 3, "relation": 4}
    lines.sort(key=lambda x: (order[x[0]], x[1]))   # sorted, so that the same data gives the same bytes
    out.write_text(HEADER + "".join(l[1] + "\n" for l in lines), encoding="utf-8")
    print(f"{len(lines)} records written to {out}")


main()
