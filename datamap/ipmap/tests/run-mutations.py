#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of the data tool: each rule of tools/build-ipmap.py is broken in a copy of the package and ipmap_check.py
must fail for each. A mutation that survives is a gap in the checks. The unmutated copy must pass first, and a mutation
that does not run (a syntax error) is reported as broken, not as killed.

usage: run-mutations.py
"""
import pathlib, shutil, subprocess, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent.parent

# (name, old, new, count) -- `old` must occur exactly `count` times in tools/build-ipmap.py
MUTATIONS = [
    ("tool: ranges that meet in a /64 are not trimmed", "if e <= out[-1][1]:\n                continue", "if False:\n                continue", 1),
    ("tool: a later range keeps the shared /64", "s = out[-1][1] + 1", "s = out[-1][1]", 1),
    ("tool: a range that ends before it starts is accepted", "if e < s:", "if False:", 1),
    ("tool: overlapping ranges are accepted", "if out[i][0] <= out[i - 1][1]:", "if False:", 1),
    ("tool: a range of the wrong IP version is accepted", "if a.version != version:", "if False:", 1),
    ("tool: an anycast prefix of the wrong length is accepted", "net.prefixlen != bits", "False", 1),
    ("tool: the anycast prefix is cut at the wrong place", "((32 if version == 4 else 128) - bits)", "((32 if version == 4 else 128) - bits + 1)", 1),
    ("tool: the anchors are not sorted", "    anchors.sort()\n", "", 1),
    ("tool: a name is not cut at 120 bytes", "MAX_NAME = 120", "MAX_NAME = 1200", 1),
    ("tool: a name is cut inside a character", "            b = b[:-1]", "            return b[:-1] + b", 1),
    ("tool: the last name of an AS number wins", "org.setdefault(int(r[0]), r[1].strip())", "org[int(r[0])] = r[1].strip()", 1),
    ("tool: a name is matched as a substring", "words = re.findall(r\"[a-z0-9]+\", name.lower())", "words = [name.lower()]", 1),
    ("tool: a phrase matches by its first word", "words[i:i + len(p)] == list(p)", "words[i:i + 1] == list(p[:1])", 1),
    ("tool: a name is matched in its own case", "name.lower()", "name", 1),
    ("tool: google does not steer", "(\"google\",), ", "", 1),
    ("tool: cloudflare does not steer", "(\"cloudflare\",), ", "", 1),
    ("tool: microsoft does not steer", "(\"microsoft\",), ", "", 1),
    ("tool: a letter outside ASCII is a country code", "or not cc.isascii()", "", 1),
    ("tool: a country code is not upper-cased", "cc = cc.strip().upper()", "cc = cc.strip()", 1),
    ("tool: the sources text is lost", "args.get(\"sources\", \"\")", "\"\"", 1),
    ("reader: an IPv4-mapped address stays IPv6", "ip.ipv4_mapped is not None", "False", 1),
    ("reader: the NAT64 prefix is not unwrapped", "int(ip) >> 32 == NAT64", "False", 1),
    ("reader: the NAT64 prefix is taken from the wrong bits", "NAT64 = int(ipaddress.ip_address(\"64:ff9b::\")) >> 32", "NAT64 = int(ipaddress.ip_address(\"64:ff9b::\")) >> 33", 1),
    ("reader: an anycast address is a place", "        if anycast:\n            return", "        if False:\n            return", 1),
    ("reader: a steered network is a place", "if asn and self._member(\"STEER\", 4, asn):", "if False:", 1),
    ("reader: a city is taken against the country", "if cc == 0 or lcc == cc:", "if True:", 1),
    ("reader: a country has no anchor", "a = self._anchor(cc)\n        if a is None:", "a = None\n        if a is None:", 1),
    ("reader: an anchor is found by the wrong side", "bisect.bisect_left(keys, cc)", "bisect.bisect_right(keys, cc)", 1),
]


def main():
    base = tempfile.mkdtemp()
    try:
        shutil.copytree(HERE, pathlib.Path(base) / "ipmap", ignore=shutil.ignore_patterns("__pycache__"))
        r = subprocess.run([sys.executable, "-I", str(pathlib.Path(base) / "ipmap/tests/ipmap_check.py")], capture_output=True, text=True)
        if r.returncode != 0:
            print("the unmutated copy does not pass:\n" + r.stdout + r.stderr)
            sys.exit(2)
    finally:
        shutil.rmtree(base, ignore_errors=True)
    survivors = []
    for name, old, new, count in MUTATIONS:
        tmp = tempfile.mkdtemp()
        try:
            shutil.copytree(HERE, pathlib.Path(tmp) / "ipmap", ignore=shutil.ignore_patterns("__pycache__"))
            f = pathlib.Path(tmp) / "ipmap/tools/build-ipmap.py"
            text = f.read_text(encoding="utf-8")
            if text.count(old) != count:
                print(f"BROKEN MUTATION {name}: found {text.count(old)} of the old text, expected {count}")
                sys.exit(2)
            f.write_text(text.replace(old, new), encoding="utf-8")
            r = subprocess.run([sys.executable, "-I", str(pathlib.Path(tmp) / "ipmap/tests/ipmap_check.py")], capture_output=True, text=True, timeout=120)
            if "SyntaxError" in r.stderr or "IndentationError" in r.stderr:
                print(f"BROKEN MUTATION {name}: it does not parse\n{r.stderr}")
                sys.exit(2)
            killed = r.returncode != 0
            print(("KILLED   " if killed else "SURVIVED ") + name)
            if not killed:
                survivors.append(name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print(f"{len(MUTATIONS) - len(survivors)} of {len(MUTATIONS)} mutations killed")
    sys.exit(1 if survivors else 0)


if __name__ == "__main__":
    main()
