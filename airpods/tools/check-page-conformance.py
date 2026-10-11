#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compares a uiautomator dump of an AirPods page with the approved mock-up metrics (ADR-129, AIR-166).

The mock-up is drawn at 350.2 css px for a phone of the real width, so css values are scaled by
k = width_dp / 350.2. The page scrolls and the toolbar collapses, so the absolute y is not compared; the
check compares the height of each matched text, the left edge of left-aligned texts and the vertical
distance between consecutive matched texts. Texts are matched by their exact string in top to bottom
order. A pair of matched texts with another real text between them (a row added since the mock), or with a
mock text between them that the real page lacks (a row dropped since the mock), is not compared for spacing. Fonts and colours are not checked here (they come from the Settings components).
"""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET

MOCK_WIDTH_CSS = 350.2
# Position of these depends on the width of their own text, so only height and spacing are compared.
CENTRED = {"Left", "Case", "Right", "Done", "Settings", "Off", "Transparency", "Noise cancel", "Adaptive"}
SKIP = {"9:41"}


def read_dump(path, density):
    out = []
    width = 0.0
    for n in ET.parse(path).getroot().iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
        if not m:
            continue
        left, top, right, bottom = (int(v) / density for v in m.groups())
        width = max(width, right)
        text = (n.get("text") or "").strip()
        if text:
            out.append({"text": text, "x": left, "y": top, "h": bottom - top})
    return out, width


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dump", required=True)
    ap.add_argument("--metrics", required=True)
    ap.add_argument("--screen", required=True)
    ap.add_argument("--density", type=float, required=True, help="pixels per dp, for example 2.625")
    ap.add_argument("--require", type=int, default=6, help="at least this many texts must match")
    ap.add_argument("--tol-size", type=float, default=3.0, help="dp, for heights")
    ap.add_argument("--tol-pos", type=float, default=4.0, help="dp, for left edges and spacing")
    a = ap.parse_args()

    nodes, width_dp = read_dump(a.dump, a.density)
    k = width_dp / MOCK_WIDTH_CSS
    mock = sorted(
        (e for e in json.load(open(a.metrics))["elements"] if e["screen"] == a.screen and e["text"] and e["text"] not in SKIP),
        key=lambda e: (e["y"], e["x"]),
    )
    used = set()
    matched = []
    floor = -1e9
    for e in mock:
        for i, nd in enumerate(nodes):
            if i not in used and nd["text"] == e["text"] and nd["y"] >= floor - 1:
                used.add(i)
                floor = nd["y"]
                matched.append((e, nd))
                break

    matched_ids = {id(e) for e, _ in matched}
    bad = 0
    print("screen=%s width=%.1fdp scale=%.4f matched=%d of %d metric texts" % (a.screen, width_dp, k, len(matched), len(mock)))
    previous = None
    for e, nd in matched:
        dh = nd["h"] - e["h"] * k
        ok = abs(dh) <= a.tol_size
        bad += not ok
        print("%-5s height  %-34s real %6.1f mock %6.1f delta %+5.1f dp" % ("ok" if ok else "FAIL", e["text"][:34], nd["h"], e["h"] * k, dh))
        if e["text"] not in CENTRED:
            dx = nd["x"] - e["x"] * k
            ok = abs(dx) <= a.tol_pos
            bad += not ok
            print("%-5s left    %-34s real %6.1f mock %6.1f delta %+5.1f dp" % ("ok" if ok else "FAIL", e["text"][:34], nd["x"], e["x"] * k, dx))
        # A real text between two matched texts that the mock does not have (a row added since the mock), or a mock
        # text between them that the real page does not have (a row dropped since the mock), makes the distance
        # differ by design, so that pair is not compared.
        if previous is not None and not any(n is not nd and i not in used and previous[1]["y"] < n["y"] < nd["y"] for i, n in enumerate(nodes)) \
                and not any(id(m) not in matched_ids and previous[0]["y"] < m["y"] < e["y"] for m in mock):
            pe, pn = previous
            dy = (nd["y"] - pn["y"]) - (e["y"] - pe["y"]) * k
            ok = abs(dy) <= a.tol_pos
            bad += not ok
            print("%-5s spacing %-34s real %6.1f mock %6.1f delta %+5.1f dp" % ("ok" if ok else "FAIL", ("after " + pe["text"])[:34], nd["y"] - pn["y"], (e["y"] - pe["y"]) * k, dy))
        previous = (e, nd)
    if len(matched) < a.require:
        print("FAIL: only %d texts matched, %d required" % (len(matched), a.require))
        bad += 1
    print("violations: %d" % bad)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
