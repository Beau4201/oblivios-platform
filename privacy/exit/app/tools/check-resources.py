#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Resource checks of the exit app (model: passwords/app/tools/check-resources.py).

    check-resources.py [APP_DIR]      exit status 0 when every rule holds, 1 with one line per broken rule

Rules: every R.string / R.drawable used in the Java or the manifest exists, and every string and drawable that is defined is
used; no colour is written into a layout, a drawable or the Java (colours come from the theme); a string that is a
notification text is at most 140 characters; a string with arguments uses numbered placeholders only.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

NOTIFICATION_LIMIT = 140
HEX_COLOR = re.compile(r'"#[0-9a-fA-F]{3,8}"')
JAVA_COLOR = re.compile(r"Color\.(parseColor|rgb|argb|valueOf)\(|0x[fF][fF][0-9a-fA-F]{6}\b")


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def check(app):
    problems = []
    src = {}
    for root, _, files in os.walk(os.path.join(app, "src")):
        for f in files:
            if f.endswith(".java"):
                src[os.path.join(root, f)] = read(os.path.join(root, f))
    manifest = read(os.path.join(app, "AndroidManifest.xml"))
    res_xml = {}
    for root, _, files in os.walk(os.path.join(app, "res")):
        for f in files:
            if f.endswith(".xml"):
                res_xml[os.path.join(root, f)] = read(os.path.join(root, f))

    strings = {}
    for path, text in res_xml.items():
        if os.sep + "values" in path:
            for s in ET.fromstring(text).iter("string"):
                strings[s.get("name")] = "".join(s.itertext())
    drawables = {os.path.splitext(f)[0] for r, _, fs in os.walk(os.path.join(app, "res", "drawable")) for f in fs} \
        if os.path.isdir(os.path.join(app, "res", "drawable")) else set()

    used_s, used_d = set(), set()
    for text in list(src.values()) + [manifest] + [t for p, t in res_xml.items() if os.sep + "values" not in p]:
        used_s |= set(re.findall(r"R\.string\.(\w+)", text)) | set(re.findall(r"@string/(\w+)", text))
        used_d |= set(re.findall(r"R\.drawable\.(\w+)", text)) | set(re.findall(r"@drawable/(\w+)", text))
    for n in sorted(used_s - set(strings)):
        problems.append(f"string {n} is used and not defined")
    for n in sorted(set(strings) - used_s):
        problems.append(f"string {n} is defined and not used")
    for n in sorted(used_d - drawables):
        problems.append(f"drawable {n} is used and not defined")
    for n in sorted(drawables - used_d):
        problems.append(f"drawable {n} is defined and not used")

    for path, text in res_xml.items():
        if HEX_COLOR.search(text):
            problems.append(f"{os.path.relpath(path, app)}: a colour is written into the resource")
    for path, text in src.items():
        if JAVA_COLOR.search(text):
            problems.append(f"{os.path.relpath(path, app)}: a colour is written into the code")

    for n, v in strings.items():
        if n.startswith("notif_") and len(v) > NOTIFICATION_LIMIT:
            problems.append(f"string {n} has {len(v)} characters; a notification text is at most {NOTIFICATION_LIMIT}")
        if re.search(r"%(?!\d+\$|%)", v):
            problems.append(f"string {n} has a placeholder without a number")
    return problems


def main():
    app = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    problems = check(app)
    for p in problems:
        print(f"FAIL: {p}")
    if not problems:
        print("exit app resource checks passed")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
