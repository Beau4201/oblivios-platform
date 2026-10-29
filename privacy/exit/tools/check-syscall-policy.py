#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Checks what the exit daemon and the route helper need (exit-needs.tsv) against a compiled policy and exit.rc.

  check-syscall-policy.py POLICY [--rc exit.rc] [--needs exit-needs.tsv]

POLICY is a binary policy: /sys/fs/selinux/policy pulled from a device, a precompiled_sepolicy of a product output, or the
output of `secilc` on plat_sepolicy.cil plus the lines under test. It needs `sesearch` (setools). Exit status 0 when every
`required` row holds, 1 otherwise; an `optional` row that does not hold is listed and does not fail.
"""
import argparse
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
GIDS = {"inet": 3003, "vpn": 1016}


def sesearch(policy, *args):
    out = subprocess.run(["sesearch", *args, policy], capture_output=True, text=True)
    if out.returncode != 0:
        raise SystemExit(f"sesearch {' '.join(args)}: {out.stderr.strip()}")
    return out.stdout.splitlines()


def perms_granted(policy, src, tgt, cls):
    """The union of the permissions that every allow rule gives src on tgt:cls (attributes are expanded by sesearch)."""
    got = set()
    for line in sesearch(policy, "-A", "-s", src, "-t", tgt, "-c", cls):
        m = re.match(r"allow \S+ \S+:\S+ (?:\{ ([^}]*) \}|(\S+));", line)
        if m:
            got.update((m.group(1) or m.group(2)).split())
    return got


def numbers_of(token_list):
    nums = set()
    for tok in token_list:
        lo, _, hi = tok.partition("-")
        nums.update(range(int(lo, 16), int(hi or lo, 16) + 1))
    return nums


def xperms_granted(policy, src, tgt, cls, perm):
    got = set()
    for line in sesearch(policy, "--allowxperm", "-s", src, "-t", tgt, "-c", cls):
        m = re.match(r"allowxperm \S+ \S+:\S+ (\S+) (.*?);?\s*$", line)
        if m and m.group(1) == perm:
            got |= numbers_of(m.group(2).strip("{} ").split())
    return got


def rc_groups(path):
    """service name -> the groups of its `group` line (the first is the gid, the rest are supplementary)."""
    groups, cur = {}, None
    for line in open(path):
        line = line.split("#")[0].strip()
        if line.startswith("service "):
            cur = line.split()[1]
        elif cur and line.startswith("group "):
            groups[cur] = line.split()[1:]
    return groups


def check_row(policy, rc, row):
    kind, src, tgt, cls, what = row[:5]
    if kind == "gid":
        groups = rc.get(src, [])
        return [] if what == "-" and tgt in groups else ([f"service {src} lacks group {tgt} (has {groups})"])
    tgt = src if tgt == "self" else tgt
    if kind == "allow":
        want = set(what.split())
        missing = want - perms_granted(policy, src, tgt, cls)
        return [f"{src} -> {tgt}:{cls} lacks {' '.join(sorted(missing))}"] if missing else []
    if kind == "allowxperm":
        perm, *nums = what.split()
        missing = numbers_of(nums) - xperms_granted(policy, src, tgt, cls, perm)
        return [f"{src} -> {tgt}:{cls} {perm} lacks {' '.join(hex(n) for n in sorted(missing))}"] if missing else []
    if kind == "deny":
        extra = set(what.split()) & perms_granted(policy, src, tgt, cls)
        return [f"{src} -> {tgt}:{cls} is granted {' '.join(sorted(extra))}"] if extra else []
    raise SystemExit(f"unknown row kind {kind!r}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("policy")
    ap.add_argument("--rc", default=os.path.join(HERE, "..", "daemon", "exit.rc"))
    ap.add_argument("--needs", default=os.path.join(HERE, "exit-needs.tsv"))
    a = ap.parse_args()
    rc = rc_groups(a.rc)
    failed = tolerated = rows = 0
    for n, line in enumerate(open(a.needs), 1):
        if not line.strip() or line.startswith("#"):
            continue
        row = line.rstrip("\n").split("\t")
        if len(row) < 7:
            raise SystemExit(f"{a.needs}:{n}: seven columns expected, got {len(row)}")
        rows += 1
        problems = check_row(a.policy, rc, row)
        if not problems:
            continue
        if row[5] == "required":
            failed += 1
            print(f"FAIL  {a.needs}:{n}  {problems[0]}\n      evidence: {row[6]}")
        else:
            tolerated += 1
            print(f"note  {a.needs}:{n}  {problems[0]} (optional: {row[6]})")
    print(f"{rows} rows, {failed} required rows fail, {tolerated} optional rows denied")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
