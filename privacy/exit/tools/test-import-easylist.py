#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Checks of import-easylist.py: which rules become a host, which are left out, exceptions, determinism."""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("iel", os.path.join(HERE, "import-easylist.py"))
iel = importlib.util.module_from_spec(spec)
spec.loader.exec_module(iel)

failures = []


def check(ok, what):
    if not ok:
        failures.append(what)


def hosts(text):
    return iel.parse(text)[0]


# Whole-host rules are taken, in any case, with the safe options.
check(hosts("||tracker.example^\n") == {"tracker.example"}, "plain rule")
check(hosts("||Tracker.Example^\n") == {"tracker.example"}, "host is lower-cased")
check(hosts("||t.example^$third-party\n||u.example^$script,image,third-party\n||v.example^$ping,xhr,important\n") == {"t.example", "u.example", "v.example"}, "safe options")
check(hosts("||sub.t.example^\n||t.example^\n") == {"sub.t.example", "t.example"}, "a host and a subhost are both kept")

# What is not one whole unconditional host is left out, and counted.
for line, why in [("||t.example/path/x.js", "shape"), ("||t.example^path", "shape"), ("||t.example", "shape"), ("||t.*.example^", "shape"),
                  ("|https://t.example^", "shape"), ("||t.example^$domain=a.com", "option"), ("||t.example^$~third-party", "option"),
                  ("||t.example^$csp=script-src", "option"), ("||t.example^$redirect=noopjs", "option"), ("||t.example^$script,removeparam=x", "option"),
                  ("||1.2.3.4^", "not a host"), ("||localhost^", "not a host"), ("||-bad.example^", "not a host"), ("||" + "a" * 64 + ".example^", "not a host"),
                  ("||" + ".".join(["abcdefghij"] * 26) + "^", "not a host")]:
    b, a, s = iel.parse(line + "\n")
    check(not b and not a and s == {why: 1}, f"left out ({why}): {line}")
check(iel.parse("! comment\n[Adblock Plus 1.1]\n\nexample.com##.ad\nexample.com#@#.ad\n#$#abort-on-property-read x\n") == (set(), set(), {"shape": 3}), "comments are ignored, element hiding is counted")

# An exception removes the host and what is below it, whatever its options, but not a parent or a neighbour.
check(iel.parse("@@||ok.example^$domain=a.com\n")[1] == {"ok.example"}, "exception with domain= counts")
text = iel.build("||ok.example^\n||x.ok.example^\n||ok.example.net^\n||example^x\n||keep.example^\n||ok.example^\n", "@@||ok.example^$script\n||ok.example^\n")
rows = [l.split("\t")[0] for l in text.splitlines() if l and l[0] not in "#e"]
check(rows == ["keep.example", "ok.example.net"], f"exception removes host and subhosts only: {rows}")
check(iel.build("@@||example.net^\n", "||a.example.net^\n||example.net.evil^\n").count("\n") == 4, "exception of a parent removes the subhost, not a lookalike")

# Tags and order.
out = iel.build("||b.example^\n||a.example^\n||both.example^\n", "||both.example^\n||c.example^\n").splitlines()
check(out[0] == iel.HEADER and out[1].startswith("# easyprivacy unknown sha256 ") and out[2].startswith("# easylist unknown sha256 "), "header and pins")
check(out[3:] == ["a.example\tEP", "b.example\tEP", "both.example\tEP+EL", "c.example\tEL"], f"rows sorted with tags: {out[3:]}")
ver = "! Version: 202611101405\n||a.example^\n"
check(iel.build(ver, "").splitlines()[1].startswith("# easyprivacy 202611101405 sha256 "), "the version comes from the input")
check(iel.build(ver, ver) == iel.build(ver, ver), "deterministic")
check(iel.build("||a.example^\n", "") != iel.build("||b.example^\n", ""), "the hash follows the input")

# The real inputs, when given (EASYLIST_DIR holds easyprivacy.txt and easylist.txt): the shapes counted in the review of 2026-11-10.
d = os.environ.get("EASYLIST_DIR")
if d:
    ep, el = (open(os.path.join(d, n), encoding="utf-8").read() for n in ("easyprivacy.txt", "easylist.txt"))
    b, a, s = iel.parse(ep)
    check(len(b) > 20000 and "google-analytics.com" in b, f"real EasyPrivacy: {len(b)} hosts")
    out = iel.build(ep, el)
    check(len(out.splitlines()) - 3 > 60000, "real lists give tens of thousands of hosts")
    check(all(iel.valid_host(l.split("\t")[0]) for l in out.splitlines()[3:]), "every output host is valid")

if failures:
    print("FAILED:\n  " + "\n  ".join(failures))
    sys.exit(1)
print("PASS import-easylist")
