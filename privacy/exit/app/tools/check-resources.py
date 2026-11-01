#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Resource checks of the exit app (model: passwords/app/tools/check-resources.py).

    check-resources.py [APP_DIR]      exit status 0 when every rule holds, 1 with one line per broken rule

Rules: every R.string / R.drawable used in the Java or the manifest exists, and every string and drawable that is defined is
used; no colour is written into a layout, a drawable or the Java (colours come from the theme); a string that is a
notification text is at most 140 characters; a string with arguments uses numbered placeholders only; no string uses a name
the pages dropped (FORBIDDEN_WORDS).
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

NOTIFICATION_LIMIT = 140
# Names the pages dropped in ADR-209 (profiles Identified, Unlinked, Substituted; addresses Trackers only, All traffic via Tor,
# Direct; group Device identity): one vocabulary on every page, notification and warning.
FORBIDDEN_WORDS = ("Trusted", "Protected", "Isolated", "Extra private", "Standard", "Apps you have not set", "Your identity")
HEX_COLOR = re.compile(r'"#[0-9a-fA-F]{3,8}"')
JAVA_COLOR = re.compile(r"Color\.(parseColor|rgb|argb|valueOf)\(|0x[fF][fF][0-9a-fA-F]{6}\b")


GET_STRING = re.compile(r"getString\(\s*R\.string\.(\w+)")


def count_arguments(text, pos):
    """The arguments after the resource id of the getString call whose id ends at pos: the commas at depth one."""
    depth, commas, i = 1, 0, pos
    while i < len(text) and depth > 0:
        c = text[i]
        if c in "\"'":
            j = i + 1
            while j < len(text) and text[j] != c:
                j += 2 if text[j] == "\\" else 1
            i = j
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == "," and depth == 1:
            commas += 1
        i += 1
    return commas


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

    # A call that passes fewer arguments than the string has placeholders throws at run time (found in the review of
    # 2026-10-22: notif_promo_text asked for %2$s and got one argument, and the exception looked like a lost daemon).
    for path, text in src.items():
        for m in GET_STRING.finditer(text):
            name = m.group(1)
            if name not in strings:
                continue
            wanted = max([int(i) for i in re.findall(r"%(\d+)\$", strings[name])] or [0])
            given = count_arguments(text, m.end())
            if given != wanted:
                problems.append(f"{os.path.relpath(path, app)}: getString(R.string.{name}) passes {given} arguments and the "
                                f"string has {wanted} placeholders")

    # Every notification names an app, a company or a privacy setting. VISIBILITY_PRIVATE is the default of a notification and
    # hides nothing unless the user chose to hide sensitive content, so only SECRET keeps it off the lock screen (review of
    # 2026-10-23, C2-02: the "fix" of 2026-10-22 set the default).
    for path, text in src.items():
        builders = len(re.findall(r"new\s+Notification\.Builder\(", text))
        secret = len(re.findall(r"\.setVisibility\(Notification\.VISIBILITY_SECRET\)", text))
        if builders != secret:
            problems.append(f"{os.path.relpath(path, app)}: {builders} notification builders and {secret} VISIBILITY_SECRET; every notification is secret")
        if re.search(r"VISIBILITY_(PRIVATE|PUBLIC)", text):
            problems.append(f"{os.path.relpath(path, app)}: VISIBILITY_PRIVATE or VISIBILITY_PUBLIC is used; a notification is secret")

    for n, v in strings.items():
        if n.startswith("notif_") and len(v) > NOTIFICATION_LIMIT:
            problems.append(f"string {n} has {len(v)} characters; a notification text is at most {NOTIFICATION_LIMIT}")
        if re.search(r"%(?!\d+\$|%)", v):
            problems.append(f"string {n} has a placeholder without a number")
        for w in FORBIDDEN_WORDS:
            if re.search(r"\b" + re.escape(w) + r"\b", v):
                problems.append(f"string {n} uses the old word {w}")
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
