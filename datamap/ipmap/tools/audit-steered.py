#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Prints the networks that build-ipmap.py treats as steered by DNS, so that the list is read before a file is shipped.

usage: audit-steered.py ORIGIN-ASN-IPV4.CSV ORIGIN-ASN-IPV6.CSV
One line per AS number: the number, the operator name and how many address ranges carry it; then the totals. The names
that match are the ones of STEERED_NAMES (whole words, any case). Read the list for a network that is not steered by DNS
(a false match) and, in the names that do not match, for a CDN or edge operator that is missing.
"""
import importlib.util, os, sys, csv, collections

spec = importlib.util.spec_from_file_location("buildipmap", os.path.join(os.path.dirname(os.path.abspath(__file__)), "build-ipmap.py"))
B = importlib.util.module_from_spec(spec)
spec.loader.exec_module(B)


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    org, ranges = {}, collections.Counter()
    for path in sys.argv[1:]:
        with B.opener(path) as f:
            for row in csv.reader(f):
                if len(row) >= 4 and not row[0].startswith("#"):
                    org.setdefault(int(row[2]), row[3].strip())
                    ranges[int(row[2])] += 1
    asns = B.steered_asns(org)
    for asn in asns:
        print("%d\t%s\t%d" % (asn, org[asn], ranges[asn]))
    print("%d of %d AS numbers steered, %d of %d ranges" % (len(asns), len(org), sum(ranges[a] for a in asns), sum(ranges.values())))


main()
