#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Company logos for the Data Map (ADR-159): checks the record of the logo files and turns each into an Android
vector drawable. A logo is third-party artwork and a trademark; a file enters the package only with a record of
where it came from and under which licence, and only with a licence from the allowed list.

usage:
  import-logos.py check    LOGOS_DIR CLASSIFIER_TSV [RES_DIR]
  import-logos.py convert  LOGOS_DIR CLASSIFIER_TSV RES_DIR
  import-logos.py coverage LOGOS_DIR CLASSIFIER_TSV
  import-logos.py propose  LOGOS_DIR CLASSIFIER_TSV SIMPLE_ICONS_DIR      (prints candidates, changes nothing)
  import-logos.py add      LOGOS_DIR CLASSIFIER_TSV OWNER_ID SVG COLOUR SOURCE LICENCE RETRIEVED [NOTE]

LOGOS_DIR holds logos.tsv, licences.txt and the svg files. logos.tsv, tab separated, one logo per line:
owner_id, file, colour (six capital hex digits), source (https), licence (SPDX), retrieved (YYYY-MM-DD), sha256 of the
file, note. `check` with RES_DIR also compares each drawable with what `convert` would write.
"""
import hashlib, pathlib, re, sys
import xml.etree.ElementTree as ET

MAX_SVG_BYTES = 65536
MAX_PATH_CHARS = 20000
MAX_PATHS = 20
SVG_NS = "{http://www.w3.org/2000/svg}"
SVG_ATTRS = {"role", "viewBox", "xmlns", "width", "height", "aria-label"}
PATH_ATTRS = {"d", "fill-rule"}
FIELDS = ["owner_id", "file", "colour", "source", "licence", "retrieved", "sha256", "note"]


class LogoError(Exception):
    pass


def resource_name(owner_id):
    """The same rule as org.sovereignos.datamap.model.LogoName; tests/fixtures/logo-names.tsv pins both."""
    out = "logo_"
    for c in owner_id:
        if "A" <= c <= "Z":
            c = c.lower()
        out += c if ("a" <= c <= "z" or "0" <= c <= "9") else "_"
    return out


# ---------------------------------------------------------------------------------------------- path data

_NUM = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
_ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def normalise_path(d):
    """Path data with every number and arc flag separated by a space. SVG allows "a1 1 0 011 1", where the two
    flags and the next number run together; not every parser reads that, so the flags are split here."""
    out = []
    i, n = 0, len(d)
    cmd = None
    k = 0   # the position of the number within the parameters of the current command
    while i < n:
        c = d[i]
        if c in " \t\r\n,":
            i += 1
        elif c.isalpha():
            if c.upper() not in _ARGS:
                raise LogoError("unknown path command " + c)
            cmd = c
            k = 0
            out.append(c)
            i += 1
        else:
            if cmd is None:
                raise LogoError("path data starts with a number")
            per = _ARGS[cmd.upper()]
            if per == 0:
                raise LogoError("numbers after a close command")
            if cmd.upper() == "A" and k % 7 in (3, 4):
                if c not in "01":
                    raise LogoError("an arc flag that is not 0 or 1")
                out.append(c)
                i += 1
            else:
                m = _NUM.match(d, i)
                if not m:
                    raise LogoError("a number was expected in the path data at " + str(i))
                out.append(m.group(0))
                i = m.end()
            k += 1
    if not out or out[0].upper() != "M":
        raise LogoError("path data must start with a move")
    return " ".join(out)


# ---------------------------------------------------------------------------------------------------- svg

def parse_svg(text):
    """Returns (width, height, [(path data, even_odd)]) from a svg that holds nothing but paths."""
    if len(text.encode("utf-8")) > MAX_SVG_BYTES:
        raise LogoError("the svg is larger than %d bytes" % MAX_SVG_BYTES)
    low = text.lower()
    if "<!doctype" in low or "<!entity" in low or "<?xml-stylesheet" in low:
        raise LogoError("a document type, entity or style sheet in the svg")
    try:
        root = ET.fromstring(text)
    except ET.ParseError as e:
        raise LogoError("the svg is not well formed: %s" % e)
    if root.tag != SVG_NS + "svg":
        raise LogoError("the root element is not svg")
    for a in root.attrib:
        if a not in SVG_ATTRS:
            raise LogoError("svg attribute not allowed: " + a)
    vb = root.attrib.get("viewBox", "").replace(",", " ").split()
    if len(vb) != 4:
        raise LogoError("the svg has no viewBox of four numbers")
    try:
        x0, y0, w, h = (float(v) for v in vb)
    except ValueError:
        raise LogoError("the viewBox is not numbers")
    if x0 != 0 or y0 != 0 or not (0 < w <= 1024) or not (0 < h <= 1024):
        raise LogoError("the viewBox must start at 0 0 and be at most 1024 square")
    paths = []
    for el in root:
        if el.tag == SVG_NS + "title":
            continue
        if el.tag != SVG_NS + "path":
            raise LogoError("element not allowed: " + el.tag.replace(SVG_NS, ""))
        for a in el.attrib:
            if a not in PATH_ATTRS:
                raise LogoError("path attribute not allowed: " + a)
        if len(list(el)) != 0:
            raise LogoError("a path with children")
        d = el.attrib.get("d", "")
        if not d or len(d) > MAX_PATH_CHARS:
            raise LogoError("a path with no data or too much")
        fr = el.attrib.get("fill-rule", "nonzero")
        if fr not in ("nonzero", "evenodd"):
            raise LogoError("fill-rule must be nonzero or evenodd")
        paths.append((normalise_path(d), fr == "evenodd"))
    if not paths or len(paths) > MAX_PATHS:
        raise LogoError("the svg must hold between 1 and %d paths" % MAX_PATHS)
    return w, h, paths


def _num(v):
    return ("%g" % v)


def to_vector(owner_id, rec, w, h, paths):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- SPDX-License-Identifier: %s -->" % rec["licence"],
        "<!-- Third-party artwork and a trademark of its owner. Generated by tools/import-logos.py from %s;" % rec["file"],
        "     source, licence and checksum are recorded in logos.tsv. Do not edit. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        '    android:viewportWidth="%s"' % _num(w),
        '    android:viewportHeight="%s">' % _num(h),
    ]
    for d, even_odd in paths:
        lines.append("    <path")
        lines.append('        android:fillColor="#%s"' % rec["colour"])
        if even_odd:
            lines.append('        android:fillType="evenOdd"')
        lines.append('        android:pathData="%s" />' % d)
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


# ------------------------------------------------------------------------------------------------ records

def load_owners(classifier_tsv):
    owners, services = set(), {}
    for line in pathlib.Path(classifier_tsv).read_text(encoding="utf-8").splitlines():
        f = line.split("\t")
        if f[0] == "owner" and len(f) > 1:
            owners.add(f[1])
        elif f[0] == "service" and len(f) > 2:
            services[f[2]] = services.get(f[2], 0) + 1
    return owners, services


def load_licences(logos_dir):
    p = pathlib.Path(logos_dir) / "licences.txt"
    return {l.strip() for l in p.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")}


def load_records(logos_dir):
    p = pathlib.Path(logos_dir) / "logos.tsv"
    recs = []
    for no, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        f = line.split("\t")
        if len(f) != len(FIELDS):
            raise LogoError("logos.tsv line %d has %d fields, not %d" % (no, len(f), len(FIELDS)))
        rec = dict(zip(FIELDS, f))
        rec["line"] = no
        recs.append(rec)
    return recs


def validate(logos_dir, classifier_tsv):
    """Returns the problems found as a list of strings (empty: the records are good) and the parsed logos."""
    base = pathlib.Path(logos_dir).resolve()
    owners, _ = load_owners(classifier_tsv)
    licences = load_licences(logos_dir)
    problems, parsed = [], {}
    seen_owner, seen_res = set(), {}
    for rec in load_records(logos_dir):
        where = "logos.tsv line %d (%s)" % (rec["line"], rec["owner_id"])
        oid = rec["owner_id"]
        if oid not in owners:
            problems.append(where + ": not an owner of the classifier")
        if oid in seen_owner:
            problems.append(where + ": a second logo for the same owner")
        seen_owner.add(oid)
        res = resource_name(oid)
        if res in seen_res:
            problems.append(where + ": the resource name %s is already used by %s" % (res, seen_res[res]))
        seen_res[res] = oid
        if not re.fullmatch(r"[0-9A-F]{6}", rec["colour"]):
            problems.append(where + ": the colour must be six capital hex digits")
        if not rec["source"].startswith("https://"):
            problems.append(where + ": the source must be an https address")
        if rec["licence"] not in licences:
            problems.append(where + ": the licence %s is not in licences.txt" % rec["licence"])
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", rec["retrieved"]):
            problems.append(where + ": retrieved must be YYYY-MM-DD")
        f = (base / rec["file"]).resolve()
        if base not in f.parents or f.suffix != ".svg":
            problems.append(where + ": the file must be an svg inside the logos directory")
            continue
        if not f.is_file():
            problems.append(where + ": the file does not exist")
            continue
        data = f.read_bytes()
        if hashlib.sha256(data).hexdigest() != rec["sha256"]:
            problems.append(where + ": the checksum does not match the file")
            continue
        try:
            parsed[oid] = (rec,) + parse_svg(data.decode("utf-8"))
        except (LogoError, UnicodeDecodeError) as e:
            problems.append(where + ": " + str(e))
    return problems, parsed


def drawables(parsed):
    return {resource_name(oid) + ".xml": to_vector(oid, p[0], p[1], p[2], p[3]) for oid, p in sorted(parsed.items())}


# --------------------------------------------------------------------------------------------------- main

def _norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def load_owner_names(classifier_tsv):
    names = {}
    for line in pathlib.Path(classifier_tsv).read_text(encoding="utf-8").splitlines():
        f = line.split("\t")
        if f[0] == "owner" and len(f) > 2:
            names[f[1]] = f[2]
    return names


def cmd_propose(logos_dir, classifier, icons_dir):
    """Candidates, for a person to confirm: an icon of Simple Icons whose title is the name or the identifier of
    an owner that has no logo yet. A name that merely looks alike is a different company, so nothing is added
    here and every pair must be read before `add`."""
    import json
    root = pathlib.Path(icons_dir)
    data = root / "data" / "simple-icons.json"
    if not data.is_file():
        data = root / "_data" / "simple-icons.json"
    meta = json.loads(data.read_text(encoding="utf-8"))
    if isinstance(meta, dict):
        meta = meta.get("icons", [])
    hexes = {_norm(m["title"]): m.get("hex", "") for m in meta if "title" in m}
    icons = []
    for f in sorted((root / "icons").glob("*.svg")):
        m = re.search(r"<title>([^<]*)</title>", f.read_text(encoding="utf-8"))
        if m:
            icons.append((f.stem, m.group(1), hexes.get(_norm(m.group(1)), "").upper()))
    have = {r["owner_id"] for r in load_records(logos_dir)}
    count = 0
    for oid, name in sorted(load_owner_names(classifier).items()):
        if oid in have:
            continue
        for slug, title, hexcolour in icons:
            how = "name" if _norm(title) == _norm(name) else "id" if _norm(title) == _norm(oid) else None
            if how:
                print("\t".join([oid, name, slug, title, hexcolour or "?", how]))
                count += 1
    print("# %d candidates; read each pair before adding it" % count)
    return 0


def cmd_add(logos_dir, classifier, owner_id, svg, colour, source, licence, retrieved, note=""):
    """Copies a svg into the logos directory as svg/OWNER_ID.svg and records it, with its checksum, in logos.tsv."""
    base = pathlib.Path(logos_dir)
    owners, _ = load_owners(classifier)
    if owner_id not in owners:
        print("FAIL: %s is not an owner of the classifier" % owner_id)
        return 1
    if not re.fullmatch(r"[a-z][a-z0-9_]*", owner_id):
        print("FAIL: the owner identifier must be lower case letters, digits and underscores")
        return 1
    if owner_id in {r["owner_id"] for r in load_records(logos_dir)}:
        print("FAIL: %s already has a logo" % owner_id)
        return 1
    data = pathlib.Path(svg).read_bytes()
    try:
        parse_svg(data.decode("utf-8"))
    except (LogoError, UnicodeDecodeError) as e:
        print("FAIL: " + str(e))
        return 1
    rec = {"owner_id": owner_id, "file": "svg/%s.svg" % owner_id, "colour": colour, "source": source,
           "licence": licence, "retrieved": retrieved, "sha256": hashlib.sha256(data).hexdigest(), "note": note}
    (base / "svg").mkdir(parents=True, exist_ok=True)
    (base / rec["file"]).write_bytes(data)
    with open(base / "logos.tsv", "a", encoding="utf-8") as f:
        f.write("\t".join(rec[k] for k in FIELDS) + "\n")
    problems, _ = validate(logos_dir, classifier)
    for p in problems:
        print("FAIL: " + p)
    if problems:
        print("The record was written; correct logos.tsv or remove the line and the file.")
        return 1
    print("added %s" % owner_id)
    return 0


def cmd_check(logos_dir, classifier, res_dir=None):
    problems, parsed = validate(logos_dir, classifier)
    if res_dir is not None:
        want = drawables(parsed)
        res = pathlib.Path(res_dir)
        have = {p.name for p in res.glob("logo_*.xml")}
        for name, text in want.items():
            f = res / name
            if not f.is_file():
                problems.append("missing drawable " + name)
            elif f.read_text(encoding="utf-8") != text:
                problems.append("the drawable %s differs from what convert writes" % name)
        for name in sorted(have - set(want)):
            problems.append("a drawable with no record: " + name)
    for p in problems:
        print("FAIL: " + p)
    if not problems:
        print("logo checks passed: %d logos" % len(parsed))
    return 1 if problems else 0


def cmd_convert(logos_dir, classifier, res_dir):
    problems, parsed = validate(logos_dir, classifier)
    if problems:
        for p in problems:
            print("FAIL: " + p)
        return 1
    res = pathlib.Path(res_dir)
    res.mkdir(parents=True, exist_ok=True)
    want = drawables(parsed)
    for name, text in want.items():
        (res / name).write_text(text, encoding="utf-8")
    stale = sorted({p.name for p in res.glob("logo_*.xml")} - set(want))
    for s in stale:
        print("STALE: %s has no record; delete it" % s)
    print("wrote %d drawables" % len(want))
    return 1 if stale else 0


def cmd_coverage(logos_dir, classifier):
    owners, services = load_owners(classifier)
    problems, parsed = validate(logos_dir, classifier)
    have = set(parsed)
    print("%d of %d companies have a logo" % (len(have & owners), len(owners)))
    missing = sorted(owners - have, key=lambda o: (-services.get(o, 0), o))
    print("without a logo, by the number of services the classifier lists for them:")
    for o in missing[:25]:
        print("  %-28s %d" % (o, services.get(o, 0)))
    return 0


def main(argv):
    if len(argv) >= 4 and argv[1] == "check":
        return cmd_check(argv[2], argv[3], argv[4] if len(argv) > 4 else None)
    if len(argv) == 5 and argv[1] == "convert":
        return cmd_convert(argv[2], argv[3], argv[4])
    if len(argv) == 4 and argv[1] == "coverage":
        return cmd_coverage(argv[2], argv[3])
    if len(argv) == 5 and argv[1] == "propose":
        return cmd_propose(argv[2], argv[3], argv[4])
    if len(argv) in (9, 10) and argv[1] == "add":
        return cmd_add(*argv[2:])
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
