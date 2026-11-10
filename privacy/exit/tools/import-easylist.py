#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Turns EasyPrivacy and EasyList into the host list that make-mask-list.py reads (ADR-181, ADR-186).

    import-easylist.py EASYPRIVACY.txt EASYLIST.txt OUT.txt

Both lists are dual-licensed GPL-3.0-or-later and CC-BY-SA-3.0-or-later (https://easylist.to/pages/licence.html); the host
list is taken under CC-BY-SA, like the classifier, and `NOTICE` carries the attribution ("The EasyList authors").

Only a rule that names one whole host is taken, because only that says "this host is a tracker" with no condition:

    ||host.example^                 and the same with the options third-party, script, image, xmlhttprequest, ping, ...

Everything else is left out and counted: a rule with a path, a wildcard or a `|` anchor, an option `domain=` (valid on some
sites only), a negated option, an option this tool does not know (`csp`, `redirect`, `removeparam`, ...), a host that is not a
valid name or ends in a number (an address), and every element hiding rule (counted as a wrong shape). An exception rule `@@||host^` of either list, with
whatever options, removes the host and everything under it from the output: the list authors found it breaking something.

Output: a header line, one `# <list> <version> sha256 <hash of the input>` line per input, and rows `host<TAB>tag` sorted by host;
the tag is EP, EL or EP+EL. The output is deterministic. Whether a host is masked is not decided here: make-mask-list.py applies
the protected rows of the classifier, the deny list and the allow list to it.
"""
import hashlib
import re
import sys

HEADER = "easylist-hosts\t1"
OK_OPTS = {"third-party", "3p", "script", "image", "xmlhttprequest", "xhr", "stylesheet", "subdocument", "ping", "other",
           "websocket", "media", "font", "important", "all"}
_LABEL = re.compile(r"^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")
_RULE = re.compile(r"^(@@)?\|\|([^/^$*|?:]+)\^(?:\$(.*))?$")


def valid_host(h):
    labels = h.split(".")
    return len(labels) >= 2 and len(h) <= 253 and all(_LABEL.match(x) for x in labels) and not labels[-1].isdigit()


def parse(text):
    """-> (block {host}, allow {host}, skipped {reason: count})"""
    block, allow, skipped = set(), set(), {}

    def skip(why):
        skipped[why] = skipped.get(why, 0) + 1

    for line in text.splitlines():
        line = line.strip()
        if not line or line[0] in "![":
            continue
        m = _RULE.match(line)  # an element hiding rule has no `||host^` shape and is counted as "shape"
        if not m:
            skip("shape")
            continue
        exception, host, opts = m.groups()
        host = host.lower()
        if not valid_host(host):
            skip("not a host")
        elif exception:
            allow.add(host)
        elif any(o not in OK_OPTS for o in (opts or "").lower().split(",") if o):  # also domain=..., ~option, csp=..., redirect=...
            skip("option")
        else:
            block.add(host)
    return block, allow, skipped


def under(host, names):
    labels = host.split(".")
    return any(".".join(labels[i:]) in names for i in range(len(labels) - 1))


def version_of(text):
    m = re.search(r"^! Version: (\S+)", text, re.M)
    return m.group(1) if m else "unknown"


def build(easyprivacy, easylist):
    """-> the output text"""
    ep, ep_allow, _ = parse(easyprivacy)
    el, el_allow, _ = parse(easylist)
    allow = ep_allow | el_allow
    rows = {}
    for tag, hosts in (("EP", ep), ("EL", el)):
        for h in hosts:
            if not under(h, allow):
                rows.setdefault(h, []).append(tag)
    out = [HEADER]
    for name, text in (("easyprivacy", easyprivacy), ("easylist", easylist)):
        out.append(f"# {name} {version_of(text)} sha256 {hashlib.sha256(text.encode('utf-8')).hexdigest()}")
    out += [f"{h}\t{'+'.join(rows[h])}" for h in sorted(rows)]
    return "\n".join(out) + "\n"


def main(argv):
    if len(argv) != 4:
        raise SystemExit(__doc__)
    texts = [open(p, encoding="utf-8").read() for p in argv[1:3]]
    text = build(*texts)
    with open(argv[3], "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print(f"{len(text.splitlines()) - 3} hosts", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv)
