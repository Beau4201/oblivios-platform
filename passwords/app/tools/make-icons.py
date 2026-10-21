#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Turns the <symbol> icons of the design reference page into vector drawables.

The reference page (freeholdos-foundation/docs/design/password-manager-2026-10-20.html) holds the paths of the
Material Symbols Rounded icons (24 px, weight 400, Apache 2.0, github.com/google/material-design-icons) that the app
uses. Each `<symbol id="i-NAME" viewBox="0 -960 960 960"><path d="..."/></symbol>` becomes res/drawable/ic_NAME.xml (a
name that ends in `_f` is the filled variant and becomes ic_NAME_filled). The list of names is written to
tools/icons.txt, which check-resources.py holds the drawable folder to.

usage: make-icons.py REFERENCE_HTML [APP_DIR]
"""
import os
import re
import sys

ref = sys.argv[1]
app = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), ".."))
html = open(ref, encoding="utf-8").read()
symbols = re.findall(r'<symbol id="i-([a-z0-9_]+)" viewBox="0 -960 960 960"><path d="([^"]+)"\s*/></symbol>', html)
if not symbols:
    sys.exit("no icons found in the reference page")

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded "{name}", 24 px, weight 400 (Apache License 2.0), path data from the design reference. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path
            android:fillColor="#FF000000"
            android:pathData="{path}" />
    </group>
</vector>
"""

out_dir = os.path.join(app, "res", "drawable")
os.makedirs(out_dir, exist_ok=True)
names = []
for name, path in symbols:
    res = "ic_" + (name[:-2] + "_filled" if name.endswith("_f") else name)
    names.append(res)
    with open(os.path.join(out_dir, res + ".xml"), "w", encoding="utf-8") as f:
        f.write(TEMPLATE.format(name=name, path=path))
with open(os.path.join(app, "tools", "icons.txt"), "w", encoding="utf-8") as f:
    f.write("# Drawables of the app: Material Symbols Rounded from the design reference (make-icons.py). One name per line.\n")
    f.write("\n".join(sorted(names)) + "\n")
print(f"{len(names)} icons written")
