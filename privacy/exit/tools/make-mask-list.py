#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Makes the mask list of the exit daemon (ADR-181, ADR-186) from the Data Map classifier.

    make-mask-list.py CLASSIFIER.tsv DENYLIST ALLOWLIST IDS OUT.tsv [EASYLIST_HOSTS]

IDS is mask-company-ids.tsv: it is read, and written again with the ids of any new company appended (see read_ids).
EASYLIST_HOSTS is the output of import-easylist.py (rule 4); without it the list is the classifier's alone.

The classifier says who runs a domain and which category it has. It is a classification of domains, not a statement that
the whole domain is only a tracker: google.com, facebook.com and android.com are all `advertising` in it. So the list is
made with three rules, and every row of the output says which one applies:

1. A domain of the categories advertising, analytics or attribution is *masked* (its company id is above 0) when its owner
   runs nothing else of significance, that is, no service of the categories core, cdn, social or identity.
2. An owner that does (a "platform owner": Google, Meta, Microsoft, Amazon, ...) has its masked-category domains protected
   unless the ALLOWLIST names them. The allowlist holds dedicated advertising and measurement hosts only, one `domain` or
   `prefix:` line each, with a reason.
3. Every domain of the DENYLIST, and every domain of the categories core, cdn, social and identity, is *protected* (company
   id 0): no name under it is masked, even when a less specific row would mask it.
4. A host of EasyPrivacy or EasyList (EASYLIST_HOSTS: whole hosts only, see import-easylist.py) is masked, its company the
   host itself, unless a row of rules 1 to 3 is at or above it: a protected row shields it, a masked row already masks it.

Output format: see exit-daemon/src/masklist.rs. The output is deterministic (sorted; a company keeps the id that IDS holds, a new company gets the next free id in the order of its name).
"""
import os
import re
import sys

MASK_CATS = {"advertising", "analytics", "attribution"}
PROTECT_CATS = {"core", "cdn", "social", "identity"}
HEADER = "exit-mask-list\t1"
EASYLIST_HEADER = "easylist-hosts\t1"
# The limits of the daemon (masklist.rs) and of the resolver (ExitMaskList.cpp): rows, and a company id of 32 bits.
MAX_ENTRIES = 262144
MAX_ID = 4294967295
MAX_NAME = 64

_LABEL = re.compile(r"^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")


def valid_domain(d):
    return 0 < len(d) <= 253 and all(_LABEL.match(label) for label in d.split("."))


def read_classifier(text):
    """-> (services {domain: (owner, category)}, owners {id: display name})"""
    services, owners = {}, {}
    for line in text.splitlines():
        c = line.split("\t")
        if c[0] == "service" and len(c) >= 4:
            if c[1].lower() in services:
                raise SystemExit(f"the classifier lists {c[1]} twice")
            services[c[1].lower()] = (c[2], c[3])
        elif c[0] == "owner" and len(c) >= 3:
            owners[c[1]] = c[2]
    return services, owners


def read_list(text):
    """Lines `domain<TAB>reason`; `#` starts a comment; a reason is required. -> (exact set, prefix list)"""
    exact, prefixes = set(), []
    for n, line in enumerate(text.splitlines(), 1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 2 or not parts[1].strip():
            raise SystemExit(f"line {n}: a domain and a reason, separated by a tab, are required")
        key = parts[0].strip().lower()
        if key.startswith("prefix:"):
            prefixes.append(key[len("prefix:"):])
        else:
            if not valid_domain(key):
                raise SystemExit(f"line {n}: not a domain: {key!r}")
            exact.add(key)
    return exact, prefixes


def read_easylist(text):
    """The output of import-easylist.py: the header, `#` lines, then `host<TAB>tag`. -> the hosts, sorted."""
    lines = text.splitlines()
    if not lines or lines[0] != EASYLIST_HEADER:
        raise SystemExit("the easylist hosts file has no header")
    hosts, seen = [], set()
    for n, line in enumerate(lines[1:], 2):
        if not line or line.startswith("#"):
            continue
        host, _, tag = line.partition("\t")
        if not valid_domain(host) or host != host.lower() or tag not in ("EP", "EL", "EP+EL") or host in seen:
            raise SystemExit(f"easylist hosts, line {n}: a lower-case host name once, and a tag EP, EL or EP+EL are required")
        seen.add(host)
        hosts.append(host)
    return hosts


def read_ids(text):
    """Lines `company key<TAB>id`; `#` starts a comment. -> {key: id}. The ids are kept in a committed file so that a company keeps its
    id across every regeneration (the exit app stores "show my address to company N"; an id that moved would point at another
    company after an update)."""
    ids, seen = {}, set()
    for n, line in enumerate(text.splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        key, _, num = line.partition("\t")
        if not key or not num.isdigit() or not 1 <= int(num) <= MAX_ID or key in ids or int(num) in seen:
            raise SystemExit(f"company ids, line {n}: a key and a unique id of 1 to {MAX_ID} are required")
        ids[key] = int(num)
        seen.add(int(num))
    return ids


def write_ids(ids):
    head = ("# Stable company ids of the mask list (make-mask-list.py). A company keeps its id for good: a new company gets the next\n"
            "# free id, a company that leaves the list keeps its id reserved, and nothing is renumbered. Do not edit by hand.\n")
    return head + "".join(f"{k}\t{i}\n" for k, i in sorted(ids.items(), key=lambda kv: kv[1]))


def build(classifier_text, deny_text, allow_text, easylist_text=""):
    return build_with_ids(classifier_text, deny_text, allow_text, "", easylist_text)[0]


def build_with_ids(classifier_text, deny_text, allow_text, ids_text, easylist_text=""):
    """-> (the mask list, the company ids file to commit)"""
    services, owners = read_classifier(classifier_text)
    deny, deny_prefixes = read_list(deny_text)
    allow, allow_prefixes = read_list(allow_text)
    if deny_prefixes:
        raise SystemExit("the denylist takes exact domains only")
    platform = {o for d, (o, c) in services.items() if o != "-" and c in PROTECT_CATS}

    def allowed(d):
        return d in allow or any(d.startswith(p) for p in allow_prefixes)

    rows = {}  # domain -> company key, or None for protected
    for d, (owner, cat) in services.items():
        if not valid_domain(d):
            continue
        if cat in PROTECT_CATS:
            rows[d] = None
        elif cat in MASK_CATS:
            if d in deny or (owner in platform and not allowed(d)):
                rows[d] = None
            else:
                rows[d] = ("o:" + owner) if owner != "-" else ("d:" + d)
    for d in deny:
        rows[d] = None
    # Rule 4: a host of EasyPrivacy or EasyList is masked unless a row at or above it exists. A protected row above it shields it
    # (no host of google.com, a platform owner or a protected domain is masked because a list names it, the allow list decides
    # those); a masked row above it already masks it. The company is the host itself: the user's choice ("show my address to
    # this company") then never covers more than the host that the notification named, and no two tenants of a shared suffix
    # (pages.dev, b-cdn.net, github.io, blob.core.windows.net) are one company.
    if easylist_text:
        for h in sorted(read_easylist(easylist_text), key=lambda x: (x.count("."), x)):  # a parent first: its subhosts are then redundant
            labels = h.split(".")
            if not any(".".join(labels[i:]) in rows for i in range(len(labels) - 1)):
                rows[h] = "d:" + h

    def name_of(key):
        if key.startswith("o:"):
            n = owners.get(key[2:], key[2:])
        else:
            n = key[2:]
        n = "".join(ch for ch in n if ch.isprintable() and ch != "\t").strip() or key[2:]
        if key.startswith("d:") and len(n.encode()) > MAX_NAME:
            # a host names its owner at the right: keep that end and cut at the left, so that "login.bank.example.xxxxx..."
            # cannot show as "login.bank.example" (a host is ASCII, so the cut is on a character)
            return "…" + n[-(MAX_NAME - 3):]
        while len(n.encode()) > MAX_NAME:
            n = n[:-1]
        return n

    companies = sorted({k for k in rows.values() if k is not None}, key=lambda k: (name_of(k).lower(), k))
    known = read_ids(ids_text)
    ids = dict(known)
    last = max(ids.values(), default=0)
    for k in companies:  # a new company, in the order of its name, gets the next free id
        if k not in ids:
            if last >= MAX_ID:
                raise SystemExit(f"more than {MAX_ID} companies")
            last += 1
            ids[k] = last
    if len(rows) > MAX_ENTRIES:
        raise SystemExit(f"{len(rows)} rows are more than the {MAX_ENTRIES} the daemon accepts")
    out = [HEADER]
    for d in sorted(rows):
        key = rows[d]
        out.append(f"{d}\t{0 if key is None else ids[key]}\t{'protected' if key is None else name_of(key)}")
    return "\n".join(out) + "\n", write_ids(ids)


def main(argv):
    if len(argv) not in (6, 7):
        raise SystemExit(__doc__)
    cls, deny, allow = (open(p, encoding="utf-8").read() for p in argv[1:4])
    ids_path, out = argv[4], argv[5]
    easylist = open(argv[6], encoding="utf-8").read() if len(argv) == 7 else ""
    ids_text = open(ids_path, encoding="utf-8").read() if os.path.exists(ids_path) else ""
    text, new_ids = build_with_ids(cls, deny, allow, ids_text, easylist)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    with open(ids_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(new_ids)
    masked = sum(1 for line in text.splitlines()[1:] if line.split("\t")[1] != "0")
    print(f"{len(text.splitlines()) - 1} rows, {masked} masked", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv)
