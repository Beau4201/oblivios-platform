#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Static guards of the autofill chips in LatinIME (ADR-191). They read the sources and resources of the LatinIME java
directory (the one with AndroidManifest.xml, res and src) and fail on anything that would let the keyboard read what the
system draws for it, or that switches the feature off or widens it.

    check-guards.py JAVA_DIR        checks a tree
    check-guards.py --selftest      builds small trees that break each rule and requires every one to be caught

Rules:
  G1  no source names an API that reads the content or the surface of a suggestion, or captures the screen
  G2  the chip classes do not read the info of a suggestion, log, print or show a toast
  G3  the manifest holds no network, screen-capture or log permission
  G4  method.xml declares supportsInlineSuggestions and not supportsInlineSuggestionsWithTouchExploration
  G5  the strip layout has the chip row
  G6  the controller offers again after typing only when the editor is empty (ADR-207), and does so somewhere
"""
import os
import re
import sys
import tempfile

READERS = re.compile(r"InlineSuggestionInfo|getAutofillHints|getContentProvider|getSurfaceControl|getSurfacePackage|ScreenCapture|ScreenshotHardwareBuffer|captureLayers|SurfaceControl\b")
CHIP_ONLY = re.compile(r"\.getInfo\(\)|\bLog\.|System\.(out|err)|printStackTrace|Toast\b")
FORBIDDEN_PERMISSION = re.compile(r"android\.permission\.(INTERNET|READ_FRAME_BUFFER|CAPTURE_[A-Z_]+|ACCESS_SURFACE_FLINGER|READ_LOGS|DUMP)\b")


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def check(root):
    bad = []
    controller = False
    src = os.path.join(root, "src")
    for dirpath, _, names in os.walk(src):
        for n in sorted(names):
            if not n.endswith(".java"):
                continue
            path = os.path.join(dirpath, n)
            text = strip_comments(open(path, encoding="utf-8").read())
            for m in READERS.finditer(text):
                bad.append(f"G1 {os.path.relpath(path, root)}: {m.group(0)}")
            if n.startswith("AutofillChip"):
                for m in CHIP_ONLY.finditer(text):
                    bad.append(f"G2 {os.path.relpath(path, root)}: {m.group(0)}")
            if n == "AutofillChipController.java":
                controller = True
                calls = list(re.finditer(r"\.onFieldEmpty\(\)", text))
                if not calls:
                    bad.append(f"G6 {os.path.relpath(path, root)}: the offer never returns at an empty field")
                for m in calls:
                    if "isFieldEmpty()" not in text[max(0, m.start() - 120):m.start()]:
                        bad.append(f"G6 {os.path.relpath(path, root)}: the offer returns without asking whether the field is empty")
    if not controller:
        bad.append("G6 AutofillChipController.java is missing")
    manifest = open(os.path.join(root, "AndroidManifest.xml"), encoding="utf-8").read()
    for m in FORBIDDEN_PERMISSION.finditer(re.sub(r"<!--.*?-->", "", manifest, flags=re.S)):
        bad.append(f"G3 AndroidManifest.xml: {m.group(0)}")
    method = open(os.path.join(root, "res/xml/method.xml"), encoding="utf-8").read()
    if 'android:supportsInlineSuggestions="true"' not in method:
        bad.append("G4 res/xml/method.xml: supportsInlineSuggestions is not true")
    if "supportsInlineSuggestionsWithTouchExploration" in method:
        bad.append("G4 res/xml/method.xml: supportsInlineSuggestionsWithTouchExploration is set")
    strip = open(os.path.join(root, "res/layout/suggestions_strip.xml"), encoding="utf-8").read()
    if "@+id/autofill_chips" not in strip or "suggestions.AutofillChipRow" not in strip:
        bad.append("G5 res/layout/suggestions_strip.xml: the chip row is missing")
    return bad


GOOD = {
    "AndroidManifest.xml": '<manifest xmlns:android="x"><uses-permission android:name="android.permission.VIBRATE"/></manifest>',
    "res/xml/method.xml": '<input-method xmlns:android="x" android:supportsInlineSuggestions="true"/>',
    "res/layout/suggestions_strip.xml": '<merge><com.android.inputmethod.latin.suggestions.AutofillChipRow android:id="@+id/autofill_chips"/></merge>',
    "src/a/AutofillChipController.java": "class A { void f(Object s) { s.inflate(); if (mPolicy.typed() && mListener.isFieldEmpty()) { mPolicy.onFieldEmpty(); } } }",
    "src/a/Other.java": "// getAutofillHints in a comment is fine\nclass B { void g() { android.util.Log.d(\"t\", \"x\"); } }",
}


def selftest():
    def build(changes):
        d = tempfile.mkdtemp(prefix="chip-guards.")
        files = dict(GOOD)
        files.update(changes)
        for rel, text in files.items():
            p = os.path.join(d, rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "w", encoding="utf-8").write(text)
        return d

    cases = [
        ("a clean tree", {}, None),
        ("G1 reads the hints", {"src/a/Other.java": "class B { void g(Object i) { i.getAutofillHints(); } }"}, "G1"),
        ("G1 reads the surface", {"src/a/Other.java": "class B { void g(Object i) { i.getSurfaceControl(); } }"}, "G1"),
        ("G1 names the info class", {"src/a/Other.java": "import android.view.inputmethod.InlineSuggestionInfo;"}, "G1"),
        ("G1 captures the screen", {"src/a/Other.java": "class B { Object x = ScreenCapture.class; }"}, "G1"),
        ("G2 reads the info in the chip class", {"src/a/AutofillChipController.java": "class A { void f(Object s) { s.getInfo().isPinned(); } }"}, "G2"),
        ("G2 logs in the chip class", {"src/a/AutofillChipController.java": "class A { void f() { Log.d(\"t\", \"x\"); } }"}, "G2"),
        ("G2 shows a toast in the chip class", {"src/a/AutofillChipRow.java": "class A { void f() { Toast.makeText(); } }"}, "G2"),
        ("G3 network permission", {"AndroidManifest.xml": '<manifest><uses-permission android:name="android.permission.INTERNET"/></manifest>'}, "G3"),
        ("G3 frame buffer permission", {"AndroidManifest.xml": '<manifest><uses-permission android:name="android.permission.READ_FRAME_BUFFER"/></manifest>'}, "G3"),
        ("G4 inline suggestions off", {"res/xml/method.xml": '<input-method android:supportsInlineSuggestions="false"/>'}, "G4"),
        ("G4 touch exploration on", {"res/xml/method.xml": '<input-method android:supportsInlineSuggestions="true" android:supportsInlineSuggestionsWithTouchExploration="true"/>'}, "G4"),
        ("G5 no chip row", {"res/layout/suggestions_strip.xml": "<merge/>"}, "G5"),
        ("G6 offers again without asking the field", {"src/a/AutofillChipController.java": "class A { void f() { mPolicy.onFieldEmpty(); } }"}, "G6"),
        ("G6 never offers again", {"src/a/AutofillChipController.java": "class A { void f(Object s) { s.inflate(); } }"}, "G6"),
    ]
    failures = 0
    for name, changes, rule in cases:
        found = check(build(changes))
        ok = (not found) if rule is None else any(f.startswith(rule) for f in found)
        print(("caught:  " if rule else "passes:  ") + name if ok else "WRONG:   " + name + " " + str(found))
        failures += 0 if ok else 1
    return 1 if failures else 0


if __name__ == "__main__":
    if sys.argv[1:] == ["--selftest"]:
        sys.exit(selftest())
    findings = check(sys.argv[1])
    print("\n".join(findings) if findings else "chip guards: OK")
    sys.exit(1 if findings else 0)
