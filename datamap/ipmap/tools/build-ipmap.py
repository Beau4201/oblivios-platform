#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Builds ipmap.bin, the file the Data Map collector reads to give a contacted address a place (ADR-161).

usage: build-ipmap.py --country4 F --country6 F --asn4 F --asn6 F --city4 F --anycast4 F --anycast6 F
                      --anchors F --out ipmap.bin

country4/6  sapics/ip-location-db server-country-ipv4.csv, -ipv6.csv        start,end,CC
asn4/6      sapics/ip-location-db origin-asn-ipv4.csv, -ipv6.csv            start,end,asn,"operator"
city4       sapics/ip-location-db dbip-city-ipv4.csv(.gz) (DB-IP Lite)      start,end,CC,state,state2,city,postcode,lat,lon,tz
anycast4/6  UT-DACS/Anycast-Census IPv4-latest.csv, IPv6-latest.csv         prefix,number_of_sites,...
anchors     data/anchors.tsv (tools/fetch-anchors.py)                       CC<TAB>lat<TAB>lon
A file name ending in .gz is read compressed. IPv6 is kept to its first 64 bits; city data is IPv4 only (D6).

The layout is big-endian and every section is an array of fixed records, so that the reader can binary search a
mapped file with no parsing:

    header   "DMIP", u16 version (1), u16 0, then 13 sections of (u32 offset, u32 length) in the order below
    CC4      u32 start, u16 cc            cc = two letters packed (0 = none); a gap in the data is a record with cc 0
    ASN4     u32 start, u32 asn           0 = none
    CITY4    u32 start, u32 loc           0xFFFFFFFF = none
    LOC      u16 cc, i16 lat*100, i16 lon*100, u32 name offset
    CC6      u64 start, u16 cc
    ASN6     u64 start, u32 asn
    ORG      u32 asn, u32 name offset     sorted by asn
    NAMES    u8 length, UTF-8 bytes       deduplicated
    ANY4     u32 prefix                   the address >> 8 of an anycast /24, sorted
    ANY6     u64 prefix                   the first 48 bits of an anycast /48, sorted
    STEER    u32 asn                      networks that steer by DNS (STEERED_OPERATORS), sorted
    ANCHOR   u16 cc, i16 lat*100, i16 lon*100, u32 0     where a country without a city is drawn, sorted by cc
    SOURCES  UTF-8 text: what the file was made from (the pins)
"""
import bisect, csv, gzip, ipaddress, struct, sys

MAGIC = b"DMIP"
VERSION = 1
SECTIONS = ["CC4", "ASN4", "CITY4", "LOC", "CC6", "ASN6", "ORG", "NAMES", "ANY4", "ANY6", "STEER", "ANCHOR", "SOURCES"]
HEADER = 8 + 8 * len(SECTIONS)
NONE32 = 0xFFFFFFFF
MAX_NAME = 120
# D3 of the design: networks that answer with unicast addresses chosen by DNS. Their ranges are not anycast prefixes
# (the census cannot see them) and the country of a range is that of the operator's registration, not of the edge.
STEERED_OPERATORS = ("Amazon", "Akamai", "Microsoft Corporation")


def opener(path):
    return gzip.open(path, "rt", encoding="utf-8", newline="") if path.endswith(".gz") else open(path, encoding="utf-8", newline="")


def pack_cc(cc):
    cc = cc.strip().upper()
    if len(cc) != 2 or not cc.isalpha():
        return 0
    return (ord(cc[0]) << 8) | ord(cc[1])


def unpack_cc(v):
    return "" if v == 0 else chr(v >> 8) + chr(v & 0xFF)


def name_bytes(s):
    b = s.encode("utf-8")[:MAX_NAME]
    while True:
        try:
            b.decode("utf-8")
            return b
        except UnicodeDecodeError:
            b = b[:-1]


class Names:
    def __init__(self):
        self.blob = bytearray()
        self.index = {}

    def add(self, s):
        if s in self.index:
            return self.index[s]
        b = name_bytes(s)
        off = len(self.blob)
        self.blob.append(len(b))
        self.blob += b
        self.index[s] = off
        return off


def ranges(path, version):
    """(start, end, row) of every data row, as integers; IPv6 to its first 64 bits."""
    out = []
    with opener(path) as f:
        for row in csv.reader(f):
            if not row or row[0].startswith("#"):
                continue
            a = ipaddress.ip_address(row[0].strip())
            b = ipaddress.ip_address(row[1].strip())
            if a.version != version:
                raise SystemExit("%s: %s is not IPv%d" % (path, row[0], version))
            s, e = (int(a), int(b)) if version == 4 else (int(a) >> 64, int(b) >> 64)
            if e < s:
                raise SystemExit("%s: a range ends before it starts: %s" % (path, row[:2]))
            out.append((s, e, row[2:]))
    out.sort(key=lambda r: r[0])
    if version == 6:
        out = trim64(out)
    for i in range(1, len(out)):
        if out[i][0] <= out[i - 1][1]:
            raise SystemExit("%s: ranges overlap at %s" % (path, out[i][0]))
    return out


def trim64(rows):
    """Ranges kept to their first 64 bits can meet inside one /64. The earlier range keeps the shared /64: a later range
    that lies inside what is covered is dropped, and one that reaches past it starts after it."""
    out = []
    for s, e, row in rows:
        if out and s <= out[-1][1]:
            if e <= out[-1][1]:
                continue
            s = out[-1][1] + 1
        out.append((s, e, row))
    return out


def stepped(rows, top, none, value_of):
    """Records (start, value) with a 'none' record in every gap, adjacent equal values merged."""
    out = []

    def push(start, value):
        if out and out[-1][1] == value:
            return
        out.append((start, value))

    next_start = 0
    for s, e, row in rows:
        if s > next_start:
            push(next_start, none)
        push(s, value_of(row))
        next_start = e + 1
    if next_start <= top:
        push(next_start, none)
    if not out or out[0][0] != 0:
        out.insert(0, (0, none))
    return out


def anycast(path, version, bits):
    keys = set()
    with opener(path) as f:
        for row in csv.reader(f):
            if not row or row[0] == "prefix" or row[0].startswith("#"):
                continue
            net = ipaddress.ip_network(row[0].strip(), strict=True)
            if net.version != version or net.prefixlen != bits:
                raise SystemExit("%s: %s is not an IPv%d /%d" % (path, row[0], version, bits))
            keys.add(int(net.network_address) >> ((32 if version == 4 else 128) - bits))
    return sorted(keys)


def build(args):
    names = Names()
    cc4_rows = ranges(args["country4"], 4)
    cc6_rows = ranges(args["country6"], 6)
    asn4_rows = ranges(args["asn4"], 4)
    asn6_rows = ranges(args["asn6"], 6)
    city_rows = ranges(args["city4"], 4)

    cc4 = stepped(cc4_rows, 2**32 - 1, 0, lambda r: pack_cc(r[0]))
    cc6 = stepped(cc6_rows, 2**64 - 1, 0, lambda r: pack_cc(r[0]))
    asn4 = stepped(asn4_rows, 2**32 - 1, 0, lambda r: int(r[0]))
    asn6 = stepped(asn6_rows, 2**64 - 1, 0, lambda r: int(r[0]))

    org = {}
    for rows in (asn4_rows, asn6_rows):
        for _, _, r in rows:
            org.setdefault(int(r[0]), r[1].strip())
    org_records = [(asn, names.add(org[asn])) for asn in sorted(org)]
    steer = sorted(asn for asn, n in org.items() if any(p in n for p in STEERED_OPERATORS))

    locs, loc_index = [], {}

    def city_value(r):
        cc, city = pack_cc(r[0]), r[3].strip()
        lat, lon = r[5].strip(), r[6].strip()
        if not city or not lat or not lon:
            return NONE32
        key = (cc, city, round(float(lat) * 100), round(float(lon) * 100))
        if key not in loc_index:
            loc_index[key] = len(locs)
            locs.append(key)
        return loc_index[key]

    city4 = stepped(city_rows, 2**32 - 1, NONE32, city_value)
    loc_records = [(cc, la, lo, names.add(city)) for cc, city, la, lo in locs]

    anchors = []
    with open(args["anchors"], encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            cc, lat, lon = line.rstrip("\n").split("\t")[:3]
            anchors.append((pack_cc(cc), round(float(lat) * 100), round(float(lon) * 100), 0))
    anchors.sort()

    sec = {
        "CC4": b"".join(struct.pack(">IH", s, v) for s, v in cc4),
        "ASN4": b"".join(struct.pack(">II", s, v) for s, v in asn4),
        "CITY4": b"".join(struct.pack(">II", s, v) for s, v in city4),
        "LOC": b"".join(struct.pack(">HhhI", cc, la, lo, off) for cc, la, lo, off in loc_records),
        "CC6": b"".join(struct.pack(">QH", s, v) for s, v in cc6),
        "ASN6": b"".join(struct.pack(">QI", s, v) for s, v in asn6),
        "ORG": b"".join(struct.pack(">II", a, o) for a, o in org_records),
        "NAMES": bytes(names.blob),
        "ANY4": b"".join(struct.pack(">I", k) for k in anycast(args["anycast4"], 4, 24)),
        "ANY6": b"".join(struct.pack(">Q", k) for k in anycast(args["anycast6"], 6, 48)),
        "STEER": b"".join(struct.pack(">I", a) for a in steer),
        "ANCHOR": b"".join(struct.pack(">HhhI", *a) for a in anchors),
        "SOURCES": args.get("sources", "").encode("utf-8"),
    }
    out = bytearray(MAGIC + struct.pack(">HH", VERSION, 0))
    pos = HEADER
    table = bytearray()
    for name in SECTIONS:
        table += struct.pack(">II", pos, len(sec[name]))
        pos += len(sec[name])
    out += table
    for name in SECTIONS:
        out += sec[name]
    return bytes(out)


# ------------------------------------------------------------------------------------------ reference reader


class Reader:
    """The same lookups as IpMap.java, in Python. The host checks compare the two."""

    def __init__(self, blob):
        if blob[:4] != MAGIC or struct.unpack(">H", blob[4:6])[0] != VERSION:
            raise ValueError("not an ipmap file")
        self.blob = blob
        self.sec = {n: struct.unpack(">II", blob[8 + 8 * i: 16 + 8 * i]) for i, n in enumerate(SECTIONS)}

    def _floor(self, name, stride, keybytes, key):
        off, length = self.sec[name]
        lo, hi = 0, length // stride - 1
        fmt = {4: ">I", 8: ">Q"}[keybytes]
        best = -1
        while lo <= hi:
            mid = (lo + hi) // 2
            k = struct.unpack(fmt, self.blob[off + mid * stride: off + mid * stride + keybytes])[0]
            if k <= key:
                best, lo = mid, mid + 1
            else:
                hi = mid - 1
        return best

    def _member(self, name, keybytes, key):
        i = self._floor(name, keybytes, keybytes, key)
        if i < 0:
            return False
        off, _ = self.sec[name]
        fmt = {4: ">I", 8: ">Q"}[keybytes]
        return struct.unpack(fmt, self.blob[off + i * keybytes: off + (i + 1) * keybytes])[0] == key

    def _name(self, off):
        base = self.sec["NAMES"][0]
        n = self.blob[base + off]
        return self.blob[base + off + 1: base + off + 1 + n].decode("utf-8")

    def _value(self, name, stride, keybytes, key, fmt):
        i = self._floor(name, stride, keybytes, key)
        if i < 0:
            return None
        off, _ = self.sec[name]
        return struct.unpack(fmt, self.blob[off + i * stride + keybytes: off + (i + 1) * stride])[0]

    def lookup(self, text):
        """(kind, country, city, lat, lon, asn, operator) with kind one of NONE, CITY, COUNTRY, UNCERTAIN, ANYCAST, STEERED."""
        ip = ipaddress.ip_address(text)
        if ip.version == 4:
            n = int(ip)
            cc = self._value("CC4", 6, 4, n, ">H") or 0
            asn = self._value("ASN4", 8, 4, n, ">I") or 0
            loc = self._value("CITY4", 8, 4, n, ">I")
            anycast = self._member("ANY4", 4, n >> 8)
        else:
            n = int(ip) >> 64
            cc = self._value("CC6", 10, 8, n, ">H") or 0
            asn = self._value("ASN6", 12, 8, n, ">I") or 0
            loc = None
            anycast = self._member("ANY6", 8, int(ip) >> 80)
        operator = self._org(asn) if asn else ""
        if anycast:
            return ("ANYCAST", unpack_cc(cc), "", 0.0, 0.0, asn, operator)
        if asn and self._member("STEER", 4, asn):
            return ("STEERED", unpack_cc(cc), "", 0.0, 0.0, asn, operator)
        if cc == 0 and (loc is None or loc == NONE32):
            return ("NONE", "", "", 0.0, 0.0, asn, operator)
        if loc is not None and loc != NONE32:
            off, _ = self.sec["LOC"]
            lcc, la, lo, noff = struct.unpack(">HhhI", self.blob[off + loc * 10: off + loc * 10 + 10])
            if cc == 0 or lcc == cc:
                return ("CITY", unpack_cc(lcc), self._name(noff), la / 100.0, lo / 100.0, asn, operator)
            return ("UNCERTAIN", unpack_cc(cc), "", 0.0, 0.0, asn, operator)
        a = self._anchor(cc)
        if a is None:
            return ("NONE", unpack_cc(cc), "", 0.0, 0.0, asn, operator)
        return ("COUNTRY", unpack_cc(cc), "", a[0], a[1], asn, operator)

    def _org(self, asn):
        i = self._floor("ORG", 8, 4, asn)
        if i < 0:
            return ""
        off, _ = self.sec["ORG"]
        a, noff = struct.unpack(">II", self.blob[off + i * 8: off + i * 8 + 8])
        return self._name(noff) if a == asn else ""

    def _anchor(self, cc):
        off, length = self.sec["ANCHOR"]
        keys = [struct.unpack(">H", self.blob[off + i * 10: off + i * 10 + 2])[0] for i in range(length // 10)]
        i = bisect.bisect_left(keys, cc)
        if i < len(keys) and keys[i] == cc:
            _, la, lo, _ = struct.unpack(">HhhI", self.blob[off + i * 10: off + i * 10 + 10])
            return (la / 100.0, lo / 100.0)
        return None


def main(argv):
    keys = ["country4", "country6", "asn4", "asn6", "city4", "anycast4", "anycast6", "anchors", "out"]
    args, i = {}, 0
    while i < len(argv):
        if argv[i].startswith("--") and argv[i][2:] in keys + ["sources"] and i + 1 < len(argv):
            args[argv[i][2:]] = argv[i + 1]
            i += 2
        else:
            raise SystemExit(__doc__)
    if any(k not in args for k in keys):
        raise SystemExit(__doc__)
    blob = build(args)
    with open(args["out"], "wb") as f:
        f.write(blob)
    print("%s: %d bytes" % (args["out"], len(blob)))


if __name__ == "__main__":
    main(sys.argv[1:])
