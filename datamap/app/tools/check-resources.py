#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Every R.string, R.plurals, R.array, R.color, R.drawable, R.raw and R.mipmap that the Java sources use must be
defined in res/, and every string must be used or listed in the manifest. A missing resource is a build error on
the server, so it is found here first.

usage: check-resources.py [APP_DIR]
"""
import pathlib, re, sys

app = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parent.parent)
res = app / "res"
src = "\n".join(p.read_text(encoding="utf-8") for p in (app / "src").rglob("*.java"))
manifest = (app / "AndroidManifest.xml").read_text(encoding="utf-8") if (app / "AndroidManifest.xml").exists() else ""

used = {}
for kind, name in re.findall(r"(?<!android\.)\bR\.(string|plurals|array|color|drawable|raw|mipmap)\.([A-Za-z0-9_]+)", src + manifest):
    used.setdefault(kind, set()).add(name)
for kind, name in re.findall(r"@(string|color|drawable|mipmap)/([A-Za-z0-9_]+)", manifest):
    used.setdefault(kind, set()).add(name)

defined = {k: set() for k in ("string", "plurals", "array", "color", "drawable", "raw", "mipmap")}
for f in res.rglob("*.xml"):
    text = f.read_text(encoding="utf-8")
    if f.parent.name.startswith("values"):
        for tag, kind in (("string", "string"), ("plurals", "plurals"), ("string-array", "array"), ("color", "color")):
            defined[kind] |= set(re.findall(rf'<{tag}\s+name="([A-Za-z0-9_]+)"', text))
    elif f.parent.name.startswith("drawable"):
        defined["drawable"].add(f.stem)
    elif f.parent.name.startswith("mipmap"):
        defined["mipmap"].add(f.stem)
for f in (res / "raw").glob("*") if (res / "raw").exists() else []:
    defined["raw"].add(f.stem)

bad = 0
for kind, names in sorted(used.items()):
    for n in sorted(names - defined[kind]):
        print(f"MISSING {kind}/{n}")
        bad = 1
unused = sorted(defined["string"] - used.get("string", set()))
for n in unused:
    print(f"UNUSED string/{n}")
    bad = 1
sys.exit(bad)
