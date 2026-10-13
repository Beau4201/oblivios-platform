#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Checks of tools/build-ipmap.py: the fixture lookups, the layout, and the files the Java check reads.

usage: ipmap_check.py [--regenerate]   --regenerate rewrites tests/fixtures/ipmap.bin and ipmap-expected.tsv
"""
import importlib.util, os, struct, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(HERE, "..", "tools", "build-ipmap.py")
FIX = os.path.join(HERE, "fixtures")
spec = importlib.util.spec_from_file_location("buildipmap", TOOL)
B = importlib.util.module_from_spec(spec)
spec.loader.exec_module(B)

ARGS = {k: os.path.join(FIX, v) for k, v in {
    "country4": "country4.csv", "country6": "country6.csv", "asn4": "asn4.csv", "asn6": "asn6.csv",
    "city4": "city4.csv", "anycast4": "anycast4.csv", "anycast6": "anycast6.csv", "anchors": "anchors.tsv"}.items()}
ARGS["sources"] = "fixture"

# (address, kind, country, city, lat, lon, asn, operator)
EXPECTED = [
    ("1.0.0.1", "ANYCAST", "AU", "", 0.0, 0.0, 13335, "Cloudflare, Inc."),
    ("1.0.1.1", "CITY", "CN", "Wenquan", 26.1, 119.3, 0, ""),
    ("8.8.8.8", "CITY", "US", "Mountain View", 37.39, -122.08, 15169, "Google LLC"),
    ("9.9.9.9", "ANYCAST", "CH", "", 0.0, 0.0, 19281, "Quad9"),
    ("52.0.1.1", "STEERED", "US", "", 0.0, 0.0, 16509, "Amazon.com, Inc."),
    ("104.16.0.5", "ANYCAST", "US", "", 0.0, 0.0, 13335, "Cloudflare, Inc."),
    ("104.16.1.5", "NONE", "", "", 0.0, 0.0, 0, ""),
    ("185.0.0.5", "UNCERTAIN", "DE", "", 0.0, 0.0, 24940, "Hetzner Online GmbH"),
    ("200.0.0.5", "COUNTRY", "BR", "", -10.0, -55.0, 0, ""),
    ("203.0.0.9", "CITY", "AU", "Sydney", -33.87, 151.21, 0, ""),
    ("10.0.0.1", "NONE", "", "", 0.0, 0.0, 0, ""),
    ("0.0.0.0", "NONE", "", "", 0.0, 0.0, 0, ""),
    ("255.255.255.255", "NONE", "", "", 0.0, 0.0, 0, ""),
    ("2001:4860::1", "COUNTRY", "US", "", 39.8, -98.6, 15169, "Google LLC"),
    ("2a00:1450::1", "COUNTRY", "IE", "", 53.4, -8.2, 15169, "Google LLC"),
    ("2606:4700:10::1", "ANYCAST", "US", "", 0.0, 0.0, 13335, "Cloudflare, Inc."),
    ("2606:4700:11::1", "COUNTRY", "US", "", 39.8, -98.6, 13335, "Cloudflare, Inc."),
    ("2607::1", "NONE", "", "", 0.0, 0.0, 0, ""),
]


def expect(cond, what):
    if not cond:
        raise AssertionError(what)


def lookups():
    blob = B.build(ARGS)
    r = B.Reader(blob)
    return blob, r


def main():
    blob, r = lookups()
    for row in EXPECTED:
        got = r.lookup(row[0])
        expect(got == row[1:], "%s: expected %s but got %s" % (row[0], row[1:], got))
    # the layout: magic, version, sections inside the file and in order, no hole
    expect(blob[:4] == b"DMIP" and struct.unpack(">H", blob[4:6])[0] == 1, "header")
    end = B.HEADER
    for i, name in enumerate(B.SECTIONS):
        off, length = struct.unpack(">II", blob[8 + 8 * i: 16 + 8 * i])
        expect(off == end, "section %s starts at %d, not %d" % (name, off, end))
        end = off + length
    expect(end == len(blob), "the sections do not fill the file")
    # equal neighbours are merged, a gap is a record with no value
    cc4_off, cc4_len = struct.unpack(">II", blob[8: 16])
    starts = [struct.unpack(">IH", blob[cc4_off + i * 6: cc4_off + i * 6 + 6]) for i in range(cc4_len // 6)]
    expect(starts[0] == (0, 0), "the first record must cover address 0 with no value")
    expect(all(starts[i][0] < starts[i + 1][0] for i in range(len(starts) - 1)), "starts must increase")
    expect(all(starts[i][1] != starts[i + 1][1] for i in range(len(starts) - 1)), "equal neighbours must be merged")
    # the build is deterministic
    expect(B.build(ARGS) == blob, "two builds differ")
    # bad input is refused, not skipped
    with tempfile.TemporaryDirectory() as d:
        bad = os.path.join(d, "bad.csv")
        open(bad, "w").write("1.0.0.0,1.0.0.255,AU\n1.0.0.128,1.0.1.255,CN\n")
        refused = False
        try:
            B.build(dict(ARGS, country4=bad))
        except SystemExit:
            refused = True
        expect(refused, "overlapping ranges must be refused")
        v6 = os.path.join(d, "v6.csv")
        open(v6, "w").write("2001:db8::,2001:db8::ffff,US\n")
        refused = False
        try:
            B.build(dict(ARGS, country4=v6))
        except SystemExit:
            refused = True
        expect(refused, "an IPv6 range in an IPv4 file must be refused")
        # IPv6 ranges kept to 64 bits can meet in one /64: the earlier range keeps it, nothing is refused
        shared = os.path.join(d, "shared6.csv")
        open(shared, "w").write("2001:db8::,2001:db8::ff,US\n2001:db8::100,2001:db8::1ff,DE\n"
                                "2001:db8::200,2001:db8:0:1:ffff:ffff:ffff:ffff,FR\n2001:db8:0:2::,2001:db8:0:2::ff,IT\n")
        r6 = B.Reader(B.build(dict(ARGS, country6=shared)))
        expect(r6.lookup("2001:db8::5")[1] == "US", "the earlier range keeps a shared /64")
        expect(r6.lookup("2001:db8:0:1::1")[1] == "FR", "a range that reaches past it starts after it")
        expect(r6.lookup("2001:db8:0:2::9")[1] == "IT", "a later /64 is its own")
        pfx = os.path.join(d, "any.csv")
        open(pfx, "w").write("prefix,number_of_sites,backing_prefix\n1.0.0.0/23,5,-\n")
        refused = False
        try:
            B.build(dict(ARGS, anycast4=pfx))
        except SystemExit:
            refused = True
        expect(refused, "an anycast prefix that is not a /24 must be refused")
    # the committed files for the Java check equal what the tool makes now
    tsv = "".join("%s\t%s\t%s\t%s\t%.2f\t%.2f\t%d\t%s\n" % row for row in EXPECTED)
    if "--regenerate" in sys.argv:
        open(os.path.join(FIX, "ipmap.bin"), "wb").write(blob)
        open(os.path.join(FIX, "ipmap-expected.tsv"), "w").write(tsv)
    expect(open(os.path.join(FIX, "ipmap.bin"), "rb").read() == blob, "fixtures/ipmap.bin is not what the tool makes (--regenerate)")
    expect(open(os.path.join(FIX, "ipmap-expected.tsv")).read() == tsv, "fixtures/ipmap-expected.tsv is stale (--regenerate)")
    print("PASS ipmap_check")


main()
