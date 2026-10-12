#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Checks of tools/import-logos.py. usage: logos_check.py [TOOL.py]  (default: the tool next to tests/)."""
import contextlib, hashlib, importlib.util, io, pathlib, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent
TOOL = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else HERE.parent / "tools" / "import-logos.py"
spec = importlib.util.spec_from_file_location("import_logos", TOOL)
L = importlib.util.module_from_spec(spec)
spec.loader.exec_module(L)

SVG = ('<svg role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>T</title>'
       '<path d="M12 0C5.4 0 0 5.4 0 12s5.4 12 12 12 12-5.4 12-12S18.6 0 12 0z"/></svg>')


def refuses(fn, what):
    try:
        fn()
    except L.LogoError:
        return
    raise AssertionError("accepted: " + what)


def eq(expected, actual, what):
    if expected != actual:
        raise AssertionError("%s: expected <%s> but was <%s>" % (what, expected, actual))


def svg_with(inner="", root_attrs='role="img" viewBox="0 0 24 24"', path='<path d="M0 0h1v1z"/>'):
    return '<svg %s xmlns="http://www.w3.org/2000/svg">%s%s</svg>' % (root_attrs, inner, path)


# ---- path data
eq("M 0 0 h 1 v 1 H 0 z", L.normalise_path("M0 0h1v1H0z"), "plain path")
eq("M 10 10 a 1 1 0 0 1 1 1", L.normalise_path("M10 10a1 1 0 011 1"), "arc flags that run together")
eq("M 1 1 A 2 2 0 1 0 5 5 3 3 0 0 1 8 8", L.normalise_path("M1 1A2 2 0 1 0 5 5 3 3 0 0 1 8 8"), "two arcs")
eq("M 1e2 -.5", L.normalise_path("M1e2 -.5"), "exponent and a leading dot")
eq("M 12.5 .5", L.normalise_path("M12.5.5"), "two numbers that share a dot")
eq("M 1 -2", L.normalise_path("M1-2"), "a minus that separates")
refuses(lambda: L.normalise_path(""), "empty path data")
refuses(lambda: L.normalise_path("L1 1"), "a path that does not start with a move")
refuses(lambda: L.normalise_path("M1 1x"), "an unknown command")
refuses(lambda: L.normalise_path("M1 1z 5"), "a number after close")
refuses(lambda: L.normalise_path("M0 0 A1 1 0 2 1 1 1"), "an arc flag of 2")
refuses(lambda: L.normalise_path("5 5"), "a number first")
refuses(lambda: L.normalise_path("M1 ."), "a dot is no number")
# Only the ASCII command letters and digits of SVG: str.isalpha, str.upper and \d accept more than that.
refuses(lambda: L.normalise_path("M0 0 ſ1 1"), "a long s that upper-cases to the command S")
refuses(lambda: L.normalise_path("M0 0 L ١٢ ٣ z"), "Arabic-Indic digits")
# Every command takes a whole number of argument sets; an unparseable pathData would fail at draw time.
refuses(lambda: L.normalise_path("M 1"), "a move with one number")
refuses(lambda: L.normalise_path("M0 0 L 1 2 3"), "a line with three numbers")
refuses(lambda: L.normalise_path("M0 0 L 1 z"), "a line with one number before close")
refuses(lambda: L.normalise_path("M0 0 A1 1 0 0 1 1"), "an arc with six numbers")
refuses(lambda: L.normalise_path("M0 0 L z"), "a line with no numbers")
eq("M 1 1 2 2", L.normalise_path("M1 1 2 2"), "an implicit line after a move is kept")
eq("M 0 0 L 1 2 3 4 z", L.normalise_path("M0 0 L 1 2 3 4 z"), "a line repeated without its letter")

# ---- svg
w, h, paths = L.parse_svg(SVG)
eq((24.0, 24.0), (w, h), "viewBox")
eq(1, len(paths), "one path")
eq(False, paths[0][1], "nonzero")
w, h, paths = L.parse_svg(svg_with(path='<path fill-rule="evenodd" d="M0 0h1v1z"/>'))
eq(True, paths[0][1], "evenodd")
L.parse_svg(svg_with(inner="<title>x</title>"))
refuses(lambda: L.parse_svg(svg_with(inner="<script>alert(1)</script>")), "a script")
refuses(lambda: L.parse_svg(svg_with(inner="<style>p{}</style>")), "a style")
refuses(lambda: L.parse_svg(svg_with(inner="<g><path d='M0 0h1v1z'/></g>")), "a group")
refuses(lambda: L.parse_svg(svg_with(inner='<image href="http://x/y.png"/>')), "an image")
refuses(lambda: L.parse_svg(svg_with(root_attrs='role="img" viewBox="0 0 24 24" onload="x()"')), "an event attribute")
refuses(lambda: L.parse_svg(svg_with(path='<path d="M0 0h1v1z" style="fill:red"/>')), "a style attribute on a path")
refuses(lambda: L.parse_svg(svg_with(path='<path d="M0 0h1v1z" fill-rule="sideways"/>')), "an unknown fill rule")
refuses(lambda: L.parse_svg(svg_with(path='<path/>')), "a path without data")
refuses(lambda: L.parse_svg(svg_with(path='<path d="M0 0h1v1z"><a/></path>')), "a path with a child")
refuses(lambda: L.parse_svg(svg_with(path="")), "no path")
refuses(lambda: L.parse_svg(svg_with(path='<path d="M0 0h1v1z"/>' * 21)), "21 paths")
L.parse_svg(svg_with(path='<path d="M0 0h1v1z"/>' * 20))
refuses(lambda: L.parse_svg(svg_with(root_attrs='role="img"')), "no viewBox")
refuses(lambda: L.parse_svg(svg_with(root_attrs='viewBox="0 0 24"')), "a viewBox of three numbers")
refuses(lambda: L.parse_svg(svg_with(root_attrs='viewBox="1 0 24 24"')), "a viewBox that does not start at 0")
refuses(lambda: L.parse_svg(svg_with(root_attrs='viewBox="0 0 2000 24"')), "a viewBox that is too wide")
refuses(lambda: L.parse_svg(svg_with(root_attrs='viewBox="0 0 0 24"')), "a viewBox with no width")
refuses(lambda: L.parse_svg(svg_with(root_attrs='viewBox="0 0 a 24"')), "a viewBox that is not numbers")
refuses(lambda: L.parse_svg('<!DOCTYPE svg [<!ENTITY a "b">]>' + SVG), "a document type")
refuses(lambda: L.parse_svg(SVG.replace("<title>", '<!ENTITY x "y"><title>')), "an entity")
refuses(lambda: L.parse_svg('<svg'), "not well formed")
refuses(lambda: L.parse_svg('<html xmlns="http://www.w3.org/2000/svg"/>'), "a root that is not svg")
refuses(lambda: L.parse_svg(SVG.replace("<title>T</title>", "<title>" + "x" * 70000 + "</title>")), "a file over the limit")
refuses(lambda: L.parse_svg(svg_with(path='<path d="' + "M0 0h1 " * 4000 + '"/>')), "path data over the limit")

# ---- the vector drawable
rec = {"licence": "CC0-1.0", "file": "svg/x.svg", "colour": "4285F4"}
xml = L.to_vector("x", rec, 24.0, 24.0, [("M 0 0 h 1 v 1 z", False), ("M 5 5 h 1 v 1 z", True)])
assert 'android:fillColor="#4285F4"' in xml and 'android:viewportWidth="24"' in xml, xml
assert xml.count("<path") == 2 and xml.count('android:fillType="evenOdd"') == 1, xml
assert "SPDX-License-Identifier: CC0-1.0" in xml and "svg/x.svg" in xml, xml
eq(xml, L.to_vector("x", rec, 24.0, 24.0, [("M 0 0 h 1 v 1 z", False), ("M 5 5 h 1 v 1 z", True)]), "deterministic")
assert 'android:viewportWidth="24.5"' in L.to_vector("x", rec, 24.5, 24.0, [("M 0 0 z", False)]), "a width with a fraction"

# ---- the record of the logos
def sha(b):
    return hashlib.sha256(b).hexdigest()


def make(tmp, rows=None, licences="CC0-1.0\nLicenseRef-Public-Domain\n", files=None):
    logos = tmp / "logos"
    (logos / "svg").mkdir(parents=True)
    files = files if files is not None else {"svg/google.svg": SVG, "svg/meta.svg": SVG.replace("M12", "M13")}
    for name, text in files.items():
        (logos / name).write_text(text, encoding="utf-8")
    if rows is None:
        rows = []
        for oid in ("google", "meta"):
            data = (logos / ("svg/%s.svg" % oid)).read_bytes()
            rows.append("\t".join([oid, "svg/%s.svg" % oid, "4285F4", "https://example.org/" + oid, "CC0-1.0",
                                   "2026-10-12", sha(data), ""]))
    (logos / "logos.tsv").write_text("# a comment\n" + "\n".join(rows) + "\n", encoding="utf-8")
    (logos / "licences.txt").write_text(licences, encoding="utf-8")
    cls = tmp / "classifier.tsv"
    cls.write_text("\n".join([
        "datamap-classifier\t1",
        "owner\tgoogle\tGoogle\tGoogle LLC\tUS\tMountain View\t37.3\t-122.0\tA-003",
        "owner\tmeta\tMeta\tMeta\tUS\tMenlo Park\t37.4\t-122.1\tA-007",
        "owner\tbig\tBig\tBig\tUS\tX\t1\t1\tA-1",
        "owner\tsmall\tSmall\tSmall\tUS\tX\t1\t1\tA-1",
        "owner\ta_b\tAB\tAB\tUS\tX\t1\t1\tA-1",
        "owner\ta-b\tAB2\tAB2\tUS\tX\t1\t1\tA-1",
        "service\ta.com\tbig\tadvertising\tA-1",
        "service\tb.com\tbig\tadvertising\tA-1",
        "service\tc.com\tbig\tadvertising\tA-1",
        "service\td.com\tsmall\tadvertising\tA-1",
        "service\te.com\tgoogle\tcore\tA-1",
    ]) + "\n", encoding="utf-8")
    return logos, cls


def problems(tmp, **kw):
    logos, cls = make(tmp, **kw)
    return L.validate(logos, cls)[0]


def has(probs, text):
    return any(text in p for p in probs)


def row(oid="google", file="svg/google.svg", colour="4285F4", source="https://example.org/g", licence="CC0-1.0",
        retrieved="2026-10-12", digest=None, note=""):
    return "\t".join([oid, file, colour, source, licence, retrieved, digest or sha(SVG.encode()), note])


with tempfile.TemporaryDirectory() as d:
    eq([], problems(pathlib.Path(d)), "the good record has no problems")
with tempfile.TemporaryDirectory() as d:   # an svg that no record names would be third-party artwork with no source
    probs = problems(pathlib.Path(d), files={"svg/google.svg": SVG, "svg/meta.svg": SVG.replace("M12", "M13"),
                                             "svg/stray.svg": SVG})
    assert has(probs, "svg/stray.svg has no record"), probs
cases = [
    ("an owner the classifier does not know", [row(oid="nobody")], "not an owner"),
    ("a second logo for one owner", [row(), row(file="svg/google.svg")], "second logo"),
    ("two owners with one resource name", [row(oid="a_b"), row(oid="a-b")], "already used"),
    ("a lower case colour", [row(colour="4285f4")], "six capital hex digits"),
    ("a colour of five digits", [row(colour="4285F")], "six capital hex digits"),
    ("a source that is not https", [row(source="http://example.org/g")], "https"),
    ("a licence that is not allowed", [row(licence="CC-BY-4.0")], "licences.txt"),
    ("a date that is no date", [row(retrieved="12-10-2026")], "YYYY-MM-DD"),
    ("a file outside the directory", [row(file="../classifier.tsv")], "inside the logos directory"),
    ("a file that is not svg", [row(file="logos.tsv")], "inside the logos directory"),
    ("a file that is missing", [row(file="svg/none.svg")], "does not exist"),
    ("a checksum that does not match", [row(digest="0" * 64)], "checksum"),
]
for what, rows, text in cases:
    with tempfile.TemporaryDirectory() as d:
        probs = problems(pathlib.Path(d), rows=rows)
        assert has(probs, text), "%s: expected a problem with <%s>, got %s" % (what, text, probs)
with tempfile.TemporaryDirectory() as d:
    probs = problems(pathlib.Path(d), files={"svg/google.svg": SVG}, rows=[row(file="svg/google.svg")])
    eq([], probs, "one good logo")
with tempfile.TemporaryDirectory() as d:
    bad = '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><script/></svg>'
    probs = problems(pathlib.Path(d), files={"svg/google.svg": bad},
                     rows=[row(digest=sha(bad.encode()))])
    assert has(probs, "element not allowed"), probs
with tempfile.TemporaryDirectory() as d:
    logos, cls = make(pathlib.Path(d), rows=["too\tfew"])
    refuses(lambda: L.validate(logos, cls), "a line with too few fields")

# ---- check, convert and coverage
def run(fn, *args):
    out = io.StringIO()
    with contextlib.redirect_stdout(out):
        rc = fn(*args)
    return rc, out.getvalue()


with tempfile.TemporaryDirectory() as d:
    tmp = pathlib.Path(d)
    logos, cls = make(tmp)
    res = tmp / "res"
    rc, out = run(L.cmd_convert, logos, cls, res)
    eq(0, rc, "convert succeeds: " + out)
    eq(["logo_google.xml", "logo_meta.xml"], sorted(p.name for p in res.glob("logo_*.xml")), "two drawables")
    rc, out = run(L.cmd_check, logos, cls, res)
    eq(0, rc, "check passes after convert: " + out)
    (res / "logo_google.xml").write_text("changed", encoding="utf-8")
    rc, out = run(L.cmd_check, logos, cls, res)
    assert rc == 1 and "differs" in out, out
    (res / "logo_google.xml").unlink()
    rc, out = run(L.cmd_check, logos, cls, res)
    assert rc == 1 and "missing drawable logo_google.xml" in out, out
    run(L.cmd_convert, logos, cls, res)
    (res / "logo_stale.xml").write_text("x", encoding="utf-8")
    rc, out = run(L.cmd_check, logos, cls, res)
    assert rc == 1 and "a drawable with no record: logo_stale.xml" in out, out
    rc, out = run(L.cmd_convert, logos, cls, res)
    assert rc == 1 and "STALE: logo_stale.xml" in out, out
    rc, out = run(L.cmd_check, logos, cls)
    eq(0, rc, "check without drawables only reads the records")
    rc, out = run(L.cmd_coverage, logos, cls)
    assert "2 of 6 companies have a logo" in out, out
    lines = [l.split()[0] for l in out.splitlines() if l.startswith("  ")]
    eq(["big", "small", "a-b", "a_b"], lines, "the missing ones, by their services and then by name")
    # convert refuses to write anything when a record is bad
    logos2, cls2 = make(tmp / "second", rows=[row(licence="CC-BY-4.0")])
    rc, out = run(L.cmd_convert, logos2, cls2, tmp / "res2")
    assert rc == 1 and not (tmp / "res2").exists(), out

# ---- propose and add
SI_SVG = ('<svg role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>%s</title>'
          '<path d="M0 0h24v24z"/></svg>')
with tempfile.TemporaryDirectory() as d:
    tmp = pathlib.Path(d)
    logos, cls = make(tmp)
    icons = tmp / "si"
    (icons / "icons").mkdir(parents=True)
    (icons / "data").mkdir()
    for slug, title in (("big", "Big"), ("bigger", "Bigger Co"), ("ab", "A-B"), ("google", "Google"), ("other", "Other")):
        (icons / "icons" / (slug + ".svg")).write_text(SI_SVG % title, encoding="utf-8")
    (icons / "data" / "simple-icons.json").write_text(
        '[{"title": "Big", "hex": "ff0000"}, {"title": "Bigger Co", "hex": "00ff00"}, {"title": "A-B", "hex": "0000FF"},'
        ' {"title": "Google", "hex": "4285F4"}, {"title": "Other", "hex": "111111"}]', encoding="utf-8")
    rc, out = run(L.cmd_propose, logos, cls, icons)
    eq(0, rc, "propose succeeds")
    rows = [l.split("\t") for l in out.splitlines() if not l.startswith("#")]
    eq(["a-b", "a_b", "big"], sorted(r[0] for r in rows), "the owners that have no logo and a like-named icon; google has one")
    big = [r for r in rows if r[0] == "big"][0]
    eq(["big", "Big", "big", "Big", "FF0000", "name"], big, "a pair, with the colour in capitals")
    assert "# 3 candidates" in out, out
    # a name that only resembles is not proposed
    assert not any(r[0] == "small" for r in rows), "small has no icon"
    # add copies the file, records it and passes the check
    src = tmp / "big.svg"
    src.write_text(SI_SVG % "Big", encoding="utf-8")
    rc, out = run(L.cmd_add, logos, cls, "big", src, "FF0000", "https://example.org/big", "CC0-1.0", "2026-10-12")
    eq(0, rc, "add succeeds: " + out)
    assert (logos / "svg" / "big.svg").is_file(), "the file is copied"
    rc, out = run(L.cmd_check, logos, cls)
    eq(0, rc, "the check passes after add: " + out)
    eq(True, "big\tsvg/big.svg\tFF0000\thttps://example.org/big\tCC0-1.0\t2026-10-12\t" + sha(src.read_bytes()) in
       (logos / "logos.tsv").read_text(encoding="utf-8"), "the record has the checksum of the file")
    rc, out = run(L.cmd_add, logos, cls, "big", src, "FF0000", "https://example.org/big", "CC0-1.0", "2026-10-12")
    assert rc == 1 and "already has a logo" in out, out
    rc, out = run(L.cmd_add, logos, cls, "nobody", src, "FF0000", "https://example.org/x", "CC0-1.0", "2026-10-12")
    assert rc == 1 and "not an owner" in out and not (logos / "svg" / "nobody.svg").exists(), out
    assert "nobody" not in (logos / "logos.tsv").read_text(encoding="utf-8"), "nothing is recorded for a stranger"
    rc, out = run(L.cmd_add, logos, cls, "a-b", src, "FF0000", "https://example.org/x", "CC0-1.0", "2026-10-12")
    assert rc == 1 and "lower case letters" in out, out
    evil = tmp / "evil.svg"
    evil.write_text('<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><script/></svg>', encoding="utf-8")
    rc, out = run(L.cmd_add, logos, cls, "small", evil, "FF0000", "https://example.org/s", "CC0-1.0", "2026-10-12")
    assert rc == 1 and "element not allowed" in out and not (logos / "svg" / "small.svg").exists(), out
    rc, out = run(L.cmd_add, logos, cls, "small", src, "FF0000", "https://example.org/s", "CC-BY-4.0", "2026-10-12")
    assert rc == 1 and "licences.txt" in out, out
    # the command line: the eight arguments of the plan, with and without the note
    logos3, cls3 = make(tmp / "cli")
    cli = ["import-logos.py", "add", str(logos3), str(cls3)]
    rc, out = run(L.main, cli + ["a_b", str(src), "00FF00", "https://example.org/ab", "CC0-1.0", "2026-10-12"])
    eq(0, rc, "add from the command line without a note: " + out)
    rc, out = run(L.main, cli + ["small", str(src), "00FF00", "https://example.org/s", "CC0-1.0", "2026-10-12", "a note"])
    eq(0, rc, "add from the command line with a note: " + out)
    assert "\ta note" in (logos3 / "logos.tsv").read_text(encoding="utf-8"), "the note is recorded"

# ---- the resource name agrees with the app
fixture = HERE / "fixtures" / "logo-names.tsv"
n = 0
for line in fixture.read_text(encoding="utf-8").splitlines():
    if line and not line.startswith("#"):
        owner, name = line.split("\t")
        eq(name, L.resource_name(owner), "resource name of " + owner)
        n += 1
assert n >= 6, n
print("PASS logos_check")
