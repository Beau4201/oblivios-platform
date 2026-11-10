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


def refused(fn):
    """Whether fn ends the run the way the generator refuses an input (SystemExit). Asserted outside the try: a
    check(False) inside it would raise SystemExit too and be swallowed (review of 2026-10-23, C2-04)."""
    try:
        fn()
    except SystemExit:
        return True
    return False


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
# A company keeps its id for good (review of 2026-10-22: ids by sorted name moved when a company joined, so a stored choice
# "show my address to company N" would point at another company after an update).
out1, ids1 = mml.build_with_ids(CLASSIFIER, DENY, ALLOW, "")
check(out1 == out, "an empty registry gives the ids by sorted name")
more = CLASSIFIER + "owner\taaa\tAAA First\tAAA\tUS\tNYC\t0\t0\tWD-9\nservice\taaa-ads.example\taaa\tadvertising\tADG\n"
out2, ids2 = mml.build_with_ids(more, DENY, ALLOW, ids1)
after = rows(out2)
check(all(after[d] == r[d] for d in r), "a new company that sorts first moves nobody")
check(after["aaa-ads.example"][0] == max(i for i, _ in r.values()) + 1, "a new company gets the next free id")
check(mml.build_with_ids(more, DENY, ALLOW, ids2) == (out2, ids2), "a second run changes nothing")
gone = more.replace("service\tadnet.example\tadco\tadvertising\tADG\n", "").replace("service\tmetrics.adnet.example\tadco\tanalytics\tADG\n", "")
out3, ids3 = mml.build_with_ids(gone, DENY, ALLOW, ids2)
check("adnet.example" not in rows(out3) and rows(out3)["aaa-ads.example"] == after["aaa-ads.example"], "a company can leave")
check(mml.read_ids(ids3)["o:adco"] == mml.read_ids(ids1)["o:adco"], "a company that left keeps its id reserved")
newer = gone + "owner\tzzz\tZZZ Last\tZZZ\tUS\tNYC\t0\t0\tWD-10\nservice\tzzz-ads.example\tzzz\tadvertising\tADG\n"
check(rows(mml.build_with_ids(newer, DENY, ALLOW, ids3)[0])["zzz-ads.example"][0] not in set(mml.read_ids(ids3).values()), "an id is never reused")
for bad_ids in ["o:a\t0\n", "o:a\t4294967296\n", "o:a\tx\n", "o:a\t1\no:b\t1\n", "o:a\t1\no:a\t2\n", "o:a\n"]:
    check(refused(lambda: mml.read_ids(bad_ids)), f"registry {bad_ids!r} should be refused")
check(mml.read_ids("# c\n\no:a\t1\n") == {"o:a": 1}, "comments and blank lines in the registry")
check(mml.read_ids("o:a\t65536\no:b\t4294967295\n") == {"o:a": 65536, "o:b": 4294967295}, "an id has 32 bits")
# The id space is 32 bits: a new company after the last id is an error, not a wrapped or oversized id.
check(refused(lambda: mml.build_with_ids(more, DENY, ALLOW, "o:last-one\t4294967295\n")), "no id is left for a new company")

# Rule 4: the hosts of EasyPrivacy and EasyList.
EL = "easylist-hosts\t1\n# easyprivacy 1 sha256 x\n"
el_rows = ["tracker.example", "a.t.example", "t.example", "b.t.example", "ads.foo.co.uk", "m.metrics.adnet.example",
           "ev.accounts.google.com", "x.google.com", "doubleclick.net", "sub.auth.example", "auth.example", "new.news.example", "k.l.m.example"]
els = EL + "".join(f"{h}\tEP\n" for h in el_rows)
r4 = rows(mml.build(CLASSIFIER, DENY, ALLOW, els))
check("tracker.example" in r4 and r4["tracker.example"][0] > 0 and r4["tracker.example"][1] == "tracker.example", "an easylist host is masked")
check("a.t.example" not in r4 and "b.t.example" not in r4 and r4["t.example"][0] > 0, "a parent host makes its subhosts redundant")
check(r4["ads.foo.co.uk"][1] == "foo.co.uk" and r4["k.l.m.example"][1] == "m.example", "the company is the registrable domain")
check("m.metrics.adnet.example" not in r4, "a host under a masked row is not repeated")
check("ev.accounts.google.com" not in r4 and "x.google.com" not in r4 and "sub.auth.example" not in r4 and "new.news.example" not in r4,
      "a protected row shields the hosts below it: platform owner, identity, deny list, social")
check(r4["doubleclick.net"][0] > 0 and r4["auth.example"][0] == 0, "an existing row is not changed by the list")
base = rows(mml.build(CLASSIFIER, DENY, ALLOW))  # ids differ (new companies sort in), so compare the state and the name
check(all((r4[k][0] == 0, r4[k][1]) == (v[0] == 0, v[1]) for k, v in base.items()), "the classifier rows are unchanged by rule 4")
for bad in ["", "tracker.example\tEP\n", EL + "Tracker.Example\tEP\n", EL + "a.example\tXX\n", EL + "a.example\tEP\na.example\tEL\n", EL + "bad_host.example\tEP\n"]:
    check(refused(lambda: mml.read_easylist(bad)), f"easylist file {bad!r} should be refused")
# More than 65535 companies work, with stable ids, and the same input gives the same output.
big = EL + "".join(f"h.c{i}.net\tEP\n" for i in range(70000))
ob, ib = mml.build_with_ids(CLASSIFIER, DENY, ALLOW, "", big)
check(max(mml.read_ids(ib).values()) > 65535 and len(mml.read_ids(ib)) == len(set(mml.read_ids(ib).values())), "ids above 65535 are unique")
check(mml.build_with_ids(CLASSIFIER, DENY, ALLOW, ib, big) == (ob, ib), "ids are stable on a second run")
check(len(ob.splitlines()) - 1 <= mml.MAX_ENTRIES, "70000 hosts fit the row limit")
# A domain that the classifier lists twice is an error, not a quiet choice of one of the two.
check(refused(lambda: mml.build(CLASSIFIER + "service\tgoogle.com\tgoogle\tcore\tADG\n", DENY, ALLOW)), "a duplicate domain should be refused")
# A deny entry for a domain that is not in the classifier still protects it.
r2 = rows(mml.build(CLASSIFIER, DENY + "not-listed.example\twhy\n", ALLOW))
check(r2["not-listed.example"][0] == 0, "deny of an unlisted domain")
# List files: a reason is required, bad domains are refused, comments and blank lines are fine.
for bad in ["x.example\n", "x.example\t\n", "Bad Domain\treason\n", "prefix:a.\tr\n" if False else "-x.example\tr\n"]:
    check(refused(lambda: mml.read_list(bad)), f"list line {bad!r} should be refused")
check(mml.read_list("\n# c\n\nx.example\tr\n") == ({"x.example"}, []), "comments")

# The daemon's limits.
check(all(len(l.split("\t")[2].encode()) <= 64 for l in out.splitlines()[1:]), "names fit the daemon")

# The real files, when the classifier is next to this repository.
real = os.path.join(HERE, "..", "..", "..", "datamap", "classifier", "classifier.tsv")
if os.path.exists(real):
    text = open(real, encoding="utf-8").read()
    deny = open(os.path.join(HERE, "..", "mask-denylist.txt"), encoding="utf-8").read()
    allow = open(os.path.join(HERE, "..", "mask-allow-platform.txt"), encoding="utf-8").read()
    ids_file = os.path.join(HERE, "..", "mask-company-ids.tsv")
    out, ids_out = mml.build_with_ids(text, deny, allow, open(ids_file, encoding="utf-8").read())
    check(ids_out == open(ids_file, encoding="utf-8").read(), "the committed company ids are complete for the real classifier")
    shipped = os.path.join(HERE, "..", "daemon", "exit-mask-list.tsv")
    check(out == open(shipped, encoding="utf-8").read(), "the shipped mask list is what the generator makes from the real files")
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
