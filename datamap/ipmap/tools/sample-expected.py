#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Writes the lookups of the Python reader for a sample of addresses, in the format of ipmap-expected.tsv, so that the
Java reader can be compared with it on the real file (IpMapCheck takes a second pair of arguments).

usage: sample-expected.py IPMAP.BIN OUT.tsv [--count N] [--seed S] [--addresses FILE]
The sample: N random IPv4 addresses, N random IPv6 addresses from the starts of the IPv6 country ranges, the first address
of every 50th anycast and city range, and every address in FILE (one per line).
"""
import importlib.util, os, random, struct, sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("buildipmap", os.path.join(HERE, "build-ipmap.py"))
B = importlib.util.module_from_spec(spec)
spec.loader.exec_module(B)


def starts(r, name, stride, keybytes):
    off, length = r.sec[name]
    fmt = ">I" if keybytes == 4 else ">Q"
    return [struct.unpack(fmt, r.blob[off + i * stride: off + i * stride + keybytes])[0] for i in range(length // stride)]


def main():
    argv = sys.argv[1:]
    if len(argv) < 2:
        raise SystemExit(__doc__)
    opts = {"--count": "2000", "--seed": "1", "--addresses": ""}
    for i in range(2, len(argv), 2):
        opts[argv[i]] = argv[i + 1]
    rnd = random.Random(int(opts["--seed"]))
    n = int(opts["--count"])
    r = B.Reader(open(argv[0], "rb").read())
    addrs = ["%d.%d.%d.%d" % tuple(rnd.randrange(256) for _ in range(4)) for _ in range(n)]
    v6 = starts(r, "CC6", 10, 8)
    for _ in range(n):
        top = rnd.choice(v6) + rnd.randrange(0, 1 << 20)
        low = rnd.getrandbits(64)
        addrs.append(B.ipaddress.ip_address((top << 64) | low).compressed)
    for name, stride, key, shift in (("ANY4", 4, 4, 8), ("CITY4", 8, 4, 0)):
        for k in starts(r, name, stride, key)[::50]:
            addrs.append(str(B.ipaddress.IPv4Address((k << shift) | 1 if shift else k)))
    if opts["--addresses"]:
        addrs += [l.strip() for l in open(opts["--addresses"]) if l.strip()]
    with open(argv[1], "w") as f:
        for a in addrs:
            kind, cc, city, lat, lon, asn, op = r.lookup(a)
            f.write("%s\t%s\t%s\t%s\t%.2f\t%.2f\t%d\t%s\n" % (a, kind, cc, city, lat, lon, asn, op))
    print("%s: %d addresses" % (argv[1], len(addrs)))


main()
