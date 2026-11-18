#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compares ExitMaskList (C++, the resolver) with `MaskList` of the exit daemon (Rust) on the real list.

    EXIT_BUILD_SSH=user@host differential.py [--list FILE] [--masklist masklist.rs] [--cpp-bin BIN]

Three comparisons, each of which must be exact:
  1. `masksName` against `classify(name).is_some()` for every name of a corpus built from the rows of the list (the
     domain, below it, above it, a longer label, other case, a trailing dot) plus edge cases and random names;
  2. `masksQuery` of the wire form of each name that can be written as a query against the same answer;
  3. the list files: every file that the daemon accepts, the resolver accepts, and both then classify alike (the
     resolver may accept more, never less: a file the daemon takes must not leave the cache unprotected).
The Rust side is compiled with the rustc of the AOSP tree on the build server (the workstation has none), with the
linker wrapper that the exit daemon's host tests use. Reads nothing but the list and the two sources, writes only to a
scratch directory of the server.
"""
import argparse
import os
import random
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
PLATFORM = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
SERVER_DIR = os.environ["DNSRESOLVER_SCRATCH"]
RUSTC = os.path.join(os.environ["AOSP_TREE"], "prebuilts/rust-toolchain/linux-x86/1.93.1/bin/rustc")
LINKER = os.path.join(os.environ["EXIT_WORK"], "toolchain/host-clang")

HEADER = "exit-mask-list\t1\n"


def read_rows(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        assert f.readline() == HEADER
        for line in f:
            domain, company, _ = line.rstrip("\n").split("\t")
            rows.append((domain, int(company)))
    return rows


def corpus(rows, rng):
    names = []
    for domain, _ in rows:
        parent = domain.split(".", 1)[1] if "." in domain else ""
        names += [domain, "www." + domain, "a.b." + domain, domain.upper(), domain.capitalize() + ".", domain + ".",
                  "x" + domain, "-" + domain, "_." + domain, domain + ".example.org", "sub." + domain + ".",
                  "." + domain, domain + ".."]
        if parent:
            names += [parent, "www." + parent, parent.upper() + "."]
    edge = ["", ".", "..", "a", "a.", "com", "net", "a..b", ".a", "a.b.", "-", "a-", "-a", "a_b.com", "1.2.3.4",
            "0", "xn--p1ai", "é.com", "ads.é.com", "a\x00b.com", "com\x00", "A" * 63 + ".com", "a" * 64 + ".com",
            "a" * 250 + ".com", ".".join(["ab"] * 90), ".".join(["ab"] * 84) + ".com", "www.google-analytics.com",
            "stats.g.doubleclick.net", "app-measurement.com", "www.googletagmanager.com", "www.wikipedia.org",
            "www.googleapis.com", "1e100.net", "x.1e100.net", "google.com", "adservice.google.com",
            "x.adservice.google.com", "play.googleapis.com", " ads.example.com", "ads.example.com ", "a b.com",
            "a\tb.com", "a\rb.com", "www.example.com\r"]
    names += edge
    alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
    domains = [d for d, _ in rows]
    for _ in range(20000):
        labels = ["".join(rng.choice(alphabet) for _ in range(rng.randint(1, 8))) for _ in range(rng.randint(0, 4))]
        if rng.random() < 0.6:
            labels.append(rng.choice(domains))
        if rng.random() < 0.1:
            labels.insert(rng.randint(0, len(labels)), "")
        name = ".".join(labels)
        if rng.random() < 0.2:
            name = name.upper()
        if rng.random() < 0.1:
            name += "."
        names.append(name)
    return names


def run(cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.returncode != 0:
        sys.exit(f"{' '.join(cmd)[:200]}\n{r.stderr[-2000:]}")
    return r.stdout


def fixtures(rows):
    big = HEADER + "".join(f"d{i}.com\t1\tx\n" for i in range(262144))
    body = "".join(f"{d}\t{c}\t{d}\n" for d, c in rows)
    sample = HEADER + "ads.example.com\t7\tA\nexample.net\t8\tB\ncdn.example.net\t0\tprotected\n"
    return {
        "real": HEADER + body,
        "sample": sample,
        "header only": HEADER,
        "header without newline": HEADER.rstrip("\n"),
        "blank lines": HEADER + "\n" + "a.com\t1\tA\n\n\n",
        "no final newline": HEADER + "a.com\t1\tA",
        "two fields": HEADER + "a.com\t1\n",
        "four fields": HEADER + "a.com\t1\tA\tB\n",
        "empty name": HEADER + "a.com\t1\t\n",
        "control in name": HEADER + "a.com\t1\tA\x01\n",
        "c1 in name": HEADER + "a.com\t1\tA\u0085B\n",
        "long name": HEADER + "a.com\t1\t" + "n" * 65 + "\n",
        "name 64": HEADER + "a.com\t1\t" + "n" * 64 + "\n",
        "upper domain": HEADER + "A.com\t1\tA\n",
        "trailing dot": HEADER + "a.com.\t1\tA\n",
        "hyphen": HEADER + "-a.com\t1\tA\n",
        "empty label": HEADER + "a..com\t1\tA\n",
        "dup": HEADER + "a.com\t1\tA\na.com\t2\tB\n",
        "id 0": HEADER + "a.com\t0\tprotected\nb.a.com\t3\tB\n",
        "id 65535": HEADER + "a.com\t65535\tA\n",
        "id 65536": HEADER + "a.com\t65536\tA\n",
        "id 4294967295": HEADER + "a.com\t4294967295\tA\n",
        "id 4294967296": HEADER + "a.com\t4294967296\tA\n",
        "id 42949672960": HEADER + "a.com\t42949672960\tA\n",
        "id leading zeros": HEADER + "a.com\t0000000000000000005\tA\n",
        "id signed": HEADER + "a.com\t+1\tA\n",
        "id spaced": HEADER + "a.com\t 1\tA\n",
        "262144 rows": big,
        "262145 rows": big + "over.com\t1\tx\n",
        "wrong version": "exit-mask-list\t2\n" + body,
        "no header": body,
        "crlf": HEADER.replace("\n", "\r\n") + "a.com\t1\tA\r\n",
        "empty": "",
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", default=os.path.join(PLATFORM, "privacy/exit/daemon/exit-mask-list.tsv"))
    ap.add_argument("--masklist", default=os.path.join(PLATFORM, "../oblivios-services/exit-daemon/src/masklist.rs"))
    ap.add_argument("--cpp-bin", required=True, help="the compiled ExitMaskList_test")
    args = ap.parse_args()
    ssh = os.environ.get("EXIT_BUILD_SSH") or sys.exit("EXIT_BUILD_SSH=user@host is needed (read it from the local server memory)")

    rows = read_rows(args.list)
    rng = random.Random(20261102)
    names = corpus(rows, rng)
    fx = fixtures(rows)

    with tempfile.TemporaryDirectory() as tmp:
        names_file = os.path.join(tmp, "names.txt")
        with open(names_file, "w", encoding="utf-8", newline="") as f:
            f.write("\n".join(names) + "\n")
        files = {}
        for label, text in fx.items():
            p = os.path.join(tmp, f"fx{len(files)}.tsv")
            with open(p, "w", encoding="utf-8", newline="") as f:
                f.write(text)
            files[label] = p

        # the Rust side, on the server
        run(["ssh", "-n", ssh, f"mkdir -p {SERVER_DIR}"])
        run(["scp", "-q", args.masklist, os.path.join(HERE, "dump.rs"), names_file, args.list, *files.values(), f"{ssh}:{SERVER_DIR}/"])
        run(["ssh", "-n", ssh, f"cd {SERVER_DIR} && {RUSTC} --edition 2021 -O -C linker={LINKER} dump.rs -o dump 2>&1 | tail -3"])
        base = os.path.basename
        rust_names = run(["ssh", "-n", ssh, f"cd {SERVER_DIR} && ./dump dump {base(args.list)} names.txt"]).split("\n")[:-1]
        rust_accept = {}
        for label, p in files.items():
            rust_accept[label] = run(["ssh", "-n", ssh, f"cd {SERVER_DIR} && ./dump accept {base(p)}"]).strip()

        # the C++ side
        cpp_names = run([args.cpp_bin, "dump", args.list, names_file]).split("\n")[:-1]
        cpp_query = run([args.cpp_bin, "dumpq", args.list, names_file]).split("\n")[:-1]
        cpp_accept = {label: run([args.cpp_bin, "accept", p]).strip() for label, p in files.items()}

        bad = 0
        assert len(rust_names) == len(cpp_names) == len(cpp_query) == len(names), (len(rust_names), len(cpp_names), len(cpp_query), len(names))
        for n, r, c, q in zip(names, rust_names, cpp_names, cpp_query):
            if r != c:
                bad += 1
                print(f"MISMATCH name {n!r}: daemon {r}, resolver {c}")
            if q != "x" and q != r:
                bad += 1
                print(f"MISMATCH query {n!r}: daemon {r}, resolver wire {q}")
        masked = sum(1 for r in rust_names if r == "1")
        wire = sum(1 for q in cpp_query if q != "x")
        print(f"names: {len(names)} compared, {masked} masked, {wire} also as wire queries, {bad} mismatches")

        weaker = 0
        for label in fx:
            r, c = rust_accept[label], cpp_accept[label]
            note = ""
            if r == "ok" and c != "ok":
                weaker += 1
                note = "  <-- the resolver refuses a file the daemon takes"
            print(f"  list {label:24s} daemon {r:3s} resolver {c:3s}{note}")
        # a file both accept must classify alike (the real list was compared above; the others by name)
        for label, p in files.items():
            if rust_accept[label] == "ok" and cpp_accept[label] == "ok" and label != "real":
                probe = os.path.join(tmp, "probe.txt")
                with open(probe, "w", encoding="utf-8", newline="") as f:
                    f.write("\n".join(names[:3000]) + "\n")
                run(["scp", "-q", probe, f"{ssh}:{SERVER_DIR}/probe.txt"])
                rr = run(["ssh", "-n", ssh, f"cd {SERVER_DIR} && ./dump dump {base(p)} probe.txt"]).split("\n")[:-1]
                cc = run([args.cpp_bin, "dump", p, probe]).split("\n")[:-1]
                if rr != cc:
                    bad += 1
                    print(f"MISMATCH on the names of list {label!r}")
        if bad or weaker:
            sys.exit(f"differential: FAILED ({bad} mismatches, {weaker} files refused that the daemon accepts)")
        print("differential: ok")


if __name__ == "__main__":
    main()
