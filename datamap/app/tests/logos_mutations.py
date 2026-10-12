#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of tests/logos_check.py: each control of tools/import-logos.py is removed or broken in a copy, and
the checks must fail for each. The unmutated tool must pass first, or every mutation would be 'killed' by a
broken setup and prove nothing. usage: logos_mutations.py"""
import pathlib, subprocess, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent
TOOL = HERE.parent / "tools" / "import-logos.py"
CHECK = HERE / "logos_check.py"

# (name, old, new) -- `old` must occur exactly once
MUTATIONS = [
    ("a licence outside the list is accepted", 'if rec["licence"] not in licences:', "if False:"),
    ("the checksum is not compared", 'if hashlib.sha256(data).hexdigest() != rec["sha256"]:', "if False:"),
    ("a source that is not https is accepted", 'if not rec["source"].startswith("https://"):', "if False:"),
    ("any colour is accepted", 'if not re.fullmatch(r"[0-9A-F]{6}", rec["colour"]):', "if False:"),
    ("an owner the classifier does not know is accepted", "if oid not in owners:", "if False:"),
    ("a second logo for one owner is accepted", "if oid in seen_owner:", "if False:"),
    ("two owners may share a resource name", "if res in seen_res:", "if False:"),
    ("a file outside the directory is accepted", 'if base not in f.parents or f.suffix != ".svg":', "if False:"),
    ("a file that is not svg is accepted", ' or f.suffix != ".svg"', ""),
    ("any date is accepted", 'if not re.fullmatch(r"\\d{4}-\\d{2}-\\d{2}", rec["retrieved"]):', "if False:"),
    ("a script element is accepted", 'if el.tag != SVG_NS + "path":', "if False:"),
    ("a document type is accepted",
     'if "<!doctype" in low or "<!entity" in low or "<?xml-stylesheet" in low:', "if False:"),
    ("any svg attribute is accepted", "if a not in SVG_ATTRS:", "if False:"),
    ("any path attribute is accepted", "if a not in PATH_ATTRS:", "if False:"),
    ("a path with children is accepted", "if len(list(el)) != 0:", "if False:"),
    ("any viewBox is accepted", "if x0 != 0 or y0 != 0 or not (0 < w <= 1024) or not (0 < h <= 1024):", "if False:"),
    ("any number of paths is accepted", "if not paths or len(paths) > MAX_PATHS:", "if not paths:"),
    ("an svg without a path is accepted", "if not paths or len(paths) > MAX_PATHS:", "if len(paths) > MAX_PATHS:"),
    ("a large svg is accepted", 'if len(text.encode("utf-8")) > MAX_SVG_BYTES:', "if False:"),
    ("long path data is accepted", "if not d or len(d) > MAX_PATH_CHARS:", "if not d:"),
    ("arc flags are not split", 'if cmd.upper() == "A" and k % 7 in (3, 4):', "if False:"),
    ("an arc flag may be any character", 'if c not in "01":', "if False:"),
    ("an unknown path command is accepted", "if c.upper() not in _ARGS:", "if False:"),
    ("numbers after close are accepted", "if per == 0:", "if False:"),
    ("path data need not start with a move", 'if not out or out[0].upper() != "M":', "if not out:"),
    ("any fill rule is accepted", 'if fr not in ("nonzero", "evenodd"):', "if False:"),
    ("the even-odd rule is dropped", "if even_odd:", "if False:"),
    ("the colour of the record is not used", "lines.append('        android:fillColor=\"#%s\"' % rec[\"colour\"])",
     "lines.append('        android:fillColor=\"#000000\"')"),
    ("a drawable with no record is not reported", "for name in sorted(have - set(want)):", "for name in []:"),
    ("a changed drawable is not reported", 'elif f.read_text(encoding="utf-8") != text:', "elif False:"),
    ("a missing drawable is not reported", 'if not f.is_file():\n                problems.append("missing drawable " + name)',
     'if False:\n                problems.append("missing drawable " + name)'),
    ("convert writes although a record is bad", "    if problems:\n        for p in problems:\n            print(\"FAIL: \" + p)\n        return 1\n    res = pathlib.Path(res_dir)",
     "    if False:\n        for p in problems:\n            print(\"FAIL: \" + p)\n        return 1\n    res = pathlib.Path(res_dir)"),
    ("the missing companies are not ordered", "key=lambda o: (-services.get(o, 0), o)", "key=lambda o: o"),
    ("the resource name keeps upper case out", 'if "A" <= c <= "Z":', "if False:"),
    ("the resource name loses digits", '"a" <= c <= "z" or "0" <= c <= "9"', '"a" <= c <= "z"'),
    ("the resource name keeps any character", 'else "_"', "else c"),
    ("a name that contains the title is proposed", '_norm(title) == _norm(name)', '_norm(name) in _norm(title)'),
    ("an owner with a logo is proposed again", "if oid in have:\n            continue", "if False:\n            continue"),
    ("the proposed colour is not in capitals", '.get(_norm(m.group(1)), "").upper()', '.get(_norm(m.group(1)), "")'),
    ("a second logo is added for one owner", 'if owner_id in {r["owner_id"] for r in load_records(logos_dir)}:', "if False:"),
    ("a logo is added for an owner that does not exist", "if owner_id not in owners:", "if False:"),
    ("an owner identifier of any characters is added", 'if not re.fullmatch(r"[a-z][a-z0-9_]*", owner_id):', "if False:"),
    ("a bad svg is copied by add", "try:\n        parse_svg(data.decode(\"utf-8\"))", "try:\n        pass"),
    ("add does not record the checksum", '"sha256": hashlib.sha256(data).hexdigest(), "note": note}', '"sha256": "0" * 64, "note": note}'),
]


def run(tool):
    return subprocess.run([sys.executable, "-I", str(CHECK), str(tool)], capture_output=True, text=True)


def main():
    base = run(TOOL)
    if base.returncode != 0:
        print("the unmutated tool does not pass the checks:\n" + base.stdout + base.stderr)
        sys.exit(2)
    text = TOOL.read_text(encoding="utf-8")
    survivors = []
    for name, old, new in MUTATIONS:
        if text.count(old) != 1:
            print("BROKEN MUTATION %s: found %d of the old text, expected 1" % (name, text.count(old)))
            sys.exit(2)
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d) / "import-logos.py"
            p.write_text(text.replace(old, new), encoding="utf-8")
            killed = run(p).returncode != 0
        print(("KILLED   " if killed else "SURVIVED ") + name)
        if not killed:
            survivors.append(name)
    print("%d of %d mutations killed" % (len(MUTATIONS) - len(survivors), len(MUTATIONS)))
    sys.exit(1 if survivors else 0)


main()
