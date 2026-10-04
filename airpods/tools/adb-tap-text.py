#!/usr/bin/env python3
"""Taps a screen element found in a fresh uiautomator dump, never in an old one.

  adb-tap-text.py --list                 print every node with text, description and bounds
  adb-tap-text.py --dump-to FILE         save the fresh dump (for check-page-conformance.py)
  adb-tap-text.py TEXT [--dry]           tap the centre of the node whose text or content description is TEXT
  adb-tap-text.py TEXT --at 0.5          tap at a fraction (0 to 1) along the width of the node (sliders)
  adb-tap-text.py TEXT --nth 2           when several nodes match, take the second (counted from 1)

It refuses to tap when the text matches more than one node and no --nth is given. Read the result back
from a new dump afterwards; Settings keeps showing the old state while the app is stopped.
"""
import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

REMOTE = "/sdcard/airpods-window.xml"


def adb(*args):
    return subprocess.run(["adb", *args], check=True, capture_output=True, text=True).stdout


def fresh_dump(local):
    adb("shell", "uiautomator", "dump", REMOTE)
    subprocess.run(["adb", "pull", "-q", REMOTE, local], check=True)
    out = []
    for n in ET.parse(local).getroot().iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
        if m:
            l, t, r, b = map(int, m.groups())
            out.append({"text": n.get("text") or "", "desc": n.get("content-desc") or "", "cls": n.get("class") or "",
                        "l": l, "t": t, "r": r, "b": b})
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("text", nargs="?")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--dump-to")
    ap.add_argument("--dry", action="store_true")
    ap.add_argument("--at", type=float, default=0.5)
    ap.add_argument("--nth", type=int)
    a = ap.parse_args()
    local = a.dump_to or "/tmp/airpods-window.xml"
    nodes = fresh_dump(local)
    if a.dump_to and not a.text and not a.list:
        print("dump saved to", local)
        return 0
    if a.list or not a.text:
        for n in nodes:
            if n["text"] or n["desc"]:
                print("%-24s %-40s [%d,%d][%d,%d]" % (n["cls"].split(".")[-1], (n["text"] or n["desc"])[:40], n["l"], n["t"], n["r"], n["b"]))
        return 0
    hits = [n for n in nodes if n["text"] == a.text or n["desc"] == a.text]
    if not hits:
        print("no node with that text on the screen", file=sys.stderr)
        return 1
    if a.nth:
        hits = hits[a.nth - 1:a.nth]
    if len(hits) != 1:
        print("%d nodes match, say which with --nth" % len(hits), file=sys.stderr)
        return 2
    n = hits[0]
    x = int(n["l"] + (n["r"] - n["l"]) * a.at)
    y = (n["t"] + n["b"]) // 2
    print("%s at (%d,%d) from [%d,%d][%d,%d]" % ("would tap" if a.dry else "tap", x, y, n["l"], n["t"], n["r"], n["b"]))
    if not a.dry:
        adb("shell", "input", "tap", str(x), str(y))
    return 0


if __name__ == "__main__":
    sys.exit(main())
