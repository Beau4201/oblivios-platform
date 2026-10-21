#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Makes the mask list of the exit daemon (ADR-181, ADR-186) from the Data Map classifier.

    make-mask-list.py CLASSIFIER.tsv DENYLIST ALLOWLIST OUT.tsv

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

Output format: see exit-daemon/src/masklist.rs. The output is deterministic (sorted, company ids by sorted name).
"""
import re
import sys

MASK_CATS = {"advertising", "analytics", "attribution"}
PROTECT_CATS = {"core", "cdn", "social", "identity"}
HEADER = "exit-mask-list\t1"
MAX_ENTRIES = 8192
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


def build(classifier_text, deny_text, allow_text):
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

    def name_of(key):
        if key.startswith("o:"):
            n = owners.get(key[2:], key[2:])
        else:
            n = key[2:]
        n = "".join(ch for ch in n if ch.isprintable() and ch != "\t").strip() or key[2:]
        while len(n.encode()) > MAX_NAME:
            n = n[:-1]
        return n

    companies = sorted({k for k in rows.values() if k is not None}, key=lambda k: (name_of(k).lower(), k))
    if len(companies) > 65535:
        raise SystemExit("more than 65535 companies")
    ids = {k: i for i, k in enumerate(companies, 1)}
    if len(rows) > MAX_ENTRIES:
        raise SystemExit(f"{len(rows)} rows are more than the {MAX_ENTRIES} the daemon accepts")
    out = [HEADER]
    for d in sorted(rows):
        key = rows[d]
        out.append(f"{d}\t{0 if key is None else ids[key]}\t{'protected' if key is None else name_of(key)}")
    return "\n".join(out) + "\n"


def main(argv):
    if len(argv) != 5:
        raise SystemExit(__doc__)
    cls, deny, allow, out = (open(p, encoding="utf-8").read() if i < 3 else p for i, p in enumerate(argv[1:]))
    text = build(cls, deny, allow)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    masked = sum(1 for line in text.splitlines()[1:] if line.split("\t")[1] != "0")
    print(f"{len(text.splitlines()) - 1} rows, {masked} masked", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv)
