#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Resource guards of the Passwords app (plan, Global Constraints).

  1. No user-visible text in code: a string literal under ui/ that has a letter and a space, or starts with a capital,
     is refused unless its line carries `// i18n-ok`. Texts are Android string resources that follow the system language.
  2. Every `R.string.X`, `R.plurals.X` and `R.drawable.X` used in code, and every `@string/X` or `@drawable/X` in the
     manifest and the resource files, exists.
  3. strings.xml is well formed, has no duplicate name, and every name starts with `pm_`.
  4. The drawables are exactly the icons of tools/icons.txt (Material Symbols Rounded, from the reference page), all
     vector drawables, no bitmap.

usage: check-resources.py [APP_DIR]   exit status 0 when all hold
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

app = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), ".."))
errors = []


def err(msg):
    errors.append(msg)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


# --- defined resources ---
strings, plurals = set(), set()
for dirpath, _, files in os.walk(os.path.join(app, "res")):
    if os.path.basename(dirpath).startswith("values") and "strings.xml" in files:
        path = os.path.join(dirpath, "strings.xml")
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as e:
            err(f"{path}: not well formed: {e}")
            continue
        seen = set()
        for el in root:
            name = el.get("name")
            if el.tag not in ("string", "plurals", "string-array"):
                continue
            if name in seen:
                err(f"{path}: duplicate name {name}")
            seen.add(name)
            if not (name or "").startswith("pm_"):
                err(f"{path}: name {name!r} does not start with pm_")
            (plurals if el.tag == "plurals" else strings).add(name)

drawable_dir = os.path.join(app, "res", "drawable")
drawables = set()
if os.path.isdir(drawable_dir):
    for f in sorted(os.listdir(drawable_dir)):
        if not f.endswith(".xml"):
            err(f"res/drawable/{f}: only vector drawables (.xml) are allowed")
            continue
        drawables.add(f[:-4])

icons_file = os.path.join(app, "tools", "icons.txt")
listed = set()
if os.path.isfile(icons_file):
    listed = {l.strip() for l in read(icons_file).splitlines() if l.strip() and not l.startswith("#")}
else:
    err("tools/icons.txt is missing")
for extra in sorted(drawables - listed):
    err(f"res/drawable/{extra}.xml is not one of the icons of tools/icons.txt")
for missing in sorted(listed - drawables):
    err(f"tools/icons.txt lists {missing} but res/drawable/{missing}.xml does not exist")

# --- references from code and XML ---
used = {"string": set(), "plurals": set(), "drawable": set()}
literal = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
for dirpath, _, files in os.walk(os.path.join(app, "src")):
    for f in files:
        if not f.endswith((".kt", ".java")):
            continue
        path = os.path.join(dirpath, f)
        rel = os.path.relpath(path, app)
        in_ui = f"{os.sep}ui{os.sep}" in path
        for n, line in enumerate(read(path).splitlines(), 1):
            for kind in ("string", "plurals", "drawable"):
                for m in re.finditer(rf"\bR\.{kind}\.([A-Za-z0-9_]+)", line):
                    used[kind].add((m.group(1), rel, n))
            code = line.split("//")[0] if "i18n-ok" not in line else ""
            if in_ui and code.strip() and not code.lstrip().startswith(("*", "/*", "import ", "package ")):
                for m in literal.finditer(code):
                    s = m.group(1)
                    if re.search(r"[A-Za-z]", s) and (" " in s or s[:1].isupper()):
                        err(f"{rel}:{n}: user-visible text in code: {s!r} (use a string resource, or mark the line // i18n-ok)")

xml_refs = set()
for dirpath, _, files in os.walk(app):
    if os.sep + "tools" in dirpath or os.sep + "tests" in dirpath:
        continue
    for f in files:
        if f == "AndroidManifest.xml" or (f.endswith(".xml") and os.sep + "res" in dirpath):
            for m in re.finditer(r"@(string|plurals|drawable)/([A-Za-z0-9_]+)", read(os.path.join(dirpath, f))):
                xml_refs.add((m.group(1), m.group(2), os.path.relpath(os.path.join(dirpath, f), app)))

for name, rel, n in sorted(used["string"]):
    if name not in strings:
        err(f"{rel}:{n}: R.string.{name} is not defined in strings.xml")
for name, rel, n in sorted(used["plurals"]):
    if name not in plurals:
        err(f"{rel}:{n}: R.plurals.{name} is not defined in strings.xml")
for name, rel, n in sorted(used["drawable"]):
    if name not in drawables:
        err(f"{rel}:{n}: R.drawable.{name} does not exist")
for kind, name, rel in sorted(xml_refs):
    pool = {"string": strings, "plurals": plurals, "drawable": drawables}[kind]
    if name not in pool:
        err(f"{rel}: @{kind}/{name} does not exist")

if errors:
    print("\n".join(errors), file=sys.stderr)
    print(f"{len(errors)} resource problem(s)", file=sys.stderr)
    sys.exit(1)
print(f"resources ok: {len(strings)} strings, {len(plurals)} plurals, {len(drawables)} icons")
