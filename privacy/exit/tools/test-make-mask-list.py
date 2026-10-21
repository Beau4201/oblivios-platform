#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Checks of make-mask-list.py: the rules on a small classifier, the real files, and the format the daemon parses."""
import importlib.util
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("mml", os.path.join(HERE, "make-mask-list.py"))
mml = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mml)

CLASSIFIER = """datamap-classifier\t1
owner\tgoogle\tGoogle\tGoogle LLC\tUS\tMountain View\t0\t0\tWD-1
owner\tadco\tAd Company\tAd Company Inc\tUS\tNYC\t0\t0\tWD-2
service\tgoogle.com\tgoogle\tadvertising\tADG
service\tads.google.com\tgoogle\tadvertising\tADG
service\tdoubleclick.net\tgoogle\tadvertising\tADG
service\tfcm.googleapis.com\tgoogle\tanalytics\tADG
service\tgstatic.com\tgoogle\tcdn\tADG
service\taccounts.google.com\tgoogle\tidentity\tADG
service\tadnet.example\tadco\tadvertising\tADG
service\tmetrics.adnet.example\tadco\tanalytics\tADG
service\tcdn.adnet.example\t-\tcdn\tADG
service\tmystery-tracker.example\t-\tanalytics\tADG
service\tother-mystery.example\t-\tadvertising\tADG
service\tauth.example\t-\tadvertising\tADG
service\tnews.example\t-\tsocial\tADG
service\tUPPER.example\t-\tadvertising\tADG
service\tbad_name.example\t-\tadvertising\tADG
"""
DENY = "# a comment\nauth.example\tsign-in\n"
ALLOW = "doubleclick.net\tGoogle ads\nprefix:ads.google.\tGoogle ads per country\n"


def rows(text):
    lines = text.splitlines()
    assert lines[0] == mml.HEADER
    return {l.split("\t")[0]: (int(l.split("\t")[1]), l.split("\t")[2]) for l in lines[1:]}


def check(cond, what):
    if not cond:
        print("FAIL:", what)
        sys.exit(1)


out = mml.build(CLASSIFIER, DENY, ALLOW)
r = rows(out)
# Rule 1: an owner that runs nothing else is masked, with its own company id and name.
check(r["adnet.example"][0] > 0 and r["adnet.example"][1] == "Ad Company", "dedicated ad company is masked")
check(r["metrics.adnet.example"][0] == r["adnet.example"][0], "one company, one id")
# An owner with no known company is masked, and its domain is its name.
check(r["mystery-tracker.example"][0] > 0 and r["mystery-tracker.example"][1] == "mystery-tracker.example", "unknown owner")
check(r["mystery-tracker.example"][0] != r["other-mystery.example"][0], "two unknown owners are two companies")
# Rule 2: a platform owner is protected unless allowed (exact or by prefix).
check(r["google.com"][0] == 0, "google.com is protected")
check(r["fcm.googleapis.com"][0] == 0, "push is protected")
check(r["doubleclick.net"][0] > 0 and r["doubleclick.net"][1] == "Google", "allowed google host is masked")
check(r["ads.google.com"][0] > 0, "prefix allow")
# Rule 3: deny, and the categories core, cdn, social, identity, are protected.
check(r["auth.example"][0] == 0, "denylist")
check(r["gstatic.com"][0] == 0 and r["accounts.google.com"][0] == 0 and r["news.example"][0] == 0, "protected categories")
check(r["cdn.adnet.example"][0] == 0, "a cdn under a masked company stays protected")
# Not domains are dropped, not turned into rows.
check("UPPER.example" not in r and "bad_name.example" not in r, "invalid domains are dropped")
# Determinism and order.
check(out == mml.build(CLASSIFIER, DENY, ALLOW), "deterministic")
check(list(r) == sorted(r), "sorted")
# A company's id does not depend on the order of the input.
shuffled = "\n".join(sorted(CLASSIFIER.splitlines(), reverse=True)) + "\n"
check(rows(mml.build(shuffled, DENY, ALLOW)) == r, "independent of input order")
# A domain that the classifier lists twice is an error, not a quiet choice of one of the two.
try:
    mml.build(CLASSIFIER + "service\tgoogle.com\tgoogle\tcore\tADG\n", DENY, ALLOW)
    check(False, "a duplicate domain should be refused")
except SystemExit:
    pass
# A deny entry for a domain that is not in the classifier still protects it.
r2 = rows(mml.build(CLASSIFIER, DENY + "not-listed.example\twhy\n", ALLOW))
check(r2["not-listed.example"][0] == 0, "deny of an unlisted domain")
# List files: a reason is required, bad domains are refused, comments and blank lines are fine.
for bad in ["x.example\n", "x.example\t\n", "Bad Domain\treason\n", "prefix:a.\tr\n" if False else "-x.example\tr\n"]:
    try:
        mml.read_list(bad)
        check(False, f"list line {bad!r} should be refused")
    except SystemExit:
        pass
check(mml.read_list("\n# c\n\nx.example\tr\n") == ({"x.example"}, []), "comments")

# The daemon's limits.
check(all(len(l.split("\t")[2].encode()) <= 64 for l in out.splitlines()[1:]), "names fit the daemon")

# The real files, when the classifier is next to this repository.
real = os.path.join(HERE, "..", "..", "..", "datamap", "classifier", "classifier.tsv")
if os.path.exists(real):
    text = open(real, encoding="utf-8").read()
    deny = open(os.path.join(HERE, "..", "mask-denylist.txt"), encoding="utf-8").read()
    allow = open(os.path.join(HERE, "..", "mask-allow-platform.txt"), encoding="utf-8").read()
    out = mml.build(text, deny, allow)
    rr = rows(out)
    masked = {d for d, (i, _) in rr.items() if i > 0}
    check(0 < len(masked) <= 3367, f"masked count {len(masked)}")
    check(len(rr) <= mml.MAX_ENTRIES, "row count")
    # The domains that must never be masked, wherever they come from.
    for must_not in ["google.com", "android.com", "facebook.com", "bing.com", "fcm.googleapis.com", "firebaseremoteconfig.googleapis.com",
                     "firebaseinstallations.googleapis.com", "auth0.com", "datadome.co", "adobe.com", "scene7.com", "amazon.com",
                     "apple.com", "wordpress.com", "cybersource.com", "turner.com", "agnss.goog", "connectivitycheck.gstatic.com"]:
        check(must_not not in masked, f"{must_not} must not be masked")
    for must in ["doubleclick.net", "google-analytics.com", "googlesyndication.com", "adnxs.com", "clarity.ms", "demdex.net"]:
        check(must in masked, f"{must} must be masked")
    check(any(d.startswith("adservice.google.") for d in masked), "adservice hosts")
    protected = {d for d, (i, _) in rr.items() if i == 0}
    print(f"real list: {len(rr)} rows, {len(masked)} masked, {len(protected)} protected")
print("all mask list checks passed")
